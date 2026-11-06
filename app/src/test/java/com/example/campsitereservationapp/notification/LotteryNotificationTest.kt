package com.example.campsitereservationapp.notification

import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.Reservation
import com.example.campsitereservationapp.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LotteryNotificationTest {
    private val lottery = Lottery(
        id = "draw",
        registered = listOf("winner", "loser"),
        pending = listOf("winner"),
        drawCompleted = true
    )

    @Test
    fun winnerGetsWinningResult() {
        assertEquals(LotteryResult.WON, lotteryResult(lottery, "winner"))
    }

    @Test
    fun entrantNotSelectedGetsLosingResult() {
        assertEquals(LotteryResult.LOST, lotteryResult(lottery, "loser"))
    }

    @Test
    fun noResultBeforeDrawOrForOtherUsers() {
        assertNull(lotteryResult(lottery.copy(drawCompleted = false), "winner"))
        assertNull(lotteryResult(lottery, "other"))
    }

    @Test
    fun bookedWinnerDoesNotGetOldNotification() {
        val booked = lottery.copy(
            pending = emptyList(),
            booked = listOf(Reservation(entrantId = "winner"))
        )
        assertNull(lotteryResult(booked, "winner"))
    }

    @Test
    fun notificationsAreOnByDefaultAndCanBeTurnedOff() {
        assertTrue(User().notificationsEnabled)
        assertEquals(false, User(notificationsEnabled = false).notificationsEnabled)
    }
}
