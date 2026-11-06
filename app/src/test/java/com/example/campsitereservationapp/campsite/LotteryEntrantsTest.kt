package com.example.campsitereservationapp.campsite

import com.example.campsitereservationapp.model.User
import org.junit.Assert.*
import org.junit.Test

/*
 * Unit tests for lotteryEntrants, which turns a lottery's waiting list into the
 * names a campsite owner sees (US 02.09.01).
 */
class LotteryEntrantsTest {

    private val amir = User(id = "entrant-amir", name = "Amir Haddad", city = "Edmonton")
    private val jen = User(id = "entrant-jen", name = "Jen Okafor", city = "Calgary")
    private val guest = User(id = "entrant-guest", name = "  ")

    @Test
    fun emptyWaitingListHasNoEntrants() {
        assertTrue(lotteryEntrants(emptyList(), listOf(amir)).isEmpty())
    }

    @Test
    fun entrantsKeepTheOrderTheyJoined() {
        val entrants = lotteryEntrants(listOf("entrant-jen", "entrant-amir"), listOf(amir, jen))
        assertEquals(listOf("Jen Okafor", "Amir Haddad"), entrants.map { it.name })
    }

    @Test
    fun entrantShowsTheirCity() {
        assertEquals("Edmonton", lotteryEntrants(listOf("entrant-amir"), listOf(amir)).single().city)
    }

    @Test
    fun duplicateRegistrationIsShownOnce() {
        val entrants = lotteryEntrants(listOf("entrant-amir", "entrant-amir"), listOf(amir))
        assertEquals(1, entrants.size)
    }

    @Test
    fun entrantWithoutNameIsShownAsGuest() {
        assertEquals("Guest entrant", lotteryEntrants(listOf("entrant-guest"), listOf(guest)).single().name)
    }

    @Test
    fun entrantWithoutProfileIsShownAsUnknown() {
        val entrant = lotteryEntrants(listOf("entrant-deleted"), listOf(amir)).single()
        assertEquals("Unknown entrant", entrant.name)
        assertEquals("", entrant.city)
    }

    @Test
    fun usersNotOnTheWaitingListAreIgnored() {
        val entrants = lotteryEntrants(listOf("entrant-jen"), listOf(amir, jen))
        assertEquals(listOf("entrant-jen"), entrants.map { it.id })
    }
}
