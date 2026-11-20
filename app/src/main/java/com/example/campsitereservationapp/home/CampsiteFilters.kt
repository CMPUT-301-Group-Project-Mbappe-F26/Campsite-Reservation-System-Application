package com.example.campsitereservationapp.home

import com.example.campsitereservationapp.model.Campsite
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Stores filters selected by user.
 */
data class CampsiteFilters(
    val location: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val guests: Int = 0
)

/**
 * Converts date string to Calendar object.
 *
 * @param date Date written as yyyy-MM-dd.
 * @return The date, or null if it is invalid.
 */
fun parseFilterDate(date: String): Calendar? {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    format.isLenient = false
    format.timeZone = TimeZone.getTimeZone("UTC")
    return try {
        val parsedDate = format.parse(date) ?: return null
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.time = parsedDate
        calendar
    } catch (e: Exception) {
        null
    }
}

/**
 * Checks if campsite is available for the selected dates.
 * Checkout date is not counted as a night
 * @param campsite The campsite being checked.
 * @param startDate The user's selected start date.
 * @param endDate The user's selected end date.
 * @return True if the campsite is available for every selected date.
 */
fun matchesDates(
    campsite: Campsite,
    startDate: String,
    endDate: String
): Boolean {
    if (startDate.isBlank() && endDate.isBlank()) { return true }
    if (startDate.isBlank() || endDate.isBlank()) { return false }
    val start = parseFilterDate(startDate) ?: return false
    val end = parseFilterDate(endDate) ?: return false
    if (!start.before(end)) { return false }
    val availableFrom = parseFilterDate(campsite.availableFrom)
        ?: return false
    val availableUntil = parseFilterDate(campsite.availableUntil)
        ?: return false
    val lastNight = end.clone() as Calendar
    lastNight.add(Calendar.DAY_OF_MONTH, -1)
    if (start.before(availableFrom) || lastNight.after(availableUntil)) {
        return false
    }
    if (campsite.availableDays.isNotEmpty()) {
        val currentDate = start.clone() as Calendar
        while (currentDate.before(end)) {
            val calendarDay = currentDate.get(Calendar.DAY_OF_WEEK)
            val weekday = if (calendarDay == Calendar.SUNDAY) {
                7
            } else {
                calendarDay - 1
            }
            if (!campsite.availableDays.contains(weekday)) { return false }
            currentDate.add(Calendar.DAY_OF_MONTH, 1)
        }
    }
    return true
}

/**
 * Filters campsites by location, dates, and number of guests.
 *
 * @param campsites The list of campsites.
 * @param filters The filters selected by the user.
 * @return The campsites matching all selected filters.
 */
fun applyCampsiteFilters(
    campsites: List<Campsite>,
    filters: CampsiteFilters
): List<Campsite> {
    return campsites.filter { campsite ->
        // Check location
        val matchesLocation = filters.location.isBlank() || campsite.location.contains(filters.location.trim(), ignoreCase = true)
        // Check number of guests
        val matchesGuests = filters.guests == 0 || (campsite.maxOccupancy ?: 0) >= filters.guests
        // Check dates
        val matchesDateRange = matchesDates(campsite, filters.startDate, filters.endDate)
        // All selected must match
        matchesLocation && matchesGuests && matchesDateRange
    }
}

/**
 * Checks user selected filters
 */
fun hasActiveFilters(filters: CampsiteFilters): Boolean {
    return filters.location.isNotBlank() ||
            filters.startDate.isNotBlank() ||
            filters.endDate.isNotBlank() ||
            filters.guests > 0
}