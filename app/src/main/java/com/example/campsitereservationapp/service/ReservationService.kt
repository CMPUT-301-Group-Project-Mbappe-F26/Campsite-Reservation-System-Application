package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.Reservation
import com.example.campsitereservationapp.model.RESERVATION_CANCELLED
import com.example.campsitereservationapp.model.RESERVATION_REJECTED
import com.example.campsitereservationapp.model.activeReservations
import com.example.campsitereservationapp.model.cancelError
import com.example.campsitereservationapp.model.declineError
import com.example.campsitereservationapp.model.rejectError
import com.example.campsitereservationapp.model.reservationDateToday
import com.example.campsitereservationapp.model.reservationError
import com.example.campsitereservationapp.model.offeredCampsiteIds
import com.example.campsitereservationapp.model.LotteryReservationSelection
import com.example.campsitereservationapp.model.occupiedCampsiteNights
import com.example.campsitereservationapp.model.reservationCampsiteChoice
import com.example.campsitereservationapp.model.reservationCampsiteError
import com.example.campsitereservationapp.model.reservationNights
import com.example.campsitereservationapp.model.isNightOpen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Handles an entrant's reservations and lottery wins in Firestore (US 01.04.01,
 * US 01.04.03, US 01.06.01, US 01.06.02, US 01.07.01), and lets an owner load and reject
 * their campsite's reservations (US 02.07.01, US 02.07.02, US 02.07.03, US 02.08.01).
 *
 * Reservations are stored at `reservations/{id}`, and each is also copied into its
 * lottery's `booked` list. Booking, declining, cancelling and rejecting run in transactions
 * and use the rules in Reservation.kt, so the screens and the database always agree.
 *
 * Outstanding issues: declining, cancelling or rejecting does not yet trigger a redraw for
 * the next entrant (US 02.05.01) or send a notification (US 01.07.02).
 */
class ReservationService {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Loads all of the signed-in entrant's reservations, whatever their status or dates.
     *
     * Screens split the result with [activeReservations] and [com.example.campsitereservationapp.model.pastReservations], so one
     * read fills both lists.
     *
     * @return Every reservation the entrant has made, in no particular order.
     * @throws Exception If no user is signed in.
     */
    suspend fun getMyReservations(): List<Reservation> {
        val uid = auth.currentUser?.uid ?: throw Exception("Not signed in")
        val result = db.collection("reservations")
            .whereEqualTo("entrantId", uid) // Get this entrant's reservations
            .get().await()
        check(auth.currentUser?.uid == uid) { "Your account changed. Please try again." }
        return result.documents.mapNotNull { doc ->
            doc.toObject(Reservation::class.java)?.copy(id = doc.id) // Use the actual Firestore document ID.
        }
    }

    /**
     * Loads every reservation made at one campsite, for its owner (US 02.07.01 to US 02.07.03).
     *
     * Firestore rules only allow this for the campsite's owner. Screens split the result with
     * [activeReservations], [com.example.campsitereservationapp.model.pastReservations] and
     * [com.example.campsitereservationapp.model.cancelledReservations].
     *
     * @param campsiteId Firestore document ID of the campsite.
     * @return The campsite's reservations, in no particular order.
     */
    suspend fun getCampsiteReservations(campsiteId: String): List<Reservation> {
        val result = db.collection("reservations")
            .whereEqualTo("campsiteId", campsiteId)
            .get().await()
        return result.documents.mapNotNull { doc ->
            doc.toObject(Reservation::class.java)?.copy(id = doc.id)
        }
    }

    /**
     * Loads the signed-in entrant's active reservations.
     *
     * @return Confirmed stays that have not ended, soonest first (see [activeReservations]).
     * @throws Exception If no user is signed in.
     */
    suspend fun getMyActiveReservations(): List<Reservation> =
        activeReservations(getMyReservations(), reservationDateToday())

    /**
     * Loads one reservation.
     *
     * @param id Firestore document ID of the reservation.
     * @return The reservation, or null if it does not exist.
     */
    suspend fun getReservation(id: String): Reservation? {
        val doc = db.collection("reservations")
            .document(id)
            .get().await()
        return doc.toObject(Reservation::class.java)?.copy(id = doc.id)
    }

    /**
     * Loads the lotteries where the signed-in entrant has won a spot they have not used yet.
     *
     * @return Lotteries whose `pending` list contains the entrant.
     * @throws Exception If no user is signed in.
     */
    suspend fun getMyPendingLotteries(): List<Lottery> {
        val uid = auth.currentUser?.uid ?: throw Exception("Not signed in")
        val result = db.collection("lotteries")
            .whereArrayContains("pending", uid) // Get lotteries waiting on this entrant
            .get().await()
        check(auth.currentUser?.uid == uid) { "Your account changed. Please try again." }
        return result.documents.mapNotNull { doc ->
            doc.toObject(Lottery::class.java)?.copy(id = doc.id)
        }
    }

    /**
     * Loads one lottery.
     *
     * @param id Firestore document ID of the lottery.
     * @return The lottery, or null if it does not exist.
     */
    suspend fun getLottery(id: String): Lottery? {
        val doc = db.collection("lotteries")
            .document(id)
            .get().await()
        return doc.toObject(Lottery::class.java)?.copy(id = doc.id)
    }

    /**
     * Loads every campsite offered to this winner and the nights occupied across its lotteries.
     * Removed campsites remain visible as disabled choices. Uses readable lottery documents,
     * rather than requesting other entrants' private reservation documents.
     */
    suspend fun getReservationSelection(lotteryId: String): LotteryReservationSelection {
        require(lotteryId.isNotBlank() && '/' !in lotteryId) { "Provide a valid lottery ID." }
        val uid = auth.currentUser?.uid ?: error("Please sign in to reserve.")
        val lottery = getLottery(lotteryId) ?: error("This lottery is no longer available.")
        check(uid in lottery.pending) { "You do not have a spot to reserve in this lottery." }
        val today = reservationDateToday()
        val choices = coroutineScope {
            lottery.offeredCampsiteIds().distinct().map { id -> async {
                val site = db.collection("campsites").document(id).get().await()
                    .toObject(Campsite::class.java)?.copy(id = id)
                val related = LotteryService().getLotteriesForCampsite(id)
                val occupied = occupiedCampsiteNights(id, related.filterNot { it.id == lottery.id } + lottery)
                reservationCampsiteChoice(id, site, lottery, uid, occupied, today)
            } }.awaitAll()
        }
        check(auth.currentUser?.uid == uid) { "Your account changed. Please try again." }
        return LotteryReservationSelection(lottery, choices)
    }

    /**
     * Books the dates and moves the signed-in entrant from the lottery's pending list to booked.
     *
     * A transaction re-reads the lottery so two winners cannot take the same night or
     * go over capacity, even when they confirm at the same time.
     *
     * @param lotteryId Firestore document ID of the lottery the entrant won.
     * @param checkIn First night as yyyy-MM-dd.
     * @param checkOut Check-out day as yyyy-MM-dd.
     * @param rulesAccepted Whether the entrant agreed to the campsite rules.
     * @param campsiteId Winner's choice among the lottery's offerings; null selects the primary campsite.
     * @return A message explaining why the dates could not be reserved, or null on success.
     * @throws Exception If no user is signed in.
     */
    suspend fun reserve(lotteryId: String, checkIn: String, checkOut: String, rulesAccepted: Boolean, campsiteId: String? = null): String? {
        val uid = auth.currentUser?.uid ?: throw Exception("Not signed in")
        val selectedId = campsiteId ?: getLottery(lotteryId)?.campsiteId
            ?: return "This lottery is no longer available."
        if (selectedId.isBlank() || '/' in selectedId) return "Choose a campsite listed in this lottery."
        // Re-read these documents in the transaction: another winner booking or cancelling
        // in a related lottery then causes Firestore to retry against its latest state.
        val relatedIds = LotteryService().getLotteriesForCampsite(selectedId).map { it.id }.distinct()
        val lotteryDoc = db.collection("lotteries").document(lotteryId)
        val reservationDoc = db.collection("reservations").document()
        return db.runTransaction { transaction ->
            val lottery = transaction.get(lotteryDoc).toObject(Lottery::class.java)?.copy(id = lotteryId)
                ?: return@runTransaction "This lottery is no longer available."
            if (selectedId !in lottery.offeredCampsiteIds()) return@runTransaction "Choose a campsite listed in this lottery."
            val campsiteDoc = db.collection("campsites").document(selectedId)
            val campsite = transaction.get(campsiteDoc).toObject(Campsite::class.java)?.copy(id = selectedId)
                ?: return@runTransaction "This campsite is no longer available."
            val related = relatedIds.filterNot { it == lotteryId }.mapNotNull { id ->
                transaction.get(db.collection("lotteries").document(id)).toObject(Lottery::class.java)?.copy(id = id)
            } + lottery
            check(auth.currentUser?.uid == uid) { "Your account changed. Please try again." }
            reservationCampsiteError(lottery, campsite, uid)?.let { return@runTransaction it }
            val today = reservationDateToday()
            if (checkIn < today) return@runTransaction "Choose dates that have not passed."
            val error = reservationError(lottery, campsite, uid, checkIn, checkOut, rulesAccepted, today)
            if (error != null) return@runTransaction error
            val occupied = occupiedCampsiteNights(selectedId, related)
            if (reservationNights(checkIn, checkOut).any { !isNightOpen(it, lottery, campsite, occupied) })
                return@runTransaction "Some of these dates are not available. Please choose others."
            val reservation = Reservation(
                id = reservationDoc.id, entrantId = uid, campsiteId = selectedId, lotteryId = lotteryId,
                campsiteName = campsite.name, checkInDate = checkIn, checkOutDate = checkOut
            )
            transaction.set(reservationDoc, reservation)
            transaction.update(lotteryDoc, mapOf(
                "pending" to lottery.pending - uid,
                "booked" to lottery.booked + reservation
            ))
            null
        }.await()
    }

    /**
     * Gives up the signed-in entrant's spot by removing them from the lottery's pending list.
     * Once removed they can no longer reserve with that win. The redraw (US 02.05.01) should start here.
     *
     * @param lotteryId Firestore document ID of the lottery the entrant won.
     * @return A message explaining why the spot could not be declined, or null on success.
     * @throws Exception If no user is signed in.
     */
    suspend fun decline(lotteryId: String): String? {
        val uid = auth.currentUser?.uid ?: throw Exception("Not signed in")
        val lotteryDoc = db.collection("lotteries").document(lotteryId)
        return db.runTransaction { transaction ->
            val lottery = transaction.get(lotteryDoc).toObject(Lottery::class.java)
                ?: return@runTransaction "This lottery is no longer available."
            val error = declineError(lottery, uid)
            if (error != null) return@runTransaction error
            transaction.update(lotteryDoc, "pending", lottery.pending - uid)
            null
        }.await()
    }

    /**
     * Cancels one of the signed-in entrant's reservations (US 01.07.01).
     *
     * The reservation's status becomes Cancelled, and the entrant is added to the lottery's
     * `cancelledEntrantIds`, which frees their nights and their spot toward capacity (see
     * confirmedReservations). This is the same way profile deletion releases bookings, so
     * the lottery's `booked` list is never rewritten by an entrant.
     *
     * @param reservationId Firestore document ID of the reservation.
     * @return A message explaining why it could not be cancelled, or null on success.
     * @throws Exception If no user is signed in.
     */
    suspend fun cancel(reservationId: String): String? {
        val uid = auth.currentUser?.uid ?: throw Exception("Not signed in")
        val reservationDoc = db.collection("reservations").document(reservationId)
        return db.runTransaction { transaction ->
            val reservation = transaction.get(reservationDoc).toObject(Reservation::class.java)
                ?: return@runTransaction "This reservation no longer exists."
            val lotteryDoc = reservation.lotteryId.takeIf { it.isNotBlank() }
                ?.let { db.collection("lotteries").document(it) }
            val lottery = lotteryDoc?.let { transaction.get(it).toObject(Lottery::class.java) }
            val error = cancelError(reservation, uid, reservationDateToday())
            if (error != null) return@runTransaction error
            transaction.update(reservationDoc, "status", RESERVATION_CANCELLED)
            if (lotteryDoc != null && lottery != null && uid !in lottery.cancelledEntrantIds) {
                transaction.update(lotteryDoc, "cancelledEntrantIds", lottery.cancelledEntrantIds + uid)
            }
            null
        }.await()
        // US 01.07.02: send the cancellation notification here (on success) once it exists.
    }

    /**
     * Rejects a reservation at one of the signed-in owner's campsites (US 02.08.01).
     *
     * The reservation's status becomes Rejected, and the entrant is added to the lottery's
     * `cancelledEntrantIds`, which frees their nights and their spot in the same way as an
     * entrant's own cancellation.
     *
     * @param reservationId Firestore document ID of the reservation.
     * @return A message explaining why it could not be rejected, or null on success.
     * @throws Exception If no user is signed in.
     */
    suspend fun reject(reservationId: String): String? {
        val uid = auth.currentUser?.uid ?: throw Exception("Not signed in")
        val reservationDoc = db.collection("reservations").document(reservationId)
        return db.runTransaction { transaction ->
            val reservation = transaction.get(reservationDoc).toObject(Reservation::class.java)
                ?.copy(id = reservationId)
                ?: return@runTransaction "This reservation no longer exists."
            val campsite = transaction.get(db.collection("campsites").document(reservation.campsiteId))
                .toObject(Campsite::class.java)
                ?: return@runTransaction "This campsite is no longer available."
            val lotteryDoc = reservation.lotteryId.takeIf { it.isNotBlank() }
                ?.let { db.collection("lotteries").document(it) }
            val lottery = lotteryDoc?.let { transaction.get(it).toObject(Lottery::class.java) }
            val error = rejectError(reservation, campsite.copy(id = reservation.campsiteId), uid, reservationDateToday())
            if (error != null) return@runTransaction error
            transaction.update(reservationDoc, "status", RESERVATION_REJECTED)
            if (lotteryDoc != null && lottery != null && reservation.entrantId !in lottery.cancelledEntrantIds) {
                transaction.update(lotteryDoc, mapOf(
                    "cancelledEntrantIds" to lottery.cancelledEntrantIds + reservation.entrantId,
                    "rejectedReservationId" to reservationId
                ))
            }
            null
        }.await()
        // US 01.07.02: notify the entrant that their reservation was rejected (on success) once it exists.
    }
}
