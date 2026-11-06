package com.example.campsitereservationapp.campsite

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Intent tests for US 02.11.01: a campsite owner types a policy on their campsite view
 * (US 02.01.04), and it is only saved after they confirm. Saving is replaced with a
 * stand-in, so no Firebase data is needed.
 */
@RunWith(AndroidJUnit4::class)
class OwnerPolicyEditorIntentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val currentRules = "Quiet hours from 10 PM to 7 AM."
    private val newRules = "  Quiet hours from 10 PM to 7 AM. No generators.  "
    private var savedDraft: String? = null
    private var shownRules: String? = null

    private fun showEditor() = composeRule.setContent {
        Column {
            OwnerPolicyEditor(
                campsiteId = "site-two-jack",
                rules = currentRules,
                onSaved = { shownRules = it },
                savePolicy = { savedDraft = it }
            )
        }
    }

    private fun typeNewPolicy() {
        composeRule.onNodeWithText("Edit policy").performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement(newRules)
    }

    @Test
    fun ownerCanTypeANewPolicy() {
        showEditor()
        composeRule.onNodeWithText("Edit policy").performClick()
        composeRule.onNodeWithText(currentRules).assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertIsDisplayed()
    }

    @Test
    fun cancelDiscardsTheDraft() {
        showEditor()
        typeNewPolicy()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Edit policy").assertIsDisplayed()
        assertNull(savedDraft)
    }

    @Test
    fun saveAsksAreYouSure() {
        showEditor()
        typeNewPolicy()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Are you sure?").assertIsDisplayed()
        assertNull(savedDraft)
    }

    @Test
    fun cancellingTheConfirmationDoesNotSave() {
        showEditor()
        typeNewPolicy()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onAllNodesWithText("Cancel").onLast().performClick()
        assertEquals(0, composeRule.onAllNodesWithText("Are you sure?").fetchSemanticsNodes().size)
        assertNull(savedDraft)
    }

    @Test
    fun proceedSavesThePolicy() {
        showEditor()
        typeNewPolicy()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Proceed").performClick()
        composeRule.waitUntil(5000) { shownRules != null }
        assertEquals(newRules, savedDraft)
        assertEquals(newRules.trim(), shownRules)
        composeRule.onNodeWithText("Edit policy").assertIsDisplayed()
    }
}
