package com.example.campsitereservationapp.admin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.campsitereservationapp.notification.AdminNotificationLog
import com.example.campsitereservationapp.notification.NotificationLogType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class AdminNotificationLogsIntentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun adminOpensNotificationLogsFromDashboard() {
        var opened = false
        composeRule.setContent {
            AdminDashboardScreen(
                onBack = {},
                onCampsitesClick = {},
                onNotificationLogsClick = { opened = true }
            )
        }

        composeRule.onNodeWithText("Notification Logs").performClick()
        assertTrue(opened)
    }

    @Test
    fun logShowsEveryRequiredField() {
        composeRule.setContent {
            AdminNotificationLogsScreen(
                onBack = {},
                loadLogs = {
                    listOf(
                        AdminNotificationLog(
                            id = "notice-1",
                            type = NotificationLogType.OWNER,
                            sender = "Daniel Roy",
                            recipients = listOf("Maya Thompson"),
                            title = "Road closure on Hwy 40",
                            message = "Use the Hwy 16 detour.",
                            campsite = "Patricia Lake",
                            sentAt = Date(1000)
                        )
                    )
                }
            )
        }

        composeRule.waitUntil(5000) {
            composeRule.onAllNodesWithText("Road closure on Hwy 40").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Notification logs").assertIsDisplayed()
        composeRule.onNodeWithText("Daniel Roy → Maya Thompson").assertIsDisplayed()
        composeRule.onNodeWithText("Use the Hwy 16 detour.").assertIsDisplayed()
        composeRule.onNodeWithText("Patricia Lake").assertIsDisplayed()
    }

    @Test
    fun emptyLogShowsTheRequiredMessage() {
        composeRule.setContent {
            AdminNotificationLogsScreen(onBack = {}, loadLogs = { emptyList() })
        }

        composeRule.waitUntil(5000) {
            composeRule.onAllNodesWithText("No notifications found").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("No notifications found").assertIsDisplayed()
    }
}
