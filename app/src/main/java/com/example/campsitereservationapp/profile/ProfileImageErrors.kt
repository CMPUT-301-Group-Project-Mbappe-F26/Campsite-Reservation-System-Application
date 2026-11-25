package com.example.campsitereservationapp.profile

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException

/** Converts save failures into a useful next action without displaying server details. */
internal fun profileImageErrorMessage(exception: Exception): String = when (exception) {
    is StorageException -> profileImageStorageErrorMessage(exception.errorCode, exception.httpResultCode)
    is FirebaseFirestoreException -> when (exception.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Your profile could not be updated. Ask the app team to check profile permissions."
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> "Please sign in again before saving your photo."
        else -> "Could not update your profile. Check your connection and try again."
    }
    is IllegalArgumentException -> exception.message ?: "Choose another image."
    else -> "Could not save your photo. Check your connection and try again, or choose another image."
}

internal fun profileImageStorageErrorMessage(errorCode: Int, httpCode: Int): String = when {
    httpCode == 404 || errorCode == StorageException.ERROR_BUCKET_NOT_FOUND ||
        errorCode == StorageException.ERROR_PROJECT_NOT_FOUND || errorCode == StorageException.ERROR_OBJECT_NOT_FOUND ->
        "Photo storage is unavailable. Ask the app team to finish setting up Firebase Storage."
    errorCode == StorageException.ERROR_NOT_AUTHORIZED ->
        "Photo upload was denied. Ask the app team to check photo storage permissions."
    errorCode == StorageException.ERROR_NOT_AUTHENTICATED -> "Please sign in again before saving your photo."
    errorCode == StorageException.ERROR_QUOTA_EXCEEDED ->
        "Photo storage has reached its limit. Please contact the app team."
    else -> "Could not upload your photo. Check your connection and try again."
}
