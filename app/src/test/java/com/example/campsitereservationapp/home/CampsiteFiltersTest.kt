package com.example.campsitereservationapp.home

import org.junit.Assert.*
import org.junit.Test
import com.example.campsitereservationapp.model.Campsite

/**
 * Tests campsite filter selections and date validation.
 */
class CampsiteFiltersTest {
    /**
     * Checks no filters are active by default.
     */
    @Test
    fun testDefaultFilters() {
        val filters = CampsiteFilters()
        assertFalse(hasActiveFilters(filters))
        assertEquals(0, filters.guests)
    }

    /**
     * Checks selecting location activates filtering.
     */
    @Test
    fun testLocationFilterSelected() {
        val filters = CampsiteFilters(location = "Edmonton")
        assertTrue(hasActiveFilters(filters))
    }

    /**
     * Checks selecting guests activates filtering.
     */
    @Test
    fun testGuestFilterSelected() {
        val filters = CampsiteFilters(guests = 4)
        assertTrue(hasActiveFilters(filters))
    }

    /**
     * Checks that selecting dates activates filtering.
     */
    @Test
    fun testDateFilterSelected() {
        val filters = CampsiteFilters(
            startDate = "2026-11-25",
            endDate = "2026-11-27"
        )
        assertTrue(hasActiveFilters(filters))
    }

    /**
     * Checks filters can be reset.
     */
    @Test
    fun testClearFilters() {
        val filters = CampsiteFilters(
            location = "Edmonton",
            guests = 4
        )
        assertTrue(hasActiveFilters(filters))
        val clearedFilters = CampsiteFilters()
        assertFalse(hasActiveFilters(clearedFilters))
    }

    @Test
    fun testValidDate() {
        val date = parseFilterDate("2026-11-25")
        assertNotNull(date)
    }

    /**
     * Checks invalid dates are rejected.
     */
    @Test
    fun testInvalidDate() {
        val date = parseFilterDate("2026-02-30")
        assertNull(date)
    }

    @Test
    fun testFilterByLocation() {
        val campsite1 = Campsite(
            name = "Mountain Camp",
            location = "Edmonton"
        )
        val campsite2 = Campsite(
            name = "Lake Camp",
            location = "Calgary"
        )
        val campsites = listOf(campsite1, campsite2)
        val filters = CampsiteFilters(location = "Edmonton")
        val result = applyCampsiteFilters(campsites, filters)
        assertEquals(1, result.size)
        assertEquals("Mountain Camp", result[0].name)
    }

    @Test
    fun testFilterByGuests() {
        val campsite1 = Campsite(
            name = "Small Camp",
            maxOccupancy = 2
        )
        val campsite2 = Campsite(
            name = "Large Camp",
            maxOccupancy = 6
        )
        val campsites = listOf(campsite1, campsite2)
        val filters = CampsiteFilters(guests = 4)
        val result = applyCampsiteFilters(campsites, filters)
        assertEquals(1, result.size)
        assertEquals("Large Camp", result[0].name)
    }

    @Test
    fun testCombinedFilters() {
        val campsite1 = Campsite(
            name = "Edmonton Camp",
            location = "Edmonton",
            maxOccupancy = 6,
            availableFrom = "2026-06-01",
            availableUntil = "2026-08-31"
        )
        val campsite2 = Campsite(
            name = "Calgary Camp",
            location = "Calgary",
            maxOccupancy = 6,
            availableFrom = "2026-06-01",
            availableUntil = "2026-08-31"
        )
        val campsite3 = Campsite(
            name = "Small Edmonton Camp",
            location = "Edmonton",
            maxOccupancy = 2,
            availableFrom = "2026-06-01",
            availableUntil = "2026-08-31"
        )
        val filters = CampsiteFilters(
            location = "Edmonton",
            guests = 4,
            startDate = "2026-07-10",
            endDate = "2026-07-12"
        )
        val result = applyCampsiteFilters(
            listOf(campsite1, campsite2, campsite3),
            filters
        )
        assertEquals(1, result.size)
        assertEquals("Edmonton Camp", result[0].name)
    }
}