package com.example.campsitereservationapp.feedback

import com.example.campsitereservationapp.home.getRatingText
import com.example.campsitereservationapp.model.CampsiteRating
import com.example.campsitereservationapp.model.CampsiteReview
import com.example.campsitereservationapp.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Unit tests for the review list on a campsite (US 01.13.02) and the lists an admin removes
 * ratings and reviews from (US 03.09.01, US 03.10.01).
 */
class FeedbackEntriesTest {

    private val users = listOf(User(id = "entrant-sam", name = " Sam Okafor "), User(id = "entrant-guest"))

    private fun review(id: String, entrant: String, date: String) =
        CampsiteReview(id, "site-two-jack", entrant, "Quiet sites and a great lake view.", date)

    @Test
    fun reviewsAreListedNewestFirst() {
        val entries = reviewEntries(
            listOf(review("r1", "entrant-sam", "2026-07-01"), review("r2", "entrant-sam", "2026-09-12")), users
        )
        assertEquals(listOf("r2", "r1"), entries.map { it.id })
    }

    @Test
    fun reviewShowsTheEntrantsNameDateAndText() {
        val entry = reviewEntries(listOf(review("r1", "entrant-sam", "2026-07-01")), users).single()
        assertEquals(FeedbackEntry("r1", "Sam Okafor", "2026-07-01", "Quiet sites and a great lake view."), entry)
    }

    @Test
    fun guestsAndDeletedAccountsGetPlaceholderNames() {
        val entries = reviewEntries(
            listOf(review("r1", "entrant-guest", "2026-09-12"), review("r2", "entrant-deleted", "2026-07-01")), users
        )
        assertEquals(listOf("Guest entrant", "Former entrant"), entries.map { it.entrantName })
    }

    @Test
    fun ratingsAreShownAsStarsNewestFirst() {
        val entries = ratingEntries(
            listOf(
                CampsiteRating("r1", "site-two-jack", "entrant-sam", 2, "2026-07-01"),
                CampsiteRating("r2", "site-two-jack", "entrant-sam", 5, "2026-09-12")
            ), users
        )
        assertEquals(listOf("★★★★★", "★★☆☆☆"), entries.map { it.text })
    }

    @Test
    fun campsiteWithNoFeedbackHasNoEntries() {
        assertTrue(reviewEntries(emptyList(), users).isEmpty())
        assertTrue(ratingEntries(emptyList(), users).isEmpty())
    }

    @Test
    fun averagesAreShownToOneDecimalPlace() {
        assertEquals("4.3 / 5", getRatingText(13.0 / 3))
    }
}
