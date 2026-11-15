package com.example.campsitereservationapp.campsite

import com.example.campsitereservationapp.model.RESERVATION_CANCELLED
import com.example.campsitereservationapp.model.Reservation
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.model.cancelledReservations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Unit tests for how an owner's campsite reservations are split into active (US 02.07.01),
 * past (US 02.07.02) and cancelled (US 02.07.03), and named by entrant.
 */
class CampsiteBookingsTest {
    private val today = "2027-07-10"

    private fun booking(id: String, entrant: String, checkIn: String, checkOut: String, status: String = "Confirmed") =
        Reservation(
            id = id, entrantId = entrant, campsiteId = "site-two-jack", lotteryId = "lottery-summer",
            campsiteName = "Two Jack Lakeside", checkInDate = checkIn, checkOutDate = checkOut, status = status
        )

    private val amir = User(id = "entrant-amir", name = "Amir Haddad")
    private val jen = User(id = "entrant-jen", name = "Jen Park")
    private val sam = User(id = "entrant-sam", name = "Sam Okafor")

    private val finished = booking("res-finished", "entrant-amir", "2027-07-02", "2027-07-04")
    private val endsToday = booking("res-ends-today", "entrant-jen", "2027-07-08", today)
    private val upcoming = booking("res-upcoming", "entrant-sam", "2027-08-14", "2027-08-16")
    private val cancelled = booking("res-cancelled", "entrant-jen", "2027-07-20", "2027-07-22", RESERVATION_CANCELLED)

    @Test
    fun activeKeepsStaysThatHaveNotEndedSoonestFirst() {
        val result = campsiteBookings(listOf(upcoming, finished, endsToday, cancelled), listOf(amir, jen, sam), today)
        assertEquals(listOf("res-ends-today", "res-upcoming"), result.active.map { it.reservation.id })
    }

    @Test
    fun pastKeepsFinishedStays() {
        val result = campsiteBookings(listOf(upcoming, finished, endsToday, cancelled), listOf(amir, jen, sam), today)
        assertEquals(listOf("res-finished"), result.past.map { it.reservation.id })
    }

    @Test
    fun cancelledKeepsOnlyCancelledReservations() {
        val result = campsiteBookings(listOf(upcoming, finished, endsToday, cancelled), listOf(amir, jen, sam), today)
        assertEquals(listOf("res-cancelled"), result.cancelled.map { it.reservation.id })
    }

    @Test
    fun everyReservationLandsInExactlyOneFilter() {
        val all = listOf(upcoming, finished, endsToday, cancelled)
        val result = campsiteBookings(all, listOf(amir, jen, sam), today)
        val ids = (result.active + result.past + result.cancelled).map { it.reservation.id }
        assertEquals(all.map { it.id }.sorted(), ids.sorted())
    }

    @Test
    fun bookingsShowTheEntrantsName() {
        val result = campsiteBookings(listOf(upcoming), listOf(sam), today)
        assertEquals("Sam Okafor", result.active.single().entrantName)
    }

    @Test
    fun entrantWithoutANameIsAGuest() {
        val result = campsiteBookings(listOf(upcoming), listOf(sam.copy(name = "  ")), today)
        assertEquals("Guest entrant", result.active.single().entrantName)
    }

    @Test
    fun entrantWithNoProfileIsUnknown() {
        val result = campsiteBookings(listOf(upcoming), emptyList(), today)
        assertEquals("Unknown entrant", result.active.single().entrantName)
    }

    @Test
    fun noReservationsGivesThreeEmptyLists() {
        val result = campsiteBookings(emptyList(), emptyList(), today)
        assertTrue(result.active.isEmpty() && result.past.isEmpty() && result.cancelled.isEmpty())
    }

    @Test
    fun cancelledReservationsAreSortedLatestFirst() {
        val earlier = cancelled.copy(id = "res-june", checkInDate = "2027-06-01", checkOutDate = "2027-06-03")
        assertEquals(listOf("res-cancelled", "res-june"), cancelledReservations(listOf(earlier, cancelled)).map { it.id })
    }
}
