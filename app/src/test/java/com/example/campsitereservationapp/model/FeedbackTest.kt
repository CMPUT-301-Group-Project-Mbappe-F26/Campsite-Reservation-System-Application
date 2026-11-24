package com.example.campsitereservationapp.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/*
 * Unit tests for rating and reviewing a stay (US 01.13.01, US 01.13.02) and for a campsite's
 * average when a rating is added or removed (US 03.09.01).
 */
class FeedbackTest {

    private val today = "2026-11-22"
    private val stay = Reservation(
        id = "res-two-jack", entrantId = "entrant-amir", campsiteId = "site-two-jack",
        lotteryId = "lottery-summer", campsiteName = "Two Jack Lakeside",
        checkInDate = "2026-08-14", checkOutDate = "2026-08-16"
    )
    private val twoJack = Campsite(id = "site-two-jack", ownerId = "owner-maria", name = "Two Jack Lakeside")

    private fun rating(id: String, stars: Int) = CampsiteRating(id, "site-two-jack", "entrant-amir", stars, today)

    @Test
    fun entrantCanRateAndReviewAFinishedStay() {
        assertNull(stayFeedbackError(stay, "entrant-amir", today))
    }

    @Test
    fun otherEntrantsCannotRateTheStay() {
        assertEquals("This reservation does not belong to your account.", stayFeedbackError(stay, "entrant-sam", today))
    }

    @Test
    fun cancelledAndRejectedStaysCannotBeRated() {
        listOf(RESERVATION_CANCELLED, RESERVATION_REJECTED).forEach { status ->
            assertEquals(
                "You can only rate and review campsites you stayed at.",
                stayFeedbackError(stay.copy(status = status), "entrant-amir", today)
            )
        }
    }

    @Test
    fun stayCannotBeRatedUntilTheDayAfterCheckOut() {
        val checkingOutToday = stay.copy(checkInDate = "2026-11-20", checkOutDate = today)
        assertEquals(
            "You can rate and review this campsite after your stay.",
            stayFeedbackError(checkingOutToday, "entrant-amir", today)
        )
        assertNull(stayFeedbackError(checkingOutToday, "entrant-amir", "2026-11-23"))
    }

    @Test
    fun ratingsAreFromOneToFiveStars() {
        (1..5).forEach { assertNull(ratingError(it)) }
        assertNotNull(ratingError(0))
        assertNotNull(ratingError(6))
    }

    @Test
    fun blankReviewsAreNotAllowed() {
        assertEquals("Write something before submitting.", reviewError("  \n "))
        assertNull(reviewError("Quiet sites and a great lake view."))
    }

    @Test
    fun reviewLengthIgnoresSpacesAroundTheText() {
        assertNull(reviewError("a".repeat(REVIEW_MAX_LENGTH) + "   "))
        assertEquals("Reviews can be at most 1000 characters.", reviewError("a".repeat(REVIEW_MAX_LENGTH + 1)))
    }

    @Test
    fun firstRatingBecomesTheAverage() {
        val rated = twoJack.withRating(rating("res-1", 4))
        assertEquals(4.0, rated.averageRating!!, 0.001)
        assertEquals(1, rated.ratingCount)
        assertEquals(4, rated.ratingTotal)
        assertEquals("res-1", rated.lastRatingId)
    }

    @Test
    fun averageUsesEveryRating() {
        val rated = twoJack.withRating(rating("res-1", 5)).withRating(rating("res-2", 4)).withRating(rating("res-3", 4))
        assertEquals(13.0 / 3, rated.averageRating!!, 0.001)
        assertEquals(3, rated.ratingCount)
        assertEquals("res-3", rated.lastRatingId)
    }

    @Test
    fun removingARatingUpdatesTheAverage() {
        val rated = twoJack.withRating(rating("res-1", 5)).withRating(rating("res-2", 1))
        val after = rated.withoutRating(rating("res-2", 1))
        assertEquals(5.0, after.averageRating!!, 0.001)
        assertEquals(1, after.ratingCount)
        assertEquals(5, after.ratingTotal)
    }

    @Test
    fun removingTheLastRatingLeavesNoAverage() {
        val after = twoJack.withRating(rating("res-1", 2)).withoutRating(rating("res-1", 2))
        assertNull(after.averageRating)
        assertEquals(0, after.ratingCount)
        assertEquals(0, after.ratingTotal)
    }

    @Test
    fun ratingChangesNothingElseAboutTheCampsite() {
        val rated = twoJack.withRating(rating("res-1", 4))
        assertEquals(twoJack, rated.copy(averageRating = null, ratingCount = 0, ratingTotal = 0, lastRatingId = ""))
    }
}
