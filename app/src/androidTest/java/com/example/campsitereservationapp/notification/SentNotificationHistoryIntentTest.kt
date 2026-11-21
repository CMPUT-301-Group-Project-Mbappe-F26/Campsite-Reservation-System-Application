package com.example.campsitereservationapp.notification

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class SentNotificationHistoryIntentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun historyMatchesTheWikiMockup() {
        composeRule.setContent {
            SentNotificationHistory(
                campsiteId = "camp-1",
                onBack = {},
                onNewNotification = {},
                loadNotifications = {
                    listOf(
                        SentNotification(
                            id = "notice-1",
                            title = "Road closure on Hwy 40",
                            message = "Use the Hwy 16 detour.",
                            recipientCount = 128,
                            campsiteName = "Two Jack Lake",
                            createdAt = Date(1000)
                        )
                    )
                }
            )
        }

        composeRule.waitUntil(5000) {
            composeRule.onAllNodesWithText("Road closure on Hwy 40").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Sent notifications").assertIsDisplayed()
        composeRule.onNodeWithText("All lotteries").assertIsDisplayed()
        composeRule.onNodeWithText("Road closure on Hwy 40").assertIsDisplayed()
        composeRule.onNodeWithText("Use the Hwy 16 detour.").assertIsDisplayed()
        composeRule.onNodeWithText("128 entrants · Two Jack Lake").assertIsDisplayed()
    }

    @Test
    fun backButtonReturnsToTheCampsite() {
        var wentBack = false
        composeRule.setContent {
            SentNotificationHistory(
                campsiteId = "camp-1",
                onBack = { wentBack = true },
                onNewNotification = {},
                loadNotifications = { emptyList() }
            )
        }

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(wentBack)
    }
}
