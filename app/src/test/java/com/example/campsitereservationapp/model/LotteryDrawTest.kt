package com.example.campsitereservationapp.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LotteryDrawTest {
    private val entrants = listOf("alice", "bob", "charlie", "dana", "eli")
    private val lottery = Lottery(registered = entrants)

    @Test
    fun returnsEveryoneWhenRequestedWinnersExceedEntrants() {
        assertEquals(entrants, lottery.draw(10))
    }

    @Test
    fun returnsEveryoneWhenRequestedWinnersEqualEntrants() {
        assertEquals(entrants, lottery.draw(entrants.size))
    }

    @Test
    fun drawsRequestedNumberOfDistinctRegisteredUsers() {
        val winners = lottery.draw(3)
        assertEquals(3, winners.size)
        assertEquals(winners.size, winners.distinct().size)
        assertTrue(entrants.containsAll(winners))
    }

    @Test
    fun canDrawOneWinner() {
        val winners = lottery.draw(1)
        assertEquals(1, winners.size)
        assertTrue(entrants.containsAll(winners))
    }

    @Test
    fun emptyRegistrationReturnsNoWinners() {
        assertTrue(Lottery().draw(3).isEmpty())
    }

    @Test
    fun zeroRequestedWinnersReturnsNobody() {
        assertTrue(lottery.draw(0).isEmpty())
    }

    @Test
    fun rejectsNegativeWinnerCount() {
        assertThrows(IllegalArgumentException::class.java) { lottery.draw(-1) }
    }

    @Test
    fun duplicateRegistrationsCannotWinTwice() {
        val duplicates = Lottery(registered = listOf("alice", "alice", "bob", "bob", "charlie"))
        assertEquals(listOf("alice", "bob", "charlie"), duplicates.draw(10))
        val winners = duplicates.draw(2)
        assertEquals(2, winners.size)
        assertEquals(winners.size, winners.distinct().size)
    }

    @Test
    fun drawingLeavesRegistrationAndParticipantStatesUnchanged() {
        val original = lottery.copy(pending = listOf("pending-user"), booked = listOf("booked-user"))
        val snapshot = original.copy()
        original.draw(2)
        assertEquals(snapshot, original)
    }
}
