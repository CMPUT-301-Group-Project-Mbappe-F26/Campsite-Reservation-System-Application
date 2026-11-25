package com.example.campsitereservationapp.service

import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * Unit tests for the pure parts of admin image removal (US 03.03.01).
 */
class AdminImageRemovalTest {

    private val photos = listOf("https://img/cover.jpg", "https://img/two.jpg", "https://img/three.jpg")

    @Test
    fun removingAPhotoKeepsTheOthersInOrder() {
        assertEquals(listOf("https://img/cover.jpg", "https://img/three.jpg"), campsiteUrlsWithout(photos, "https://img/two.jpg"))
    }

    @Test
    fun removingTheCoverMakesTheNextPhotoTheCover() {
        assertEquals("https://img/two.jpg", campsiteUrlsWithout(photos, "https://img/cover.jpg").first())
    }

    @Test
    fun removingTheOnlyPhotoLeavesNoPhotosSoThePlaceholderShows() {
        assertEquals(emptyList<String>(), campsiteUrlsWithout(listOf("https://img/only.jpg"), "https://img/only.jpg"))
    }

    @Test
    fun removingAPhotoThatIsNotThereChangesNothing() {
        assertEquals(photos, campsiteUrlsWithout(photos, "https://img/missing.jpg"))
    }

    @Test
    fun notAnAdminExplainsWhy() {
        assertEquals("Only admins can remove images.", imageRemovalError(IllegalStateException("Only admins can remove images.")))
    }

    @Test
    fun unknownFailureGivesAGeneralMessage() {
        assertEquals("Could not remove this image. Please try again.", imageRemovalError(RuntimeException()))
    }
}
