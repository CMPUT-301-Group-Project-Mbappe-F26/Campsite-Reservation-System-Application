package com.example.campsitereservationapp.feedback

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Intent tests for the reviews on a campsite (US 01.13.02) and for an admin removing ratings
 * (US 03.09.01) and reviews (US 03.10.01). The lists are given sample data directly, so no
 * Firebase data is needed.
 */
@RunWith(AndroidJUnit4::class)
class CampsiteFeedbackIntentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val reviews = listOf(
        FeedbackEntry("res-sam", "Sam Okafor", "2026-09-12", "Quiet sites and a great lake view."),
        FeedbackEntry("res-priya", "Priya Nair", "2026-07-01", "Showers were closed all weekend.")
    )
    private val ratings = listOf(
        FeedbackEntry("res-sam", "Sam Okafor", "2026-09-12", "★★★★★"),
        FeedbackEntry("res-priya", "Priya Nair", "2026-07-01", "★☆☆☆☆")
    )

    private fun showReviews(entries: List<FeedbackEntry> = reviews, onRemove: ((FeedbackEntry) -> Unit)? = null,
        removingId: String? = null, message: String = "") = composeRule.setContent {
        FeedbackList("Reviews", "review", entries, "No reviews yet.", onRemove, removingId, message)
    }

    private fun showRatings(onRemove: (FeedbackEntry) -> Unit) = composeRule.setContent {
        FeedbackList("Ratings", "rating", ratings, "No ratings yet.", onRemove)
    }

    @Test
    fun campsiteShowsEachReviewWithNameAndDate() {
        showReviews()
        composeRule.onNodeWithText("Reviews (2)").assertIsDisplayed()
        composeRule.onNodeWithText("Sam Okafor").assertIsDisplayed()
        composeRule.onNodeWithText("Sep 12, 2026").assertIsDisplayed()
        composeRule.onNodeWithText("Quiet sites and a great lake view.").assertIsDisplayed()
        composeRule.onNodeWithText("Priya Nair").assertIsDisplayed()
    }

    @Test
    fun campsiteWithoutReviewsSaysSo() {
        showReviews(emptyList())
        composeRule.onNodeWithText("No reviews yet.").assertIsDisplayed()
    }

    @Test
    fun entrantsCannotRemoveReviews() {
        showReviews()
        composeRule.onNodeWithText("Remove").assertDoesNotExist()
    }

    @Test
    fun adminRemovesAReviewAfterConfirming() {
        var removed: String? = null
        showReviews(onRemove = { removed = it.id })
        composeRule.onAllNodesWithText("Remove").onFirst().performClick()
        composeRule.onNodeWithText("Remove Sam Okafor's review?").assertIsDisplayed()
        assertNull(removed)
        composeRule.onNodeWithText("Yes, remove").performClick()
        assertEquals("res-sam", removed)
    }

    @Test
    fun adminCanKeepAReview() {
        var removed: String? = null
        showReviews(onRemove = { removed = it.id })
        composeRule.onAllNodesWithText("Remove").onFirst().performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Remove Sam Okafor's review?").assertDoesNotExist()
        assertNull(removed)
    }

    @Test
    fun adminSeesEveryRatingOfACampsite() {
        showRatings(onRemove = {})
        composeRule.onNodeWithText("Ratings (2)").assertIsDisplayed()
        composeRule.onNodeWithText("★★★★★").assertIsDisplayed()
        composeRule.onNodeWithText("★☆☆☆☆").assertIsDisplayed()
    }

    @Test
    fun adminRemovesARatingAfterConfirming() {
        var removed: String? = null
        showRatings(onRemove = { removed = it.id })
        composeRule.onAllNodesWithText("Remove")[1].performClick()
        composeRule.onNodeWithText("Remove Priya Nair's rating?").assertIsDisplayed()
        composeRule.onNodeWithText("The campsite's average rating will be updated. This cannot be undone.").assertIsDisplayed()
        composeRule.onNodeWithText("Yes, remove").performClick()
        assertEquals("res-priya", removed)
    }

    @Test
    fun removeButtonsAreDisabledWhileRemoving() {
        showReviews(onRemove = {}, removingId = "res-sam")
        composeRule.onNodeWithText("Removing…").assertIsNotEnabled()
        composeRule.onNodeWithText("Remove").assertIsNotEnabled()
    }

    @Test
    fun adminIsToldTheReviewWasRemoved() {
        showReviews(entries = reviews.drop(1), onRemove = {}, message = "The review was removed.")
        composeRule.onNodeWithText("The review was removed.").assertIsDisplayed()
        composeRule.onNodeWithText("Sam Okafor").assertDoesNotExist()
    }
}
