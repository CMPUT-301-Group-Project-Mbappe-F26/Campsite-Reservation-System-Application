package com.example.campsitereservationapp.campsite

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.campsitereservationapp.model.*
import com.example.campsitereservationapp.service.*
import com.example.campsitereservationapp.ui.theme.PinedrawTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Role visibility and explicit consent, using callbacks without Firebase. */
@RunWith(AndroidJUnit4::class)
class LotteryCollaborationIntentTest {
    @get:Rule val compose = createComposeRule()
    private val primary = Campsite(id = "primary", ownerId = "coordinator", name = "First campsite", published = true, maxOccupancy = 8)
    private val partner = primary.copy(id = "partner", ownerId = "partner-owner", name = "Partner campsite", location = "Jasper")
    private val lottery = Lottery(id = "draw", ownerId = "coordinator", campsiteId = "primary", numberOfWinners = 3,
        registrationStartDate = "2027-04-01", registrationEndDate = "2027-04-07",
        bookingStartDate = "2027-04-08", bookingEndDate = "2027-04-14", stayStartDate = "2027-05-01", stayEndDate = "2027-05-04")
    private var chosen: Pair<String, LotteryCollaborationAction>? = null
    private fun show(data: Lottery, owned: Campsite) = compose.setContent {
        PinedrawTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                LotteryCollaborationCard(LotteryCollaborationListing(data, listOf(primary, partner)), owned,
                    listOf(primary, partner), false, {}, { id, action -> chosen = id to action })
            }
        }
    }

    @Test fun pendingOwnerMustConfirmBeforeAccepting() {
        show(lottery.copy(campsiteInvitations = mapOf("partner" to "partner-owner")), partner)
        compose.onNodeWithText("Shared lottery invitation").assertIsDisplayed()
        compose.onNodeWithText("Accept invitation").performScrollTo().performClick()
        assertNull(chosen)
        compose.onNodeWithText("Cancel").performClick()
        assertNull(chosen)
        compose.onNodeWithText("Accept invitation").performClick()
        compose.onNodeWithText("Proceed").performClick()
        assertEquals("partner" to LotteryCollaborationAction.ACCEPT, chosen)
    }

    @Test fun contributorSeesOnlyOwnMembershipActions() {
        show(lottery.copy(campsiteIds = listOf("primary", "partner"), campsiteOwners = mapOf("partner" to "partner-owner")), partner)
        compose.onNodeWithText("Invite another owner's campsite").assertDoesNotExist()
        compose.onNodeWithText("Withdraw my campsite").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Proceed").performClick()
        assertEquals("partner" to LotteryCollaborationAction.WITHDRAW, chosen)
    }

    @Test fun coordinatorInvitesWithoutAddingACampsiteImmediately() {
        show(lottery, primary)
        compose.onNodeWithText("Invite another owner's campsite").performScrollTo().performClick()
        compose.onNodeWithText("Send invitation").assertIsNotEnabled()
        compose.onNodeWithText("Choose a campsite").performClick()
        compose.onNodeWithText("Partner campsite · Jasper").performClick()
        compose.onNodeWithText("Send invitation").performClick()
        assertEquals("partner" to LotteryCollaborationAction.INVITE, chosen)
    }

    @Test fun drawingDisablesMembershipActions() {
        show(lottery.copy(drawCompleted = true, campsiteInvitations = mapOf("partner" to "partner-owner")), partner)
        compose.onNodeWithText("Accept invitation").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Decline").assertIsNotEnabled()
    }
}
