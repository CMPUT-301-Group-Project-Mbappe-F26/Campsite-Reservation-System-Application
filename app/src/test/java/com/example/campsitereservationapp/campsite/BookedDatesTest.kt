package com.example.campsitereservationapp.campsite

import com.example.campsitereservationapp.model.RESERVATION_CANCELLED
import com.example.campsitereservationapp.model.RESERVATION_REJECTED
import com.example.campsitereservationapp.model.Reservation
import com.example.campsitereservationapp.model.bookedNights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Unit tests for the owner's booked-dates calendar (US 02.06.01): which nights count as
 * booked, who holds them, and the month layout and navigation.
 */
class BookedDatesTest {

    private fun stay(id: String, checkIn: String, checkOut: String, status: String = "Confirmed") = Reservation(
        id = id, entrantId = "entrant-$id", campsiteId = "site-two-jack", lotteryId = "lottery-summer",
        campsiteName = "Two Jack Lakeside", checkInDate = checkIn, checkOutDate = checkOut, status = status
    )

    @Test
    fun eachNightOfAStayIsBookedButNotCheckOutDay() {
        val nights = bookedNights(listOf(stay("sam", "2027-07-02", "2027-07-05")))
        assertEquals(listOf("2027-07-02", "2027-07-03", "2027-07-04"), nights.keys.sorted())
    }

    @Test
    fun cancelledAndRejectedStaysDoNotBookNights() {
        val nights = bookedNights(
            listOf(
                stay("jen", "2027-07-20", "2027-07-22", RESERVATION_CANCELLED),
                stay("lee", "2027-09-01", "2027-09-03", RESERVATION_REJECTED)
            )
        )
        assertTrue(nights.isEmpty())
    }

    @Test
    fun stayAcrossMonthEndBooksNightsInBothMonths() {
        val nights = bookedNights(listOf(stay("priya", "2027-07-30", "2027-08-02")))
        assertEquals(listOf("2027-07-30", "2027-07-31", "2027-08-01"), nights.keys.sorted())
    }

    @Test
    fun earlierCheckInKeepsANightClaimedTwice() {
        val nights = bookedNights(listOf(stay("later", "2027-07-03", "2027-07-05"), stay("earlier", "2027-07-02", "2027-07-04")))
        assertEquals("earlier", nights["2027-07-03"]?.id)
    }

    @Test
    fun bookedNightsShowTheEntrantsName() {
        val bookings = CampsiteBookings(
            active = listOf(CampsiteBooking(stay("sam", "2027-08-14", "2027-08-16"), "Sam Okafor")),
            past = listOf(CampsiteBooking(stay("amir", "2027-07-02", "2027-07-03"), "Amir Haddad")),
            cancelled = listOf(CampsiteBooking(stay("jen", "2027-07-20", "2027-07-22", RESERVATION_CANCELLED), "Jen Park"))
        )
        val names = bookedNightNames(bookings)
        assertEquals("Sam Okafor", names["2027-08-15"])
        assertEquals("Amir Haddad", names["2027-07-02"])
        assertNull(names["2027-07-20"])
    }

    @Test
    fun monthStartingOnThursdayHasThreeBlankCells() {
        // July 1, 2027 is a Thursday.
        val cells = calendarMonthCells("2027-07")
        assertEquals(listOf(null, null, null, "2027-07-01"), cells.take(4))
        assertEquals("2027-07-31", cells.last())
        assertEquals(3 + 31, cells.size)
    }

    @Test
    fun monthStartingOnMondayHasNoBlankCells() {
        // March 1, 2027 is a Monday.
        assertEquals("2027-03-01", calendarMonthCells("2027-03").first())
    }

    @Test
    fun februaryInALeapYearHas29Days() {
        assertEquals("2028-02-29", calendarMonthCells("2028-02").last())
    }

    @Test
    fun unreadableMonthGivesNoCells() {
        assertTrue(calendarMonthCells("July").isEmpty())
    }

    @Test
    fun monthsMoveAcrossYearEnds() {
        assertEquals("2028-01", shiftCalendarMonth("2027-12", 1))
        assertEquals("2026-12", shiftCalendarMonth("2027-01", -1))
    }

    @Test
    fun monthTitleIsReadable() {
        assertEquals("July 2027", formatCalendarMonth("2027-07"))
    }

    @Test
    fun calendarOpensOnTheNextBookedMonth() {
        val nights = listOf("2027-03-10", "2027-08-14", "2027-07-02")
        assertEquals("2027-07", startingCalendarMonth(nights, "2027-06-01"))
    }

    @Test
    fun calendarOpensOnThisMonthWithNoUpcomingBookings() {
        assertEquals("2027-06", startingCalendarMonth(listOf("2027-03-10"), "2027-06-01"))
    }
}
