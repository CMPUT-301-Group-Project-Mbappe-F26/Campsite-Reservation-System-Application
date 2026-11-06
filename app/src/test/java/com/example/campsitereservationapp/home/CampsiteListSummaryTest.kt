package com.example.campsitereservationapp.home

import com.example.campsitereservationapp.model.Campsite
import org.junit.Assert.*
import org.junit.Test

class CampsiteListSummaryTest {

    private val campsites = listOf(
        Campsite(
            id = "1",
            name = "Banff Campsite",
            location = "Banff",
            maxOccupancy = 6,
            averageRating = 4.5,
            published = true
        ),
        Campsite(
            id = "2",
            name = "Edmonton Campsite",
            location = "Edmonton",
            maxOccupancy = 4,
            published = false
        ),
        Campsite(
            id = "3",
            name = "Jasper Campsite",
            location = "Jasper",
            maxOccupancy = 8,
            published = true
        )
    )

    @Test
    fun onlyPublishedCampsitesAreDisplayed() {
        val result = getDisplayedCampsites(campsites, "")

        assertEquals(2, result.size)
        assertTrue(result.all { it.published })
    }

    @Test
    fun unpublishedCampsitesCannotBeFoundBySearch() {
        val result = getDisplayedCampsites(campsites, "Edmonton")

        assertTrue(result.isEmpty())
    }

    @Test
    fun allPublishedCampsitesShowWhenSearchIsEmpty() {
        val result = getDisplayedCampsites(campsites, "")

        assertEquals(listOf("1", "3"), result.map { it.id })
    }

    @Test
    fun emptyListShowsNoCampsites() {
        val result = getDisplayedCampsites(emptyList(), "")

        assertTrue(result.isEmpty())
    }

    @Test
    fun displayedListUpdatesWhenCampsitesChange() {
        val firstResult = getDisplayedCampsites(campsites, "")

        val updatedCampsites = campsites + Campsite(
            id = "4",
            name = "Calgary Campsite",
            location = "Calgary",
            published = true
        )

        val secondResult = getDisplayedCampsites(updatedCampsites, "")

        assertEquals(2, firstResult.size)
        assertEquals(3, secondResult.size)
    }

    @Test
    fun campsiteSummaryHasCorrectInformation() {
        val campsite = campsites[0]

        assertEquals("Banff Campsite", campsite.name)
        assertEquals("Banff", campsite.location)
        assertEquals(6, campsite.maxOccupancy)
        assertEquals(4.5, campsite.averageRating!!, 0.001)
    }

    @Test
    fun ratingShowsCorrectStars() {
        assertEquals("★★★★☆", getRatingStars(4.5))
        assertEquals("★★★★★", getRatingStars(5.0))
        assertEquals("☆☆☆☆☆", getRatingStars(0.0))
    }

    @Test
    fun ratingShowsCorrectNumber() {
        assertEquals("4.5 / 5", getRatingText(4.5))
    }

    @Test
    fun missingRatingShowsCorrectMessage() {
        assertEquals("No ratings yet", getRatingText(null))
    }
}