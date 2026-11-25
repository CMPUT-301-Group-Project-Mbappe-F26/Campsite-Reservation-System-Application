package com.example.campsitereservationapp.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileImagePathTest {
    @Test
    fun cleanupAcceptsOnlyTheCurrentUsersProfileFolder() {
        assertTrue(isOwnedProfileImagePath("entrant", "profile-images/entrant/photo.jpg"))
        assertFalse(isOwnedProfileImagePath("entrant", "profile-images/another-user/photo.jpg"))
        assertFalse(isOwnedProfileImagePath("entrant", "profile-images/entrant-other/photo.jpg"))
        assertFalse(isOwnedProfileImagePath("entrant", "campsites/entrant/site/photo.jpg"))
    }

    @Test
    fun cleanupRejectsEmptyFoldersAndNestedOrRelativePaths() {
        listOf("", "profile-images/entrant/", "profile-images/entrant/..",
            "profile-images/entrant/.", "profile-images/entrant/../other/photo.jpg",
            "profile-images/entrant/nested/photo.jpg").forEach {
            assertFalse(isOwnedProfileImagePath("entrant", it))
        }
        assertFalse(isOwnedProfileImagePath("", "profile-images//photo.jpg"))
        assertFalse(isOwnedProfileImagePath("entrant/other", "profile-images/entrant/other/photo.jpg"))
    }
}
