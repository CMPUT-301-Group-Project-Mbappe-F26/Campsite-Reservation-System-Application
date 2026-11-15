package com.example.campsitereservationapp.reservation

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.campsite.CampsitePhoto
import com.example.campsitereservationapp.campsite.rememberCampsiteOwnerId
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.RESERVATION_CANCELLED
import com.example.campsitereservationapp.model.RESERVATION_COMPLETED
import com.example.campsitereservationapp.model.Reservation
import com.example.campsitereservationapp.model.cancelError
import com.example.campsitereservationapp.model.formatReservationDate
import com.example.campsitereservationapp.model.reservationDateToday
import com.example.campsitereservationapp.model.reservationStatusLabel
import com.example.campsitereservationapp.service.CampsiteService
import com.example.campsitereservationapp.service.ReservationService
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Details of one of the entrant's reservations, opened from My Reservations (US 01.06.01),
 * where the entrant can also cancel a stay that has not started (US 01.07.01).
 *
 * [ReservationDetailsScreen] loads the reservation and its campsite from Firebase and only
 * shows reservations belonging to the signed-in entrant. [ReservationDetailsContent] only
 * draws them, so intent tests can check the screen without Firebase.
 *
 * Outstanding issues: cancelling sends no notification yet (US 01.07.02).
 */

/**
 * Loads and shows a reservation belonging to the signed-in entrant.
 *
 * @param reservationId Firestore document ID of the reservation.
 * @param onBack Returns to My Reservations.
 * @param onOpenMessages Opens the message thread with the campsite owner.
 */
@Composable
fun ReservationDetailsScreen(reservationId: String, onBack: () -> Unit, onOpenMessages: () -> Unit = {}) {
    val reservationService = remember { ReservationService() }
    val campsiteService = remember { CampsiteService() }
    val entrantId = rememberCampsiteOwnerId()
    val scope = rememberCoroutineScope()
    var reservation by remember(reservationId, entrantId) { mutableStateOf<Reservation?>(null) }
    var campsite by remember(reservationId, entrantId) { mutableStateOf<Campsite?>(null) }
    var loading by remember(reservationId, entrantId) { mutableStateOf(true) }
    var error by remember(reservationId, entrantId) { mutableStateOf("") }
    var cancelling by remember(reservationId) { mutableStateOf(false) }
    var cancelMessage by remember(reservationId) { mutableStateOf("") }
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

    // Cancels the reservation and shows it as Cancelled without reloading the screen.
    fun cancelReservation() {
        val booking = reservation ?: return
        scope.launch {
            cancelling = true
            cancelMessage = ""
            try {
                val problem = reservationService.cancel(booking.id)
                if (problem == null) {
                    reservation = booking.copy(status = RESERVATION_CANCELLED)
                    cancelMessage = "Your reservation has been cancelled."
                } else cancelMessage = problem
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ReservationDetails", "Could not cancel reservation ${booking.id}", e)
                cancelMessage = "Could not cancel this reservation. Check your connection and try again."
            } finally {
                cancelling = false
            }
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
                val canCancel = cancelError(booking, entrantId.orEmpty(), reservationDateToday()) == null
                ReservationDetailsContent(
                    booking, campsite, onOpenMessages,
                    onCancel = if (canCancel) ::cancelReservation else null,
                    cancelling = cancelling, cancelMessage = cancelMessage
                )
            }
        }
    }
}

/**
 * Draws a loaded reservation: campsite, status, dates, location and the campsite's rules.
 *
 * When [onCancel] is given, a Cancel reservation button asks the entrant to confirm before
 * calling it.
 *
 * @param booking The reservation to show.
 * @param site The reserved campsite, or null if it could not be loaded; its location and rules are then skipped.
 * @param onOpenMessages Opens the message thread with the owner, or null to hide that button.
 * @param onCancel Cancels the reservation after the entrant confirms, or null when it cannot be cancelled.
 * @param cancelling True while the cancellation is being saved.
 * @param cancelMessage Result of the last cancel attempt, or blank.
 * @param today Today's date as yyyy-MM-dd, used to show a finished stay as Completed.
 */
@Composable
fun ReservationDetailsContent(
    booking: Reservation,
    site: Campsite?,
    onOpenMessages: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    cancelling: Boolean = false,
    cancelMessage: String = "",
    today: String = reservationDateToday()
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val status = reservationStatusLabel(booking, today)
    val statusColor = when (status) {
        RESERVATION_CANCELLED -> PinedrawColors.Coral
        RESERVATION_COMPLETED -> PinedrawColors.TextMuted
        else -> PinedrawColors.Lime
    }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        CampsitePhoto(site?.imageUrls?.firstOrNull(), Modifier.fillMaxWidth().height(230.dp))
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(booking.campsiteName.ifBlank { site?.name ?: "Unnamed campsite" }, style = MaterialTheme.typography.headlineLarge)
            Surface(color = statusColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.large) {
                Text(status, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = statusColor, fontWeight = FontWeight.Bold)
            }
            if (onOpenMessages != null) {
                OutlinedButton(onClick = onOpenMessages, modifier = Modifier.fillMaxWidth()) {
                    Text("Message campsite owner")
                }
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
            if (onCancel != null) {
                OutlinedButton(
                    onClick = { confirming = true }, enabled = !cancelling, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PinedrawColors.Coral)
                ) {
                    Text(if (cancelling) "Cancelling…" else "Cancel reservation")
                }
            }
            if (cancelMessage.isNotEmpty()) {
                Text(
                    cancelMessage,
                    color = if (booking.status == RESERVATION_CANCELLED) PinedrawColors.TextSecondary else PinedrawColors.Coral
                )
            }
        }
    }

    if (confirming && onCancel != null) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Cancel this reservation?") },
            text = {
                Text(
                    "Your stay from ${formatReservationDate(booking.checkInDate)} to " +
                        "${formatReservationDate(booking.checkOutDate)} will be released to other " +
                        "entrants. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirming = false; onCancel() }) {
                    Text("Yes, cancel", color = PinedrawColors.Coral)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Keep reservation") }
            }
        )
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
