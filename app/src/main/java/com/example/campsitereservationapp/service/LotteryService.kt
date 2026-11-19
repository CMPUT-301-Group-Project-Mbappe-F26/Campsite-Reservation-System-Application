package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.offeredCampsiteIds
import com.example.campsitereservationapp.model.MAX_LOTTERY_CAMPSITES
import com.example.campsitereservationapp.model.campsiteOwnerId
import com.example.campsitereservationapp.model.includesAcceptedCampsite
import com.example.campsitereservationapp.model.participatingOwnerIds
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

    /** Includes legacy documents and lotteries where this campsite is an additional offering. */
    suspend fun getLotteriesForCampsite(campsiteId: String): List<Lottery> {
        require(campsiteId.isNotBlank() && '/' !in campsiteId) { "Provide a valid campsite document ID." }
        val primary = db.collection("lotteries").whereEqualTo("campsiteId", campsiteId).get().await()
        val shared = db.collection("lotteries").whereArrayContains("campsiteIds", campsiteId).get().await()
        return (primary.documents + shared.documents).distinctBy { it.id }.mapNotNull {
            it.toObject(Lottery::class.java)?.copy(id = it.id)
        }
    }

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
        val document = db.collection("lotteries").document(lottery.id)
        db.runTransaction { transaction ->
            val existing = transaction.get(document)
            val campsites = lottery.offeredCampsiteIds().map { id ->
                transaction.get(db.collection("campsites").document(id)).toObject(Campsite::class.java)
                    ?.copy(id = id) ?: error("This campsite is no longer available.")
            }
            check(auth.currentUser?.uid == uid) { "Your account changed. Please try again." }
            check(campsites.all { it.ownerId == uid } && lottery.campsiteInvitations.isEmpty()) {
                "Create the lottery with your own campsites, then invite other owners."
            }
            val listingError = lotteryListingError(lottery, campsites)
            require(listingError == null) { listingError.orEmpty() }
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

    /** Updates only listing fields and atomically records a durable notice for every entrant. */
    suspend fun updateLottery(original: Lottery, draft: Lottery) {
        validateLottery(draft)
        require(original.id == draft.id && original.ownerId == draft.ownerId) { "The lottery identity cannot change." }
        val uid = auth.currentUser?.uid ?: error("Please sign in.")
        val document = db.collection("lotteries").document(original.id)
        val notice = db.collection("lotteryNotices").document("${original.id}_${original.listingRevision + 1}")
        db.runTransaction { transaction ->
            val current = transaction.get(document).toObject(Lottery::class.java)?.copy(id = original.id)
                ?: error("This lottery is no longer available.")
            check(current.ownerId == uid && auth.currentUser?.uid == uid) { "You can only update your own lottery." }
            check(current.listingRevision == original.listingRevision) { "This lottery changed. Reopen the form before saving." }
            val sites = (current.offeredCampsiteIds() + draft.offeredCampsiteIds()).distinct().associateWith { id ->
                transaction.get(db.collection("campsites").document(id)).toObject(Campsite::class.java)
                    ?.copy(id = id) ?: error("A campsite is no longer available.")
            }
            val changedIds = (current.offeredCampsiteIds() - draft.offeredCampsiteIds().toSet()) +
                (draft.offeredCampsiteIds() - current.offeredCampsiteIds().toSet())
            check(changedIds.all { sites.getValue(it).ownerId == uid }) { "You can only manage your own campsites." }
            val updated = updatedLotteryListing(current, draft)
            val error = lotteryListingError(updated, updated.offeredCampsiteIds().map { sites.getValue(it) })
            require(error == null) { error.orEmpty() }
            transaction.update(document, mapOf(
                "campsiteId" to updated.campsiteId, "campsiteIds" to updated.campsiteIds,
                "numberOfWinners" to updated.numberOfWinners, "useMaxCapacity" to updated.useMaxCapacity,
                "waitingListCapacity" to updated.waitingListCapacity,
                "registrationStartDate" to updated.registrationStartDate, "registrationEndDate" to updated.registrationEndDate,
                "bookingStartDate" to updated.bookingStartDate, "bookingEndDate" to updated.bookingEndDate,
                "stayStartDate" to updated.stayStartDate, "stayEndDate" to updated.stayEndDate,
                "listingRevision" to updated.listingRevision,
                "campsiteOwners" to updated.campsiteOwners, "campsiteInvitations" to updated.campsiteInvitations
            ))
            transaction.set(notice, mapOf(
                "lotteryId" to original.id, "ownerId" to uid, "recipientIds" to current.participantIds(),
                "kind" to "updated",
                "title" to "Lottery updated",
                "message" to "${updated.offeredCampsiteIds().joinToString(", ") { sites.getValue(it).name }}: " +
                    "registration ${updated.registrationStartDate} to ${updated.registrationEndDate}; " +
                    "booking ${updated.bookingStartDate} to ${updated.bookingEndDate}; " +
                    "stay ${updated.stayStartDate} to ${updated.stayEndDate}.",
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            ))
        }.await()
    }

    /** Deletes an undrawn listing and keeps its cancellation notice independently of that document. */
    suspend fun deleteLottery(original: Lottery) {
        val uid = auth.currentUser?.uid ?: error("Please sign in.")
        val document = db.collection("lotteries").document(original.id)
        val notice = db.collection("lotteryNotices").document("${original.id}_deleted")
        db.runTransaction { transaction ->
            val current = transaction.get(document).toObject(Lottery::class.java)?.copy(id = original.id)
                ?: error("This lottery is no longer available.")
            check(current.ownerId == uid && auth.currentUser?.uid == uid) { "You can only delete your own lottery." }
            check(current.participatingOwnerIds().size == 1) { "Other owners must withdraw their campsites before this shared lottery can be deleted." }
            check(current.listingRevision == original.listingRevision) { "This lottery changed. Reopen the form before deleting." }
            val siteNames = current.offeredCampsiteIds().map { id ->
                val site = transaction.get(db.collection("campsites").document(id)).toObject(Campsite::class.java)
                check(site?.ownerId == uid) { "You can only manage your own campsites." }
                site!!.name
            }
            requireLotteryEditable(current)
            transaction.set(notice, mapOf(
                "lotteryId" to original.id, "ownerId" to uid, "recipientIds" to current.participantIds(),
                "kind" to "deleted",
                "title" to "Lottery deleted", "message" to "The owner deleted the lottery for ${siteNames.joinToString(", ")}. Your entry is no longer active.",
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            ))
            transaction.delete(document)
        }.await()
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
            val sites = lottery.offeredCampsiteIds().mapNotNull { id ->
                db.collection("campsites").document(id).get().await()
                    .toObject(Campsite::class.java)?.copy(id = id)?.takeIf { it.published }
            }
            sites.firstOrNull()?.let { LotteryListing(lottery, it, sites) }
        }.sortedWith(compareBy({ it.lottery.registrationEndDate }, { it.campsite.name }))
    }

    /** Returns the open lottery shown on a campsite details screen. */
    suspend fun getOpenLotteryForCampsite(
        campsiteId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Lottery? {
        require(campsiteId.isNotBlank() && '/' !in campsiteId) { "Provide a valid campsite document ID." }
        val today = lotteryAlbertaDate(nowMillis)
        return getLotteriesForCampsite(campsiteId)
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
            val sites = lottery.offeredCampsiteIds().map { id ->
                transaction.get(db.collection("campsites").document(id)).toObject(Campsite::class.java)
                    ?.copy(id = id) ?: error("This campsite is no longer available.")
            }
            val campsite = sites.filter { it.published && lottery.includesAcceptedCampsite(it) }.maxByOrNull { it.maxOccupancy ?: 0 }
                ?: error("This campsite is not available to entrants.")
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
     * The caller must be signed in as the lottery coordinator; each campsite must
     * still match its accepted owner's grant. Anonymous owners are supported, including
     * when using a guest account. Registration remains open through
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
            for (id in lottery.offeredCampsiteIds()) {
                val site = transaction.get(db.collection("campsites").document(id))
                check(site.exists() && site.getString("ownerId") == lottery.campsiteOwnerId(id) && auth.currentUser?.uid == uid) {
                    "A listed campsite changed owner."
                }
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
        return currentLottery(getLotteriesForCampsite(campsiteId))
    }
}

/** A lottery paired with the campsite information required by the entrant UI. */
data class LotteryListing(val lottery: Lottery, val campsite: Campsite, val campsites: List<Campsite> = listOf(campsite))

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
        entrantId in lottery.participatingOwnerIds() -> "Campsite owners cannot enter their own lottery."
        groupSize <= 0 -> "Party size must be at least 1."
        maximum == null || maximum <= 0 -> "This campsite does not have a valid maximum occupancy."
        groupSize > maximum -> "Party size cannot exceed this campsite's maximum occupancy of $maximum."
        lottery.waitingListCapacity < 0 -> "This lottery's waiting list is unavailable."
        alreadyEntered -> "You are already on this lottery's waiting list."
        lottery.waitingListCapacity > 0 && lottery.registered.distinct().size >= lottery.waitingListCapacity -> "This lottery's waiting list is full."
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
    val ids = lottery.offeredCampsiteIds()
    require(ids.size <= MAX_LOTTERY_CAMPSITES) { "A lottery can include at most $MAX_LOTTERY_CAMPSITES campsites." }
    require(ids.all { it.isNotBlank() && '/' !in it } && lottery.campsiteId in ids) { "Select valid campsites."
    }
    require(ids.distinct().size == ids.size) { "Duplicate campsites are not allowed." }
    val waitlistError = lotteryWaitlistCapacityError(lottery.waitingListCapacity, lottery.numberOfWinners)
    require(waitlistError == null) { waitlistError.orEmpty() }
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
