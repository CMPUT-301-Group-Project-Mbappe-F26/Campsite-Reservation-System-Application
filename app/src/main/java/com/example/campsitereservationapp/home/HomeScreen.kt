package com.example.campsitereservationapp.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.campsitereservationapp.qr.CampsiteQr
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme

// Shows the welcome screen and profile shortcut.
/**
 * Displays the Discover screen where users can browse and search campsites.
 *
 * Loads published campsites from Firebase and shows their basic information.
 * Users can search by name or location and open a campsite to see its details.
 *
 * @param user The currently signed-in user.
 * @param onProfileClick Opens the user's profile.
 * @param onCampsiteClick Opens the selected campsite using its ID.
 * @param searchText The current text entered in the search bar.
 * @param onSearchTextChange Updates the search text when the user types.
 */
@Composable
fun HomeScreen(
    user: User,
    onProfileClick: () -> Unit,
    onCampsiteClick: (String) -> Unit,  // Opens selected campsite
    searchText: String,
    onSearchTextChange: (String) -> Unit
) {
    val campsiteService = remember { CampsiteService() }  // Service to get campsite data
    var selectedFilters by remember { mutableStateOf(CampsiteFilters()) } // Stores filters currently selected by the user
    var showFilters by remember { mutableStateOf(false) } // Controls whether the filter popup is visible
    // Context is needed to check camera permission
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanError by remember { mutableStateOf("") }  // Stores error messages
    var checkingCampsite by remember { mutableStateOf(false) }
    // Opens the QR scanner and handles the scanned result
    val qrScanner = rememberLauncherForActivityResult(
        ScanContract()
    ) { result ->
        val qrText = result.contents
        if (qrText != null) {
            // Get the campsite ID from QR code
            val campsiteId = CampsiteQr.getCampsiteId(qrText)
            if (campsiteId == null) { scanError = "Invalid campsite QR code."
            } else {
                checkingCampsite = true
                scope.launch {
                    try {
                        // Finds campsite in Firebase
                        val campsite = campsiteService.getCampsite(campsiteId)
                        if (campsite != null && campsite.published) {
                            scanError = ""
                            // Open the campsite details screen
                            onCampsiteClick(campsiteId)
                        } else {
                            scanError = "Campsite not found."
                        }
                    } catch (e: Exception) {
                        scanError = "Could not load campsite."
                    } finally {
                        checkingCampsite = false
                    }
                }
            }
        }
    }

    // Requests camera permission before opening the scanner
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionGranted ->
        if (permissionGranted) {
            // Set up the scanner to read QR codes
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            options.setPrompt("Scan a campsite QR code")
            options.setBeepEnabled(false)
            qrScanner.launch(options)
        } else {
            scanError = "Camera permission is required."
        }
    }

    var campsites by remember { mutableStateOf<List<Campsite>>(emptyList()) }  // Store the campsites returned from Firebase
    var loading by remember { mutableStateOf(true) }  // Track loading and errors
    var error by remember { mutableStateOf("") }
    // First apply the existing search
    val searchedCampsites = getDisplayedCampsites(campsites, searchText)
    // Then apply the selected filters
    val filteredCampsites = applyCampsiteFilters(searchedCampsites, selectedFilters)
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
    // Show the filter popup when the user presses Filters
    if (showFilters) {
        CampsiteFilterDialog(
            currentFilters = selectedFilters,
            onApply = { newFilters ->
                selectedFilters = newFilters
                showFilters = false
            },
            onClose = {
                showFilters = false
            }
        )
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
        // Space between the search bar and QR button
        Spacer(modifier = Modifier.height(12.dp))
        // Space between the search bar and buttons
        Spacer(modifier = Modifier.height(12.dp))

        // Opens the campsite filter selection popup
        OutlinedButton(
            onClick = {
                showFilters = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Filter Campsites")
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Button to scan a campsite QR code
        OutlinedButton(
            onClick = {
                scanError = ""
                // Check if camera permission is already allowed
                val permission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                )
                if (permission == PackageManager.PERMISSION_GRANTED) {
                    // Open the QR scanner
                    val options = ScanOptions()
                    options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    options.setPrompt("Scan a campsite QR code")
                    options.setBeepEnabled(false)
                    qrScanner.launch(options)
                } else {
                    // Ask user for camera permission
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }
            },
            enabled = !checkingCampsite,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Scan QR Code")
        }
        //Loading message while checking campsite
        if (checkingCampsite) {
            Text(
                text = "Checking campsite...",
                color = PinedrawColors.TextSecondary
            )
        }
        // Error if scanning fails
        if (scanError.isNotEmpty()) {
            Text(
                text = scanError,
                color = PinedrawColors.Coral
            )
        }
        // Show the filters user has selected
        if (hasActiveFilters(selectedFilters)) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Active Filters",
                style = MaterialTheme.typography.titleSmall,
                color = PinedrawColors.Lime
            )
            // Show selected location
            if (selectedFilters.location.isNotBlank()) {
                Text(
                    text = "Location: ${selectedFilters.location}",
                    color = PinedrawColors.TextSecondary
                )
            }
            // Show selected dates
            if (selectedFilters.startDate.isNotBlank()) {
                Text(
                    text = "Dates: ${selectedFilters.startDate} to ${selectedFilters.endDate}",
                    color = PinedrawColors.TextSecondary
                )
            }
            // Show selected number of guests
            if (selectedFilters.guests > 0) {
                Text(
                    text = "Guests: ${selectedFilters.guests}",
                    color = PinedrawColors.TextSecondary
                )
            }
            // Remove all selected filters
            TextButton(
                onClick = {
                    selectedFilters = CampsiteFilters()
                }
            ) {
                Text("Clear All Filters")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

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
                                    Row {
                                        Text(  // Stars using unicode
                                            text = getRatingStars(rating),
                                            color = Color(0xFFFFC107)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = getRatingText(rating),
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
/**
 * Filters campsites based on the user's search.
 *
 * Searches campsite names and locations without considering uppercase
 * or lowercase letters.
 *
 * @param campsites List of campsites to search through.
 * @param searchText Text entered by the user.
 * @return A list of campsites matching the search.
 */
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
/**
 * Gets the campsites that should appear on the Discover screen.
 *
 * Removes unpublished campsites and applies the user's search.
 *
 * @param campsites List of available campsites.
 * @param searchText Current search text.
 * @return A list of published campsites matching the search.
 */
fun getDisplayedCampsites(
    campsites: List<Campsite>,
    searchText: String
): List<Campsite> {
    val publishedCampsites = campsites.filter { it.published }
    return filterCampsites(publishedCampsites, searchText)
}
/**
 * Converts a campsite rating into a string of stars.
 *
 * Uses filled and empty stars to display the rating.
 * Decimal ratings are rounded down for the star display.
 *
 * @param rating The campsite's average rating.
 * @return A string containing five stars.
 */
fun getRatingStars(rating: Double): String {
    val fullStars = rating.toInt().coerceIn(0, 5)
    return "\u2605".repeat(fullStars) + "\u2606".repeat(5 - fullStars)
}
/**
 * Gets the text used to display a campsite's rating.
 *
 * Shows the numerical rating when available, or a message
 * when the campsite has no ratings.
 *
 * @param rating The average rating, or null if there are no ratings.
 * @return The formatted rating or "No ratings yet".
 */
fun getRatingText(rating: Double?): String {
    if (rating == null) {
        return "No ratings yet"
    }

    // Averages rarely divide evenly, so show one decimal place.
    return String.format(Locale.CANADA, "%.1f / 5", rating)
}

