package com.example.campsitereservationapp.service

import android.util.Log
import com.example.campsitereservationapp.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

internal const val MAX_PROFILE_IMAGE_BYTES = 5 * 1024 * 1024

/** Saves profile images without overwriting other profile fields or the previous image on failure. */
class ProfileImageService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    /** Uploads a prepared JPEG, or removes the image when [jpeg] is null. */
    suspend fun save(userId: String, jpeg: ByteArray?): User {
        check(auth.currentUser?.uid == userId && userId.isNotBlank()) { "Please sign in again." }
        require(jpeg == null || jpeg.size in 1..MAX_PROFILE_IMAGE_BYTES) { "Choose a smaller image." }
        val document = db.collection("users").document(userId)
        val original = document.get(Source.SERVER).await().toObject(User::class.java)
            ?: error("Your profile is unavailable. Please sign in again.")
        val path = if (jpeg == null) "" else "profile-images/$userId/${UUID.randomUUID()}.jpg"
        var saved = false
        try {
            val url = if (jpeg == null) "" else {
                val image = storage.reference.child(path)
                val metadata = StorageMetadata.Builder()
                    .setContentType("image/jpeg")
                    .setCustomMetadata("ownerId", userId)
                    .setCustomMetadata("imageType", "profile")
                    .build()
                val upload = image.putBytes(jpeg, metadata)
                try {
                    upload.await()
                } catch (exception: Exception) {
                    upload.cancel()
                    throw exception
                }
                image.downloadUrl.await().toString()
            }
            val updated = db.runTransaction { transaction ->
                check(auth.currentUser?.uid == userId) { "Your account changed. Please try again." }
                val latest = transaction.get(document).toObject(User::class.java)
                    ?: error("Your profile is unavailable. Please sign in again.")
                check(latest.profileImagePath == original.profileImagePath &&
                    latest.profileImageUrl == original.profileImageUrl) {
                    "Your profile image changed on another device. Please try saving again."
                }
                transaction.update(document, mapOf(
                    "profileImageUrl" to url,
                    "profileImagePath" to path
                ))
                latest.copy(id = userId, profileImageUrl = url, profileImagePath = path)
            }.await()
            saved = true
            deleteUnusedImage(userId, original.profileImagePath)
            return updated
        } finally {
            // A transaction can succeed on the server even if its response is interrupted.
            // Cleanup checks the server before deleting, so it never deletes the saved photo.
            if (!saved && path.isNotBlank()) deleteUnusedImage(userId, path)
        }
    }

    /** Best-effort cleanup after replacement, removal, a failed upload, or profile deletion. */
    internal suspend fun deleteUnusedImage(userId: String, path: String) {
        if (!isOwnedProfileImagePath(userId, path)) return
        withContext(NonCancellable) {
            withTimeoutOrNull(10_000) {
                try {
                    if (auth.currentUser?.uid != userId) return@withTimeoutOrNull
                    val latest = db.collection("users").document(userId).get(Source.SERVER).await()
                    if (latest.getString("profileImagePath") != path) {
                        storage.reference.child(path).delete().await()
                    }
                } catch (_: Exception) {
                    // Cleanup must not turn a successfully saved image into a failed save.
                    Log.w("ProfileImage", "Unused image cleanup could not be completed.")
                }
            }
        }
    }
}

/** Only files in this user's profile folder may be cleaned up by profile actions. */
internal fun isOwnedProfileImagePath(userId: String, path: String): Boolean {
    if (userId.isBlank() || '/' in userId) return false
    val prefix = "profile-images/$userId/"
    if (!path.startsWith(prefix)) return false
    val filename = path.removePrefix(prefix)
    return filename.isNotBlank() && '/' !in filename && filename != "." && filename != ".."
}
