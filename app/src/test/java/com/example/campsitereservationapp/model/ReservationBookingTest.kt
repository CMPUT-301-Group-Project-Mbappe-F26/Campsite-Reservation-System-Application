package com.example.campsitereservationapp.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReservationBookingTest {
    private val today = "2027-05-01"
    private val campsite = Campsite(requireRulesAcceptance = true)
    private val lottery = Lottery(
        numberOfWinners = 2, pending = listOf("winner"),
        bookingStartDate = "2027-04-01", bookingEndDate = "2027-05-31",
        stayStartDate = "2027-06-01", stayEndDate = "2027-06-30"
    )
    private val otherStay = Reservation(checkInDate = "2027-06-10", checkOutDate = "2027-06-12")

    private fun error(
        lottery: Lottery = this.lottery, campsite: Campsite = this.campsite, entrant: String = "winner",
        checkIn: String = "2027-06-01", checkOut: String = "2027-06-03", accepted: Boolean = true, today: String = this.today
    ) = reservationError(lottery, campsite, entrant, checkIn, checkOut, accepted, today)

    @Test
    fun reservationNights_excludesCheckOutDay() {
        assertEquals(listOf("2027-06-30", "2027-07-01"), reservationNights("2027-06-30", "2027-07-02"))
    }

    @Test
    fun reservationNights_isEmptyWhenCheckOutIsNotAfterCheckIn() {
        assertTrue(reservationNights("2027-06-03", "2027-06-03").isEmpty())
        assertTrue(reservationNights("2027-06-03", "2027-06-01").isEmpty())
    }

    @Test
    fun isNightOpen_rejectsNightsOutsideStayPeriod() {
        assertFalse(isNightOpen("2027-05-31", lottery, campsite))
        assertTrue(isNightOpen("2027-06-29", lottery, campsite))
        assertFalse(isNightOpen("2027-06-30", lottery, campsite))
    }

    @Test
    fun isNightOpen_rejectsNightsAlreadyBooked() {
        val taken = lottery.copy(booked = listOf(otherStay))
        assertTrue(isNightOpen("2027-06-09", taken, campsite))
        assertFalse(isNightOpen("2027-06-10", taken, campsite))
        assertFalse(isNightOpen("2027-06-11", taken, campsite))
        assertTrue(isNightOpen("2027-06-12", taken, campsite))
    }

    @Test
    fun isNightOpen_ignoresCancelledReservations() {
        val cancelled = lottery.copy(booked = listOf(otherStay.copy(status = "Cancelled")))
        assertTrue(isNightOpen("2027-06-10", cancelled, campsite))
    }

    @Test
    fun isNightOpen_respectsCampsiteAvailability() {
        val mondays = campsite.copy(availableDays = listOf(1))
        assertTrue(isNightOpen("2027-06-07", lottery, mondays)) // A Monday
        assertFalse(isNightOpen("2027-06-08", lottery, mondays)) // A Tuesday
        assertFalse(isNightOpen("2027-06-01", lottery, campsite.copy(availableFrom = "2027-06-02")))
        assertFalse(isNightOpen("2027-06-03", lottery, campsite.copy(availableUntil = "2027-06-02")))
    }

    @Test
    fun reservationError_allowsValidReservation() {
        assertNull(error())
    }

    @Test
    fun reservationError_allowsStayEndingWhenAnotherBegins() {
        assertNull(error(lottery.copy(booked = listOf(otherStay)), checkIn = "2027-06-08", checkOut = "2027-06-10"))
    }

    @Test
    fun reservationError_rejectsEntrantWithoutSpot() {
        assertNotNull(error(entrant = "someone else"))
    }

    @Test
    fun reservationError_rejectsOutsideBookingPeriod() {
        assertNotNull(error(today = "2027-03-31"))
        assertNotNull(error(today = "2027-06-01"))
        assertNull(error(today = "2027-05-31"))
    }

    @Test
    fun reservationError_rejectsWhenCapacityIsReached() {
        val second = Reservation(checkInDate = "2027-06-20", checkOutDate = "2027-06-22")
        assertNotNull(error(lottery.copy(booked = listOf(otherStay, second))))
    }

    @Test
    fun reservationError_doesNotCountCancelledTowardCapacity() {
        val second = Reservation(checkInDate = "2027-06-20", checkOutDate = "2027-06-22", status = "Cancelled")
        assertNull(error(lottery.copy(booked = listOf(otherStay, second))))
    }

    @Test
    fun reservationError_rejectsOverlapAndBadDates() {
        val taken = lottery.copy(booked = listOf(otherStay))
        assertNotNull(error(taken, checkIn = "2027-06-09", checkOut = "2027-06-11"))
        assertNotNull(error(checkIn = "2027-06-03", checkOut = "2027-06-01"))
    }

    @Test
    fun reservationError_requiresRulesOnlyWhenCampsiteDoes() {
        assertNotNull(error(accepted = false))
        assertNull(error(campsite = campsite.copy(requireRulesAcceptance = false), accepted = false))
    }
}
