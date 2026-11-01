package com.example.campsitereservationapp.service

import android.net.Uri
import com.example.campsitereservationapp.model.Campsite
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class CampsiteService {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Generates a campsite ID without saving a document.
    fun newCampsiteId(): String = db.collection("campsites").document().id

    // Uploads the photos and saves the campsite with its owner ID.
    suspend fun addCampsite(
        campsite: Campsite,
        photos: List<Uri> = emptyList(),
        registrationId: String = newCampsiteId()
    ): String {
        val uid = auth.currentUser?.uid ?: throw Exception("Not signed in")
        require(campsite.name.isNotBlank() && campsite.location.isNotBlank() && campsite.description.isNotBlank())
        require(campsite.latitude.isFinite() && campsite.latitude in -90.0..90.0)
        require(campsite.longitude.isFinite() && campsite.longitude in -180.0..180.0)
        require(campsite.maxOccupancy == null || campsite.maxOccupancy > 0)
        require(campsite.pricePerNight.isFinite() && campsite.pricePerNight >= 0)
        require(photos.size <= 10)
        val doc = db.collection("campsites").document(registrationId)
        val urls = if (photos.isEmpty()) campsite.imageUrls else photos.mapIndexed { index, uri ->
            val image = FirebaseStorage.getInstance().reference
                .child("campsites/$uid/${doc.id}/photo-$index")
            image.putFile(uri).await()
            image.downloadUrl.await().toString()
        }
        check(auth.currentUser?.uid == uid) { "Your account changed. Please try again." }
        // Reuse the draft ID on retry so a delayed response cannot create two listings.
        doc.set(campsite.copy(id = doc.id, ownerId = uid, imageUrls = urls)).await()
        return doc.id
    }

    // Fetches campsites belonging to the signed-in user.
    suspend fun getMyCampsites(): List<Campsite> {
        val uid = auth.currentUser?.uid ?: throw Exception("Not signed in")
        val result = db.collection("campsites")
            .whereEqualTo("ownerId", uid) // Get owner's campsites
            .get().await()
        check(auth.currentUser?.uid == uid) { "Your account changed. Please try again." }
        return result.documents.mapNotNull { doc ->
            doc.toObject(Campsite::class.java)?.copy(id = doc.id) // Use the actual Firestore document ID.
        }
    }

    // Fetches one campsite using its Firestore document ID.
    suspend fun getCampsite(id: String): Campsite? {
        val doc = db.collection("campsites")
            .document(id)
            .get().await()
        return doc.toObject(Campsite::class.java)?.copy(id = doc.id)
    }
}
