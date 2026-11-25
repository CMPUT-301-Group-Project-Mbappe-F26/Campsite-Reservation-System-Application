package com.example.campsitereservationapp.notification

import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.RESERVATION_CANCELLED
import com.example.campsitereservationapp.model.Reservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class ReservationConfirmationTest {
    private val campsite = Campsite(
        name = "Pyramid \"Lake\" Hideaway",
        location = "Jasper, AB",
        pricePerNight = 69.0
    )

    @Test
    fun notificationContainsReservationDetails() {
        val message = reservationConfirmationMessage(
            campsite, "2027-06-11", "2027-06-13", 4, 138.0
        )

        assertTrue(message.contains("Pyramid \"Lake\" Hideaway"))
        assertTrue(message.contains("Jasper, AB"))
        assertTrue(message.contains("Jun 11, 2027 to Jun 13, 2027"))
        assertTrue(message.contains("4 guests"))
        assertTrue(message.contains("$138.00 CAD"))
    }

    @Test
    fun csvContainsEveryRequiredField() {
        val csv = reservationCsv(campsite, "2027-06-11", "2027-06-13", 4, 138.0)

        assertEquals(
            "Campsite name,Location,Check-in,Check-out,Guests,Price\n" +
                "\"Pyramid \"\"Lake\"\" Hideaway\",\"Jasper, AB\",2027-06-11,2027-06-13,4,$138.00 CAD",
            csv
        )
    }

    @Test
    fun csvWriterReportsSuccessAndFailure() {
        val output = ByteArrayOutputStream()
        assertTrue(writeReservationCsv("reservation") { output })
        assertEquals("reservation", output.toString(Charsets.UTF_8.name()))
        assertFalse(writeReservationCsv("reservation") { null })
        assertFalse(writeReservationCsv("reservation") { error("Storage unavailable") })
    }

    @Test
    fun cancellationNotificationAndCsvShowCancelledStatus() {
        val reservation = Reservation(
            campsiteName = "Pyramid Lake Hideaway",
            checkInDate = "2027-06-11",
            checkOutDate = "2027-06-13"
        )
        val message = cancellationConfirmationMessage(reservation)
        val csv = reservationCsv(campsite, "2027-06-11", "2027-06-13", 4, 138.0, RESERVATION_CANCELLED)

        assertTrue(message.contains("Pyramid Lake Hideaway"))
        assertTrue(message.contains("Jun 11, 2027 to Jun 13, 2027"))
        assertTrue(message.endsWith("Cancelled"))
        assertTrue(csv.startsWith("Campsite name,Location,Check-in,Check-out,Guests,Price,Status"))
        assertTrue(csv.endsWith("4,$138.00 CAD,Cancelled"))
    }
}
