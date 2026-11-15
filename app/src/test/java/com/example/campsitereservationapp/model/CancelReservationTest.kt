package com.example.campsitereservationapp.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Unit tests for cancelling a reservation (US 01.07.01): who can cancel and when, how the
 * status is shown, and that a cancelled stay frees its nights and its spot in the lottery.
 */
class CancelReservationTest {
    private val today = "2027-06-01"

    private val amirAtTwoJack = Reservation(
        id = "res-two-jack", entrantId = "entrant-amir", campsiteId = "site-two-jack",
        lotteryId = "lottery-summer", campsiteName = "Two Jack Lakeside",
        checkInDate = "2027-07-02", checkOutDate = "2027-07-04"
    )

    @Test
    fun entrantCanCancelAStayThatHasNotStarted() {
        assertNull(cancelError(amirAtTwoJack, "entrant-amir", today))
    }

    @Test
    fun anotherEntrantCannotCancelIt() {
        assertEquals(
            "This reservation does not belong to your account.",
            cancelError(amirAtTwoJack, "entrant-jen", today)
        )
    }

    @Test
    fun cancelledReservationCannotBeCancelledAgain() {
        val cancelled = amirAtTwoJack.copy(status = RESERVATION_CANCELLED)
        assertEquals("This reservation is already cancelled.", cancelError(cancelled, "entrant-amir", today))
    }

    @Test
    fun stayCannotBeCancelledOnTheCheckInDay() {
        assertEquals(
            "Your stay has already started, so it can no longer be cancelled.",
            cancelError(amirAtTwoJack, "entrant-amir", "2027-07-02")
        )
    }

    @Test
    fun stayCanStillBeCancelledTheDayBeforeCheckIn() {
        assertNull(cancelError(amirAtTwoJack, "entrant-amir", "2027-07-01"))
    }

    @Test
    fun finishedStayCannotBeCancelled() {
        assertEquals(
            "Your stay has already started, so it can no longer be cancelled.",
            cancelError(amirAtTwoJack, "entrant-amir", "2027-08-01")
        )
    }

    @Test
    fun statusLabelShowsCompletedOnlyAfterCheckOut() {
        assertEquals(RESERVATION_CONFIRMED, reservationStatusLabel(amirAtTwoJack, today))
        assertEquals(RESERVATION_CONFIRMED, reservationStatusLabel(amirAtTwoJack, "2027-07-04"))
        assertEquals(RESERVATION_COMPLETED, reservationStatusLabel(amirAtTwoJack, "2027-07-05"))
    }

    @Test
    fun statusLabelKeepsCancelledEvenAfterTheDates() {
        val cancelled = amirAtTwoJack.copy(status = RESERVATION_CANCELLED)
        assertEquals(RESERVATION_CANCELLED, reservationStatusLabel(cancelled, "2027-08-01"))
    }

    @Test
    fun cancellingFreesTheNightsAndTheSpot() {
        val site = Campsite(id = "site-two-jack", name = "Two Jack Lakeside")
        val lottery = Lottery(
            id = "lottery-summer", campsiteId = "site-two-jack", numberOfWinners = 1,
            stayStartDate = "2027-07-01", stayEndDate = "2027-07-10", booked = listOf(amirAtTwoJack)
        )
        assertEquals(1, confirmedReservations(lottery).size)
        assertFalse(isNightOpen("2027-07-02", lottery, site))

        // The service records a cancellation by adding the entrant to cancelledEntrantIds.
        val afterCancel = lottery.copy(cancelledEntrantIds = listOf("entrant-amir"))
        assertTrue(confirmedReservations(afterCancel).isEmpty())
        assertTrue(isNightOpen("2027-07-02", afterCancel, site))
    }

    @Test
    fun cancelledStayIsNoLongerActive() {
        val cancelled = amirAtTwoJack.copy(status = RESERVATION_CANCELLED)
        assertTrue(activeReservations(listOf(cancelled), today).isEmpty())
        assertTrue(pastReservations(listOf(cancelled), "2027-08-01").isEmpty())
    }
}
