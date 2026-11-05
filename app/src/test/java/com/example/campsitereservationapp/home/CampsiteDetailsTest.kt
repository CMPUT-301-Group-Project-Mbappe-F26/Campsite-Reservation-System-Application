package com.example.campsitereservationapp.campsite

import com.example.campsitereservationapp.model.Campsite
import org.junit.Assert.*
import org.junit.Test

class CampsiteDetailsTest {

    private val publishedCampsite = Campsite(
        id = "1",
        name = "Banff Campsite",
        ownerId = "owner123",
        published = true
    )

    private val unpublishedCampsite = Campsite(
        id = "2",
        name = "Jasper Campsite",
        ownerId = "owner123",
        published = false
    )

    @Test
    fun entrantCanViewPublishedCampsite() {
        assertTrue(canViewCampsite(publishedCampsite, "entrant456"))
    }

    @Test
    fun entrantCannotViewUnpublishedCampsite() {
        assertFalse(canViewCampsite(unpublishedCampsite, "entrant456"))
    }

    @Test
    fun ownerCanViewOwnUnpublishedCampsite() {
        assertTrue(canViewCampsite(unpublishedCampsite, "owner123"))
    }

    @Test
    fun entrantCannotSeeOwnerButtons() {
        assertFalse(canShowOwnerControls(publishedCampsite, "entrant456", true))
    }

    @Test
    fun ownerCanSeeButtonsInMyCampsites() {
        assertTrue(canShowOwnerControls(publishedCampsite, "owner123", false))
    }

    @Test
    fun ownerCannotSeeButtonsThroughDiscover() {
        assertFalse(canShowOwnerControls(publishedCampsite, "owner123", true))
    }

    @Test
    fun missingUserCannotSeeOwnerButtons() {
        assertFalse(canShowOwnerControls(publishedCampsite, null, false))
    }

    @Test
    fun discoverBackButtonHasCorrectLabel() {
        assertEquals("Discover", getCampsiteBackLabel(true))
    }

    @Test
    fun ownerBackButtonHasCorrectLabel() {
        assertEquals("My Campsites", getCampsiteBackLabel(false))
    }
}