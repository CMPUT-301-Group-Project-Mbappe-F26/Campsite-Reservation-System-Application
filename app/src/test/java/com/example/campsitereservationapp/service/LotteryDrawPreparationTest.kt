package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.Reservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LotteryDrawPreparationTest {
    private val lottery = Lottery(
        numberOfWinners = 2, registrationEndDate = "2027-04-07",
        registered = listOf("alice", "bob", "charlie")
    )
    private val deadline = Instant.parse("2027-04-08T06:00:00Z").toEpochMilli()

    @Test
    fun registrationRemainsOpenThroughTheLastMinuteInAlberta() {
        assertThrows(IllegalStateException::class.java) { prepareLotteryDraw(lottery, deadline - 1) }
    }

    @Test
    fun midnightAfterRegistrationDrawsAndMarksCompletionWithoutChangingOriginal() {
        val drawn = prepareLotteryDraw(lottery, deadline)
        assertTrue(drawn.drawCompleted)
        assertEquals(2, drawn.pending.size)
        assertTrue(lottery.registered.containsAll(drawn.pending))
        assertEquals(drawn.pending.size, drawn.pending.distinct().size)
        assertEquals(lottery.registered, drawn.registered)
        assertEquals(lottery.booked, drawn.booked)
        assertFalse(lottery.drawCompleted)
        assertTrue(lottery.pending.isEmpty())
    }

    @Test
    fun laterRetryReturnsTheSavedDraw() {
        val drawn = prepareLotteryDraw(lottery, deadline)
        assertEquals(drawn, prepareLotteryDraw(drawn, deadline + 86400000L))
    }

    @Test
    fun completedDrawIsPreservedAfterWinnersBook() {
        val bookings = listOf(
            Reservation(id = "alice-booking", entrantId = "alice", checkInDate = "2027-05-01", checkOutDate = "2027-05-03"),
            Reservation(id = "bob-booking", entrantId = "bob", checkInDate = "2027-05-03", checkOutDate = "2027-05-05")
        )
        val booked = lottery.copy(drawCompleted = true, pending = emptyList(), booked = bookings)
        assertEquals(booked, prepareLotteryDraw(booked, deadline))
    }

    @Test
    fun noEntrantsStillProducesACompletedDrawThatCannotRunAgain() {
        val drawn = prepareLotteryDraw(lottery.copy(registered = emptyList()), deadline)
        assertTrue(drawn.drawCompleted)
        assertTrue(drawn.pending.isEmpty())
        val changed = drawn.copy(registered = listOf("late-user"))
        assertEquals(changed, prepareLotteryDraw(changed, deadline + 1))
    }

    @Test
    fun fewerEntrantsThanWinnersReturnsEveryEntrant() {
        val drawn = prepareLotteryDraw(lottery.copy(numberOfWinners = 5), deadline)
        assertEquals(lottery.registered, drawn.pending)
    }

    @Test
    fun existingParticipantStatesWithoutCompletionMarkerAreNotOverwritten() {
        assertThrows(IllegalStateException::class.java) {
            prepareLotteryDraw(lottery.copy(pending = listOf("alice")), deadline)
        }
        assertThrows(IllegalStateException::class.java) {
            prepareLotteryDraw(lottery.copy(booked = listOf(Reservation(entrantId = "alice"))), deadline)
        }
    }

    @Test
    fun invalidWinnerCountsAreRejected() {
        for (count in listOf(0, -1)) {
            assertThrows(IllegalArgumentException::class.java) {
                prepareLotteryDraw(lottery.copy(numberOfWinners = count), deadline)
            }
        }
    }

    @Test
    fun missingMalformedAndImpossibleDatesAreRejected() {
        for (date in listOf("", "2027-4-07", "2027-02-29", "2027-04-07extra")) {
            assertThrows(IllegalArgumentException::class.java) {
                prepareLotteryDraw(lottery.copy(registrationEndDate = date), deadline)
            }
        }
    }

    @Test
    fun closingInstantUsesAlbertaTimeInsteadOfDeviceDefaultTimeZone() {
        assertEquals(deadline, lotteryDrawOpensAt("2027-04-07"))
    }

    @Test
    fun spring2027UsesPermanentAlbertaTime() {
        assertEquals(Instant.parse("2027-03-15T06:00:00Z").toEpochMilli(), lotteryDrawOpensAt("2027-03-14"))
        assertEquals(24L * 60 * 60 * 1000, lotteryDrawOpensAt("2027-03-14") - lotteryDrawOpensAt("2027-03-13"))
    }

    @Test
    fun fall2027UsesPermanentAlbertaTimeWithoutFallingBack() {
        assertEquals(Instant.parse("2027-11-08T06:00:00Z").toEpochMilli(), lotteryDrawOpensAt("2027-11-07"))
        assertEquals(24L * 60 * 60 * 1000, lotteryDrawOpensAt("2027-11-07") - lotteryDrawOpensAt("2027-11-06"))
    }

    @Test
    fun winter2027UsesUtcMinusSix() {
        assertEquals(Instant.parse("2027-01-16T06:00:00Z").toEpochMilli(), lotteryDrawOpensAt("2027-01-15"))
    }

    @Test
    fun november2026PolicyBoundaryDoesNotFallBack() {
        assertEquals(Instant.parse("2026-11-01T06:00:00Z").toEpochMilli(), lotteryDrawOpensAt("2026-10-31"))
        assertEquals(Instant.parse("2026-11-02T06:00:00Z").toEpochMilli(), lotteryDrawOpensAt("2026-11-01"))
    }

    @Test
    fun historicalSpringTransitionStillHas23Hours() {
        assertEquals(Instant.parse("2025-03-10T06:00:00Z").toEpochMilli(), lotteryDrawOpensAt("2025-03-09"))
        assertEquals(23L * 60 * 60 * 1000, lotteryDrawOpensAt("2025-03-09") - lotteryDrawOpensAt("2025-03-08"))
    }

    @Test
    fun historicalFallTransitionStillHas25Hours() {
        assertEquals(Instant.parse("2025-11-03T07:00:00Z").toEpochMilli(), lotteryDrawOpensAt("2025-11-02"))
        assertEquals(25L * 60 * 60 * 1000, lotteryDrawOpensAt("2025-11-02") - lotteryDrawOpensAt("2025-11-01"))
    }
}
