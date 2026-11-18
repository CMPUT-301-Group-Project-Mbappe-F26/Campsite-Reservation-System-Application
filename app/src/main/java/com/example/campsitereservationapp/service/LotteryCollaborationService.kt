package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** A shared lottery and the current campsites needed to render its owner controls. */
data class LotteryCollaborationListing(val lottery: Lottery, val campsites: List<Campsite>)

/** Supported owner actions; every mutation checks the current actor, revision and draw state. */
enum class LotteryCollaborationAction { INVITE, ACCEPT, DECLINE, WITHDRAW }

/**
 * Persists owner consent in the lottery document. Invitations never enter campsiteIds;
 * accepting atomically adds the campsite and its ownership grant. Only an invited owner
 * can accept, and only a campsite's accepted owner can withdraw it.
 */
class LotteryCollaborationService {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /** Finds accepted memberships and pending invitations for one of the signed-in owner's campsites. */
    suspend fun getForCampsite(campsiteId: String): List<LotteryCollaborationListing> {
        val uid = auth.currentUser?.uid ?: error("Please sign in.")
        val site = db.collection("campsites").document(campsiteId).get().await().toObject(Campsite::class.java)
        check(site?.ownerId == uid) { "You can only manage your own campsite." }
        val invitations = db.collection("lotteries").whereEqualTo(FieldPath.of("campsiteInvitations", campsiteId), uid).get().await()
            .documents.mapNotNull { it.toObject(Lottery::class.java)?.copy(id = it.id) }
        return (LotteryService().getLotteriesForCampsite(campsiteId) + invitations).distinctBy { it.id }.map { lottery ->
            val campsites = (lottery.offeredCampsiteIds() + lottery.campsiteInvitations.keys).distinct().mapNotNull { id ->
                db.collection("campsites").document(id).get().await().toObject(Campsite::class.java)?.copy(id = id)
            }
            LotteryCollaborationListing(lottery, campsites)
        }.sortedBy { it.lottery.registrationStartDate }
    }

    /**
     * Applies an explicitly confirmed action to the version displayed to the owner.
     * A racing edit/draw causes a retry against fresh data or a stale-form error, never silent consent.
     * Accepted membership changes and entrant notices commit together.
     */
    suspend fun change(original: Lottery, campsiteId: String, action: LotteryCollaborationAction): Lottery {
        require(campsiteId.isNotBlank() && '/' !in campsiteId) { "Choose a valid campsite." }
        val uid = auth.currentUser?.uid ?: error("Please sign in.")
        val ref = db.collection("lotteries").document(original.id)
        val notice = db.collection("lotteryNotices").document("${original.id}_${original.listingRevision + 1}")
        return db.runTransaction { transaction ->
            val current = transaction.get(ref).toObject(Lottery::class.java)?.copy(id = original.id)
                ?: error("This lottery is no longer available.")
            check(current.listingRevision == original.listingRevision) { "This lottery changed. Review its current terms and try again." }
            check(auth.currentUser?.uid == uid) { "Your account changed. Please try again." }
            val sites = (current.offeredCampsiteIds() + campsiteId).distinct().associateWith { id ->
                transaction.get(db.collection("campsites").document(id)).toObject(Campsite::class.java)?.copy(id = id)
            }
            val target = sites[campsiteId]
            if (action == LotteryCollaborationAction.WITHDRAW) check(target?.ownerId == uid) {
                "You can only withdraw your own campsite."
            }
            val updated = when (action) {
                LotteryCollaborationAction.INVITE -> inviteLotteryCampsite(current, target ?: error("This campsite is unavailable."), uid)
                LotteryCollaborationAction.ACCEPT -> acceptLotteryCampsite(current, target ?: error("This campsite is unavailable."), uid)
                LotteryCollaborationAction.DECLINE -> declineLotteryCampsite(current, campsiteId, uid)
                LotteryCollaborationAction.WITHDRAW -> withdrawLotteryCampsite(current, campsiteId, uid,
                    current.offeredCampsiteIds().filterNot { it == campsiteId }.map { sites[it] ?: error("A remaining campsite is unavailable.") })
            }.copy(collaborationAction = action.name.lowercase(java.util.Locale.ROOT), collaborationCampsiteId = campsiteId)
            validateLottery(updated)
            if (action == LotteryCollaborationAction.INVITE || action == LotteryCollaborationAction.ACCEPT) {
                val error = lotteryListingError(updated, updated.offeredCampsiteIds().map { sites[it] ?: error("A campsite is unavailable.") })
                require(error == null) { error.orEmpty() }
            }
            val fields = mutableMapOf<String, Any>("listingRevision" to updated.listingRevision,
                "collaborationAction" to updated.collaborationAction, "collaborationCampsiteId" to campsiteId)
            when (action) {
                LotteryCollaborationAction.INVITE, LotteryCollaborationAction.DECLINE ->
                    fields["campsiteInvitations"] = updated.campsiteInvitations
                LotteryCollaborationAction.ACCEPT -> {
                    fields["campsiteIds"] = updated.campsiteIds
                    fields["campsiteOwners"] = updated.campsiteOwners
                    fields["campsiteInvitations"] = updated.campsiteInvitations
                    fields["useMaxCapacity"] = updated.useMaxCapacity
                }
                LotteryCollaborationAction.WITHDRAW -> {
                    fields["campsiteIds"] = updated.campsiteIds
                    fields["campsiteOwners"] = updated.campsiteOwners
                    fields["numberOfWinners"] = updated.numberOfWinners
                    fields["useMaxCapacity"] = updated.useMaxCapacity
                }
            }
            transaction.update(ref, fields)
            if (action == LotteryCollaborationAction.ACCEPT || action == LotteryCollaborationAction.WITHDRAW) {
                transaction.set(notice, mapOf("lotteryId" to current.id, "ownerId" to uid,
                    "recipientIds" to current.participantIds(), "kind" to "updated", "title" to "Lottery campsites updated",
                    "message" to "${target?.name ?: "A campsite"} was ${if (action == LotteryCollaborationAction.ACCEPT) "added to" else "withdrawn from"} your lottery. Review its available campsites before booking.",
                    "createdAt" to FieldValue.serverTimestamp()))
            }
            updated
        }.await()
    }
}
