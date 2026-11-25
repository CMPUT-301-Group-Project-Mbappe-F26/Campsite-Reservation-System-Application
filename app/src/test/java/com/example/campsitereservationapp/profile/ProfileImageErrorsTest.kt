package com.example.campsitereservationapp.profile

import com.google.firebase.storage.StorageException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileImageErrorsTest {
    @Test
    fun missingStorageDuringUploadExplainsTheSetupProblem() {
        val message = profileImageStorageErrorMessage(StorageException.ERROR_OBJECT_NOT_FOUND, 404)
        assertEquals("Photo storage is unavailable. Ask the app team to finish setting up Firebase Storage.", message)
        assertEquals(message, profileImageStorageErrorMessage(StorageException.ERROR_BUCKET_NOT_FOUND, 0))
    }

    @Test
    fun authorizationAndQuotaErrorsDoNotAskUsersToChooseAnotherImage() {
        assertTrue(profileImageStorageErrorMessage(StorageException.ERROR_NOT_AUTHORIZED, 403).contains("permissions"))
        assertTrue(profileImageStorageErrorMessage(StorageException.ERROR_NOT_AUTHENTICATED, 401).contains("sign in"))
        assertTrue(profileImageStorageErrorMessage(StorageException.ERROR_QUOTA_EXCEEDED, 403).contains("limit"))
    }

    @Test
    fun imageValidationKeepsItsActionableMessage() {
        assertEquals("Choose an image smaller than 20 MB.",
            profileImageErrorMessage(IllegalArgumentException("Choose an image smaller than 20 MB.")))
    }
}
