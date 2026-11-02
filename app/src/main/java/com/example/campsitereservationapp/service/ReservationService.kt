package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.Reservation
import com.example.campsitereservationapp.model.activeReservations
import com.example.campsitereservationapp.model.reservationDateToday
import com.example.campsitereservationapp.model.reservationError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ReservationService {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Fetches the signed-in entrant's active reservations, soonest first.
    suspend fun getMyActiveReservations(): List<Reservation> {
        val uid = auth.currentUser?.uid ?: throw Exception("Not signed in")
        val result = db.collection("reservations")
            .whereEqualTo("entrantId", uid) // Get this entrant's reservations
            .get().await()
        check(auth.currentUser?.uid == uid) { "Your account changed. Please try again." }
        val reservations = result.documents.mapNotNull { doc ->
            doc.toObject(Reservation::class.java)?.copy(id = doc.id) // Use the actual Firestore document ID.
        }
        return activeReservations(reservations, reservationDateToday())
    }

    // Fetches one reservation using its Firestore document ID.
    suspend fun getReservation(id: String): Reservation? {
        val doc = db.collection("reservations")
            .document(id)
            .get().await()
        return doc.toObject(Reservation::class.java)?.copy(id = doc.id)
    }

    // Fetches the lotteries where the signed-in entrant has won a spot they have not used yet.
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

    // Fetches one lottery using its Firestore document ID.
    suspend fun getLottery(id: String): Lottery? {
        val doc = db.collection("lotteries")
            .document(id)
            .get().await()
        return doc.toObject(Lottery::class.java)?.copy(id = doc.id)
    }

    /**
     * Books the dates and moves the signed-in entrant from the lottery's pending list to booked.
     *
     * A transaction re-reads the lottery so two winners cannot take the same night or
     * go over capacity, even when they confirm at the same time.
     *
     * @return A message explaining why the dates could not be reserved, or null on success.
     */
    suspend fun reserve(lotteryId: String, checkIn: String, checkOut: String, rulesAccepted: Boolean): String? {
        val uid = auth.currentUser?.uid ?: throw Exception("Not signed in")
        val lotteryDoc = db.collection("lotteries").document(lotteryId)
        val reservationDoc = db.collection("reservations").document()
        return db.runTransaction { transaction ->
            val lottery = transaction.get(lotteryDoc).toObject(Lottery::class.java)
                ?: return@runTransaction "This lottery is no longer available."
            val campsiteDoc = db.collection("campsites").document(lottery.campsiteId)
            val campsite = transaction.get(campsiteDoc).toObject(Campsite::class.java)
                ?: return@runTransaction "This campsite is no longer available."
            val error = reservationError(lottery, campsite, uid, checkIn, checkOut, rulesAccepted, reservationDateToday())
            if (error != null) return@runTransaction error
            val reservation = Reservation(
                id = reservationDoc.id, entrantId = uid, campsiteId = lottery.campsiteId, lotteryId = lotteryId,
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
}
