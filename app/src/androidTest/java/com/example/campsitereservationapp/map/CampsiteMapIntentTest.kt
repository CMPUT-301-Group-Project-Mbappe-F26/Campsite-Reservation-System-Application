package com.example.campsitereservationapp.map

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.campsitereservationapp.model.Campsite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Intent tests for the campsite map (WOW factor, mockup B04). The screen is given sample
 * Jasper campsites and a stand-in for the map, so no Firebase, GPS or map tiles are needed.
 */
@RunWith(AndroidJUnit4::class)
class CampsiteMapIntentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val jasper = MapCenter(MapPoint(52.8737, -118.0814), "Jasper, Alberta")
    private val you = MapCenter(MapPoint(52.8737, -118.0814), "you", isUserLocation = true)
    private val pyramid = Campsite(id = "site-pyramid", name = "Pyramid Lake Hideaway", location = "Jasper NP",
        latitude = 52.9230, longitude = -118.0950, pricePerNight = 69.0, maxOccupancy = 8,
        averageRating = 4.9, ratingCount = 176)
    private val whistlers = Campsite(id = "site-whistlers", name = "Whistlers Campground", location = "Jasper NP",
        latitude = 52.8445, longitude = -118.0790, pricePerNight = 52.0)

    private var state by mutableStateOf(CampsiteMapState())
    private var searched = 0
    private var located = 0
    private var opened: String? = null

    private fun show(initial: CampsiteMapState, message: String = "", lotteryText: String? = null) {
        state = initial
        composeRule.setContent {
            CampsiteMapContent(
                state = state,
                campsites = campsitesOnMap(listOf(pyramid, whistlers), state.center?.point, state.radiusKm),
                loading = false, searching = false, locating = false,
                message = message, lotteryText = lotteryText,
                onStateChange = { state = it },
                onSearch = { searched++ },
                onLocate = { located++ },
                onFilterClick = {}, onBack = {},
                onOpen = { opened = it },
                map = { modifier -> Box(modifier.testTag("map")) }
            )
        }
    }

    @Test
    fun mapShowsDistanceAroundTheEntrant() {
        show(CampsiteMapState(center = you))
        composeRule.onNodeWithTag("map").assertIsDisplayed()
        composeRule.onNodeWithText("Within 25 km of you").assertIsDisplayed()
    }

    @Test
    fun searchingAPlaceRunsTheSearch() {
        show(CampsiteMapState())
        composeRule.onNodeWithText("Search a place").performTextInput("Jasper")
        composeRule.onNodeWithText("Jasper").performImeAction()
        assertEquals("Jasper", state.query)
        assertEquals(1, searched)
    }

    @Test
    fun entrantPicksALargerDistance() {
        show(CampsiteMapState(center = jasper))
        composeRule.onNodeWithContentDescription("Search distance").performClick()
        composeRule.onNodeWithText("Within 100 km").performClick()
        assertEquals(100, state.radiusKm)
        composeRule.onNodeWithText("Within 100 km of Jasper, Alberta").assertIsDisplayed()
    }

    @Test
    fun listShowsNearbyCampsitesClosestFirst() {
        show(CampsiteMapState(center = you))
        composeRule.onNodeWithText("List").performClick()
        composeRule.onNodeWithText("2 campsites within 25 km of you").assertIsDisplayed()
        composeRule.onNodeWithText("3.3 km away · Jasper NP").assertIsDisplayed()
        composeRule.onNodeWithText("Whistlers Campground").performClick()
        assertEquals("site-whistlers", state.selectedId)
        assertTrue("Choosing a campsite closes the list", !state.showingList)
    }

    @Test
    fun selectedCampsiteCardMatchesTheMockup() {
        show(CampsiteMapState(center = you, selectedId = "site-pyramid"), lotteryText = "Lottery open · 3d left")
        composeRule.onNodeWithText("Pyramid Lake Hideaway").assertIsDisplayed()
        composeRule.onNodeWithText("Jasper NP · 5.6 km").assertIsDisplayed()
        composeRule.onNodeWithText("Sleeps 8").assertIsDisplayed()
        composeRule.onNodeWithText("4.9").assertIsDisplayed()
        composeRule.onNodeWithText("Lottery open · 3d left").assertIsDisplayed()
        composeRule.onNodeWithText("$69").assertIsDisplayed()
    }

    @Test
    fun viewOpensTheCampsite() {
        show(CampsiteMapState(center = you, selectedId = "site-pyramid"))
        composeRule.onNodeWithText("View").performClick()
        assertEquals("site-pyramid", opened)
    }

    @Test
    fun locationButtonFindsTheEntrant() {
        show(CampsiteMapState())
        composeRule.onNodeWithContentDescription("Show my location").performClick()
        assertEquals(1, located)
    }

    @Test
    fun clearingTheSearchShowsEveryCampsiteAgain() {
        show(CampsiteMapState(query = "Jasper", center = jasper))
        composeRule.onNodeWithContentDescription("Clear search").performClick()
        assertEquals(null, state.center)
        composeRule.onNodeWithText("All campsites").assertIsDisplayed()
    }

    @Test
    fun emptyAreaMessageIsShown() {
        show(CampsiteMapState(center = jasper), message = "No campsites within 10 km of Jasper, Alberta.")
        composeRule.onNodeWithText("No campsites within 10 km of Jasper, Alberta.").assertIsDisplayed()
    }
}
