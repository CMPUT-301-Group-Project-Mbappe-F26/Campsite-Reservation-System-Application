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
 * Creates, joins, and draws [Lottery] documents in Firestore.
 *
 * The model is stored at `lotteries/{id}`. Ownership and current campsite capacity
 * are checked in the creation transaction. [drawLottery] saves initial winners to
 * pending after registration closes. Entrants join through [joinWaitingList], which
 * rechecks the current lottery and campsite inside one transaction. [getCurrentLottery]
 * finds the lottery a campsite is running so its owner can see the waiting list.
 * This service does not schedule automatic draws or manage reservation actions.
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
     * determines ownership, capacity, and the availability used by [lotteryStayAvailabilityError]. An existing,
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
            val currentCampsite = site.toObject(Campsite::class.java)
                ?: error("This campsite is no longer available.")
            val capacityError = lotteryCapacityError(
                lottery.numberOfWinners, currentCampsite.maxOccupancy, lottery.useMaxCapacity
            )
            require(capacityError == null) { capacityError.orEmpty() }
            val availabilityError = lotteryStayAvailabilityError(
                lottery.stayStartDate, lottery.stayEndDate, currentCampsite
            )
            require(availabilityError == null) { availabilityError.orEmpty() }
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
     * Loads currently open lotteries and their published campsites for entrant browsing.
     *
     * Date filtering uses the same Alberta calendar convention as joining. The join
     * transaction remains authoritative because a registration can close or fill after
     * this list is loaded.
     */
    suspend fun getOpenLotteryListings(nowMillis: Long = System.currentTimeMillis()): List<LotteryListing> {
        val today = lotteryAlbertaDate(nowMillis)
        val lotteries = db.collection("lotteries").get().await().documents.mapNotNull { snapshot ->
            snapshot.toObject(Lottery::class.java)?.copy(id = snapshot.id)
        }.filter { lottery ->
            !lottery.drawCompleted && today >= lottery.registrationStartDate && today <= lottery.registrationEndDate
        }
        return lotteries.mapNotNull { lottery ->
            val campsite = db.collection("campsites").document(lottery.campsiteId).get().await()
                .toObject(Campsite::class.java)?.copy(id = lottery.campsiteId)
            campsite?.takeIf { it.published }?.let { LotteryListing(lottery, it) }
        }.sortedWith(compareBy({ it.lottery.registrationEndDate }, { it.campsite.name }))
    }

    /** Returns the open lottery shown on a campsite details screen. */
    suspend fun getOpenLotteryForCampsite(
        campsiteId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Lottery? {
        require(campsiteId.isNotBlank() && '/' !in campsiteId) { "Provide a valid campsite document ID." }
        val today = lotteryAlbertaDate(nowMillis)
        return db.collection("lotteries")
            .whereEqualTo("campsiteId", campsiteId)
            .get().await().documents
            .mapNotNull { snapshot -> snapshot.toObject(Lottery::class.java)?.copy(id = snapshot.id) }
            .filter { lottery ->
                !lottery.drawCompleted &&
                    today >= lottery.registrationStartDate &&
                    today <= lottery.registrationEndDate
            }
            .minByOrNull { it.registrationEndDate }
    }

    /**
     * Atomically registers the signed-in entrant for a lottery.
     *
     * The latest lottery and campsite are read in the transaction before checking the
     * Alberta registration date, waiting-list capacity, duplicate participant state,
     * and campsite occupancy. A successful write adds the UID once and stores its party
     * size. Concurrent attempts are retried by Firestore against the newest values.
     *
     * @param lotteryId Firestore lottery document ID.
     * @param groupSize Number of people in the entrant's party.
     * @return The committed local representation of the lottery.
     * @throws IllegalArgumentException When the ID or party size is invalid.
     * @throws IllegalStateException When joining is not currently allowed.
     */
    suspend fun joinWaitingList(lotteryId: String, groupSize: Int): Lottery {
        require(lotteryId.isNotBlank() && '/' !in lotteryId) { "Provide a valid lottery document ID." }
        val uid = auth.currentUser?.uid ?: error("Please sign in before joining a lottery.")
        val document = db.collection("lotteries").document(lotteryId)
        return db.runTransaction { transaction ->
            val snapshot = transaction.get(document)
            val lottery = snapshot.toObject(Lottery::class.java)?.copy(id = snapshot.id)
                ?: error("This lottery is no longer available.")
            require(lottery.campsiteId.isNotBlank() && '/' !in lottery.campsiteId) {
                "This lottery has no valid campsite."
            }
            val siteSnapshot = transaction.get(db.collection("campsites").document(lottery.campsiteId))
            val campsite = siteSnapshot.toObject(Campsite::class.java)
                ?: error("This campsite is no longer available.")
            check(siteSnapshot.exists() && campsite.published) { "This campsite is not available to entrants." }
            check(auth.currentUser?.uid == uid) { "Your account changed. Please try again." }
            val joinError = waitingListJoinError(
                lottery = lottery,
                campsite = campsite,
                entrantId = uid,
                groupSize = groupSize,
                nowMillis = System.currentTimeMillis()
            )
            check(joinError == null) { joinError.orEmpty() }
            val registered = lottery.registered + uid
            val groupSizes = lottery.registeredGroupSizes + (uid to groupSize)
            transaction.update(document, mapOf(
                "registered" to registered,
                "registeredGroupSizes" to groupSizes
            ))
            lottery.copy(registered = registered, registeredGroupSizes = groupSizes)
        }.await()
    }

    /** Removes the signed-in entrant while registration is still open. */
    suspend fun leaveWaitingList(lotteryId: String): Lottery {
        require(lotteryId.isNotBlank() && '/' !in lotteryId) { "Provide a valid lottery document ID." }
        val uid = auth.currentUser?.uid ?: error("Please sign in before leaving a lottery.")
        val document = db.collection("lotteries").document(lotteryId)
        return db.runTransaction { transaction ->
            val snapshot = transaction.get(document)
            val lottery = snapshot.toObject(Lottery::class.java)?.copy(id = snapshot.id)
                ?: error("This lottery is no longer available.")
            check(auth.currentUser?.uid == uid) { "Your account changed. Please try again." }
            val leaveError = waitingListLeaveError(lottery, uid, System.currentTimeMillis())
            check(leaveError == null) { leaveError.orEmpty() }
            val registered = lottery.registered.filterNot { it == uid }
            val groupSizes = lottery.registeredGroupSizes - uid
            transaction.update(document, mapOf(
                "registered" to registered,
                "registeredGroupSizes" to groupSizes
            ))
            lottery.copy(registered = registered, registeredGroupSizes = groupSizes)
        }.await()
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

    /**
     * Loads the lottery a campsite is currently running, if any.
     *
     * A lottery counts as current until its draw is completed, including before
     * registration opens and after it closes. Used to show the owner who has joined.
     *
     * @param campsiteId Firestore document ID of the campsite.
     * @return The current lottery with its document ID, or null if the campsite has none.
     * @see currentLottery
     */
    suspend fun getCurrentLottery(campsiteId: String): Lottery? {
        require(campsiteId.isNotBlank() && '/' !in campsiteId) { "Provide a valid campsite document ID." }
        val result = db.collection("lotteries")
            .whereEqualTo("campsiteId", campsiteId)
            .get().await()
        return currentLottery(result.documents.mapNotNull { doc ->
            doc.toObject(Lottery::class.java)?.copy(id = doc.id)
        })
    }
}

/** A lottery paired with the campsite information required by the entrant UI. */
data class LotteryListing(val lottery: Lottery, val campsite: Campsite)

/**
 * Returns a displayable reason an entrant cannot join, or null when joining is valid.
 *
 * Registration start and end dates are inclusive Alberta calendar dates. Participant
 * state checks include registered, pending, and booked users so a person cannot enter
 * the same lottery again after the draw has changed their state.
 */
internal fun waitingListJoinError(
    lottery: Lottery,
    campsite: Campsite,
    entrantId: String,
    groupSize: Int,
    nowMillis: Long
): String? {
    val maximum = campsite.maxOccupancy
    val today = lotteryAlbertaDate(nowMillis)
    val alreadyEntered = entrantId in lottery.registered || entrantId in lottery.pending ||
        lottery.booked.any { it.entrantId == entrantId }
    return when {
        entrantId.isBlank() -> "Please sign in before joining a lottery."
        lottery.ownerId == entrantId -> "Campsite owners cannot enter their own lottery."
        groupSize <= 0 -> "Party size must be at least 1."
        maximum == null || maximum <= 0 -> "This campsite does not have a valid maximum occupancy."
        groupSize > maximum -> "Party size cannot exceed this campsite's maximum occupancy of $maximum."
        lottery.waitingListCapacity <= 0 -> "This lottery's waiting list is unavailable."
        alreadyEntered -> "You are already on this lottery's waiting list."
        lottery.registered.distinct().size >= lottery.waitingListCapacity -> "This lottery's waiting list is full."
        lottery.drawCompleted -> "This lottery has already been drawn."
        today < lottery.registrationStartDate -> "Registration for this lottery has not opened yet."
        today > lottery.registrationEndDate -> "Registration for this lottery has closed."
        else -> null
    }
}

/** Returns a reason the entrant cannot leave, or null while leaving is allowed. */
internal fun waitingListLeaveError(
    lottery: Lottery,
    entrantId: String,
    nowMillis: Long
): String? {
    val today = lotteryAlbertaDate(nowMillis)
    return when {
        entrantId.isBlank() -> "Please sign in before leaving a lottery."
        entrantId !in lottery.registered -> "You are not on this lottery's waiting list."
        lottery.drawCompleted || today > lottery.registrationEndDate ->
            "Registration has closed. You can no longer leave the waiting list."
        today < lottery.registrationStartDate -> "Registration has not opened yet."
        else -> null
    }
}

/** Formats an instant as the Alberta calendar date used by lottery registration. */
internal fun lotteryAlbertaDate(nowMillis: Long): String {
    val historical = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("America/Edmonton")
    }.format(nowMillis)
    val zone = if (historical >= "2026-11-01") TimeZone.getTimeZone("GMT-06:00")
        else TimeZone.getTimeZone("America/Edmonton")
    return SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = zone }.format(nowMillis)
}

/**
 * Picks the lottery a campsite is currently running from all of its lotteries.
 *
 * Lotteries whose draw is completed are finished, as are those closed when an admin
 * removed the campsite. If more than one is still running, the one whose registration
 * starts first is returned.
 *
 * @param lotteries Every lottery stored for one campsite.
 * @return The current lottery, or null when every lottery is finished.
 */
internal fun currentLottery(lotteries: List<Lottery>): Lottery? =
    lotteries.filter { !it.drawCompleted }.minByOrNull { it.registrationStartDate }

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
 * 00:00 the following Alberta day. From November 1, 2026, Alberta uses permanent
 * UTC-6. That offset is explicit so older Android timezone databases do not apply
 * an obsolete winter fallback. Earlier dates retain historical Edmonton rules.
 * Calendar-day addition also handles historical daylight saving transitions.
 *
 * @param registrationEndDate Strict `yyyy-MM-dd` registration end date.
 * @return Midnight immediately after that date, as epoch milliseconds.
 * @throws IllegalArgumentException If the date is missing, malformed, or impossible.
 */
internal fun lotteryDrawOpensAt(registrationEndDate: String): Long {
    val zone = TimeZone.getTimeZone(
        if (registrationEndDate >= "2026-11-01") "GMT-06:00" else "America/Edmonton"
    )
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
    require(lottery.waitingListCapacity > 0) { "The waiting list must allow at least one entrant." }
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
