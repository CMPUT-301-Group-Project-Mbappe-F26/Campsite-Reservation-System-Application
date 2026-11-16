package com.example.campsitereservationapp.reservation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.RESERVATION_CANCELLED
import com.example.campsitereservationapp.model.Reservation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Intent tests for US 01.07.01: an entrant cancels a reservation from its details screen.
 * The screen is given a sample reservation and a stand-in cancel action, so no Firebase
 * data is needed.
 */
@RunWith(AndroidJUnit4::class)
class CancelReservationIntentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = "2027-06-01"
    private val twoJack = Reservation(
        id = "res-two-jack", entrantId = "entrant-amir", campsiteId = "site-two-jack",
        lotteryId = "lottery-summer", campsiteName = "Two Jack Lakeside",
        checkInDate = "2027-07-02", checkOutDate = "2027-07-04"
    )
    private val site = Campsite(
        id = "site-two-jack", name = "Two Jack Lakeside", location = "Banff, AB",
        rules = "Quiet hours from 10 PM to 7 AM.", cancellationPolicy = "Flexible"
    )

    @Test
    fun entrantConfirmsBeforeTheReservationIsCancelled() {
        var cancelled = 0
        composeRule.setContent {
            ReservationDetailsContent(twoJack, site, onCancel = { cancelled++ }, today = today)
        }
        composeRule.onNodeWithText("Cancel reservation").performScrollTo().performClick()
        composeRule.onNodeWithText("Cancel this reservation?").assertIsDisplayed()
        composeRule.onNodeWithText("Your stay from Jul 2, 2027 to Jul 4, 2027", substring = true).assertIsDisplayed()
        assertEquals(0, cancelled)
        composeRule.onNodeWithText("Yes, cancel").performClick()
        assertEquals(1, cancelled)
    }

    @Test
    fun keepingTheReservationDoesNotCancelIt() {
        var cancelled = 0
        composeRule.setContent {
            ReservationDetailsContent(twoJack, site, onCancel = { cancelled++ }, today = today)
        }
        composeRule.onNodeWithText("Cancel reservation").performScrollTo().performClick()
        composeRule.onNodeWithText("Keep reservation").performClick()
        composeRule.onNodeWithText("Cancel this reservation?").assertDoesNotExist()
        assertEquals(0, cancelled)
    }

    @Test
    fun cancelledReservationShowsItsStatusAndNoCancelButton() {
        val cancelled = twoJack.copy(status = RESERVATION_CANCELLED)
        composeRule.setContent {
            ReservationDetailsContent(
                cancelled, site, onCancel = null,
                cancelMessage = "Your reservation has been cancelled.", today = today
            )
        }
        composeRule.onNodeWithText("Cancelled").assertIsDisplayed()
        composeRule.onNodeWithText("Your reservation has been cancelled.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Cancel reservation").assertDoesNotExist()
    }

    @Test
    fun cancelButtonIsDisabledWhileSaving() {
        composeRule.setContent {
            ReservationDetailsContent(twoJack, site, onCancel = {}, cancelling = true, today = today)
        }
        composeRule.onNodeWithText("Cancelling…").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun finishedStayShowsCompletedAndCannotBeCancelled() {
        composeRule.setContent {
            ReservationDetailsContent(twoJack, site, onCancel = null, today = "2027-07-10")
        }
        composeRule.onNodeWithText("Completed").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel reservation").assertDoesNotExist()
    }

    @Test
    fun failedCancelShowsTheReason() {
        composeRule.setContent {
            ReservationDetailsContent(
                twoJack, site, onCancel = {},
                cancelMessage = "Your stay has already started, so it can no longer be cancelled.", today = today
            )
        }
        composeRule.onNodeWithText("Your stay has already started, so it can no longer be cancelled.")
            .performScrollTo().assertIsDisplayed()
    }
}
