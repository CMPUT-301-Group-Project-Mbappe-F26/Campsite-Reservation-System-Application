package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.reservationDateFromUtcMillis
import com.example.campsitereservationapp.model.reservationDateToUtcMillis
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Checks that a selected period fits inside a campsite's inclusive season dates.
 *
 * Empty campsite bounds mean no restriction on that end, matching campsite registration.
 * Dates are compared as strict `yyyy-MM-dd` calendar dates, without applying clock times.
 * This checks season boundaries; individual stay weekdays and existing reservations
 * require separate reservation availability checks.
 *
 * @param startDate First date of the period being offered.
 * @param endDate Last date of that period, on or after its start.
 * @param campsite Campsite whose current season bounds should constrain the period.
 * @return A displayable error, or null when the period fits the campsite's season.
 */
internal fun lotteryPeriodAvailabilityError(startDate: String, endDate: String, campsite: Campsite): String? {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    fun validDate(value: String): Boolean = value.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) &&
        runCatching { format.parse(value)?.let { format.format(it) == value } == true }.getOrDefault(false)

    if (!validDate(startDate) || !validDate(endDate)) return "Select valid start and end dates."
    if (startDate > endDate) return "The end date must be on or after the start date."
    val from = campsite.availableFrom
    val until = campsite.availableUntil
    if ((from.isNotBlank() && !validDate(from)) || (until.isNotBlank() && !validDate(until)) ||
        (from.isNotBlank() && until.isNotBlank() && from > until)) {
        return "The campsite has invalid availability dates. Correct the campsite availability first."
    }
    if (from.isNotBlank() && startDate < from) return "Dates must start on or after the campsite is available ($from)."
    if (until.isNotBlank() && endDate > until) return "Dates must end on or before the campsite availability ends ($until)."
    return null
}

/**
 * Checks that an offered stay fits the campsite's current season and available weekdays.
 *
 * The start is the first offered night and the end is an exclusive checkout date,
 * matching reservation availability. Checkout may be the day after the last available
 * night. Both the first and last offered nights must be available weekdays. A lottery
 * can span multiple weekends; reservation validation filters closed nights in between.
 * Empty weekday lists mean no weekday restriction, matching reservation validation.
 *
 * @param startDate First offered night as `yyyy-MM-dd`.
 * @param checkoutDate Exclusive end of the offered stay window as `yyyy-MM-dd`.
 * @param campsite Latest campsite availability, re-read by the creation transaction.
 * @return A displayable error, or null when the offered stay fits current availability.
 */
internal fun lotteryStayAvailabilityError(startDate: String, checkoutDate: String, campsite: Campsite): String? {
    // Validate date syntax before using the shared reservation date conversion functions.
    val dateError = lotteryPeriodAvailabilityError(startDate, checkoutDate, Campsite())
    if (dateError != null) return dateError
    if (startDate >= checkoutDate) return "Stay checkout must be after the first offered night."
    val startMillis = reservationDateToUtcMillis(startDate)!!
    val lastNightMillis = reservationDateToUtcMillis(checkoutDate)!! - 86400000L
    val lastNight = reservationDateFromUtcMillis(lastNightMillis)
    val seasonError = lotteryPeriodAvailabilityError(startDate, lastNight, campsite)
    if (seasonError != null) return seasonError
    if (campsite.availableDays.any { it !in 1..7 }) {
        return "The campsite has invalid available weekdays. Correct the campsite availability first."
    }
    if (campsite.availableDays.isNotEmpty()) {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT)
        for (night in listOf(startMillis, lastNightMillis)) {
            calendar.timeInMillis = night
            val weekday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
            if (weekday !in campsite.availableDays) {
                return "The first and last offered nights must fall on the campsite's available weekdays."
            }
        }
    }
    return null
}

/**
 * Determines whether a stay endpoint can be selected in a UTC-based Material date picker.
 *
 * @param utcMillis Date-picker day in UTC milliseconds; UTC only represents a calendar date.
 * @param campsite Availability used to constrain the date picker.
 * @param checkout Whether the selection is checkout (the preceding night must be available).
 * @return True when this endpoint can bound at least a one-night stay.
 */
internal fun isLotteryStayDateSelectable(utcMillis: Long, campsite: Campsite, checkout: Boolean): Boolean {
    val selected = reservationDateFromUtcMillis(utcMillis)
    val start = if (checkout) reservationDateFromUtcMillis(utcMillis - 86400000L) else selected
    val end = if (checkout) selected else reservationDateFromUtcMillis(utcMillis + 86400000L)
    return lotteryStayAvailabilityError(start, end, campsite) == null
}
