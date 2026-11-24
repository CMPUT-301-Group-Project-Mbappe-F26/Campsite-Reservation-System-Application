package com.example.campsitereservationapp.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bathtub
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Deck
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Shower
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

/** Nightly price as whole dollars, capped so bad data can't break a layout. */
fun formatPrice(price: Double): String = when {
    price.isNaN() || price < 0 -> "$0"
    price >= 100_000 -> "$99,999+"
    else -> String.format(Locale.CANADA, "$%,.0f", price)
}

/** Matches an amenity name to a Material icon. */
fun amenityIcon(name: String): ImageVector {
    val key = name.lowercase()
    return when {
        "fire" in key -> Icons.Rounded.LocalFireDepartment
        "water" in key -> Icons.Rounded.WaterDrop
        "shower" in key -> Icons.Rounded.Shower
        "toilet" in key || "washroom" in key || "outhouse" in key -> Icons.Rounded.Wc
        "pet" in key || "dog" in key -> Icons.Rounded.Pets
        "wifi" in key || "wi-fi" in key -> Icons.Rounded.Wifi
        "power" in key || "electric" in key -> Icons.Rounded.ElectricBolt
        "park" in key -> Icons.Rounded.LocalParking
        "picnic" in key || "table" in key -> Icons.Rounded.Deck
        "food" in key || "kitchen" in key || "cook" in key -> Icons.Rounded.Restaurant
        "trail" in key || "hik" in key -> Icons.Rounded.Hiking
        "bath" in key -> Icons.Rounded.Bathtub
        "tennis" in key -> Icons.Rounded.SportsTennis
        else -> Icons.Rounded.CheckCircle
    }
}
