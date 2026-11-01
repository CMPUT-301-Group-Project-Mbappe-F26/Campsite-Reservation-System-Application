package com.example.campsitereservationapp.reservation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.campsite.CampsitePhoto
import com.example.campsitereservationapp.campsite.rememberCampsiteOwnerId
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.Reservation
import com.example.campsitereservationapp.model.formatReservationDate
import com.example.campsitereservationapp.service.CampsiteService
import com.example.campsitereservationapp.service.ReservationService
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

// Loads and shows a reservation belonging to the current entrant.
@Composable
fun ReservationDetailsScreen(reservationId: String, onBack: () -> Unit) {
    val reservationService = remember { ReservationService() }
    val campsiteService = remember { CampsiteService() }
    val entrantId = rememberCampsiteOwnerId()
    var reservation by remember(reservationId, entrantId) { mutableStateOf<Reservation?>(null) }
    var campsite by remember(reservationId, entrantId) { mutableStateOf<Campsite?>(null) }
    var loading by remember(reservationId, entrantId) { mutableStateOf(true) }
    var error by remember(reservationId, entrantId) { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(reservationId, entrantId, refresh) {
        loading = true
        error = ""
        reservation = null
        campsite = null
        try {
            check(entrantId != null) { "Not signed in" }
            val result = withTimeoutOrNull(20000) { reservationService.getReservation(reservationId) }
            if (result == null) error = "This reservation is unavailable or could not be loaded. Please try again."
            else if (result.entrantId != entrantId) error = "This reservation does not belong to your account."
            else {
                reservation = result
                // The booking can still be shown if its campsite cannot be loaded.
                campsite = try {
                    withTimeoutOrNull(20000) { campsiteService.getCampsite(result.campsiteId) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            coroutineContext.ensureActive() // Stop normally when leaving the screen.
            error = "Could not load this reservation. Please try again."
        } finally {
            loading = false
        }
    }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(0f to Color(0xFF1E3A26), 0.45f to PinedrawColors.Background, 1f to PinedrawColors.Background))
            .statusBarsPadding().navigationBarsPadding()
    ) {
        TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 12.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("My Reservations")
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PinedrawColors.Lime)
            }
            error.isNotEmpty() -> Column(Modifier.padding(24.dp)) {
                Text(error, color = PinedrawColors.Coral)
                TextButton(onClick = { refresh++ }) { Text("Try again") }
            }
            else -> reservation?.let { booking ->
                val site = campsite
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CampsitePhoto(site?.imageUrls?.firstOrNull(), Modifier.fillMaxWidth().height(230.dp))
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(booking.campsiteName.ifBlank { site?.name ?: "Unnamed campsite" }, style = MaterialTheme.typography.headlineLarge)
                        Surface(color = PinedrawColors.Lime.copy(alpha = 0.15f), shape = MaterialTheme.shapes.large) {
                            Text(booking.status, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = PinedrawColors.Lime, fontWeight = FontWeight.Bold)
                        }
                        ReservationCard {
                            ReservationRow("Check-in", formatReservationDate(booking.checkInDate))
                            ReservationRow("Check-out", formatReservationDate(booking.checkOutDate))
                            ReservationRow("Location", site?.location?.ifBlank { null } ?: "Not available")
                        }
                        if (site != null) ReservationCard {
                            Text("Rules for entrants", style = MaterialTheme.typography.titleLarge)
                            Text(site.rules.ifBlank { "No additional rules provided." }, color = PinedrawColors.TextSecondary)
                            ReservationRow("Cancellation policy", site.cancellationPolicy.ifBlank { "Not specified" })
                        }
                    }
                }
            }
        }
    }
}

// Groups reservation details inside a rounded card.
@Composable
private fun ReservationCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = PinedrawColors.Glass, contentColor = PinedrawColors.Text)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

// Shows one reservation label and its value.
@Composable
private fun ReservationRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = PinedrawColors.TextSecondary)
        Text(value, Modifier.weight(1.3f), fontWeight = FontWeight.SemiBold)
    }
}
