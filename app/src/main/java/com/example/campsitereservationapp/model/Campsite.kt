package com.example.campsitereservationapp.model

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
    val published: Boolean = false
)
