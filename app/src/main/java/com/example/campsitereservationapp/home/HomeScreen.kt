package com.example.campsitereservationapp.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.service.CampsiteService
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.DisposableEffect
// Shows the welcome screen and profile shortcut.
@Composable
fun HomeScreen(
    user: User,
    onProfileClick: () -> Unit,
    onCampsiteClick: (String) -> Unit,  // Opens selected campsite
    searchText: String,
    onSearchTextChange: (String) -> Unit
) {
    val campsiteService = remember { CampsiteService() }  // Service to get campsite data
    var campsites by remember { mutableStateOf<List<Campsite>>(emptyList()) }  // Store the campsites returned from Firebase
    var loading by remember { mutableStateOf(true) }  // Track loading and errors
    var error by remember { mutableStateOf("") }
    // Finds campsites matching based on filter
    val filteredCampsites = getDisplayedCampsites(campsites, searchText)
    // Loading campsites
    DisposableEffect(campsiteService) {
        // Listening for changes
        val listener = campsiteService.listenToPublishedCampsites(
            onUpdate = { updatedCampsites ->
                // Updating the list on the screen
                campsites = updatedCampsites
                error = ""
                loading = false
            },
            onError = {  // Error shown if firebase fails
                error = "Could not load campsites."
                loading = false
            }
        )
        // Stops listening when screen is not present
        onDispose {
            listener.remove()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF1E3A26),
                    0.45f to PinedrawColors.Background,
                    1f to PinedrawColors.Background
                )
            )
            .statusBarsPadding()
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Welcome back,",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PinedrawColors.TextSecondary
                )

                Text(
                    text = user.name.ifBlank { "Explorer" },
                    style = MaterialTheme.typography.headlineLarge,
                    color = PinedrawColors.Text
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .border(2.dp, PinedrawColors.Lime, CircleShape)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2E8C86), Color(0xFF1F5C73))
                        )
                    )
                    .clickable { onProfileClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.name.firstOrNull()?.uppercase() ?: "P",
                    style = MaterialTheme.typography.titleLarge,
                    color = PinedrawColors.Text
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Find your next adventure.",
            style = MaterialTheme.typography.headlineLarge,
            color = PinedrawColors.Lime
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Discover campsites and explore new places.",
            style = MaterialTheme.typography.bodyLarge,
            color = PinedrawColors.TextSecondary
        )
        // Space added above the search bar
        Spacer(modifier = Modifier.height(16.dp))
        // Let users enter campsite name or location in the searchbar
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchTextChange,  // Update search text in MainScreen
            label = { Text("Search campsites") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(24.dp))
        // Display Loading, errors, or the list of campsites
        when {
            loading -> {
                CircularProgressIndicator()
            }
            error.isNotEmpty() -> {
                Text(error, color = PinedrawColors.Text)
            }
            // Message shown when campsites not found
            filteredCampsites.isEmpty() -> {
                Text(
                    text = if (searchText.isBlank()) {
                        "No campsites available."
                    } else {
                        "No campsites found"
                    },
                    color = PinedrawColors.TextSecondary
                )
            }
            else -> {
                // Displays in a scrollable list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredCampsites) { campsite ->
                        // Card is created for each campsite
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .clickable { onCampsiteClick(campsite.id) }  // main screen gets campsite ID
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = campsite.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = campsite.location,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                // Displays the maximum number of people allowed at the campsite
                                Text(
                                    text = campsite.maxOccupancy?.let { "Max occupancy: $it" }
                                        ?: "Max occupancy not provided",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                // Rating and no ratings are available
                                if (campsite.averageRating != null) {
                                    val rating = campsite.averageRating
                                    val fullStars = rating.toInt()

                                    Row {
                                        Text(  // Stars using unicode
                                            text = "\u2605".repeat(fullStars) + "\u2606".repeat(5 - fullStars),
                                            color = Color(0xFFFFC107)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "$rating / 5",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                } else {
                                    // If no ratings available
                                    Text(
                                        text = "No ratings yet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PinedrawColors.TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Find campsites using their name or location
fun filterCampsites(
    campsites: List<Campsite>,
    searchText: String
): List<Campsite> {
    val keyword = searchText.trim()
    // Return campsites that match the search
    return campsites.filter { campsite ->
        campsite.name.contains(keyword, ignoreCase = true) || campsite.location.contains(keyword, ignoreCase = true)
    }
}
// For testing
fun getDisplayedCampsites(
    campsites: List<Campsite>,
    searchText: String
): List<Campsite> {
    val publishedCampsites = campsites.filter { it.published }
    return filterCampsites(publishedCampsites, searchText)
}

fun getRatingStars(rating: Double): String {
    val fullStars = rating.toInt().coerceIn(0, 5)
    return "\u2605".repeat(fullStars) + "\u2606".repeat(5 - fullStars)
}

fun getRatingText(rating: Double?): String {
    if (rating == null) {
        return "No ratings yet"
    }

    return "$rating / 5"
}

