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
    fun pastReservations_keepsStaysThatAlreadyEnded() {
        val result = pastReservations(listOf(stay("lake-louise", "2026-08-14", "2026-08-16")), today)
        assertEquals(listOf("lake-louise"), result.map { it.id })
    }

    @Test
    fun pastReservations_leavesOutStayEndingToday() {
        val result = pastReservations(listOf(stay("current", "2026-10-30", today)), today)
        assertTrue(result.isEmpty())
    }

    @Test
    fun pastReservations_leavesOutUpcomingStays() {
        val result = pastReservations(listOf(stay("next-summer", "2027-06-11", "2027-06-13")), today)
        assertTrue(result.isEmpty())
    }

    @Test
    fun pastReservations_leavesOutCancelledStays() {
        val result = pastReservations(listOf(stay("cancelled", "2026-08-14", "2026-08-16", RESERVATION_CANCELLED)), today)
        assertTrue(result.isEmpty())
    }

    @Test
    fun pastReservations_sortsMostRecentFirst() {
        val result = pastReservations(
            listOf(stay("july", "2026-07-03", "2026-07-05"), stay("september", "2026-09-19", "2026-09-21")),
            today
        )
        assertEquals(listOf("september", "july"), result.map { it.id })
    }

    @Test
    fun activeAndPastReservations_splitEveryConfirmedStayExactlyOnce() {
        val stays = listOf(
            stay("ended", "2026-09-19", "2026-09-21"),
            stay("ends-today", "2026-10-30", today),
            stay("upcoming", "2027-06-11", "2027-06-13")
        )
        assertEquals(listOf("ends-today", "upcoming"), activeReservations(stays, today).map { it.id })
        assertEquals(listOf("ended"), pastReservations(stays, today).map { it.id })
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
