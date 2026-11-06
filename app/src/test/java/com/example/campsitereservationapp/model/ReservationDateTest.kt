package com.example.campsitereservationapp.model

import org.junit.Assert.*
import org.junit.Test

/*
 * Unit tests for the date conversions the reserve screen's calendar relies on, and for
 * which bookings count toward a lottery's capacity (US 01.04.01).
 */
class ReservationDateTest {

    @Test
    fun dateConvertsToMidnightUtc() {
        // 2027-07-01 00:00 UTC
        assertEquals(1814400000000L, reservationDateToUtcMillis("2027-07-01"))
    }

    @Test
    fun dateRoundTripsThroughThePicker() {
        val millis = reservationDateToUtcMillis("2027-07-01")!!
        assertEquals("2027-07-01", reservationDateFromUtcMillis(millis))
    }

    @Test
    fun lateUtcTimeStaysOnTheSameDay() {
        val lastMinute = reservationDateToUtcMillis("2027-07-01")!! + 23 * 60 * 60 * 1000L + 59 * 60 * 1000L
        assertEquals("2027-07-01", reservationDateFromUtcMillis(lastMinute))
    }

    @Test
    fun unreadableDateGivesNull() {
        assertNull(reservationDateToUtcMillis("July 1st"))
        assertNull(reservationDateToUtcMillis("2027-02-30"))
    }

    @Test
    fun staySpansMonthEnd() {
        assertEquals(listOf("2027-06-30", "2027-07-01"), reservationNights("2027-06-30", "2027-07-02"))
    }

    @Test
    fun onlyConfirmedBookingsCountTowardCapacity() {
        val confirmed = Reservation(id = "res-amir", entrantId = "entrant-amir",
            checkInDate = "2027-07-01", checkOutDate = "2027-07-03")
        val cancelled = confirmed.copy(id = "res-jen", entrantId = "entrant-jen", status = "Cancelled")
        val lottery = Lottery(id = "lottery-summer", booked = listOf(confirmed, cancelled))
        assertEquals(listOf(confirmed), confirmedReservations(lottery))
    }
}
