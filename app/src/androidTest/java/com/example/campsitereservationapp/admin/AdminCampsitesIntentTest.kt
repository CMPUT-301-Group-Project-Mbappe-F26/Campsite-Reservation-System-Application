package com.example.campsitereservationapp.admin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.profile.ProfileScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Intent tests for US 03.04.01 (admin browses campsites) and US 03.01.01 (admin removes
 * a campsite). The screens are given sample data directly, so no Firebase data is needed.
 */
@RunWith(AndroidJUnit4::class)
class AdminCampsitesIntentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val rows = listOf(
        AdminCampsiteRow("site-two-jack", "Two Jack Lakeside", "Banff, AB", "Maria Lopez", true),
        AdminCampsiteRow("site-whistlers", "Whistlers Campground", "Jasper, AB", "Devon Clarke", false)
    )

    @Test
    fun adminOpensCampsitesFromTheDashboard() {
        var opened = false
        composeRule.setContent { AdminDashboardScreen(onBack = {}, onCampsitesClick = { opened = true }) }
        composeRule.onNodeWithText("Campsites").performClick()
        assertTrue(opened)
    }

    @Test
    fun adminSeesTheAdminDashboardLinkOnTheirProfile() {
        composeRule.setContent { ProfileScreen(user = User(id = "admin-priya", name = "Priya", isAdmin = true), onEditClick = {}) }
        composeRule.onNodeWithText("Admin Dashboard").assertIsDisplayed()
    }

    @Test
    fun regularUserDoesNotSeeTheAdminDashboardLink() {
        composeRule.setContent { ProfileScreen(user = User(id = "entrant-amir", name = "Amir"), onEditClick = {}) }
        assertEquals(0, composeRule.onAllNodesWithText("Admin Dashboard").fetchSemanticsNodes().size)
    }

    @Test
    fun adminSeesNameLocationAndOwnerOfEveryCampsite() {
        composeRule.setContent { AdminCampsiteList(rows, removingId = null, onCampsiteClick = {}, onRemove = {}) }
        composeRule.onNodeWithText("2 campsites").assertIsDisplayed()
        composeRule.onNodeWithText("Two Jack Lakeside").assertIsDisplayed()
        composeRule.onNodeWithText("Jasper, AB").assertIsDisplayed()
        composeRule.onNodeWithText("Owner: Maria Lopez").assertIsDisplayed()
    }

    @Test
    fun adminSeesMessageWhenThereAreNoCampsites() {
        composeRule.setContent { AdminCampsiteList(emptyList(), removingId = null, onCampsiteClick = {}, onRemove = {}) }
        composeRule.onNodeWithText("No campsites found").assertIsDisplayed()
    }

    @Test
    fun tappingACampsiteOpensItsDetails() {
        var openedId: String? = null
        composeRule.setContent { AdminCampsiteList(rows, removingId = null, onCampsiteClick = { openedId = it }, onRemove = {}) }
        composeRule.onNodeWithText("Whistlers Campground").performClick()
        assertEquals("site-whistlers", openedId)
    }

    @Test
    fun removeAsksForConfirmationBeforeRemoving() {
        var removed: AdminCampsiteRow? = null
        composeRule.setContent { AdminCampsiteList(rows, removingId = null, onCampsiteClick = {}, onRemove = { removed = it }) }
        composeRule.onAllNodesWithText("Remove")[0].performClick()
        composeRule.onNodeWithText("Remove Two Jack Lakeside?").assertIsDisplayed()
        assertNull(removed)
        composeRule.onAllNodesWithText("Remove").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        assertEquals("site-two-jack", removed?.id)
    }

    @Test
    fun cancellingTheConfirmationKeepsTheCampsite() {
        var removed: AdminCampsiteRow? = null
        composeRule.setContent { AdminCampsiteList(rows, removingId = null, onCampsiteClick = {}, onRemove = { removed = it }) }
        composeRule.onAllNodesWithText("Remove")[0].performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        assertNull(removed)
        assertEquals(0, composeRule.onAllNodesWithText("Remove Two Jack Lakeside?").fetchSemanticsNodes().size)
    }
}
