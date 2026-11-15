package com.example.campsitereservationapp.campsite

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.campsitereservationapp.model.RESERVATION_CANCELLED
import com.example.campsitereservationapp.model.Reservation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Intent tests for US 02.07.01, US 02.07.02 and US 02.07.03: a campsite owner views the
 * active, past and cancelled reservations at their campsite. The list is given sample
 * bookings directly, so no Firebase data is needed.
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
