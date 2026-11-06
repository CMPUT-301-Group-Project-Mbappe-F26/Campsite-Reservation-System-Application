package com.example.campsitereservationapp.reservation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.Reservation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Intent tests for US 01.06.01: an entrant views their active reservations and opens one.
 * The screens are given sample reservations directly, so no Firebase data is needed.
 */
@RunWith(AndroidJUnit4::class)
class MyReservationsIntentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val twoJack = Reservation(
        id = "res-two-jack", entrantId = "entrant-amir", campsiteId = "site-two-jack",
        lotteryId = "lottery-summer", campsiteName = "Two Jack Lakeside",
        checkInDate = "2027-07-02", checkOutDate = "2027-07-04"
    )
    private val whistlers = twoJack.copy(
        id = "res-whistlers", campsiteId = "site-whistlers", campsiteName = "Whistlers Campground",
        checkInDate = "2027-08-14", checkOutDate = "2027-08-16"
    )

    private fun showList(
        reservations: List<Reservation>,
        wins: List<Pair<String, String>> = emptyList(),
        onReservationClick: (String) -> Unit = {},
        onReserveClick: (String) -> Unit = {}
    ) = composeRule.setContent {
        MyReservationsContent(wins, reservations, loading = false, error = "", onRetry = {},
            onReservationClick = onReservationClick, onReserveClick = onReserveClick)
    }

    @Test
    fun entrantSeesCampsiteNameAndDatesOfEachReservation() {
        showList(listOf(twoJack, whistlers))
        composeRule.onNodeWithText("Two Jack Lakeside").assertIsDisplayed()
        composeRule.onNodeWithText("Jul 2, 2027 – Jul 4, 2027").assertIsDisplayed()
        composeRule.onNodeWithText("Whistlers Campground").assertIsDisplayed()
    }

    @Test
    fun tappingAReservationOpensItsDetails() {
        var opened: String? = null
        showList(listOf(twoJack, whistlers), onReservationClick = { opened = it })
        composeRule.onNodeWithText("Whistlers Campground").performClick()
        assertEquals("res-whistlers", opened)
    }

    @Test
    fun entrantIsToldWhenTheyHaveNoReservations() {
        showList(emptyList())
        composeRule.onNodeWithText("No active reservations").assertIsDisplayed()
    }

    @Test
    fun unusedWinOpensTheReserveScreen() {
        var reserving: String? = null
        showList(emptyList(), wins = listOf("lottery-summer" to "Two Jack Lakeside"), onReserveClick = { reserving = it })
        composeRule.onNodeWithText("You won a spot").assertIsDisplayed()
        composeRule.onNodeWithText("Choose your dates to reserve.").performClick()
        assertEquals("lottery-summer", reserving)
    }

    @Test
    fun detailsShowDatesLocationAndRules() {
        val site = Campsite(id = "site-two-jack", name = "Two Jack Lakeside", location = "Banff, AB",
            rules = "Quiet hours from 10 PM to 7 AM.", cancellationPolicy = "Flexible")
        composeRule.setContent { ReservationDetailsContent(twoJack, site) }
        composeRule.onNodeWithText("Two Jack Lakeside").assertIsDisplayed()
        composeRule.onNodeWithText("Confirmed").assertIsDisplayed()
        composeRule.onNodeWithText("Jul 2, 2027").assertIsDisplayed()
        composeRule.onNodeWithText("Banff, AB").assertIsDisplayed()
        composeRule.onNodeWithText("Quiet hours from 10 PM to 7 AM.").assertExists()
    }

    @Test
    fun detailsStillShowWhenTheCampsiteCannotBeLoaded() {
        composeRule.setContent { ReservationDetailsContent(twoJack, site = null) }
        composeRule.onNodeWithText("Two Jack Lakeside").assertIsDisplayed()
        composeRule.onNodeWithText("Not available").assertIsDisplayed()
    }
}
