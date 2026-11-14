package com.example.campsitereservationapp.model

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/*
 * The Reservation model and the booking rules for reserving a campsite won in a lottery
 * (US 01.04.01, US 01.04.03, US 01.06.01).
 *
 * The functions here do no database work, so the screens, ReservationService and unit
 * tests all share the same rules.
 *
 * Profile deletion cancels active reservations. The separate cancellation flow
 * (US 01.07.01) is still outstanding. Dates use the device's time zone.
 */

/** Status of a reservation that is booked and counts toward its lottery's capacity. */
const val RESERVATION_CONFIRMED = "Confirmed"
const val RESERVATION_CANCELLED = "Cancelled"

/**
 * An entrant's booking of a campsite, stored at `reservations/{id}` in Firestore and
 * copied into its lottery's `booked` list.
 *
 * Check-in and check-out dates use yyyy-MM-dd, the same format as campsite season dates.
 *
 * @property id Firestore document ID of the reservation.
 * @property entrantId Firebase UID of the entrant who booked.
 * @property campsiteId Document ID of the booked campsite.
 * @property lotteryId Document ID of the lottery the entrant won.
 * @property campsiteName Campsite name saved at booking time, so the list loads without the campsite.
 * @property checkInDate First night of the stay.
 * @property checkOutDate Day the entrant leaves; this night is not part of the stay.
 * @property status Booking status, either [RESERVATION_CONFIRMED] or [RESERVATION_CANCELLED].
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

/**
 * Keeps confirmed stays that have not ended, soonest first.
 *
 * A stay checking out today still counts as active. yyyy-MM-dd text sorts in date order.
 *
 * @param reservations An entrant's reservations.
 * @param today Today's date as yyyy-MM-dd.
 * @return The active reservations, sorted by check-in date.
 */
fun activeReservations(reservations: List<Reservation>, today: String): List<Reservation> =
    reservations
        .filter { it.status == RESERVATION_CONFIRMED && it.checkOutDate >= today }
        .sortedBy { it.checkInDate }

/**
 * Gets today's date in the format used by reservations.
 *
 * @return Today's date as yyyy-MM-dd, in the device's time zone.
 */
fun reservationDateToday(): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

/**
 * Turns a stored date into readable text for the screens.
 *
 * @param date A date as yyyy-MM-dd.
 * @return Text like "Jun 11, 2027", or [date] unchanged if it cannot be read.
 */
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

/**
 * Converts a stored date to the UTC time the date picker uses.
 *
 * @param date A date as yyyy-MM-dd.
 * @return Midnight UTC on that date in epoch milliseconds, or null if it cannot be read.
 */
fun reservationDateToUtcMillis(date: String): Long? = try {
    utcDateFormat().parse(date)?.time
} catch (_: ParseException) {
    null
}

/**
 * Converts a UTC time from the date picker to a stored date.
 *
 * @param millis Epoch milliseconds, read in UTC.
 * @return The date as yyyy-MM-dd.
 */
fun reservationDateFromUtcMillis(millis: Long): String = utcDateFormat().format(Date(millis))

/**
 * Lists each night of a stay.
 *
 * The check-out date is not a night because the entrant leaves that day.
 *
 * @param checkIn First night as yyyy-MM-dd.
 * @param checkOut Check-out day as yyyy-MM-dd.
 * @return Each night as yyyy-MM-dd, or an empty list if check-out is not after check-in
 * or either date cannot be read.
 */
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

/**
 * Gets the reservations that count toward a lottery's capacity.
 *
 * @param lottery The lottery to check.
 * @return Confirmed bookings whose entrants have not deleted their profiles.
 */
fun confirmedReservations(lottery: Lottery): List<Reservation> =
    lottery.booked.filter {
        it.status == RESERVATION_CONFIRMED && it.entrantId !in lottery.cancelledEntrantIds
    }

/**
 * Checks whether a night can be booked, which decides which dates are greyed out.
 *
 * The night must be inside the lottery's stay period and the campsite's season, fall on
 * one of the campsite's available days, and not already be booked.
 *
 * @param night The night as yyyy-MM-dd.
 * @param lottery The lottery being reserved from.
 * @param campsite The campsite being reserved.
 * @return True if the night is open.
 */
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
 * The entrant must hold a winning spot, booking must be open, the lottery must not be full,
 * every night must be open, and the rules must be accepted if the campsite requires it.
 *
 * @param lottery The lottery being reserved from.
 * @param campsite The campsite being reserved.
 * @param entrantId Firebase UID of the entrant.
 * @param checkIn First night as yyyy-MM-dd.
 * @param checkOut Check-out day as yyyy-MM-dd.
 * @param rulesAccepted Whether the entrant agreed to the campsite rules.
 * @param today Today's date as yyyy-MM-dd.
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

/**
 * Checks whether an entrant has a winning spot to decline.
 *
 * @param lottery The lottery the entrant won.
 * @param entrantId Firebase UID of the entrant.
 * @return A message suitable for display, or null if the entrant can decline.
 */
fun declineError(lottery: Lottery, entrantId: String): String? =
    if (entrantId in lottery.pending) null else "You do not have a spot to decline in this lottery."
