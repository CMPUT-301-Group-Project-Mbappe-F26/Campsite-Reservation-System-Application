package com.example.campsitereservationapp.campsite

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.campsitereservationapp.model.RESERVATION_CANCELLED
import com.example.campsitereservationapp.model.RESERVATION_REJECTED
import com.example.campsitereservationapp.model.Reservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Intent tests for US 02.07.01, US 02.07.02, US 02.07.03 and US 02.08.01: a campsite owner
 * views the active, past and cancelled reservations at their campsite, and rejects one.
 * The list is given sample bookings and a stand-in reject action, so no Firebase data is
 * needed.
 */
@RunWith(AndroidJUnit4::class)
class CampsiteReservationsIntentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun booking(id: String, checkIn: String, checkOut: String, status: String = "Confirmed") = Reservation(
        id = id, entrantId = "entrant-$id", campsiteId = "site-two-jack", lotteryId = "lottery-summer",
        campsiteName = "Two Jack Lakeside", checkInDate = checkIn, checkOutDate = checkOut, status = status
    )

    private val bookings = CampsiteBookings(
        active = listOf(
            CampsiteBooking(booking("sam", "2027-08-14", "2027-08-16"), "Sam Okafor"),
            CampsiteBooking(booking("priya", "2027-08-21", "2027-08-23"), "Priya Nair")
        ),
        past = listOf(CampsiteBooking(booking("amir", "2027-07-02", "2027-07-04"), "Amir Haddad")),
        cancelled = listOf(CampsiteBooking(booking("jen", "2027-07-20", "2027-07-22", RESERVATION_CANCELLED), "Jen Park"))
    )

    @Test
    fun ownerSeesActiveReservationsFirst() {
        composeRule.setContent { CampsiteReservationList(bookings) }
        composeRule.onNodeWithText("Active (2)").assertIsDisplayed()
        composeRule.onNodeWithText("Sam Okafor").assertIsDisplayed()
        composeRule.onNodeWithText("Aug 14, 2027 – Aug 16, 2027").assertIsDisplayed()
        composeRule.onNodeWithText("Priya Nair").assertIsDisplayed()
        composeRule.onNodeWithText("Amir Haddad").assertDoesNotExist()
    }

    @Test
    fun ownerSeesPastStays() {
        composeRule.setContent { CampsiteReservationList(bookings) }
        composeRule.onNodeWithText("Past (1)").performClick()
        composeRule.onNodeWithText("Amir Haddad").assertIsDisplayed()
        composeRule.onNodeWithText("Jul 2, 2027 – Jul 4, 2027").assertIsDisplayed()
        composeRule.onNodeWithText("Sam Okafor").assertDoesNotExist()
    }

    @Test
    fun ownerSeesCancelledReservations() {
        composeRule.setContent { CampsiteReservationList(bookings) }
        composeRule.onNodeWithText("Cancelled (1)").performClick()
        composeRule.onNodeWithText("Jen Park").assertIsDisplayed()
        composeRule.onNodeWithText("Jul 20, 2027 – Jul 22, 2027").assertIsDisplayed()
        composeRule.onNodeWithText("Sam Okafor").assertDoesNotExist()
    }

    // US 02.08.01: an owner rejects a reservation.

    @Test
    fun ownerConfirmsBeforeRejectingAReservation() {
        var rejected: String? = null
        composeRule.setContent { CampsiteReservationList(bookings, onReject = { rejected = it.reservation.id }) }
        composeRule.onAllNodesWithText("Reject")[0].performClick()
        composeRule.onNodeWithText("Reject Sam Okafor's reservation?").assertIsDisplayed()
        composeRule.onNodeWithText("Their stay from Aug 14, 2027 to Aug 16, 2027", substring = true).assertIsDisplayed()
        assertNull(rejected)
        composeRule.onNodeWithText("Yes, reject").performClick()
        assertEquals("sam", rejected)
    }

    @Test
    fun keepingTheReservationDoesNotRejectIt() {
        var rejected: String? = null
        composeRule.setContent { CampsiteReservationList(bookings, onReject = { rejected = it.reservation.id }) }
        composeRule.onAllNodesWithText("Reject")[1].performClick()
        composeRule.onNodeWithText("Keep reservation").performClick()
        composeRule.onNodeWithText("Reject Priya Nair's reservation?").assertDoesNotExist()
        assertNull(rejected)
    }

    @Test
    fun rejectIsOnlyOfferedOnActiveReservations() {
        composeRule.setContent { CampsiteReservationList(bookings, onReject = {}) }
        composeRule.onAllNodesWithText("Reject").assertCountEquals(2)
        composeRule.onNodeWithText("Past (1)").performClick()
        composeRule.onAllNodesWithText("Reject").assertCountEquals(0)
    }

    @Test
    fun rejectedReservationIsMarkedUnderCancelled() {
        val withRejected = bookings.copy(
            cancelled = bookings.cancelled + CampsiteBooking(
                booking("lee", "2027-09-01", "2027-09-03", RESERVATION_REJECTED), "Lee Wong"
            )
        )
        composeRule.setContent { CampsiteReservationList(withRejected, onReject = {}) }
        composeRule.onNodeWithText("Cancelled (2)").performClick()
        composeRule.onNodeWithText("Lee Wong").assertIsDisplayed()
        composeRule.onNodeWithText("Rejected by you").assertIsDisplayed()
        composeRule.onNodeWithText("Cancelled by entrant").assertIsDisplayed()
    }

    @Test
    fun rejectButtonsAreDisabledWhileSaving() {
        composeRule.setContent {
            CampsiteReservationList(bookings, onReject = {}, rejectingId = "sam", message = "")
        }
        composeRule.onNodeWithText("Rejecting…").assertIsNotEnabled()
        composeRule.onNodeWithText("Reject").assertIsNotEnabled()
    }

    @Test
    fun ownerSeesTheResultOfRejecting() {
        composeRule.setContent {
            CampsiteReservationList(bookings, onReject = {}, message = "Sam Okafor's reservation was rejected.")
        }
        composeRule.onNodeWithText("Sam Okafor's reservation was rejected.").assertIsDisplayed()
    }

    @Test
    fun ownerIsToldWhenAFilterIsEmpty() {
        composeRule.setContent { CampsiteReservationList(CampsiteBookings()) }
        composeRule.onNodeWithText("No active reservations.").assertIsDisplayed()
        composeRule.onNodeWithText("Past (0)").performClick()
        composeRule.onNodeWithText("No past stays yet.").assertIsDisplayed()
        composeRule.onNodeWithText("Cancelled (0)").performClick()
        composeRule.onNodeWithText("No cancelled reservations.").assertIsDisplayed()
    }
}
