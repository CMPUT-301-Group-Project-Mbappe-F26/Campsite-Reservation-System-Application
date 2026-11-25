package com.example.campsitereservationapp.admin

import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.User
import org.junit.Assert.*
import org.junit.Test

/*
 * Unit tests for adminImages, which collects every uploaded image an admin browses
 * (US 03.06.01): campsite promo photos and profile photos, with what each belongs to.
 */
class AdminImagesTest {

    private val twoJack = Campsite(
        id = "site-two-jack", name = "Two Jack Lakeside",
        imageUrls = listOf("https://img/two-jack-1.jpg", "https://img/two-jack-2.jpg")
    )
    private val whistlers = Campsite(id = "site-whistlers", name = "Whistlers Campground",
        imageUrls = listOf("https://img/whistlers.jpg"))
    private val maria = User(id = "user-maria", name = "Maria Lopez", profileImageUrl = "https://img/maria.jpg")
    private val devon = User(id = "user-devon", name = "Devon Clarke")

    @Test
    fun noUploadsGivesNoImages() {
        assertTrue(adminImages(emptyList(), emptyList()).isEmpty())
        assertTrue(adminImages(listOf(Campsite(id = "bare", name = "Bare")), listOf(devon)).isEmpty())
    }

    @Test
    fun everyCampsitePhotoIsListedWithItsCampsite() {
        val images = adminImages(listOf(twoJack), emptyList())
        assertEquals(listOf("https://img/two-jack-1.jpg", "https://img/two-jack-2.jpg"), images.map { it.url })
        assertTrue(images.all { it.source == AdminImageSource.CAMPSITE })
        assertTrue(images.all { it.ownerName == "Two Jack Lakeside" && it.ownerId == "site-two-jack" })
        assertEquals(listOf(0, 1), images.map { it.position })
    }

    @Test
    fun onlyProfilesWithAPhotoAreListed() {
        val image = adminImages(emptyList(), listOf(maria, devon)).single()
        assertEquals(AdminImageSource.PROFILE, image.source)
        assertEquals("Maria Lopez", image.ownerName)
        assertEquals("user-maria", image.ownerId)
    }

    @Test
    fun campsitePhotosComeFirstThenProfilesEachSortedByName() {
        val images = adminImages(listOf(whistlers, twoJack), listOf(maria))
        assertEquals(
            listOf("Two Jack Lakeside", "Two Jack Lakeside", "Whistlers Campground", "Maria Lopez"),
            images.map { it.ownerName }
        )
    }

    @Test
    fun blankUrlsAreSkippedAndIdsAreUnique() {
        val site = twoJack.copy(imageUrls = listOf("https://img/a.jpg", "", " "))
        val images = adminImages(listOf(site), listOf(maria))
        assertEquals(2, images.size)
        assertEquals(images.size, images.map { it.id }.toSet().size)
    }

    @Test
    fun unnamedOwnersGetAReadableLabel() {
        val images = adminImages(
            listOf(Campsite(id = "x", imageUrls = listOf("https://img/x.jpg"))),
            listOf(User(id = "y", profileImageUrl = "https://img/y.jpg"))
        )
        assertEquals(listOf("Unnamed campsite", "Unnamed profile"), images.map { it.ownerName })
    }
}
