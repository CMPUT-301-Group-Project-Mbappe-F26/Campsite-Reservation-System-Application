package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.RESERVATION_CANCELLED
import com.example.campsitereservationapp.model.Reservation
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.model.activeReservations
import com.example.campsitereservationapp.model.formatReservationDate
import com.example.campsitereservationapp.model.offeredCampsiteIds
import com.example.campsitereservationapp.model.reservationDateToday
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

/*
 * Lets an admin remove a campsite owner who violates app policy (US 03.07.01).
 *
 * Every campsite the owner listed is removed the same way as US 03.01.01: the document is
 * deleted and its active lotteries are closed. Unlike removing a single campsite, upcoming
 * reservations there are cancelled, and each entrant is notified. The person keeps their
 * profile but is no longer a campsite owner; US 03.02.01 removes the whole profile.
 *
 * Outstanding issues: nothing stops the person registering as a campsite owner again, and
 * the removed campsites' photos stay in Firebase Storage.
 */

/** Firestore allows 500 writes in one transaction; leave room so a removal never half-fails. */
private const val MAX_REMOVAL_WRITES = 450

/**
 * What removing an owner changed, for the admin's success message.
 *
 * @property campsitesRemoved Number of the owner's campsites deleted.
 * @property reservationsCancelled Number of upcoming reservations cancelled at those campsites.
 */
data class OwnerRemovalResult(val campsitesRemoved: Int, val reservationsCancelled: Int)

/**
 * Marks every profile that has listed at least one campsite as a campsite owner.
 *
 * The app has no separate step for becoming an owner, so the saved `isOwner` flag is
 * usually false; listing a campsite is what makes someone an owner.
 *
 * @param users Profiles to check.
 * @param campsites Campsites whose owners should be marked.
 * @return The same profiles, with `isOwner` true for anyone who owns a campsite.
 */
fun withOwnerRoles(users: List<User>, campsites: List<Campsite>): List<User> {
    val ownerIds = campsites.map { it.ownerId }.filter { it.isNotBlank() }.toSet()
    return users.map { if (it.id in ownerIds && !it.isOwner) it.copy(isOwner = true) else it }
}

/**
 * Whether an admin may remove a profile as a campsite owner: it must be an owner, and never
 * the admin's own profile.
 *
 * @param adminId ID of the signed-in admin.
 * @param user The profile being viewed, with its role from [withOwnerRoles].
 */
fun adminCanRemoveOwner(adminId: String, user: User): Boolean =
    user.isOwner && adminCanRemoveProfile(adminId, user.id)

/**
 * Picks the reservations cancelled when an owner is removed: confirmed stays at the owner's
 * campsites that have not ended. Past stays stay as they are so they keep their history.
 *
 * @param reservations Reservations found at the owner's campsites.
 * @param campsiteIds IDs of the owner's campsites being removed.
 * @param today Today's date as yyyy-MM-dd.
 * @return The reservations to cancel, earliest check-in first.
 */
fun reservationsCancelledByOwnerRemoval(
    reservations: List<Reservation>,
    campsiteIds: Set<String>,
    today: String
): List<Reservation> = activeReservations(reservations.filter { it.campsiteId in campsiteIds }, today)

/**
 * Builds the notification an entrant gets when their reservation is cancelled because the
 * campsite's owner was removed.
 *
 * @param reservation The cancelled reservation.
 */
fun ownerRemovalNoticeMessage(reservation: Reservation): String =
    "${reservation.campsiteName.ifBlank { "Your campsite" }} · " +
        "${formatReservationDate(reservation.checkInDate)} to ${formatReservationDate(reservation.checkOutDate)} · " +
        "Cancelled because this campsite was removed for violating app policy."

/**
 * Builds the message shown to the admin after an owner is removed.
 *
 * @param name The owner's display name.
 * @param result What the removal changed.
 */
fun ownerRemovalSummary(name: String, result: OwnerRemovalResult): String {
    val campsites = plural(result.campsitesRemoved, "campsite")
    val reservations = plural(result.reservationsCancelled, "reservation")
    return "$name is no longer a campsite owner. $campsites removed, $reservations cancelled."
}

private fun plural(count: Int, noun: String) = "$count $noun${if (count == 1) "" else "s"}"

/**
 * Removes campsite owners as an admin (US 03.07.01). See the file header for what is changed.
 *
 * Needs the rules in docs/remove-owner.rules as well as the admin rules for campsites,
 * lotteries and reservations.
 */
class OwnerRemovalService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    /**
     * Removes all of an owner's campsites, cancels upcoming reservations there with a
     * notification to each entrant, and takes away the owner role, all in one transaction.
     *
     * @param ownerId ID of the owner's profile.
     * @return How many campsites were removed and reservations cancelled.
     * @throws IllegalStateException If the caller is not an admin, targets themselves, the
     * profile is gone or not an owner, or the owner has too many bookings to remove at once.
     */
    suspend fun removeOwner(ownerId: String): OwnerRemovalResult {
        val adminId = auth.currentUser?.uid
        check(!adminId.isNullOrBlank()) { "Please sign in again." }
        check(ownerId.isNotBlank() && '/' !in ownerId) { "This profile could not be found." }
        check(adminId != ownerId) { "You can't remove yourself as an owner." }
        val users = db.collection("users")
        val admin = users.document(adminId).get(Source.SERVER).await().toObject(User::class.java)
        check(admin?.isAdmin == true) { "Only admins can remove campsite owners." }
        val owner = users.document(ownerId).get(Source.SERVER).await().toObject(User::class.java)
            ?: error("This profile was already removed.")

        val campsites = db.collection("campsites")
        val lotteries = db.collection("lotteries")
        val reservations = db.collection("reservations")
        val campsiteRefs = campsites.whereEqualTo("ownerId", ownerId).get(Source.SERVER).await()
            .documents.map { it.reference }
        check(owner.isOwner || campsiteRefs.isNotEmpty()) { "This profile has no campsites to remove." }
        val reservationDocs = campsiteRefs.flatMap { site ->
            reservations.whereEqualTo("campsiteId", site.id).get(Source.SERVER).await().documents
        }
        val lotteryIds = campsiteRefs.flatMap { site ->
            lotteries.whereEqualTo("campsiteId", site.id).get(Source.SERVER).await().documents.map { it.id } +
                lotteries.whereArrayContains("campsiteIds", site.id).get(Source.SERVER).await().documents.map { it.id }
        } + reservationDocs.mapNotNull { it.getString("lotteryId") }
        val lotteryRefs = lotteryIds.filter { it.isNotBlank() && '/' !in it }.distinct().map { lotteries.document(it) }
        // Notification IDs are chosen up front so a retried transaction reuses them.
        val noticeRefs = reservationDocs.associate { it.id to db.collection("notifications").document() }
        val today = reservationDateToday()

        return db.runTransaction { transaction ->
            // Firestore needs every read before the first write.
            check(auth.currentUser?.uid == adminId) { "Your account changed. Please try again." }
            check(transaction.get(users.document(adminId)).toObject(User::class.java)?.isAdmin == true) {
                "Only admins can remove campsite owners."
            }
            val latestOwner = transaction.get(users.document(ownerId)).toObject(User::class.java)
                ?: error("This profile was already removed.")
            val sites = campsiteRefs.mapNotNull { ref ->
                transaction.get(ref).toObject(Campsite::class.java)?.takeIf { it.ownerId == ownerId }?.copy(id = ref.id)
            }
            check(latestOwner.isOwner || sites.isNotEmpty()) { "This profile has no campsites to remove." }
            val siteIds = sites.map { it.id }.toSet()
            val stays = reservationDocs.mapNotNull { doc ->
                transaction.get(doc.reference).toObject(Reservation::class.java)?.copy(id = doc.id)
            }
            val cancelled = reservationsCancelledByOwnerRemoval(stays, siteIds, today)
            val lotteryChanges = lotteryRefs.mapNotNull { ref ->
                val lottery = transaction.get(ref).toObject(Lottery::class.java) ?: return@mapNotNull null
                val changes = lotteryChangesForOwnerRemoval(lottery, siteIds, cancelled.filter { it.lotteryId == ref.id })
                if (changes.isEmpty()) null else ref to changes
            }
            val writes = sites.size + cancelled.size * 2 + lotteryChanges.size + 1
            check(writes <= MAX_REMOVAL_WRITES) {
                "This owner has too many campsites and bookings to remove at once. Remove some campsites first."
            }

            for ((ref, changes) in lotteryChanges) transaction.update(ref, changes)
            for (reservation in cancelled) {
                transaction.update(reservations.document(reservation.id), "status", RESERVATION_CANCELLED)
                transaction.set(noticeRefs.getValue(reservation.id), mapOf(
                    "senderId" to adminId,
                    "recipientIds" to listOf(reservation.entrantId),
                    "title" to "Reservation cancelled",
                    "message" to ownerRemovalNoticeMessage(reservation),
                    "campsiteId" to reservation.campsiteId,
                    "campsiteName" to reservation.campsiteName,
                    "lotteryId" to reservation.lotteryId,
                    "reservationId" to reservation.id,
                    "kind" to "owner_removed",
                    "createdAt" to FieldValue.serverTimestamp()
                ))
            }
            for (site in sites) transaction.delete(campsites.document(site.id))
            transaction.update(users.document(ownerId), "isOwner", false)
            OwnerRemovalResult(sites.size, cancelled.size)
        }.await()
    }
}

/**
 * Works out how one lottery changes when an owner's campsites are removed, without
 * accessing Firebase.
 *
 * An active lottery offering one of the campsites is closed as in US 03.01.01. Entrants whose
 * reservations were cancelled are added to `cancelledEntrantIds`, which frees their nights,
 * the same way an entrant's own cancellation does.
 *
 * @param lottery The lottery as currently saved.
 * @param removedCampsiteIds IDs of the campsites being removed.
 * @param cancelled Reservations from this lottery that are being cancelled.
 * @return The fields to update, or an empty map if the lottery is unaffected.
 */
internal fun lotteryChangesForOwnerRemoval(
    lottery: Lottery,
    removedCampsiteIds: Set<String>,
    cancelled: List<Reservation>
): Map<String, Any> {
    val changes = mutableMapOf<String, Any>()
    val removedHere = lottery.offeredCampsiteIds().firstOrNull { it in removedCampsiteIds }
    if (removedHere != null && isLotteryActive(lottery)) {
        val closed = closeLotteryForRemovedCampsite(lottery)
        changes["registered"] = closed.registered
        changes["registeredGroupSizes"] = emptyMap<String, Int>()
        changes["campsiteInvitations"] = emptyMap<String, String>()
        changes["pending"] = closed.pending
        changes["closedCampsiteId"] = removedHere
        changes["drawCompleted"] = closed.drawCompleted
    }
    val released = cancelled.map { it.entrantId }.filter { it.isNotBlank() && it !in lottery.cancelledEntrantIds }.distinct()
    if (released.isNotEmpty()) changes["cancelledEntrantIds"] = lottery.cancelledEntrantIds + released
    return changes
}
