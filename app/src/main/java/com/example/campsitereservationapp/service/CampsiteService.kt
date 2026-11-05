package com.example.campsitereservationapp.service

import android.net.Uri
import com.example.campsitereservationapp.model.Campsite
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.ListenerRegistration
import com.example.campsitereservationapp.model.Lottery
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Handles campsite data using Firebase.
 *
 * Provides functions for creating campsites, retrieving campsite
 * information, listening for updates, and checking open lotteries.
 */
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
    /* Gets all published campsites from Firestore
     * return A list of published campsites
     */
    suspend fun getPublishedCampsites(): List<Campsite> {
        val result = db.collection("campsites")
            .whereEqualTo("published", true)
            .get()
            .await()
        // Convert into Campsite objects, Use DocID for firestore as CampsiteID
        return result.documents.mapNotNull { doc ->
            doc.toObject(Campsite::class.java)?.copy(id = doc.id)
        }
    }

    // Listens to changes in the published campsites
    fun listenToPublishedCampsites(
        onUpdate: (List<Campsite>) -> Unit,
        onError: () -> Unit
    ): ListenerRegistration {
        // Listen for changes to published campsites
        return db.collection("campsites")
            .whereEqualTo("published", true)
            .addSnapshotListener { snapshot, error ->
                // Checking for errors
                if (error != null) {
                    onError()
                } else {
                    // Convert documents into Campsite objects
                    val campsites = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(Campsite::class.java)?.copy(id = doc.id)
                    } ?: emptyList()
                    // Sends updated list to the Screen
                    onUpdate(campsites)
                }
            }
    }

    // Fetches one campsite using its Firestore document ID.
    suspend fun getCampsite(id: String): Campsite? {
        val doc = db.collection("campsites")
            .document(id)
            .get().await()
        return doc.toObject(Campsite::class.java)?.copy(id = doc.id)
    }

    // Saves the rules and policy entrants see for a campsite. Firestore rules limit this to its owner.
    suspend fun updateCampsitePolicy(id: String, rules: String) {
        if (auth.currentUser == null) throw Exception("Not signed in")
        db.collection("campsites").document(id).update("rules", rules.trim()).await()
    }
    suspend fun getOpenLottery(campsiteId: String): Lottery? {

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CANADA)
        dateFormat.timeZone = TimeZone.getTimeZone("America/Edmonton")
        val today = dateFormat.format(Date())

        val result = db.collection("lotteries")
            .whereEqualTo("campsiteId", campsiteId)
            .get()
            .await()

        for (document in result.documents) {
            val lottery = document.toObject(Lottery::class.java)

            if (lottery != null) {
                val hasStarted = lottery.registrationStartDate <= today
                val hasNotEnded = lottery.registrationEndDate >= today
                val notDrawn = !lottery.drawCompleted

                if (hasStarted && hasNotEnded && notDrawn) {
                    return lottery
                }
            }
        }

        return null
    }
}


