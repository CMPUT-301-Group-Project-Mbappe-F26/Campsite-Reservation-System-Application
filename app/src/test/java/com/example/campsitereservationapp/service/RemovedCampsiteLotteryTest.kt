package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.Reservation
import org.junit.Assert.*
import org.junit.Test

/*
 * Unit tests for how a campsite's lotteries are closed when an admin removes it
 * (US 03.01.01): active lotteries are closed and their waiting lists cleared.
 */
class RemovedCampsiteLotteryTest {

    private val openLottery = Lottery(
        id = "lottery-summer",
        ownerId = "owner-maria",
        campsiteId = "site-two-jack",
        numberOfWinners = 3,
        registrationStartDate = "2027-05-01",
        registrationEndDate = "2027-05-15",
        registered = listOf("entrant-amir", "entrant-jen", "entrant-li")
    )

    private val drawnLottery = openLottery.copy(
        pending = listOf("entrant-jen"),
        drawCompleted = true
    )

    private val finishedLottery = drawnLottery.copy(pending = emptyList())

    @Test
    fun undrawnLotteryIsActive() {
        assertTrue(isLotteryActive(openLottery))
    }

    @Test
    fun drawnLotteryWithPendingWinnersIsActive() {
        assertTrue(isLotteryActive(drawnLottery))
    }

    @Test
    fun finishedLotteryIsNotActive() {
        assertFalse(isLotteryActive(finishedLottery))
    }

    @Test
    fun closingClearsTheWaitingList() {
        assertTrue(closeLotteryForRemovedCampsite(openLottery).registered.isEmpty())
    }

    @Test
    fun closingClearsPendingWinners() {
        assertTrue(closeLotteryForRemovedCampsite(drawnLottery).pending.isEmpty())
    }

    @Test
    fun closedLotteryCannotBeDrawnAndIsNoLongerActive() {
        val closed = closeLotteryForRemovedCampsite(openLottery)
        assertTrue(closed.drawCompleted)
        assertFalse(isLotteryActive(closed))
        assertNull(currentLottery(listOf(closed)))
    }

    @Test
    fun closingKeepsBookedReservations() {
        val booking = Reservation(id = "res-1", entrantId = "entrant-amir")
        val closed = closeLotteryForRemovedCampsite(drawnLottery.copy(booked = listOf(booking)))
        assertEquals(listOf(booking), closed.booked)
    }

    @Test
    fun closingKeepsTheLotteryDetails() {
        val closed = closeLotteryForRemovedCampsite(openLottery)
        assertEquals(openLottery.id, closed.id)
        assertEquals(openLottery.campsiteId, closed.campsiteId)
        assertEquals(openLottery.registrationEndDate, closed.registrationEndDate)
    }
}
