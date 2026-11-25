package com.example.campsitereservationapp.service

import android.util.Log
import com.example.campsitereservationapp.admin.AdminImage
import com.example.campsitereservationapp.admin.AdminImageSource
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.tasks.await

/** Outcome of an admin removing an image, shown to the admin afterwards. */
enum class ImageRemovalResult(val message: String) {
    REMOVED("Image removed."),
    REMOVED_FILE_KEPT("Image removed from the listing, but the file could not be deleted from storage."),
    ALREADY_GONE("This image was already removed.")
}

/*
 * Lets an admin remove an uploaded image (US 03.03.01).
 *
 * The image is first unlinked from its campsite or profile, so the app immediately shows the
 * placeholder (a landscape illustration for campsites, initials for profiles). The file is then
 * deleted from Firebase Storage. Firestore and Storage rules must allow admins to do both; see
 * docs/admin-images.rules.
 *
 * Outstanding issues: the campsite owner or profile owner is not notified that an image was removed.
 */
class AdminImageService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    /**
     * Removes an image from its campsite or profile and deletes the file from storage.
     *
     * @param image The image the admin confirmed removing.
     * @return Whether the image and its file were removed.
     * @throws IllegalStateException If the signed-in user is not an admin.
     */
    suspend fun remove(image: AdminImage): ImageRemovalResult {
        val uid = auth.currentUser?.uid
        check(!uid.isNullOrBlank()) { "Please sign in again." }
        val admin = db.collection("users").document(uid).get(Source.SERVER).await().toObject(User::class.java)
        check(admin?.isAdmin == true) { "Only admins can remove images." }

        val unlinked = when (image.source) {
            AdminImageSource.CAMPSITE -> unlinkCampsitePhoto(image)
            AdminImageSource.PROFILE -> unlinkProfilePhoto(image)
        }
        if (!unlinked) return ImageRemovalResult.ALREADY_GONE
        return if (deleteFile(image.url)) ImageRemovalResult.REMOVED else ImageRemovalResult.REMOVED_FILE_KEPT
    }

    // Removes the photo from the campsite's list, keeping the order of the others.
    private suspend fun unlinkCampsitePhoto(image: AdminImage): Boolean {
        val document = db.collection("campsites").document(image.ownerId)
        return db.runTransaction { transaction ->
            val campsite = transaction.get(document).toObject(Campsite::class.java)
            if (campsite == null || image.url !in campsite.imageUrls) return@runTransaction false
            transaction.update(document, "imageUrls", campsiteUrlsWithout(campsite.imageUrls, image.url))
            true
        }.await()
    }

    // Clears the profile photo so the profile falls back to its initials avatar.
    private suspend fun unlinkProfilePhoto(image: AdminImage): Boolean {
        val document = db.collection("users").document(image.ownerId)
        return db.runTransaction { transaction ->
            val user = transaction.get(document).toObject(User::class.java)
            if (user == null || user.profileImageUrl != image.url) return@runTransaction false
            transaction.update(document, mapOf("profileImageUrl" to "", "profileImagePath" to ""))
            true
        }.await()
    }

    // Deletes the stored file. A file that is already gone counts as deleted.
    private suspend fun deleteFile(url: String): Boolean = try {
        storage.getReferenceFromUrl(url).delete().await()
        true
    } catch (exception: StorageException) {
        if (exception.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) true
        else {
            Log.w("AdminImages", "Could not delete image file: ${exception.errorCode}")
            false
        }
    } catch (exception: IllegalArgumentException) {
        // The URL does not point at this app's storage bucket, so there is no file to delete.
        Log.w("AdminImages", "Image URL is not a Firebase Storage URL.")
        true
    }
}

/**
 * Returns a campsite's photo list with one photo removed, keeping the order of the rest.
 * The next photo becomes the cover when the cover is removed.
 *
 * @param urls The campsite's current photo URLs.
 * @param removed The URL being removed.
 */
fun campsiteUrlsWithout(urls: List<String>, removed: String): List<String> = urls.filter { it != removed }

/**
 * Turns a failed removal into a message for the admin.
 *
 * @param exception What went wrong.
 */
fun imageRemovalError(exception: Exception): String {
    if (exception is IllegalStateException && !exception.message.isNullOrBlank()) return exception.message!!
    if (exception is FirebaseFirestoreException) {
        when (exception.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                return "Firebase denied this removal. Check the admin image rules."
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                return "Could not reach Firebase. Check your connection and try again."
            else -> Unit
        }
    }
    return "Could not remove this image. Please try again."
}
