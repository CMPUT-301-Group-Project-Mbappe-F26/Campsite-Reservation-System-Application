package com.example.campsitereservationapp.service

import android.net.Uri
import com.example.campsitereservationapp.campsite.CampsiteForm
import com.example.campsitereservationapp.model.Campsite
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.ListenerRegistration
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Handles campsite data using Firebase.
 *
 * Provides functions for creating campsites, retrieving campsite
 * information, listening for updates, and checking open lotteries.
 * Admins can also load every campsite and remove one.
 *
 * Outstanding issues: removing a campsite leaves its photos in Firebase Storage and does
 * not cancel reservations already booked there.
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

    // Updates the owner's listing and keeps its ID, rating and publication status.
    suspend fun updateCampsite(id: String, original: CampsiteForm, form: CampsiteForm): Campsite {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        val doc = db.collection("campsites").document(id)
        val details = form.createCampsiteData()
        val current = doc.get(com.google.firebase.firestore.Source.SERVER).await().toObject(Campsite::class.java)
        check(current?.isOwnedBy(uid) == true) { "This campsite is unavailable or you no longer own it." }
        check(CampsiteForm.fromCampsite(current) == original) { "This campsite changed. Reopen the form before saving." }
        val urls = form.photos.map { photo ->
            if (photo in current.imageUrls) photo
            else {
                val uri = Uri.parse(photo)
                require(uri.scheme == "content" || uri.scheme == "file")
                // Give new uploads their own paths so existing photos stay intact until Save succeeds.
                val image = FirebaseStorage.getInstance().reference
                    .child("campsites/$uid/$id/photo-${UUID.randomUUID()}")
                image.putFile(uri).await()
                image.downloadUrl.await().toString()
            }
        }
        return db.runTransaction { transaction ->
            check(auth.currentUser?.uid == uid) { "Your account changed. Please try again." }
            val latest = transaction.get(doc).toObject(Campsite::class.java)
            check(latest?.isOwnedBy(uid) == true) { "This campsite is unavailable or you no longer own it." }
            check(CampsiteForm.fromCampsite(latest) == original) { "This campsite changed. Reopen the form before saving." }
            val updated = details.copy(
                id = id, ownerId = uid, imageUrls = urls, published = latest.published,
                averageRating = latest.averageRating, requireRulesAcceptance = latest.requireRulesAcceptance,
                availableDays = if (form.availableDays.sorted() == original.availableDays.sorted()) {
                    latest.availableDays
                } else details.availableDays
            )
            transaction.update(doc, mapOf(
                "name" to updated.name,
                "description" to updated.description,
                "location" to updated.location,
                "latitude" to updated.latitude,
                "longitude" to updated.longitude,
                "maxOccupancy" to updated.maxOccupancy,
                "pricePerNight" to updated.pricePerNight,
                "tags" to updated.tags,
                "amenities" to updated.amenities,
                "imageUrls" to updated.imageUrls,
                "availableDays" to updated.availableDays,
                "availableFrom" to updated.availableFrom,
                "availableUntil" to updated.availableUntil,
                "rules" to updated.rules,
                "cancellationPolicy" to updated.cancellationPolicy
            ))
            updated
        }.await()
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

    /**
     * Loads every registered campsite, published or not, for the Admin Dashboard.
     *
     * @return All campsites, each with its Firestore document ID.
     * @throws IllegalStateException If no user is signed in.
     */
    suspend fun getAllCampsites(): List<Campsite> {
        checkNotNull(auth.currentUser) { "Not signed in" }
        val result = db.collection("campsites").get().await()
        return result.documents.mapNotNull { doc ->
            doc.toObject(Campsite::class.java)?.copy(id = doc.id)
        }
    }

    /**
     * Removes a campsite as an admin, closing its active lotteries in the same write.
     *
     * The campsite document is deleted, so it disappears from Discover. Each active
     * lottery is closed and its waiting list cleared (see [closeLotteryForRemovedCampsite]).
     * Everything is saved in one batch, so either all of it happens or none of it does.
     *
     * @param campsiteId Firestore document ID of the campsite to remove.
     * @throws IllegalArgumentException If the ID is blank or invalid.
     * @throws IllegalStateException If the signed-in user is not an admin.
     */
    suspend fun removeCampsite(campsiteId: String) {
        require(campsiteId.isNotBlank() && '/' !in campsiteId) { "Provide a valid campsite document ID." }
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        val admin = db.collection("users").document(uid).get().await().toObject(User::class.java)
        check(admin?.isAdmin == true) { "Only admins can remove campsites." }
        deleteCampsite(campsiteId, uid, asAdmin = true)
    }

    // Removes a listing from the owner's edit screen.
    suspend fun deleteOwnedCampsite(campsiteId: String) {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        deleteCampsite(campsiteId, uid, asAdmin = false)
    }

    // Closes active lotteries and removes the listing in one transaction.
    private suspend fun deleteCampsite(campsiteId: String, uid: String, asAdmin: Boolean) {
        val campsite = db.collection("campsites").document(campsiteId)
        if (!asAdmin) {
            val current = campsite.get(com.google.firebase.firestore.Source.SERVER).await().toObject(Campsite::class.java)
            check(current?.isOwnedBy(uid) == true) { "You can only delete your own campsite." }
        }
        val lotteries = db.collection("lotteries")
            .whereEqualTo("campsiteId", campsiteId)
            .get(com.google.firebase.firestore.Source.SERVER).await()
        db.runTransaction { transaction ->
            check(auth.currentUser?.uid == uid) { "Your account changed. Please try again." }
            if (asAdmin) {
                val profile = transaction.get(db.collection("users").document(uid)).toObject(User::class.java)
                check(profile?.isAdmin == true) { "Only admins can remove campsites." }
            } else {
                val current = transaction.get(campsite).toObject(Campsite::class.java)
                check(current?.isOwnedBy(uid) == true) { "You can only delete your own campsite." }
            }
            // Read each lottery again so a concurrent draw cannot be overwritten.
            val active = lotteries.documents.mapNotNull { document ->
                val lottery = transaction.get(document.reference).toObject(Lottery::class.java)
                if (lottery != null && isLotteryActive(lottery)) document.reference to lottery else null
            }
            for ((reference, lottery) in active) {
                val closed = closeLotteryForRemovedCampsite(lottery)
                transaction.update(reference, mapOf(
                    "registered" to closed.registered,
                    "registeredGroupSizes" to emptyMap<String, Int>(),
                    "pending" to closed.pending,
                    "drawCompleted" to closed.drawCompleted
                ))
            }
            transaction.delete(campsite)
            Unit
        }.await()
    }
}

/**
 * Checks whether a lottery still has entrants waiting on it.
 *
 * A lottery is active until it is drawn, and stays active afterwards while selected
 * entrants are still deciding whether to reserve.
 *
 * @param lottery The lottery to check.
 * @return True if removing its campsite would affect entrants.
 */
internal fun isLotteryActive(lottery: Lottery): Boolean =
    !lottery.drawCompleted || lottery.pending.isNotEmpty()

/**
 * Closes a lottery whose campsite an admin removed, without accessing Firebase.
 *
 * The waiting list and pending winners are cleared and the lottery is marked as drawn,
 * so it can no longer be joined, drawn, or reserved from. Booked reservations are kept.
 *
 * @param lottery The lottery to close.
 * @return A closed copy of the lottery.
 */
internal fun closeLotteryForRemovedCampsite(lottery: Lottery): Lottery =
    lottery.copy(registered = emptyList(), pending = emptyList(), drawCompleted = true)


