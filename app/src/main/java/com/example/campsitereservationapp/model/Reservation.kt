package com.example.campsitereservationapp.model

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val RESERVATION_CONFIRMED = "Confirmed"

/**
 * An entrant's booking of a campsite.
 * Check-in and check-out dates use yyyy-MM-dd, the same format as campsite season dates.
 */
data class Reservation(
    val id: String = "",
    val entrantId: String = "",
    val campsiteId: String = "",
    val campsiteName: String = "",
    val checkInDate: String = "",
    val checkOutDate: String = "",
    val status: String = RESERVATION_CONFIRMED
)

// Keeps confirmed stays that have not ended, soonest first. yyyy-MM-dd text sorts in date order.
fun activeReservations(reservations: List<Reservation>, today: String): List<Reservation> =
    reservations
        .filter { it.status == RESERVATION_CONFIRMED && it.checkOutDate >= today }
        .sortedBy { it.checkInDate }

// Gets today's date in the format used by reservations.
fun reservationDateToday(): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

// Turns yyyy-MM-dd into text like Jun 11, 2027, or leaves a date it cannot read unchanged.
fun formatReservationDate(date: String): String {
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
    return try {
        SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).format(parser.parse(date) ?: return date)
    } catch (_: ParseException) {
        date
    }
}
