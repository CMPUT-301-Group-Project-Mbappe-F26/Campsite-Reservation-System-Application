package com.example.campsitereservationapp.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Unit tests for an owner rejecting a reservation at their campsite (US 02.08.01): who can
 * reject and when, and where a rejected reservation is listed afterwards.
 */
class RejectReservationTest {
    private val today = "2027-06-01"
    private val twoJack = Campsite(id = "site-two-jack", ownerId = "owner-maria", name = "Two Jack Lakeside")

    private val samAtTwoJack = Reservation(
        id = "res-sam", entrantId = "entrant-sam", campsiteId = "site-two-jack",
        lotteryId = "lottery-summer", campsiteName = "Two Jack Lakeside",
        checkInDate = "2027-07-02", checkOutDate = "2027-07-04"
    )

    @Test
    fun ownerCanRejectAnUpcomingStay() {
        assertNull(rejectError(samAtTwoJack, twoJack, "owner-maria", today))
    }

    @Test
    fun ownerCanRejectAStayThatIsUnderway() {
        assertNull(rejectError(samAtTwoJack, twoJack, "owner-maria", "2027-07-03"))
    }

    @Test
    fun someoneElseCannotReject() {
        assertEquals(
            "Only the campsite owner can reject reservations.",
            rejectError(samAtTwoJack, twoJack, "entrant-sam", today)
        )
    }

    @Test
    fun reservationForAnotherCampsiteCannotBeRejectedHere() {
        val elsewhere = samAtTwoJack.copy(campsiteId = "site-whistlers")
        assertEquals("This reservation is not for this campsite.", rejectError(elsewhere, twoJack, "owner-maria", today))
    }

    @Test
    fun rejectedReservationCannotBeRejectedAgain() {
        val rejected = samAtTwoJack.copy(status = RESERVATION_REJECTED)
        assertEquals("This reservation is already rejected.", rejectError(rejected, twoJack, "owner-maria", today))
    }

    @Test
    fun cancelledReservationCannotBeRejected() {
        val cancelled = samAtTwoJack.copy(status = RESERVATION_CANCELLED)
        assertEquals("Only confirmed reservations can be rejected.", rejectError(cancelled, twoJack, "owner-maria", today))
    }

    @Test
    fun finishedStayCannotBeRejected() {
        assertEquals("This stay has already ended.", rejectError(samAtTwoJack, twoJack, "owner-maria", "2027-07-05"))
    }

    @Test
    fun rejectedReservationIsListedWithCancelledOnes() {
        val rejected = samAtTwoJack.copy(status = RESERVATION_REJECTED)
        assertEquals(listOf("res-sam"), cancelledReservations(listOf(rejected)).map { it.id })
        assertTrue(activeReservations(listOf(rejected), today).isEmpty())
        assertTrue(pastReservations(listOf(rejected), "2027-08-01").isEmpty())
    }

    @Test
    fun entrantCannotCancelARejectedReservation() {
        val rejected = samAtTwoJack.copy(status = RESERVATION_REJECTED)
        assertEquals("This reservation cannot be cancelled.", cancelError(rejected, "entrant-sam", today))
    }
}
