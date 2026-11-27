package com.example.campsitereservationapp.map

import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.Lottery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Unit tests for the campsite map (WOW factor): distances, which campsites are within the
 * chosen distance, place lookup from campsites, map areas, and the text the map shows.
 * Uses real Alberta coordinates so the distances can be checked against a map.
 */
class CampsiteMapTest {

    private val jasper = MapPoint(52.8737, -118.0814)
    private val banff = MapPoint(51.1784, -115.5708)
    private val edmonton = MapPoint(53.5461, -113.4938)
    private val calgary = MapPoint(51.0447, -114.0719)

    private val pyramidLake = Campsite(id = "site-pyramid", name = "Pyramid Lake Hideaway", location = "Jasper NP",
        latitude = 52.9230, longitude = -118.0950, pricePerNight = 69.0)
    private val whistlers = Campsite(id = "site-whistlers", name = "Whistlers Campground", location = "Jasper NP",
        latitude = 52.8445, longitude = -118.0790, pricePerNight = 52.0)
    private val twoJack = Campsite(id = "site-two-jack", name = "Two Jack Lakeside", location = "Banff NP",
        latitude = 51.2330, longitude = -115.5100, pricePerNight = 84.0)
    private val unset = Campsite(id = "site-unset", name = "Draft Listing")

    private val all = listOf(twoJack, pyramidLake, whistlers, unset)

    @Test
    fun distanceBetweenEdmontonAndCalgaryIsAbout280Km() {
        assertEquals(281.0, distanceKm(edmonton, calgary), 3.0)
    }

    @Test
    fun distanceToTheSamePlaceIsZero() {
        assertEquals(0.0, distanceKm(jasper, jasper), 0.0001)
    }

    @Test
    fun campsitesWithoutALocationAreNotPinned() {
        assertNull(unset.mapPoint())
        assertNull(pyramidLake.copy(latitude = 95.0).mapPoint())
        assertNull(pyramidLake.copy(longitude = Double.NaN).mapPoint())
        assertEquals(MapPoint(52.9230, -118.0950), pyramidLake.mapPoint())
    }

    @Test
    fun withoutACenterEveryPinnedCampsiteIsShownByName() {
        val shown = campsitesOnMap(all, center = null, radiusKm = 25)
        assertEquals(listOf("Pyramid Lake Hideaway", "Two Jack Lakeside", "Whistlers Campground"), shown.map { it.campsite.name })
        assertTrue(shown.all { it.distanceKm == null })
    }

    @Test
    fun searchShowsOnlyCampsitesWithinTheDistanceClosestFirst() {
        val shown = campsitesOnMap(all, jasper, radiusKm = 25)
        assertEquals(listOf("site-whistlers", "site-pyramid"), shown.map { it.campsite.id })
        assertEquals(3.3, shown.first().distanceKm!!, 0.3)
    }

    @Test
    fun largerDistanceIncludesMoreCampsites() {
        assertEquals(2, campsitesOnMap(all, jasper, radiusKm = 250).size)
        assertEquals(3, campsitesOnMap(all, jasper, radiusKm = 300).size)
    }

    @Test
    fun nearestCampsiteIsSuggestedWhenNoneAreClose() {
        val nearest = nearestCampsite(all, edmonton)
        assertEquals("site-two-jack", nearest?.campsite?.id)
        assertNull(nearestCampsite(listOf(unset), edmonton))
    }

    @Test
    fun distancesAreFormattedForTheirSize() {
        assertEquals("850 m", formatDistance(0.85))
        assertEquals("6.2 km", formatDistance(6.24))
        assertEquals("120 km", formatDistance(119.6))
    }

    @Test
    fun distancePillNamesTheUserOrThePlace() {
        assertEquals("All campsites", radiusLabel(null, 25))
        assertEquals("Within 25 km of you", radiusLabel(MapCenter(jasper, "you", isUserLocation = true), 25))
        assertEquals("Within 50 km of Jasper, Alberta", radiusLabel(MapCenter(jasper, "Jasper, Alberta"), 50))
    }

    @Test
    fun listTitleCountsTheCampsites() {
        assertEquals("1 campsite within 25 km of you", mapListTitle(1, MapCenter(jasper, "you", true), 25))
        assertEquals("3 campsites on the map", mapListTitle(3, null, 25))
    }

    @Test
    fun emptyAreaPointsToTheClosestCampsite() {
        val message = emptyAreaMessage(MapCenter(edmonton, "Edmonton, Alberta"), 25, nearestCampsite(all, edmonton))
        assertTrue(message.startsWith("No campsites within 25 km of Edmonton, Alberta. The closest is Two Jack Lakeside, "))
        assertEquals("No campsites within 10 km of you.", emptyAreaMessage(MapCenter(edmonton, "you", true), 10, null))
    }

    @Test
    fun searchingACampsiteLocationFindsItsArea() {
        val place = placeFromCampsites("jasper", all)!!
        assertEquals("jasper", place.label)
        assertEquals((52.9230 + 52.8445) / 2, place.point.latitude, 0.0001)
        assertNull(placeFromCampsites("Toronto", all))
        assertNull(placeFromCampsites("  ", all))
    }

    @Test
    fun searchAreaCoversTheWholeCircle() {
        val bounds = areaBounds(banff, 25)
        assertTrue(distanceKm(banff, MapPoint(bounds.north, banff.longitude)) >= 25)
        assertTrue(distanceKm(banff, MapPoint(banff.latitude, bounds.east)) >= 25)
        assertTrue(bounds.south < banff.latitude && bounds.west < banff.longitude)
    }

    @Test
    fun mapAreaFitsEveryPin() {
        val points = listOf(jasper, banff)
        val bounds = pointsBounds(points)!!
        assertTrue(points.all { it.latitude in bounds.south..bounds.north && it.longitude in bounds.west..bounds.east })
        assertNull(pointsBounds(emptyList()))
    }

    @Test
    fun singlePinStillShowsTheAreaAroundIt() {
        val bounds = pointsBounds(listOf(jasper))!!
        assertTrue(bounds.north - bounds.south >= 0.2)
    }

    @Test
    fun cardShowsHowManyPeopleASiteSleeps() {
        assertEquals("Sleeps 8", sleepsText(8))
        assertNull(sleepsText(null))
        assertNull(sleepsText(0))
    }

    @Test
    fun lotteryChipCountsDaysUntilRegistrationCloses() {
        val lottery = Lottery(registrationStartDate = "2026-11-20", registrationEndDate = "2026-11-29")
        assertEquals("Lottery open · 3d left", lotteryChipText(lottery, "2026-11-26"))
        assertEquals("Lottery open · closes today", lotteryChipText(lottery, "2026-11-29"))
    }

    @Test
    fun noLotteryChipWhenEntriesAreClosed() {
        val lottery = Lottery(registrationStartDate = "2026-11-20", registrationEndDate = "2026-11-25")
        assertNull(lotteryChipText(lottery, "2026-11-26"))
        assertNull(lotteryChipText(lottery.copy(registrationEndDate = "2026-12-01", drawCompleted = true), "2026-11-26"))
        assertNull(lotteryChipText(null, "2026-11-26"))
    }
}
