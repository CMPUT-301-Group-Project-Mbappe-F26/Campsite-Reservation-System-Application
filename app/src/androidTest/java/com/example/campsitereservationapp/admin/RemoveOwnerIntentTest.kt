package com.example.campsitereservationapp.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.campsitereservationapp.model.User
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Intent tests for US 03.07.01: an admin removes a campsite owner who violates app policy
 * from the owner's profile. The profile is given directly, so no Firebase data is needed.
 */
@RunWith(AndroidJUnit4::class)
class RemoveOwnerIntentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val maria = User(id = "owner-maria", name = "Maria Lopez", email = "maria@example.com", isOwner = true)
    private val amir = User(id = "entrant-amir", name = "Amir Haddad")

    private fun show(user: User = maria, canRemoveOwner: Boolean = true, removingOwner: Boolean = false, onRemoveOwner: () -> Unit = {}) =
        composeRule.setContent {
            // The real screen scrolls its content, so the test does too.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AdminProfileDetails(
                    user, canRemove = true, removing = false, onRemove = {},
                    canRemoveOwner = canRemoveOwner, removingOwner = removingOwner, onRemoveOwner = onRemoveOwner
                )
            }
        }

    @Test
    fun ownersProfileHasARemoveOwnerButton() {
        show()
        composeRule.onNodeWithText("Campsite Owner").assertIsDisplayed()
        composeRule.onNodeWithText("Remove owner").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun entrantsProfileHasNoRemoveOwnerButton() {
        show(user = amir, canRemoveOwner = false)
        composeRule.onNodeWithText("Remove owner").assertDoesNotExist()
        composeRule.onNodeWithText("Remove profile").assertExists()
    }

    @Test
    fun adminIsWarnedThatAllCampsitesWillBeRemoved() {
        var removed = 0
        show(onRemoveOwner = { removed++ })
        composeRule.onNodeWithText("Remove owner").performScrollTo().performClick()
        composeRule.onNodeWithText("Remove Maria Lopez as an owner?").assertIsDisplayed()
        composeRule.onNodeWithText(REMOVE_OWNER_WARNING).assertIsDisplayed()
        assertEquals("Nothing is removed before the admin confirms", 0, removed)
    }

    @Test
    fun confirmingRemovesTheOwner() {
        var removed = 0
        show(onRemoveOwner = { removed++ })
        composeRule.onNodeWithText("Remove owner").performScrollTo().performClick()
        composeRule.onAllNodesWithText("Remove owner").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        assertEquals(1, removed)
        composeRule.onNodeWithText("Remove Maria Lopez as an owner?").assertDoesNotExist()
    }

    @Test
    fun cancellingKeepsTheOwner() {
        var removed = 0
        show(onRemoveOwner = { removed++ })
        composeRule.onNodeWithText("Remove owner").performScrollTo().performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(0, removed)
        composeRule.onNodeWithText("Remove Maria Lopez as an owner?").assertDoesNotExist()
    }

    @Test
    fun buttonShowsProgressWhileRemoving() {
        show(removingOwner = true)
        composeRule.onNodeWithText("Removing owner...").performScrollTo().assertIsNotEnabled()
    }
}
