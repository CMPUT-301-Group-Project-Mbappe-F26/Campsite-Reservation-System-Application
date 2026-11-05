package com.example.campsitereservationapp.home

import com.example.campsitereservationapp.model.Campsite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampsiteSearchTest {

    private val campsites = listOf(
        Campsite(id = "1", name = "Banff Campground", location = "Banff"),
        Campsite(id = "2", name = "Elk Island Camp", location = "Edmonton"),
        Campsite(id = "3", name = "Jasper Lake", location = "Jasper")
    )

    @Test
    fun searchByName() {
        val result = filterCampsites(campsites, "Banff")

        assertEquals(1, result.size)
        assertEquals("Banff Campground", result[0].name)
    }

    @Test
    fun searchByLocation() {
        val result = filterCampsites(campsites, "Edmonton")

        assertEquals(1, result.size)
        assertEquals("Elk Island Camp", result[0].name)
    }

    @Test
    fun searchIgnoresCapitalization() {
        val result = filterCampsites(campsites, "banff")

        assertEquals(1, result.size)
    }

    @Test
    fun searchWithNoMatches() {
        val result = filterCampsites(campsites, "Toronto")

        assertTrue(result.isEmpty())
    }

    @Test
    fun emptySearchReturnsAll() {
        val result = filterCampsites(campsites, "")

        assertEquals(3, result.size)
    }

    @Test
    fun searchIgnoresExtraSpaces() {
        val result = filterCampsites(campsites, "  Jasper  ")

        assertEquals(1, result.size)
        assertEquals("Jasper Lake", result[0].name)
    }
    @Test
    fun emptyCampsiteList() {
        val result = filterCampsites(emptyList(), "")

        assertTrue(result.isEmpty())
    }

    @Test
    fun multipleCampsitesMatchLocation() {
        val testCampsites = listOf(
            Campsite(id = "1", name = "River Camp", location = "Edmonton"),
            Campsite(id = "2", name = "Lake Camp", location = "Edmonton"),
            Campsite(id = "3", name = "Mountain Camp", location = "Banff")
        )

        val result = filterCampsites(testCampsites, "Edmonton")

        assertEquals(2, result.size)
    }
}