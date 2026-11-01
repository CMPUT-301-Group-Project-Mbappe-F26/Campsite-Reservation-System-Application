package com.example.campsitereservationapp.reservation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.campsite.rememberCampsiteOwnerId
import com.example.campsitereservationapp.model.Reservation
import com.example.campsitereservationapp.model.formatReservationDate
import com.example.campsitereservationapp.service.ReservationService
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

// Loads and displays the signed-in entrant's active reservations.
@Composable
fun MyReservationsScreen(onReservationClick: (String) -> Unit) {
    val service = remember { ReservationService() }
    val entrantId = rememberCampsiteOwnerId()
    var reservations by remember(entrantId) { mutableStateOf<List<Reservation>>(emptyList()) }
    var loading by remember(entrantId) { mutableStateOf(true) }
    var error by remember(entrantId) { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(entrantId, refresh) {
        loading = true
        error = ""
        try {
            check(entrantId != null) { "Not signed in" }
            val result = withTimeoutOrNull(20000) { service.getMyActiveReservations() }
            if (result == null) error = "Could not load your reservations. Please try again."
            else reservations = result
        } catch (_: Exception) {
            coroutineContext.ensureActive() // Stop normally when leaving the screen.
            error = if (entrantId == null) "Sign in to view your reservations." else "Could not load your reservations. Please try again."
        } finally {
            loading = false
        }
    }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(0f to Color(0xFF1E3A26), 0.45f to PinedrawColors.Background, 1f to PinedrawColors.Background))
            .statusBarsPadding().padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("My Reservations", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Your upcoming campsite stays.", color = PinedrawColors.TextSecondary)
        Spacer(Modifier.height(16.dp))

        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PinedrawColors.Lime)
            }
            error.isNotEmpty() -> Column(Modifier.padding(vertical = 24.dp)) {
                Text(error, color = PinedrawColors.Coral)
                TextButton(onClick = { refresh++ }) { Text("Try again") }
            }
            reservations.isEmpty() -> Card(
                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = PinedrawColors.Glass, contentColor = PinedrawColors.Text)
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text("No active reservations", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("Campsites you reserve will appear here.", color = PinedrawColors.TextSecondary)
                }
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(reservations, key = { it.id }) { reservation ->
                    Card(
                        onClick = { onReservationClick(reservation.id) },
                        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = PinedrawColors.Glass, contentColor = PinedrawColors.Text)
                    ) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(reservation.campsiteName.ifBlank { "Unnamed campsite" }, fontWeight = FontWeight.Bold)
                                Text(
                                    "${formatReservationDate(reservation.checkInDate)} – ${formatReservationDate(reservation.checkOutDate)}",
                                    color = PinedrawColors.TextSecondary
                                )
                                Text(reservation.status, color = PinedrawColors.Lime, style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                                modifier = Modifier.size(20.dp), tint = PinedrawColors.TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}
