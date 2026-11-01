package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Reservation
import com.example.campsitereservationapp.model.activeReservations
import com.example.campsitereservationapp.model.reservationDateToday
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
}
