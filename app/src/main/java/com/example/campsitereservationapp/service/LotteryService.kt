package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.Campsite
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Creates [Lottery] documents in Firestore using the signed-in owner's identity.
 *
 * The model is stored at `lotteries/{id}`. Ownership and current campsite capacity
 * are checked in the write transaction. This service does not draw winners,
 * schedule registration/booking windows, or update participant lists.
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
