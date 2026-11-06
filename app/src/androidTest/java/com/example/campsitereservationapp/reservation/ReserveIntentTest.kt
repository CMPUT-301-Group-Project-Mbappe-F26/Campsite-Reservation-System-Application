package com.example.campsitereservationapp.reservation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.Reservation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Intent tests for US 01.04.01 (pick available dates and reserve), US 01.04.03 (decline a
 * spot) and US 02.11.01 (agree to the campsite rules before reserving). Reserve and decline
 * are replaced with stand-ins, so no Firebase data is needed.
 */
@RunWith(AndroidJUnit4::class)
class ReserveIntentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val campsite = Campsite(
        id = "site-two-jack", ownerId = "owner-maria", name = "Two Jack Lakeside", location = "Banff, AB",
        maxOccupancy = 6, pricePerNight = 38.50, published = true,
        rules = "Quiet hours from 10 PM to 7 AM. Store food in the bear lockers.",
        cancellationPolicy = "Flexible", requireRulesAcceptance = true
    )
    private val lottery = Lottery(
        id = "lottery-summer", ownerId = "owner-maria", campsiteId = "site-two-jack", numberOfWinners = 3,
        registrationStartDate = "2027-05-01", registrationEndDate = "2027-05-15",
        bookingStartDate = "2027-05-20", bookingEndDate = "2027-06-10",
        stayStartDate = "2027-07-01", stayEndDate = "2027-07-08",
        pending = listOf("entrant-amir")
    )

    private var reserved: Triple<String, String, Boolean>? = null
    private var reservedCalled = false
    private var declined = false
    private var wentBack = false

    private fun showReserve(
        checkIn: String? = null,
        checkOut: String? = null,
        lottery: Lottery = this.lottery,
        reserveResult: String? = null
    ) = composeRule.setContent {
        Column(Modifier.fillMaxSize()) {
            ReserveFlow(
                lottery, campsite,
                onReserve = { inDate, outDate, accepted ->
                    reserved = Triple(inDate, outDate, accepted)
                    reserveResult
                },
                onDecline = {
                    declined = true
                    null
                },
                onBack = { wentBack = true },
                onReserved = { reservedCalled = true },
                initialCheckIn = checkIn,
                initialCheckOut = checkOut
            )
        }
    }

    @Test
    fun winnerSeesTheCalendarForTheirCampsite() {
        showReserve()
        composeRule.onNodeWithText("Two Jack Lakeside").assertIsDisplayed()
        composeRule.onNodeWithText("Pick your dates. Dates that are greyed out are not available.").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").assertIsNotEnabled()
    }

    @Test
    fun alreadyBookedDatesCannotBeReserved() {
        val booking = Reservation(id = "res-jen", entrantId = "entrant-jen", campsiteId = "site-two-jack",
            lotteryId = "lottery-summer", checkInDate = "2027-07-03", checkOutDate = "2027-07-05")
        showReserve("2027-07-02", "2027-07-04", lottery.copy(booked = listOf(booking)))
        composeRule.onNodeWithText("Some of these dates are not available. Please choose others.").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").assertIsNotEnabled()
    }

    @Test
    fun winnerMustAgreeToTheRulesBeforeReserving() {
        showReserve("2027-07-02", "2027-07-04")
        composeRule.onNodeWithText("Continue").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Review & confirm").assertIsDisplayed()
        composeRule.onNodeWithText("Quiet hours from 10 PM to 7 AM. Store food in the bear lockers.").assertIsDisplayed()
        composeRule.onNodeWithText("Reserve").assertIsNotEnabled()
        composeRule.onNodeWithText("I've read and agree to the campsite rules and cancellation policy.").performClick()
        composeRule.onNodeWithText("Reserve").assertIsEnabled()
    }

    @Test
    fun reservingBooksTheSelectedDates() {
        showReserve("2027-07-02", "2027-07-04")
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Nights").assertIsDisplayed()
        composeRule.onNodeWithText("$77.00 CAD").assertIsDisplayed()
        composeRule.onNodeWithText("I've read and agree to the campsite rules and cancellation policy.").performClick()
        composeRule.onNodeWithText("Reserve").performClick()
        composeRule.waitUntil(5000) { reservedCalled }
        assertEquals(Triple("2027-07-02", "2027-07-04", true), reserved)
    }

    @Test
    fun winnerIsToldWhenTheReservationFails() {
        showReserve("2027-07-02", "2027-07-04", reserveResult = "This lottery is fully booked.")
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("I've read and agree to the campsite rules and cancellation policy.").performClick()
        composeRule.onNodeWithText("Reserve").performClick()
        composeRule.waitUntil(5000) { composeRule.onAllNodesWithText("This lottery is fully booked.").fetchSemanticsNodes().isNotEmpty() }
        assertFalse(reservedCalled)
    }

    @Test
    fun decliningAsksForConfirmationFirst() {
        showReserve()
        composeRule.onNodeWithText("Decline spot").performClick()
        composeRule.onNodeWithText("Decline this spot?").assertIsDisplayed()
        composeRule.onNodeWithText("Keep my spot").performClick()
        assertFalse(declined)
        assertEquals(0, composeRule.onAllNodesWithText("Decline this spot?").fetchSemanticsNodes().size)
    }

    @Test
    fun confirmingDeclineGivesUpTheSpot() {
        showReserve()
        composeRule.onNodeWithText("Decline spot").performClick()
        composeRule.onNodeWithText("Decline").performClick()
        composeRule.waitUntil(5000) { wentBack }
        assertTrue(declined)
    }
}
