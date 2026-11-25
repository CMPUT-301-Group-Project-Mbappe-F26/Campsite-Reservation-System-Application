package com.example.campsitereservationapp.admin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.campsitereservationapp.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Intent tests for US 03.05.01 (admin browses profiles) and US 03.02.01 (admin removes a
 * profile). The list is given sample profiles directly, so no Firebase data is needed.
 */
@RunWith(AndroidJUnit4::class)
class AdminProfilesIntentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val priya = User(id = "admin-priya", name = "Priya Nair", isAdmin = true)
    private val amir = User(id = "entrant-amir", name = "Amir Haddad", email = "amir@example.com")
    private val rows = listOf(amir, priya)

    private fun show(onRemove: (User) -> Unit = {}, onOpen: (String) -> Unit = {}) = composeRule.setContent {
        AdminProfileList(rows, currentUserId = "admin-priya", removingId = null, onProfileClick = onOpen, onRemove = onRemove)
    }

    @Test
    fun adminOpensProfilesFromTheDashboard() {
        var opened = false
        composeRule.setContent { AdminDashboardScreen(onBack = {}, onCampsitesClick = {}, onProfilesClick = { opened = true }) }
        composeRule.onNodeWithText("Profiles").performClick()
        assertTrue(opened)
    }

    @Test
    fun adminSeesEveryProfile() {
        show()
        composeRule.onNodeWithText("2 profiles").assertIsDisplayed()
        composeRule.onNodeWithText("Amir Haddad").assertIsDisplayed()
        composeRule.onNodeWithText("amir@example.com").assertIsDisplayed()
        composeRule.onNodeWithText("Priya Nair").assertIsDisplayed()
    }

    @Test
    fun adminCannotRemoveTheirOwnProfile() {
        show()
        composeRule.onNodeWithText("You").assertIsDisplayed()
        assertEquals(1, composeRule.onAllNodesWithText("Remove").fetchSemanticsNodes().size)
    }

    @Test
    fun removeAsksForConfirmationBeforeRemoving() {
        var removed: User? = null
        show(onRemove = { removed = it })
        composeRule.onNodeWithText("Remove").performClick()
        composeRule.onNodeWithText("Remove Amir Haddad?").assertIsDisplayed()
        assertNull(removed)
        composeRule.onAllNodesWithText("Remove").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        assertEquals("entrant-amir", removed?.id)
    }

    @Test
    fun cancellingTheConfirmationKeepsTheProfile() {
        var removed: User? = null
        show(onRemove = { removed = it })
        composeRule.onNodeWithText("Remove").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        assertNull(removed)
        composeRule.onNodeWithText("Remove Amir Haddad?").assertDoesNotExist()
    }

    @Test
    fun tappingAProfileOpensIt() {
        var opened: String? = null
        show(onOpen = { opened = it })
        composeRule.onNodeWithText("Amir Haddad").performClick()
        assertEquals("entrant-amir", opened)
    }

    @Test
    fun searchFiltersProfiles() {
        show()
        composeRule.onNodeWithText("Search name, email or city").performTextInput("priya")
        composeRule.onNodeWithText("Priya Nair").assertIsDisplayed()
        composeRule.onNodeWithText("Amir Haddad").assertDoesNotExist()
    }

    @Test
    fun ownProfileDetailsHaveNoRemoveButton() {
        composeRule.setContent { AdminProfileDetails(priya, canRemove = false, removing = false, onRemove = {}) }
        composeRule.onNodeWithText("This is your profile. You can't remove it from here.").assertIsDisplayed()
        composeRule.onNodeWithText("Remove profile").assertDoesNotExist()
    }
}
