package com.example.campsitereservationapp.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReservationTest {
    private val today = "2026-11-01"

    private fun stay(id: String, checkIn: String, checkOut: String, status: String = RESERVATION_CONFIRMED) =
        Reservation(id = id, checkInDate = checkIn, checkOutDate = checkOut, status = status)

    @Test
    fun activeReservations_removesStaysThatAlreadyEnded() {
        val result = activeReservations(listOf(stay("past", "2026-10-01", "2026-10-03")), today)
        assertTrue(result.isEmpty())
    }

    @Test
    fun activeReservations_keepsStayEndingToday() {
        val result = activeReservations(listOf(stay("current", "2026-10-30", today)), today)
        assertEquals(listOf("current"), result.map { it.id })
    }

    @Test
    fun activeReservations_removesUnconfirmedStays() {
        val result = activeReservations(listOf(stay("cancelled", "2027-06-11", "2027-06-13", "Cancelled")), today)
        assertTrue(result.isEmpty())
    }

    @Test
    fun activeReservations_sortsSoonestFirst() {
        val result = activeReservations(
            listOf(stay("later", "2027-07-01", "2027-07-03"), stay("sooner", "2027-06-11", "2027-06-13")),
            today
        )
        assertEquals(listOf("sooner", "later"), result.map { it.id })
    }

    @Test
    fun formatReservationDate_showsReadableDate() {
        assertEquals("Jun 11, 2027", formatReservationDate("2027-06-11"))
    }

    @Test
    fun formatReservationDate_leavesInvalidDateUnchanged() {
        assertEquals("not a date", formatReservationDate("not a date"))
        assertEquals("2027-02-31", formatReservationDate("2027-02-31"))
    }
}
