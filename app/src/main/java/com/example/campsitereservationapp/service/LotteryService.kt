package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.Campsite
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Creates and draws [Lottery] documents in Firestore using the signed-in owner's identity.
 *
 * The model is stored at `lotteries/{id}`. Ownership and current campsite capacity
 * are checked in the creation transaction. [drawLottery] saves initial winners to
 * pending after registration closes. This service does not schedule automatic draws
 * or manage registration and reservation actions.
 */
class LotteryService {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Generates an ID for a lottery draft without writing a document.
     *
     * @return An auto-generated Firestore document ID to reuse across submission retries.
     */
    fun newLotteryId(): String = db.collection("lotteries").document().id

    /**
     * Validates and saves a lottery belonging to the signed-in campsite owner.
     *
     * Checks required fields and period ordering with [validateLottery], then reads
     * the campsite and lottery documents in a transaction. The stored campsite
     * determines ownership and the maximum used by [lotteryCapacityError]. An existing,
     * identical lottery is accepted as a successful retry; different data at the
     * same document ID is rejected. Firebase failures and cancellation propagate to the caller.
     *
     * @param lottery Draft with a stable document ID and the owner's Firebase UID.
     * @return The ID of the created document, or the identical document found on retry.
     * @throws IllegalArgumentException If the draft's required fields or dates are invalid.
     * @throws IllegalStateException If no user is signed in or the draft owner differs from that user.
     * @see newLotteryId
     */
    suspend fun createLottery(lottery: Lottery): String {
        validateLottery(lottery)
        val uid = auth.currentUser?.uid ?: error("Please sign in before creating a lottery.")
        check(lottery.ownerId == uid) { "This campsite does not belong to your account." }
        val campsite = db.collection("campsites").document(lottery.campsiteId)
        val document = db.collection("lotteries").document(lottery.id)
        db.runTransaction { transaction ->
            val site = transaction.get(campsite)
            val existing = transaction.get(document)
            check(site.exists()) { "This campsite is no longer available." }
            check(site.getString("ownerId") == uid && auth.currentUser?.uid == uid) {
                "This campsite does not belong to your account."
            }
            val capacityError = lotteryCapacityError(
                lottery.numberOfWinners, site.toObject(Campsite::class.java)?.maxOccupancy, lottery.useMaxCapacity
            )
            require(capacityError == null) { capacityError.orEmpty() }
            if (existing.exists()) {
                // A retry after a delayed response must not create a second lottery.
                check(existing.toObject(Lottery::class.java) == lottery) {
                    "This lottery was already created. Close the form before starting another."
                }
            } else {
                transaction.set(document, lottery)
            }
        }.await()
        return document.id
    }

    /**
     * Loads a lottery, draws its configured winner count, and saves pending winners atomically.
     *
     * The caller must be signed in as both the lottery and campsite owner, including
     * when using an anonymous guest account. Registration remains open through
     * 23:59 on its end date in `America/Edmonton`; an initial draw is allowed from
     * midnight the following day. This Android implementation uses the device clock.
     *
     * Reads the latest registration list inside a transaction and updates only
     * `pending` and `drawCompleted`, preserving all other fields. A completed draw
     * returns the current stored lottery without selecting again, even if pending
     * is empty or a previously selected entrant has since booked. Concurrent calls
     * are resolved by Firestore transaction retries.
     *
     * @param lotteryId Single Firestore document ID of the lottery to draw.
     * @return The lottery with committed pending winners, or its existing completed state.
     * @throws IllegalArgumentException If the ID is invalid or stored draw data is invalid.
     * @throws IllegalStateException If signed out, ownership fails, registration is open,
     * or an unmarked lottery already contains pending users or reservations.
     * Firebase failures and cancellation propagate to the caller. This function needs
     * network access and does not install a timer or trigger itself automatically.
     */
    suspend fun drawLottery(lotteryId: String): Lottery {
        require(lotteryId.isNotBlank() && '/' !in lotteryId) { "Provide a valid lottery document ID." }
        val uid = auth.currentUser?.uid ?: error("Please sign in before drawing a lottery.")
        val document = db.collection("lotteries").document(lotteryId)
        return db.runTransaction { transaction ->
            val snapshot = transaction.get(document)
            val lottery = snapshot.toObject(Lottery::class.java)?.copy(id = snapshot.id)
                ?: error("This lottery is no longer available.")
            check(lottery.ownerId == uid) { "This lottery does not belong to your account." }
            require(lottery.campsiteId.isNotBlank() && '/' !in lottery.campsiteId) {
                "This lottery has no valid campsite."
            }
            val site = transaction.get(db.collection("campsites").document(lottery.campsiteId))
            check(site.exists() && site.getString("ownerId") == uid && auth.currentUser?.uid == uid) {
                "This campsite does not belong to your account."
            }
            val drawn = prepareLotteryDraw(lottery, System.currentTimeMillis())
            if (!lottery.drawCompleted) {
                transaction.update(document, mapOf(
                    "pending" to drawn.pending,
                    "drawCompleted" to true
                ))
            }
            drawn
        }.await()
    }
}

/**
 * Prepares the initial draw from a stored lottery without performing database operations.
 *
 * Already completed draws are returned unchanged. Unmarked lotteries with pending
 * users or booked reservations are rejected to avoid overwriting older results. An empty entrant
 * list still produces a completed draw, so retries cannot draw a later registration.
 *
 * @param lottery Latest lottery snapshot read by the service transaction.
 * @param nowMillis Current epoch milliseconds, supplied explicitly for deadline tests.
 * @return A copy with selected pending winners and the completion marker, or the existing result.
 * @throws IllegalArgumentException If the winner count or registration end date is invalid.
 * @throws IllegalStateException If registration is still open or existing participants would be overwritten.
 */
internal fun prepareLotteryDraw(lottery: Lottery, nowMillis: Long): Lottery {
    if (lottery.drawCompleted) return lottery
    require(lottery.numberOfWinners > 0) { "The lottery must have a positive winner count." }
    check(lottery.pending.isEmpty() && lottery.booked.isEmpty()) {
        "This lottery already has pending users or reservations. Its draw status must be reviewed before drawing."
    }
    check(nowMillis >= lotteryDrawOpensAt(lottery.registrationEndDate)) {
        "Registration must close before drawing. Try after 23:59 Alberta time on the registration end date."
    }
    return lottery.copy(pending = lottery.draw(lottery.numberOfWinners), drawCompleted = true)
}

/**
 * Converts the first allowed draw time to epoch milliseconds using Alberta local time.
 *
 * The entire registration end date remains available to entrants. Draws start at
 * 00:00 the following day in `America/Edmonton`, respecting daylight saving changes.
 * Calendar-day addition is used because a local day can be 23 or 25 hours long.
 *
 * @param registrationEndDate Strict `yyyy-MM-dd` registration end date.
 * @return Midnight immediately after that date, as epoch milliseconds.
 * @throws IllegalArgumentException If the date is missing, malformed, or impossible.
 */
internal fun lotteryDrawOpensAt(registrationEndDate: String): Long {
    val zone = TimeZone.getTimeZone("America/Edmonton")
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        isLenient = false
        timeZone = zone
    }
    val date = runCatching { format.parse(registrationEndDate) }.getOrNull()
    require(registrationEndDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) &&
        date != null && format.format(date) == registrationEndDate) {
        "The lottery must have a valid registration end date."
    }
    return Calendar.getInstance(zone, Locale.ROOT).apply {
        time = date
        add(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis
}

/**
 * Describes a violation of the lottery's winner-capacity rule, without accessing Firebase.
 *
 * Manual entry must be positive and strictly below [maximum]. Selecting Max requires
 * an exact match. A missing or non-positive campsite maximum prevents creation.
 * The form uses its loaded maximum; [LotteryService.createLottery] checks again
 * against the current campsite document in its transaction.
 *
 * @param winners Proposed number of lottery winners.
 * @param maximum Campsite maximum occupancy; null means no maximum is configured.
 * @param useMaxCapacity Whether the owner selected the Max checkbox.
 * @return A message suitable for display, or null when capacity is valid.
 */
internal fun lotteryCapacityError(winners: Int, maximum: Int?, useMaxCapacity: Boolean): String? = when {
    maximum == null || maximum <= 0 -> "Set a positive maximum occupancy on the campsite before creating a lottery."
    winners <= 0 -> "Enter a positive whole number of winners."
    useMaxCapacity && winners != maximum -> "Max must match the campsite maximum ($maximum). Reopen the form if the campsite changed."
    !useMaxCapacity && winners >= maximum -> "Capacity must be strictly less than $maximum. Select Max to use the campsite maximum."
    else -> null
}

/**
 * Validates the identifiers, winner count, and date periods of a lottery draft.
 *
 * All six dates must be real, zero-padded `yyyy-MM-dd` dates. Registration and
 * booking may each start and end on the same day, but registration must end on
 * an earlier date than booking starts. Booking must end before the stay starts,
 * and the stay must span at least one night. No clock time or time zone is applied.
 *
 * This check performs no database operations. Ownership, campsite capacity, and
 * existing-reservation conflicts are not validated here.
 *
 * @param lottery Draft to validate; its constructor defaults are not valid for submission.
 * @throws IllegalArgumentException If any required value or period rule is violated.
 * @see lotteryCapacityError
 */
internal fun validateLottery(lottery: Lottery) {
    require(lottery.id.isNotBlank() && lottery.ownerId.isNotBlank() && lottery.campsiteId.isNotBlank()) {
        "The lottery must belong to a campsite and its owner."
    }
    require(lottery.numberOfWinners > 0) { "Enter a positive whole number of winners." }
    val dates = listOf(lottery.registrationStartDate, lottery.registrationEndDate,
        lottery.bookingStartDate, lottery.bookingEndDate, lottery.stayStartDate, lottery.stayEndDate)
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
    require(dates.all { date ->
        date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) &&
            runCatching { parser.parse(date)?.let { parser.format(it) == date } == true }.getOrDefault(false)
    }) { "Select all registration, booking, and stay dates." }
    require(lottery.registrationStartDate <= lottery.registrationEndDate) {
        "Registration must end on or after its start date."
    }
    require(lottery.bookingStartDate <= lottery.bookingEndDate) {
        "Booking must end on or after its start date."
    }
    require(lottery.stayStartDate < lottery.stayEndDate) {
        "The stay end date must be after its start date."
    }
    // Strictly separate calendar dates: sharing an endpoint is also an overlap.
    require(lottery.registrationEndDate < lottery.bookingStartDate) {
        "Booking must start on a later date than registration ends. The periods cannot share a date or overlap."
    }
    require(lottery.bookingEndDate < lottery.stayStartDate) {
        "Booking must end before the stay starts."
    }
}
