package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Campsite
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
