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
    val requireRulesAcceptance: Boolean = true,
    val averageRating: Double? = null,  // Average campsite rating
    val ratingCount: Int = 0,  // Number of ratings behind the average
    val ratingTotal: Int = 0,  // Sum of their stars
    val lastRatingId: String = ""  // Newest rating, so Firestore rules can check it against the average
) {
    // Checks whether this user owns the campsite and can use its owner-only options.
    fun isOwnedBy(userId: String?): Boolean = !userId.isNullOrBlank() && ownerId == userId
}
