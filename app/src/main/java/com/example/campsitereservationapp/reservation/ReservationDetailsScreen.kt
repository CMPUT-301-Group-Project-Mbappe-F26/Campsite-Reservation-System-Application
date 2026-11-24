package com.example.campsitereservationapp.reservation

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.ui.text.style.TextAlign
import com.example.campsitereservationapp.ui.components.ButtonStyle
import com.example.campsitereservationapp.ui.components.CircleIconButton
import com.example.campsitereservationapp.ui.components.ErrorState
import com.example.campsitereservationapp.ui.components.GlassCard
import com.example.campsitereservationapp.ui.components.InfoRow
import com.example.campsitereservationapp.ui.components.InlineMessage
import com.example.campsitereservationapp.ui.components.LoadingState
import com.example.campsitereservationapp.ui.components.PinedrawBackground
import com.example.campsitereservationapp.ui.components.PinedrawButton
import com.example.campsitereservationapp.ui.components.PinedrawDialog
import com.example.campsitereservationapp.ui.components.PinedrawTopBar
import com.example.campsitereservationapp.ui.components.StatusChip
import com.example.campsitereservationapp.ui.theme.PinedrawRadius
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.campsite.CampsitePhoto
import com.example.campsitereservationapp.campsite.rememberCampsiteOwnerId
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.RESERVATION_CANCELLED
import com.example.campsitereservationapp.model.RESERVATION_COMPLETED
import com.example.campsitereservationapp.model.RESERVATION_REJECTED
import com.example.campsitereservationapp.model.Reservation
import com.example.campsitereservationapp.model.cancelError
import com.example.campsitereservationapp.model.formatReservationDate
import com.example.campsitereservationapp.model.reservationNights
import com.example.campsitereservationapp.model.reservationDateToday
import com.example.campsitereservationapp.model.reservationStatusLabel
import com.example.campsitereservationapp.notification.reservationCsv
import com.example.campsitereservationapp.notification.writeReservationCsv
import com.example.campsitereservationapp.service.CampsiteService
import com.example.campsitereservationapp.messaging.canMessageOwner
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

    PinedrawBackground {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            when {
                loading -> Column(Modifier.statusBarsPadding()) {
                    PinedrawTopBar("Reservation", onBack = onBack, backLabel = "My trips")
                    LoadingState()
                }
                error.isNotEmpty() -> Column(Modifier.statusBarsPadding()) {
                    PinedrawTopBar("Reservation", onBack = onBack, backLabel = "My trips")
                    ErrorState(error, onRetry = { refresh++ })
                }
                else -> reservation?.let { booking ->
                    val canCancel = cancelError(booking, entrantId.orEmpty(), reservationDateToday()) == null
                    ReservationDetailsContent(
                        booking, campsite, onOpenMessages,
                        onCancel = if (canCancel) ::cancelReservation else null,
                        cancelling = cancelling, cancelMessage = cancelMessage,
                        onBackToTrips = onBack,
                        onBack = onBack
                    )
                }
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
    today: String = reservationDateToday(),
    onBackToTrips: () -> Unit = {},
    onDownloadCsv: ((String) -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    if (booking.status == RESERVATION_CANCELLED) {
        CancellationConfirmation(booking, site, onBackToTrips, onDownloadCsv)
        return
    }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val status = reservationStatusLabel(booking, today)
    val statusColor = when (status) {
        RESERVATION_CANCELLED, RESERVATION_REJECTED -> PinedrawColors.Coral
        RESERVATION_COMPLETED -> PinedrawColors.TextMuted
        else -> PinedrawColors.Lime
    }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(280.dp)) {
            CampsitePhoto(site?.imageUrls?.firstOrNull(), Modifier.matchParentSize(), seed = booking.campsiteId, shape = null)
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to PinedrawColors.Background.copy(alpha = 0.45f),
                        0.3f to Color.Transparent,
                        0.7f to Color.Transparent,
                        1f to PinedrawColors.Background
                    )
                )
            )
            if (onBack != null) CircleIconButton(
                Icons.AutoMirrored.Rounded.ArrowBack, "My trips", onBack,
                container = PinedrawColors.Background.copy(alpha = 0.6f),
                modifier = Modifier.statusBarsPadding().padding(PinedrawSpacing.Large)
            )
        }
        Column(
            Modifier.padding(horizontal = PinedrawSpacing.Gutter).padding(bottom = PinedrawSpacing.Screen),
            verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Gutter)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Small)) {
                Text(booking.campsiteName.ifBlank { site?.name ?: "Unnamed campsite" }, style = MaterialTheme.typography.headlineLarge)
                StatusChip(status, color = statusColor)
            }
            GlassCard {
                Row(Modifier.fillMaxWidth()) {
                    DateBlock("Check-in", formatReservationDate(booking.checkInDate), Modifier.weight(1f))
                    DateBlock("Check-out", formatReservationDate(booking.checkOutDate), Modifier.weight(1f))
                }
                HorizontalDivider(color = PinedrawColors.Border)
                InfoRow("Location", site?.location?.ifBlank { null } ?: "Not available")
            }
            if (onOpenMessages != null && canMessageOwner(booking)) {
                PinedrawButton(
                    "Message owner", onOpenMessages, Modifier.fillMaxWidth(),
                    style = ButtonStyle.Secondary, icon = Icons.AutoMirrored.Rounded.Chat, enabled = !cancelling
                )
            }
            if (site != null) GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Gavel, null, tint = PinedrawColors.Lime, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(PinedrawSpacing.Small))
                    Text("Rules for entrants", style = MaterialTheme.typography.titleMedium)
                }
                Text(site.rules.ifBlank { "No additional rules provided." }, style = MaterialTheme.typography.bodyLarge, color = PinedrawColors.TextSecondary)
                InfoRow("Cancellation policy", site.cancellationPolicy.ifBlank { "Not specified" })
            }
            if (onCancel != null) {
                PinedrawButton(
                    if (cancelling) "Cancelling…" else "Cancel reservation",
                    onClick = { confirming = true },
                    modifier = Modifier.fillMaxWidth(),
                    style = ButtonStyle.SoftDestructive,
                    icon = Icons.Rounded.EventBusy,
                    enabled = !cancelling
                )
            }
            if (cancelMessage.isNotEmpty()) {
                InlineMessage(
                    cancelMessage,
                    color = if (booking.status == RESERVATION_CANCELLED) PinedrawColors.TextSecondary else PinedrawColors.Coral
                )
            }
        }
    }

    if (confirming && onCancel != null) {
        PinedrawDialog(
            title = "Cancel this reservation?",
            message = "Your stay from ${formatReservationDate(booking.checkInDate)} to " +
                "${formatReservationDate(booking.checkOutDate)} will be released to other " +
                "entrants. This cannot be undone.",
            icon = Icons.Rounded.EventBusy,
            iconTint = PinedrawColors.Coral,
            dismissText = "Keep reservation",
            confirmText = "Yes, cancel",
            confirmStyle = ButtonStyle.Destructive,
            onDismiss = { confirming = false },
            onConfirm = { confirming = false; onCancel() }
        )
    }
}

@Composable
private fun DateBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = PinedrawColors.Text)
    }
}

/** Receipt shown after a reservation is cancelled (US 01.07.02). */
@Composable
internal fun CancellationConfirmation(
    booking: Reservation,
    site: Campsite?,
    onBackToTrips: () -> Unit,
    onDownloadCsv: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val campsite = site ?: Campsite(name = booking.campsiteName)
    val guests = booking.guestCount.coerceAtLeast(1)
    val total = booking.totalPrice.takeIf { it > 0.0 }
        ?: (site?.pricePerNight ?: 0.0) * reservationNights(booking.checkInDate, booking.checkOutDate).size
    val csv = remember(booking, campsite, guests, total) {
        reservationCsv(campsite, booking.checkInDate, booking.checkOutDate, guests, total, RESERVATION_CANCELLED)
    }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null && !writeReservationCsv(csv) { context.contentResolver.openOutputStream(uri) }) {
            Toast.makeText(context, "Could not download CSV", Toast.LENGTH_LONG).show()
        }
    }
    BackHandler(onBack = onBackToTrips)

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(PinedrawSpacing.Screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(Modifier.height(PinedrawSpacing.Section))
        Box(Modifier.size(88.dp).background(PinedrawColors.Teal.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(64.dp).background(PinedrawColors.Teal, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = PinedrawColors.Ink, modifier = Modifier.size(36.dp))
            }
        }
        Text("Reservation cancelled", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Text(
            "A cancellation confirmation is in your notifications.",
            style = MaterialTheme.typography.bodyLarge,
            color = PinedrawColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        Card(
            modifier = Modifier.fillMaxWidth(), shape = PinedrawRadius.Large,
            colors = CardDefaults.cardColors(containerColor = PinedrawColors.Text, contentColor = PinedrawColors.Ink)
        ) {
            Column(Modifier.padding(PinedrawSpacing.Gutter), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CancellationRow("Reservation", booking.id)
                CancellationRow("Campsite", booking.campsiteName.ifBlank { campsite.name })
                CancellationRow("Dates", "${formatReservationDate(booking.checkInDate)} – ${formatReservationDate(booking.checkOutDate)}")
                CancellationRow("Status", RESERVATION_CANCELLED)
            }
        }
        PinedrawButton(
            "Download CSV confirmation",
            onClick = {
                if (onDownloadCsv != null) onDownloadCsv(csv)
                else fileLauncher.launch("cancellation.csv")
            },
            modifier = Modifier.fillMaxWidth(),
            style = ButtonStyle.Ghost,
            icon = Icons.Rounded.Download
        )
        PinedrawButton("Back to my trips", onBackToTrips, Modifier.fillMaxWidth())
    }
}

@Composable
private fun CancellationRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = PinedrawColors.Background.copy(alpha = 0.65f))
        Text(value, Modifier.weight(1.4f), fontWeight = FontWeight.SemiBold)
    }
}

