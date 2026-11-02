package com.example.campsitereservationapp.model

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

const val RESERVATION_CONFIRMED = "Confirmed"

/**
 * An entrant's booking of a campsite.
 * Check-in and check-out dates use yyyy-MM-dd, the same format as campsite season dates.
 */
data class Reservation(
    val id: String = "",
    val entrantId: String = "",
    val campsiteId: String = "",
    val lotteryId: String = "",
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

// Reads calendar dates in UTC so a time zone or daylight saving change cannot shift a day.
private fun utcDateFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
    isLenient = false
}

// Converts yyyy-MM-dd to the UTC time used by the date picker, or null if it cannot be read.
fun reservationDateToUtcMillis(date: String): Long? = try {
    utcDateFormat().parse(date)?.time
} catch (_: ParseException) {
    null
}

// Converts a UTC time from the date picker to yyyy-MM-dd.
fun reservationDateFromUtcMillis(millis: Long): String = utcDateFormat().format(Date(millis))

// Lists each night of a stay. The check-out date is not a night because the entrant leaves that day.
fun reservationNights(checkIn: String, checkOut: String): List<String> {
    val start = reservationDateToUtcMillis(checkIn) ?: return emptyList()
    val end = reservationDateToUtcMillis(checkOut) ?: return emptyList()
    val day = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = start }
    return buildList {
        while (day.timeInMillis < end) {
            add(reservationDateFromUtcMillis(day.timeInMillis))
            day.add(Calendar.DAY_OF_MONTH, 1)
        }
    }
}

// Gets the confirmed reservations that count toward a lottery's capacity.
fun confirmedReservations(lottery: Lottery): List<Reservation> =
    lottery.booked.filter { it.status == RESERVATION_CONFIRMED }

// Checks that a night is in the lottery's stay period, offered by the campsite, and not taken.
fun isNightOpen(night: String, lottery: Lottery, campsite: Campsite): Boolean {
    if (night < lottery.stayStartDate || night >= lottery.stayEndDate) return false
    if (campsite.availableFrom.isNotBlank() && night < campsite.availableFrom) return false
    if (campsite.availableUntil.isNotBlank() && night > campsite.availableUntil) return false
    val millis = reservationDateToUtcMillis(night) ?: return false
    val calendarDay = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
        .get(Calendar.DAY_OF_WEEK)
    val day = (calendarDay + 5) % 7 + 1 // Campsites use Monday = 1 through Sunday = 7.
    if (campsite.availableDays.isNotEmpty() && day !in campsite.availableDays) return false
    return confirmedReservations(lottery).none { night in reservationNights(it.checkInDate, it.checkOutDate) }
}

/**
 * Checks whether an entrant can reserve these dates through a lottery.
 *
 * @return A message suitable for display, or null when the reservation can be made.
 */
fun reservationError(
    lottery: Lottery,
    campsite: Campsite,
    entrantId: String,
    checkIn: String,
    checkOut: String,
    rulesAccepted: Boolean,
    today: String
): String? {
    val nights = reservationNights(checkIn, checkOut)
    return when {
        entrantId !in lottery.pending -> "You do not have a spot to reserve in this lottery."
        today < lottery.bookingStartDate -> "Booking for this lottery has not started yet."
        today > lottery.bookingEndDate -> "Booking for this lottery has ended."
        confirmedReservations(lottery).size >= lottery.numberOfWinners -> "This lottery is fully booked."
        nights.isEmpty() -> "Your check-out date must be after your check-in date."
        nights.any { !isNightOpen(it, lottery, campsite) } -> "Some of these dates are not available. Please choose others."
        campsite.requireRulesAcceptance && !rulesAccepted -> "Please agree to the campsite rules and policies."
        else -> null
    }
}

// Checks whether an entrant has a spot to decline, returning a message for display or null if they do.
fun declineError(lottery: Lottery, entrantId: String): String? =
    if (entrantId in lottery.pending) null else "You do not have a spot to decline in this lottery."
