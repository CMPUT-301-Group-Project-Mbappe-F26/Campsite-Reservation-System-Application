package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Campsite
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class CampsiteService {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun addCampsite(campsite: Campsite): String {
        val uid = auth.currentUser?.uid ?: throw Exception("Not signed in")
        val doc = db.collection("campsites").document()
        val newCampsite = campsite.copy(
            id = doc.id,
            ownerId = uid
        )
        doc.set(newCampsite).await() // Saves campsite
        return doc.id
    }

    /*
    This method will get all campsites owned by the currently logged-in user
     */
    suspend fun getMyCampsites(): List<Campsite> {
        val uid = auth.currentUser?.uid ?: throw Exception("Not signed in")
        return db.collection("campsites")
            .whereEqualTo("ownerId", uid) // Get owner's campsites
            .get().await()
            .toObjects(Campsite::class.java)
    }

    /*
    This will get one specific campsite using its ID
     */
    suspend fun getCampsite(id: String): Campsite? {
        return db.collection("campsites")
            .document(id)
            .get().await()
            .toObject(Campsite::class.java)
    }
}
