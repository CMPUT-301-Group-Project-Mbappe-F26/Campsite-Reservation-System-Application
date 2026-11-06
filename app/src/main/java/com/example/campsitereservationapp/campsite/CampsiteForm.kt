package com.example.campsitereservationapp.campsite

import com.example.campsitereservationapp.model.Campsite
import java.io.Serializable
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

/** Keeps the registration draft separate from a saved campsite. */
data class CampsiteForm(
    val name: String = "",
    val description: String = "",
    val occupancy: String = "",
    val price: String = "",
    val tags: String = "",
    val location: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val amenities: List<String> = emptyList(),
    val photos: List<String> = emptyList(),
    val availableDays: List<Int> = (1..7).toList(),
    val availableFrom: String = "",
    val availableUntil: String = "",
    val rules: String = "",
    val cancellationPolicy: String = "Flexible"
) : Serializable {

    /**
     * Checks the form for errors in the current step.
     *
     * @param step The step being checked.
     * @return The errors found in the form.
     */
    fun errors(step: Int): Map<String, String> = buildMap {
        when (step) {
            0 -> {
                if (name.isBlank()) put("name", "Enter a campsite name.")
                if (description.isBlank() || description.length > 600)
                    put("description", "Enter a description of up to 600 characters.")
                if (occupancy.isNotBlank() && (occupancy.toIntOrNull() ?: 0) <= 0)
                    put("occupancy", "Enter a positive whole number or leave blank for no maximum.")
                val amount = price.toDoubleOrNull()
                if (amount == null || !amount.isFinite() || amount < 0)
                    put("price", "Enter a price of 0 or more.")
            }
            1 -> {
                if (location.isBlank()) put("location", "Enter an address or location description.")
                val lat = latitude.toDoubleOrNull()
                val lon = longitude.toDoubleOrNull()
                if (lat == null || !lat.isFinite() || lat !in -90.0..90.0)
                    put("latitude", "Enter a latitude between -90 and 90.")
                if (lon == null || !lon.isFinite() || lon !in -180.0..180.0)
                    put("longitude", "Enter a longitude between -180 and 180.")
            }
            2 -> if (photos.size > 10) put("photos", "Choose up to 10 photos.")
            3 -> {
                if (availableDays.isEmpty() || availableDays.any { it !in 1..7 })
                    put("availability", "Select at least one available day.")
                val format = SimpleDateFormat("yyyy-MM-dd", Locale.CANADA).apply { isLenient = false }
                // Checks that the date has the right format and exists on the calendar.
                fun validDate(value: String): Boolean {
                    if (!value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return false
                    val date = format.parse(value, ParsePosition(0)) ?: return false
                    return format.format(date) == value
                }
                if (availableFrom.isNotBlank() && !validDate(availableFrom))
                    put("availability", "Choose a valid start date.")
                if (availableUntil.isNotBlank() && !validDate(availableUntil))
                    put("availability", "Choose a valid end date.")
                if (availableFrom.isNotBlank() && availableUntil.isNotBlank() && availableFrom > availableUntil)
                    put("availability", "End date must be on or after the start date.")
                if (cancellationPolicy !in listOf("Flexible", "Moderate", "Strict"))
                    put("policy", "Choose a cancellation policy.")
            }
        }
    }
    /**
     * Creates a campsite using the information entered in the form.
     *
     * @return The new campsite.
     */
    // Converts a valid form into a campsite ready to save.
    fun createCampsiteData(): Campsite {
        require((0..3).all { errors(it).isEmpty() }) { "Please correct the required fields." }
        return Campsite(
            name = name.trim(), description = description.trim(), location = location.trim(),
            latitude = latitude.toDouble(), longitude = longitude.toDouble(),
            maxOccupancy = occupancy.takeIf { it.isNotBlank() }?.toInt(),
            pricePerNight = price.toDouble(),
            tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            amenities = amenities, published = true, availableDays = availableDays,
            availableFrom = availableFrom, availableUntil = availableUntil,
            rules = rules.trim(), cancellationPolicy = cancellationPolicy
        )
    }
}
