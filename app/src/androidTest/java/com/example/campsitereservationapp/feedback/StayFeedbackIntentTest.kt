package com.example.campsitereservationapp.feedback

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.campsitereservationapp.model.Reservation
import com.example.campsitereservationapp.reservation.MyReservationsContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Intent tests for US 01.13.01 (entrant rates a campsite) and US 01.13.02 (entrant posts a
 * review), both from a finished stay in My Reservations. Saving is replaced with stand-ins,
 * so no Firebase data is needed.
 */
@RunWith(AndroidJUnit4::class)
class StayFeedbackIntentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val stay = Reservation(
        id = "res-two-jack", entrantId = "entrant-amir", campsiteId = "site-two-jack",
        lotteryId = "lottery-summer", campsiteName = "Two Jack Lakeside",
        checkInDate = "2026-08-14", checkOutDate = "2026-08-16"
    )
    private val upcoming = stay.copy(
        id = "res-whistlers", campsiteName = "Whistlers Campground", checkInDate = "2027-08-14", checkOutDate = "2027-08-16"
    )

    private fun show(
        feedback: StayFeedback? = StayFeedback(),
        onRate: suspend (Reservation, Int) -> String? = { _, _ -> null },
        onReview: suspend (Reservation, String) -> String? = { _, _ -> null },
        openPast: Boolean = true
    ) {
        composeRule.setContent {
            MyReservationsContent(emptyList(), listOf(upcoming), loading = false, error = "", onRetry = {},
                onReservationClick = {}, onReserveClick = {}, past = listOf(stay), feedback = feedback,
                onRate = onRate, onReview = onReview)
        }
        if (openPast) composeRule.onNodeWithText("Past").performClick()
    }

    @Test
    fun upcomingStaysCannotBeRatedOrReviewed() {
        show(openPast = false)
        composeRule.onNodeWithText("Whistlers Campground").assertIsDisplayed()
        composeRule.onNodeWithText("Rate").assertDoesNotExist()
        composeRule.onNodeWithText("Write review").assertDoesNotExist()
    }

    @Test
    fun entrantRatesAFinishedStay() {
        var saved: Pair<String, Int>? = null
        show(onRate = { stay, stars -> saved = stay.id to stars; null })
        composeRule.onNodeWithText("Rate").performClick()
        composeRule.onNodeWithText("Rate Two Jack Lakeside").assertIsDisplayed()
        composeRule.onNodeWithText("Submit").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("4 stars").performClick()
        composeRule.onNodeWithText("Submit").performClick()
        composeRule.waitForIdle()
        assertEquals("res-two-jack" to 4, saved)
        composeRule.onNodeWithText("Rate Two Jack Lakeside").assertDoesNotExist()
    }

    @Test
    fun entrantPostsAReviewOfAFinishedStay() {
        var saved: Pair<String, String>? = null
        show(onReview = { stay, text -> saved = stay.id to text; null })
        composeRule.onNodeWithText("Write review").performClick()
        composeRule.onNodeWithText("Review Two Jack Lakeside").assertIsDisplayed()
        composeRule.onNodeWithText("Submit").assertIsNotEnabled()
        composeRule.onNodeWithText("Your review").performTextInput("Quiet sites and a great lake view.")
        composeRule.onNodeWithText("Submit").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        assertEquals("res-two-jack" to "Quiet sites and a great lake view.", saved)
        composeRule.onNodeWithText("Review Two Jack Lakeside").assertDoesNotExist()
    }

    @Test
    fun cancellingLeavesNoRating() {
        var saved: Int? = null
        show(onRate = { _, stars -> saved = stars; null })
        composeRule.onNodeWithText("Rate").performClick()
        composeRule.onNodeWithContentDescription("1 star").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Rate Two Jack Lakeside").assertDoesNotExist()
        assertNull(saved)
    }

    @Test
    fun failedReviewStaysOpenWithItsText() {
        show(onReview = { _, _ -> "Could not save your review. Check your connection and try again." })
        composeRule.onNodeWithText("Write review").performClick()
        composeRule.onNodeWithText("Your review").performTextInput("Great fire pits.")
        composeRule.onNodeWithText("Submit").performClick()
        composeRule.onNodeWithText("Could not save your review. Check your connection and try again.").assertIsDisplayed()
        composeRule.onNodeWithText("Great fire pits.").assertIsDisplayed()
    }

    @Test
    fun ratedAndReviewedStayShowsWhatWasLeftInstead() {
        show(StayFeedback(stars = mapOf("res-two-jack" to 4), reviewed = setOf("res-two-jack")))
        composeRule.onNodeWithText("Your rating: ★★★★☆").assertIsDisplayed()
        composeRule.onNodeWithText("Review posted").assertIsDisplayed()
        composeRule.onNodeWithText("Rate").assertDoesNotExist()
        composeRule.onNodeWithText("Write review").assertDoesNotExist()
    }

    @Test
    fun buttonsAreHiddenWhenRatingsCouldNotBeLoaded() {
        show(feedback = null)
        composeRule.onNodeWithText("Two Jack Lakeside").assertIsDisplayed()
        composeRule.onNodeWithText("Rate").assertDoesNotExist()
    }
}
