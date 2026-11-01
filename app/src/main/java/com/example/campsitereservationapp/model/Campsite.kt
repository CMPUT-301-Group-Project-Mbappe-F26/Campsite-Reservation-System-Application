package com.example.campsitereservationapp.model

/**
 * A campsite listing, its owner, location, photos, and reservation rules.
 * Available days use Monday = 1 through Sunday = 7; season dates use yyyy-MM-dd.
 */
data class Campsite(
    val id: String = "",
    val ownerId: String = "",
    val name: String = "",
    val location: String = "",
    val description: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val maxOccupancy: Int? = null,
    val pricePerNight: Double = 0.0,
    val tags: List<String> = emptyList(),
    val amenities: List<String> = emptyList(),
    val imageUrls: List<String> = emptyList(),
    val published: Boolean = false,
    val availableDays: List<Int> = emptyList(),
    val availableFrom: String = "",
    val availableUntil: String = "",
    val rules: String = "",
    val cancellationPolicy: String = "Flexible",
    val requireRulesAcceptance: Boolean = true
)
