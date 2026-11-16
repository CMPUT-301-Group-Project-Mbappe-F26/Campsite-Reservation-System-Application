package com.example.campsitereservationapp.reservation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.campsitereservationapp.model.*
import com.example.campsitereservationapp.ui.theme.PinedrawTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises US 01.04.02 without Firebase or real reservations. */
@RunWith(AndroidJUnit4::class)
class MultipleCampsiteReservationIntentTest {
    @get:Rule val compose = createComposeRule()
    private val first = Campsite(id = "first", ownerId = "owner", name = "First Meadow", published = true, maxOccupancy = 6)
    private val second = first.copy(id = "second", name = "Lake Side")
    private val lottery = Lottery(id = "draw", ownerId = "owner", campsiteId = "first",
        campsiteIds = listOf("first", "second"), numberOfWinners = 3, pending = listOf("winner"),
        stayStartDate = "2027-07-01", stayEndDate = "2027-07-04")
    private var left = false
    private var declined = false
    private var refreshed = false

    private fun show(blockFirst: Boolean = false, blockSecond: Boolean = false, single: Boolean = false) {
        val draw = if (single) lottery.copy(campsiteIds = emptyList()) else lottery
        val blocked = setOf("2027-07-01", "2027-07-02", "2027-07-03")
        val choices = (if (single) listOf(first) else listOf(first, second)).map { site ->
            val full = if (site.id == first.id) blockFirst else blockSecond
            reservationCampsiteChoice(site.id, site, draw, "winner", if (full) blocked else emptySet(), "2027-06-01")
        }
        compose.setContent {
            PinedrawTheme {
                Column(Modifier.fillMaxSize()) {
                    LotteryReservationFlow(LotteryReservationSelection(draw, choices),
                        onReserve = { _, _, _, _ -> null }, onDecline = { declined = true; null },
                        onBack = { left = true }, onReserved = {}, onRefresh = { refreshed = true }, today = "2027-06-01")
                }
            }
        }
    }

    @Test fun listAppearsBeforeDatesAndRequiresOneSelection() {
        show()
        compose.onNodeWithText("Choose a campsite").assertIsDisplayed()
        compose.onNodeWithText("First Meadow").assertIsDisplayed()
        compose.onNodeWithTag("campsite-choice-second").performScrollTo()
        compose.onNodeWithText("Lake Side").assertIsDisplayed()
        compose.onNodeWithText("Choose dates").assertIsNotEnabled()
        compose.onNodeWithText("Pick your dates. Dates that are greyed out are not available.").assertDoesNotExist()
    }

    @Test fun selectingASecondCampsiteOpensItsCalendar() {
        show()
        compose.onNodeWithTag("campsite-choice-second").performScrollTo().performClick()
        compose.onNodeWithText("Choose dates").assertIsEnabled().performClick()
        compose.onNodeWithText("Lake Side").assertIsDisplayed()
        compose.onNodeWithText("Pick your dates. Dates that are greyed out are not available.").assertIsDisplayed()
        compose.onNodeWithText("Choose a campsite").assertDoesNotExist()
    }

    @Test fun fullCampsitesCannotBeSelected() {
        show(blockFirst = true)
        compose.onNodeWithTag("campsite-choice-first").assertIsNotEnabled()
        compose.onNodeWithText("No available dates in this lottery.").assertIsDisplayed()
        compose.onNodeWithTag("campsite-choice-second").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Choose dates").assertIsEnabled()
    }

    @Test fun winnerCanReturnAndChooseAnotherCampsite() {
        show()
        compose.onNodeWithTag("campsite-choice-first").performClick()
        compose.onNodeWithText("Choose dates").performClick()
        compose.onNodeWithText("Change campsite").performClick()
        compose.onNodeWithTag("campsite-choice-second").performScrollTo().performClick()
        compose.onNodeWithText("Choose dates").performClick()
        compose.onNodeWithText("Lake Side").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
    }

    @Test fun allFullStillAllowsRefreshOrDecline() {
        show(blockFirst = true, blockSecond = true)
        compose.onNodeWithText("Choose dates").assertIsNotEnabled()
        compose.onNodeWithText("Refresh").performClick()
        assertTrue(refreshed)
        compose.onNodeWithText("Decline spot").performClick()
        compose.onNodeWithText("Decline").performClick()
        compose.waitUntil(5000) { left }
        assertTrue(declined)
    }

    @Test fun existingSingleCampsiteLotteryStillOpensItsCalendarDirectly() {
        show(single = true)
        compose.onNodeWithText("First Meadow").assertIsDisplayed()
        compose.onNodeWithText("Pick your dates. Dates that are greyed out are not available.").assertIsDisplayed()
        compose.onNodeWithText("Choose a campsite").assertDoesNotExist()
    }
}
