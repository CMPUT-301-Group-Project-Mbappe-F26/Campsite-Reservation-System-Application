package com.example.campsitereservationapp.notification

import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.formatReservationDate
import java.io.OutputStream
import java.util.Locale

/** Builds the reservation details used by the entrant notification. */
internal fun reservationConfirmationMessage(
    campsite: Campsite,
    checkIn: String,
    checkOut: String,
    guests: Int,
    total: Double
): String = buildString {
    append(campsite.name.ifBlank { "Unnamed campsite" })
    if (campsite.location.isNotBlank()) append(" · ${campsite.location}")
    append(" · ${formatReservationDate(checkIn)} to ${formatReservationDate(checkOut)}")
    append(" · $guests ${if (guests == 1) "guest" else "guests"}")
    append(String.format(Locale.CANADA, " · $%.2f CAD", total))
}

/** Creates the CSV shown on the reservation confirmation screen. */
internal fun reservationCsv(
    campsite: Campsite,
    checkIn: String,
    checkOut: String,
    guests: Int,
    total: Double
): String {
    val values = listOf(
        campsite.name.ifBlank { "Unnamed campsite" },
        campsite.location,
        checkIn,
        checkOut,
        guests.toString(),
        String.format(Locale.CANADA, "$%.2f CAD", total)
    )
    return "Campsite name,Location,Check-in,Check-out,Guests,Price\n" +
        values.joinToString(",", transform = ::csvValue)
}

/** Writes a CSV and reports failure without crashing the confirmation screen. */
internal fun writeReservationCsv(csv: String, openFile: () -> OutputStream?): Boolean = try {
    val output = openFile() ?: return false
    output.writer(Charsets.UTF_8).use { it.write(csv) }
    true
} catch (_: Exception) {
    false
}

private fun csvValue(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' }) "\"${value.replace("\"", "\"\"")}\"" else value
