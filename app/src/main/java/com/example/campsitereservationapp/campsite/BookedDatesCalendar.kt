package com.example.campsitereservationapp.campsite

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.model.bookedNights
import com.example.campsitereservationapp.model.formatReservationDate
import com.example.campsitereservationapp.model.reservationDateFromUtcMillis
import com.example.campsitereservationapp.model.reservationDateToUtcMillis
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import androidx.compose.foundation.shape.CircleShape
import com.example.campsitereservationapp.ui.components.GlassCard
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/*
 * A month calendar that shows a campsite owner which nights are booked at their campsite
 * (US 02.06.01). It is shown on the owner's view of [CampsiteDetailsScreen], above the
 * reservation list, by [CampsiteReservationsSection].
 *
 * The month helpers here are plain functions, so unit tests can check them without Android.
 * Months are written as yyyy-MM and read in UTC, like reservation dates.
 *
 * Outstanding issues: the calendar only shows booked nights, not the campsite's available
 * days or lottery stay periods.
 */

/**
 * Lists the cells of a month calendar whose weeks start on Monday.
 *
 * @param month The month as yyyy-MM.
 * @return Null for each blank cell before the 1st, then every date of the month as
 * yyyy-MM-dd, or an empty list if [month] cannot be read.
 */
fun calendarMonthCells(month: String): List<String?> {
    val first = reservationDateToUtcMillis("$month-01") ?: return emptyList()
    val day = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = first }
    val blanks = (day.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0 blanks, Sunday = 6.
    val length = day.getActualMaximum(Calendar.DAY_OF_MONTH)
    return List<String?>(blanks) { null } + (1..length).map { "$month-%02d".format(Locale.ROOT, it) }
}

/**
 * Moves a month forward or back.
 *
 * @param month The month as yyyy-MM.
 * @param by Number of months to move; negative moves back.
 * @return The new month as yyyy-MM, or [month] unchanged if it cannot be read.
 */
fun shiftCalendarMonth(month: String, by: Int): String {
    val first = reservationDateToUtcMillis("$month-01") ?: return month
    val day = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = first
        add(Calendar.MONTH, by)
    }
    return reservationDateFromUtcMillis(day.timeInMillis).take(7)
}

/**
 * Turns a month into a title like "July 2027".
 *
 * @param month The month as yyyy-MM.
 * @return The readable month, or [month] unchanged if it cannot be read.
 */
fun formatCalendarMonth(month: String): String {
    val first = reservationDateToUtcMillis("$month-01") ?: return month
    return SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(first)
}

/**
 * Chooses which month the calendar opens on.
 *
 * Opens on the month of the next booked night from today, so the owner sees upcoming
 * bookings first. With no upcoming bookings it opens on the current month.
 *
 * @param nights Booked nights as yyyy-MM-dd.
 * @param today Today's date as yyyy-MM-dd.
 * @return The month to show as yyyy-MM.
 */
fun startingCalendarMonth(nights: Collection<String>, today: String): String =
    (nights.filter { it >= today }.minOrNull() ?: today).take(7)

/**
 * Maps each booked night to the name of the entrant staying that night.
 *
 * @param bookings The campsite's reservations with entrant names; only active and past
 * stays book nights.
 * @return Booked nights as yyyy-MM-dd, each with an entrant name.
 */
fun bookedNightNames(bookings: CampsiteBookings): Map<String, String> {
    val all = bookings.active + bookings.past
    val names = all.associate { it.reservation.id to it.entrantName }
    return bookedNights(all.map { it.reservation }).mapValues { (_, reservation) -> names[reservation.id].orEmpty() }
}

/**
 * Draws a month calendar with the campsite's booked nights highlighted.
 *
 * Arrows move between months. Tapping a booked night shows who is staying.
 *
 * @param booked Booked nights as yyyy-MM-dd, each with the entrant's name.
 * @param today Today's date as yyyy-MM-dd, used to choose the first month shown.
 */
@Composable
fun BookedDatesCalendar(booked: Map<String, String>, today: String) {
    var month by rememberSaveable(booked.keys) { mutableStateOf(startingCalendarMonth(booked.keys, today)) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val cells = calendarMonthCells(month)
    val bookedThisMonth = booked.keys.count { it.startsWith(month) }

    GlassCard(modifier = Modifier.testTag("bookedDates")) {
        run {
            Text("Booked dates", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = shiftCalendarMonth(month, -1); selected = null }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(formatCalendarMonth(month), Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                IconButton(onClick = { month = shiftCalendarMonth(month, 1); selected = null }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = PinedrawColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (week + List(7 - week.size) { null }).forEach { date ->
                        val isBooked = date != null && date in booked
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                                .background(
                                    when {
                                        date != null && date == selected -> PinedrawColors.Lime
                                        isBooked -> PinedrawColors.Lime.copy(alpha = 0.35f)
                                        else -> Color.Transparent
                                    }
                                )
                                .then(if (date != null) Modifier.testTag("day-$date") else Modifier)
                                .then(if (isBooked) Modifier.clickable { selected = date } else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            if (date != null) Text(
                                date.takeLast(2).trimStart('0'),
                                color = when {
                                    date == selected -> PinedrawColors.Ink
                                    isBooked -> PinedrawColors.Text
                                    else -> PinedrawColors.TextSecondary
                                },
                                fontWeight = if (isBooked) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            Text(
                selected?.let { "${formatReservationDate(it)}: ${booked[it]?.ifBlank { null } ?: "Booked"}" }
                    ?: when (bookedThisMonth) {
                        0 -> "No nights booked this month."
                        1 -> "1 night booked this month. Tap it to see who is staying."
                        else -> "$bookedThisMonth nights booked this month. Tap one to see who is staying."
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = PinedrawColors.TextSecondary
            )
        }
    }
}
