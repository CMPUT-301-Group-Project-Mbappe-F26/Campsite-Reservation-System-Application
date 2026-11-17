package com.example.campsitereservationapp.notification

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.campsitereservationapp.model.Lottery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnerNotificationComposerIntentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val lottery = Lottery(id = "lottery-1", registered = listOf("entrant-1", "entrant-2"))
    private var sentTitle: String? = null
    private var sentMessage: String? = null

    private fun showForm() = composeRule.setContent {
        OwnerNotificationComposer(
            campsiteId = "campsite-1",
            campsiteName = "Two Jack Lake",
            lottery = lottery,
            sendNotification = { title, message ->
                sentTitle = title
                sentMessage = message
            }
        )
    }

    private fun enterNotification() {
        composeRule.onNodeWithText("Notify entrants").performClick()
        composeRule.onNodeWithTag("notificationTitle").performTextReplacement("  Weather update  ")
        composeRule.onNodeWithTag("notificationMessage").performTextReplacement("  Heavy rain is expected.  ")
    }

    @Test
    fun formUsesTheWikiMockupLabels() {
        showForm()
        composeRule.onNodeWithText("Notify entrants").performClick()
        composeRule.onNodeWithText("New notification").assertIsDisplayed()
        composeRule.onNodeWithText("Send to").assertIsDisplayed()
        composeRule.onNodeWithText("Everyone").assertIsDisplayed()
        composeRule.onNodeWithText("Title").assertIsDisplayed()
        composeRule.onNodeWithText("Message").assertIsDisplayed()
        composeRule.onNodeWithText("Preview").assertIsDisplayed()
        composeRule.onNodeWithText("Send to 2 entrants").assertIsDisplayed()
    }

    @Test
    fun cancelClosesTheFormWithoutSending() {
        showForm()
        enterNotification()
        composeRule.onNodeWithTag("closeNotification").performClick()
        composeRule.onNodeWithText("Notify entrants").assertIsDisplayed()
        assertNull(sentTitle)
    }

    @Test
    fun sendAsksForConfirmation() {
        showForm()
        enterNotification()
        composeRule.onNodeWithText("Send to 2 entrants").performClick()
        composeRule.onNodeWithText("Are you sure?").assertIsDisplayed()
        assertNull(sentTitle)
    }

    @Test
    fun cancellingConfirmationDoesNotSend() {
        showForm()
        enterNotification()
        composeRule.onNodeWithText("Send to 2 entrants").performClick()
        composeRule.onAllNodesWithText("Cancel").onLast().performClick()
        assertEquals(0, composeRule.onAllNodesWithText("Are you sure?").fetchSemanticsNodes().size)
        assertNull(sentTitle)
    }

    @Test
    fun proceedSendsTheTrimmedNotification() {
        showForm()
        enterNotification()
        composeRule.onNodeWithText("Send to 2 entrants").performClick()
        composeRule.onNodeWithText("Proceed").performClick()
        composeRule.waitUntil(5000) { sentTitle != null }
        assertEquals("Weather update", sentTitle)
        assertEquals("Heavy rain is expected.", sentMessage)
        composeRule.onNodeWithText("Notify entrants").assertIsDisplayed()
    }
}
