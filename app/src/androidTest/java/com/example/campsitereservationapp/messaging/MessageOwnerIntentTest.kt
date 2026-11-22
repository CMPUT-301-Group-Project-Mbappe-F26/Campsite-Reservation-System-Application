package com.example.campsitereservationapp.messaging

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.campsitereservationapp.model.*
import com.example.campsitereservationapp.reservation.ReservationDetailsContent
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the entrant flow with local callbacks; no real messages are sent. */
@RunWith(AndroidJUnit4::class)
class MessageOwnerIntentTest {
    @get:Rule val composeRule = createComposeRule()
    private val booking = Reservation(id = "booking", entrantId = "guest", campsiteId = "site",
        campsiteName = "Two Jack", checkInDate = "2027-07-02", checkOutDate = "2027-07-04")

    @Test
    fun confirmedReservationOpensOwnerMessages() {
        var opened = false
        composeRule.setContent {
            ReservationDetailsContent(booking, null, onOpenMessages = { opened = true }, today = "2027-06-01")
        }
        composeRule.onNodeWithText("Message owner").performScrollTo().performClick()
        assertTrue(opened)
    }

    @Test
    fun rejectedReservationDoesNotOfferMessaging() {
        composeRule.setContent {
            ReservationDetailsContent(booking.copy(status = RESERVATION_REJECTED), null,
                onOpenMessages = {}, today = "2027-06-01")
        }
        composeRule.onNodeWithText("Message owner").assertDoesNotExist()
    }

    @Test
    fun cancelledReservationDoesNotOfferMessaging() {
        composeRule.setContent {
            ReservationDetailsContent(booking.copy(status = RESERVATION_CANCELLED), null,
                onOpenMessages = {}, today = "2027-06-01")
        }
        composeRule.onNodeWithText("Message owner").assertDoesNotExist()
    }

    @Test
    fun entrantCanSendTheFirstMessageAndBlankMessagesStayDisabled() {
        var sent = ""
        composeRule.setContent {
            Column { ReservationMessageComposer("booking", "guest") { text, _ -> sent = text } }
        }
        composeRule.onNodeWithContentDescription("Send message").assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performTextInput("   ")
        composeRule.onNodeWithContentDescription("Send message").assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("  What time is check-in?  ")
        composeRule.onNodeWithContentDescription("Send message").performClick()
        composeRule.waitUntil { sent.isNotEmpty() }
        assertEquals("What time is check-in?", sent)
        assertEquals("", composeRule.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
    }

    @Test
    fun failedSendKeepsTheDraftAndReusesItsIdForRetry() {
        val attempts = mutableListOf<Pair<String, String>>()
        composeRule.setContent {
            Column { ReservationMessageComposer("booking", "guest") { text, id ->
                attempts += text to id
                if (attempts.size == 1) error("Simulated connection error")
            } }
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("Can we arrive after 6?")
        composeRule.onNodeWithContentDescription("Send message").performClick()
        composeRule.onNodeWithText("Couldn't send your message.", substring = true).assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertTextContains("Can we arrive after 6?")
        composeRule.onNodeWithContentDescription("Send message").performClick()
        composeRule.waitUntil { attempts.size == 2 }
        assertEquals(attempts[0], attempts[1])
    }

    @Test
    fun changedReservationDisablesSending() {
        var calls = 0
        composeRule.setContent {
            Column { ReservationMessageComposer("booking", "guest", blockedReason = "This reservation is no longer confirmed.") { _, _ -> calls++ } }
        }
        composeRule.onNodeWithText("Message").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Send message").assertIsNotEnabled()
        assertEquals(0, calls)
    }

    @Test
    fun sendingPreventsAnotherClickAndOversizedMessagesAreNotSent() {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        composeRule.setContent {
            Column { ReservationMessageComposer("booking", "guest") { _, _ -> calls++; gate.await() } }
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("a".repeat(1001))
        composeRule.onNodeWithContentDescription("Send message").assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Hi there")
        composeRule.onNodeWithContentDescription("Send message").performClick()
        composeRule.onNodeWithText("Message").assertIsNotEnabled()
        assertEquals(1, calls)
        gate.complete(Unit)
        composeRule.waitForIdle()
    }
}
