package com.example.campsitereservationapp.campsite

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.campsitereservationapp.qr.CampsiteQrDialog
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.border
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.campsitereservationapp.ui.components.ButtonStyle
import com.example.campsitereservationapp.ui.components.CircleIconButton
import com.example.campsitereservationapp.ui.components.ErrorState
import com.example.campsitereservationapp.ui.components.GlassCard
import com.example.campsitereservationapp.ui.components.IconBadge
import com.example.campsitereservationapp.ui.components.InfoRow
import com.example.campsitereservationapp.ui.components.InlineMessage
import com.example.campsitereservationapp.ui.components.LoadingState
import com.example.campsitereservationapp.ui.components.PinedrawBackground
import com.example.campsitereservationapp.ui.components.PinedrawButton
import com.example.campsitereservationapp.ui.components.PinedrawDialog
import com.example.campsitereservationapp.ui.components.PinedrawScreen
import com.example.campsitereservationapp.ui.components.PinedrawTextField
import com.example.campsitereservationapp.ui.components.StatusChip
import com.example.campsitereservationapp.ui.components.amenityIcon
import com.example.campsitereservationapp.ui.components.formatPrice
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape
import com.example.campsitereservationapp.ui.components.SceneImage
import com.example.campsitereservationapp.ui.theme.PinedrawRadius
import com.example.campsitereservationapp.feedback.AdminRatingsSection
import com.example.campsitereservationapp.feedback.CampsiteReviewsSection
import com.example.campsitereservationapp.home.getRatingStars
import com.example.campsitereservationapp.home.getRatingText
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.formatReservationDate
import com.example.campsitereservationapp.lottery.JoinWaitingListDialog
import com.example.campsitereservationapp.lottery.LeaveWaitingListDialog
import com.example.campsitereservationapp.notification.OwnerNotificationComposer
import com.example.campsitereservationapp.service.CampsiteService
import com.example.campsitereservationapp.service.LotteryService
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Loads a campsite and displays its details, with extra options for its owner.
 * Campsite owners can also generate a QR code to share their campsite.
 * Hides other users' unpublished campsites, except from admins. Create Lottery is shown to the owner and
 * opens [LotteryCreation] for the loaded campsite; closing that form returns here.
 * The owner also sees the current lottery's waiting list ([LotteryEntrantsSection]) and the
 * campsite's active, past and cancelled reservations ([CampsiteReservationsSection]).
 * Everyone sees the campsite's reviews ([CampsiteReviewsSection]), and an admin can remove
 * reviews and ratings ([AdminRatingsSection]).
 * Loading failures display an error with a retry action.
 *
 * @param campsiteId Firestore document ID of the campsite to load.
 * @param entrantId Current entrant when opened from Discover or Lotteries; null in host or admin mode.
 * @param onBack Returns to the previous screen.
 * @param fromDiscover True when an entrant opened the campsite from Discover.
 * @param asAdmin True when an admin opened the campsite from the Admin Dashboard.
 */
// Loads and shows a campsite, with extra options when the signed-in user owns it.
@Composable
fun CampsiteDetailsScreen(
    campsiteId: String,
    onBack: () -> Unit,
    fromDiscover: Boolean = false,
    asAdmin: Boolean = false,
    entrantId: String? = null,
    onDeleted: (() -> Unit)? = null,
    onMessageGuests: (() -> Unit)? = null
) {
    val service = remember { CampsiteService() }
    val lotteryService = remember { LotteryService() }
    var openLottery by remember(campsiteId) { mutableStateOf<Lottery?>(null) }
    var lotteryStatus by remember(campsiteId) { mutableStateOf("Checking lottery...") }
    val ownerId = rememberCampsiteOwnerId()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showQrCode by rememberSaveable(campsiteId) { mutableStateOf(false) } // Keeps track of whether the QR code popup is open
    var campsite by remember(campsiteId, ownerId) { mutableStateOf<Campsite?>(null) }
    var lottery by remember(campsiteId, entrantId) { mutableStateOf<Lottery?>(null) }
    var loading by remember(campsiteId, ownerId) { mutableStateOf(true) }
    var error by remember(campsiteId, ownerId) { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var creatingLottery by rememberSaveable(campsiteId, ownerId) { mutableStateOf(false) }
    var editingLotteryId by rememberSaveable(campsiteId, ownerId) { mutableStateOf<String?>(null) }
    var ownerLotteries by remember(campsiteId, ownerId) { mutableStateOf<List<Lottery>>(emptyList()) }
    var currentOwnerLottery by remember(campsiteId, ownerId) { mutableStateOf<Lottery?>(null) }
    var editingCampsite by rememberSaveable(campsiteId, ownerId) { mutableStateOf(false) }
    var joining by rememberSaveable(campsiteId) { mutableStateOf(false) }
    var leaving by rememberSaveable(campsiteId) { mutableStateOf(false) }

    LaunchedEffect(campsiteId, ownerId, entrantId, refresh) {
        loading = true
        error = ""
        campsite = null
        lottery = null
        currentOwnerLottery = null
        try {
            check(ownerId != null) { "Not signed in" }
            val result = withTimeoutOrNull(20000) { service.getCampsite(campsiteId) }
            if (result == null) error = "This campsite is unavailable or could not be loaded. Please try again."
            else if (!canViewCampsite(result, ownerId, asAdmin))
                error = "This campsite is unavailable or could not be loaded. Please try again."
            else {
                campsite = result
                if (result.isOwnedBy(ownerId)) {
                    ownerLotteries = lotteryService.getLotteriesForCampsite(campsiteId)
                        .filter { it.ownerId == ownerId && !it.drawCompleted && it.pending.isEmpty() && it.booked.isEmpty() }
                    currentOwnerLottery = lotteryService.getCurrentLottery(campsiteId)
                        ?.takeIf { it.ownerId == ownerId }
                }
                if (entrantId != null && result.ownerId != entrantId) {
                    lottery = withTimeoutOrNull(20000) {
                        lotteryService.getOpenLotteryForCampsite(campsiteId)
                    }
                }
            }
        } catch (_: Exception) {
            coroutineContext.ensureActive() // Stop normally when leaving the screen.
            error = "Could not load this campsite. Please try again."
        } finally {
            loading = false
        }
    }
    // If selected campsite has an open lottery
    LaunchedEffect(campsiteId, refresh) {
        try {
            openLottery = lotteryService.getOpenLotteryForCampsite(campsiteId)
            // Update status
            if (openLottery != null) {
                lotteryStatus = "Registration open"
            } else {
                lotteryStatus = "No open lottery"
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            lotteryStatus = "Could not load lottery information"
        }
    }
    // Show the QR code for any published campsite
    if (showQrCode) {
        val currentCampsite = campsite
        if (currentCampsite != null && currentCampsite.published) {
            CampsiteQrDialog(
                campsiteId = currentCampsite.id,
                campsiteName = currentCampsite.name,
                onClose = {
                    showQrCode = false
                }
            )
        }
    }
    val ownedCampsite = campsite?.takeIf { ownerId != null && it.ownerId == ownerId }
    if (editingCampsite && ownedCampsite != null && onDeleted != null && !loading && error.isEmpty()) {
        EditOwnedCampsite(
            campsite = ownedCampsite,
            service = service,
            onCancel = { editingCampsite = false },
            onSaved = { updated ->
                campsite = updated
                editingCampsite = false
                Toast.makeText(context, "Campsite updated", Toast.LENGTH_SHORT).show()
            },
            onDeleted = {
                Toast.makeText(context, "Campsite deleted", Toast.LENGTH_SHORT).show()
                onDeleted()
            }
        )
        return
    }
    if (creatingLottery && !loading && error.isEmpty() && ownedCampsite != null) {
        LotteryCreation(campsite = ownedCampsite, onCancel = { creatingLottery = false; refresh++ })
        return
    }
    val editingLottery = ownerLotteries.find { it.id == editingLotteryId }
    if (editingLottery != null && !loading && error.isEmpty() && ownedCampsite != null) {
        LotteryCreation(campsite = ownedCampsite, existingLottery = editingLottery,
            onCancel = { editingLotteryId = null; refresh++ })
        return
    }

    val ownerControls = campsite?.let { canShowOwnerControls(it, ownerId, fromDiscover || asAdmin || entrantId != null) } == true
    val backLabel = if (entrantId != null && !fromDiscover && !asAdmin) "Lotteries" else getCampsiteBackLabel(fromDiscover, asAdmin)

    when {
        loading -> PinedrawScreen(title = "", onBack = onBack, backLabel = backLabel) { LoadingState() }
        error.isNotEmpty() -> PinedrawScreen(title = "", onBack = onBack, backLabel = backLabel) {
            ErrorState(error, onRetry = { refresh++ })
        }
        else -> campsite?.let { site ->
            val entrantLottery = lottery.takeIf { entrantId != null }
            PinedrawBackground {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Box(Modifier.fillMaxWidth().height(320.dp)) {
                            CampsitePhoto(site.imageUrls.firstOrNull(), Modifier.matchParentSize(), seed = site.id, shape = null)
                            Box(
                                Modifier.matchParentSize().background(
                                    Brush.verticalGradient(
                                        0f to PinedrawColors.Background.copy(alpha = 0.45f),
                                        0.3f to Color.Transparent,
                                        0.75f to Color.Transparent,
                                        1f to PinedrawColors.Background
                                    )
                                )
                            )
                            Row(
                                Modifier.fillMaxWidth().statusBarsPadding().padding(PinedrawSpacing.Large),
                                horizontalArrangement = Arrangement.spacedBy(PinedrawSpacing.Small)
                            ) {
                                CircleIconButton(
                                    Icons.AutoMirrored.Rounded.ArrowBack, backLabel, onBack,
                                    container = PinedrawColors.Background.copy(alpha = 0.6f)
                                )
                                Spacer(Modifier.weight(1f))
                                if (site.published) CircleIconButton(
                                    Icons.Rounded.QrCode2, "Campsite QR code", { showQrCode = true },
                                    container = PinedrawColors.Background.copy(alpha = 0.6f)
                                )
                            }
                        }
                        Column(
                            Modifier.padding(horizontal = PinedrawSpacing.Gutter),
                            verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Gutter)
                        ) {
                            CampsiteHeading(site, showStatus = site.isOwnedBy(ownerId) || asAdmin)
                            if (site.amenities.any { it.isNotBlank() }) AmenityRow(site.amenities)

                            if (entrantLottery != null) LotteryDetailsActions(entrantLottery, entrantId!!)

                            if (site.isOwnedBy(ownerId) && (onDeleted != null || onMessageGuests != null || ownerControls)) {
                                OwnerActions(
                                    onEdit = if (onDeleted != null) ({ editingCampsite = true }) else null,
                                    onQr = if (site.published) ({ showQrCode = true }) else null,
                                    onMessageGuests = onMessageGuests,
                                    onCreateLottery = if (ownerControls) ({ creatingLottery = true }) else null
                                )
                                if (ownerControls) ownerLotteries.forEach { current ->
                                    PinedrawButton(
                                        "Edit lottery: ${current.registrationStartDate} – ${current.registrationEndDate}",
                                        onClick = { editingLotteryId = current.id },
                                        style = ButtonStyle.Ghost,
                                        icon = Icons.Rounded.EditCalendar,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            if (site.description.isNotBlank()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("About this campsite", style = MaterialTheme.typography.titleLarge)
                                    Text(site.description, style = MaterialTheme.typography.bodyLarge, color = PinedrawColors.TextSecondary)
                                }
                            }

                            DetailCard("Details", Icons.Rounded.Info) {
                                DetailRow("Location", site.location.ifBlank { "Not provided" })
                                DetailRow("Coordinates", String.format(Locale.CANADA, "%.5f, %.5f", site.latitude, site.longitude))
                                DetailRow("Max occupancy", site.maxOccupancy?.let { if (it == 1) "1 guest" else "$it guests" } ?: "No maximum")
                                DetailRow("Price", String.format(Locale.CANADA, "$%.2f CAD / night", site.pricePerNight))
                                DetailRow("Rating", site.averageRating?.let { "${getRatingStars(it)}  ${getRatingText(it)}" } ?: "No ratings yet")
                                if (site.tags.any { it.isNotBlank() }) DetailLabels(site.tags, "")
                            }

                            DetailCard("Lottery", Icons.Rounded.ConfirmationNumber) {
                                StatusChip(
                                    lotteryStatus,
                                    color = if (openLottery != null) PinedrawColors.Lime else PinedrawColors.TextSecondary
                                )
                                openLottery?.let {
                                    DetailRow("Registration opens", formatReservationDate(it.registrationStartDate))
                                    DetailRow("Registration closes", formatReservationDate(it.registrationEndDate))
                                }
                            }

                            if (ownerControls) LotteryEntrantsSection(site.id)
                            if (ownerControls && currentOwnerLottery != null) {
                                OwnerNotificationComposer(
                                    campsiteId = site.id,
                                    campsiteName = site.name.ifBlank { "this campsite" },
                                    lottery = currentOwnerLottery!!
                                )
                            }
                            if (ownerControls) LotteryCollaborationSection(site, refresh, onChanged = { refresh++ })
                            if (ownerControls) CampsiteReservationsSection(site.id)

                            if (site.imageUrls.size > 1) {
                                Text("Photos", style = MaterialTheme.typography.titleLarge)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(PinedrawSpacing.Medium)) {
                                    itemsIndexed(site.imageUrls.drop(1)) { index, url ->
                                        CampsitePhoto(url, Modifier.width(240.dp).height(160.dp), seed = site.id + index)
                                    }
                                }
                            }

                            DetailCard("Availability", Icons.Rounded.CalendarMonth) {
                                val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                                val available = site.availableDays.distinct().sorted().mapNotNull { days.getOrNull(it - 1) }
                                Text(
                                    available.joinToString(" · ").ifBlank { "Every day" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = PinedrawColors.Text
                                )
                                DetailRow("From", site.availableFrom.ifBlank { "Any date" })
                                DetailRow("Until", site.availableUntil.ifBlank { "Any date" })
                            }

                            DetailCard("Rules for entrants", Icons.Rounded.Gavel) {
                                Text(
                                    site.rules.ifBlank { "No additional rules provided." },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = PinedrawColors.TextSecondary
                                )
                                DetailRow("Cancellation policy", site.cancellationPolicy.ifBlank { "Not specified" })
                                if (site.requireRulesAcceptance) {
                                    StatusChip("Entrants must accept the rules.", color = PinedrawColors.Amber, icon = Icons.Rounded.Gavel)
                                }
                                if (ownerControls) OwnerPolicyEditor(
                                    campsiteId = site.id,
                                    rules = site.rules,
                                    onSaved = { campsite = site.copy(rules = it) }
                                )
                            }

                            CampsiteReviewsSection(site.id, asAdmin)
                            if (asAdmin) AdminRatingsSection(site.id, onCampsiteChanged = { updated ->
                                campsite = campsite?.copy(
                                    averageRating = updated.averageRating, ratingCount = updated.ratingCount,
                                    ratingTotal = updated.ratingTotal
                                )
                            })
                            Spacer(Modifier.height(PinedrawSpacing.Large))
                        }
                    }
                    if (entrantLottery != null) {
                        val entered = entrantId in entrantLottery.registered
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(PinedrawColors.Surface)
                                .navigationBarsPadding()
                                .padding(horizontal = PinedrawSpacing.Gutter, vertical = PinedrawSpacing.Medium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(formatPrice(site.pricePerNight), style = MaterialTheme.typography.headlineMedium, color = PinedrawColors.Lime, maxLines = 1)
                                    Text(" / night", style = MaterialTheme.typography.bodyMedium, color = PinedrawColors.TextSecondary)
                                }
                                Text("Free to enter", style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextMuted)
                            }
                            if (entered) PinedrawButton(
                                "Leave waiting list", { leaving = true },
                                style = ButtonStyle.SoftDestructive, icon = Icons.AutoMirrored.Rounded.Logout
                            ) else PinedrawButton(
                                "Join lottery", { joining = true },
                                icon = Icons.Rounded.ConfirmationNumber
                            )
                        }
                    } else {
                        Spacer(Modifier.navigationBarsPadding())
                    }
                }
            }
        }
    }

    if (joining && campsite != null && lottery != null) JoinWaitingListDialog(
        lottery = lottery!!,
        campsite = campsite!!,
        onCancel = { joining = false },
        onConfirm = { groupSize, setSaving, setDialogError ->
            scope.launch {
                setSaving(true)
                setDialogError("")
                try {
                    lottery = lotteryService.joinWaitingList(lottery!!.id, groupSize)
                    joining = false
                    Toast.makeText(context, "You're on the waiting list", Toast.LENGTH_SHORT).show()
                } catch (exception: Exception) {
                    coroutineContext.ensureActive()
                    setDialogError(exception.lotteryMessage("Could not join the waiting list."))
                } finally {
                    setSaving(false)
                }
            }
        }
    )

    if (leaving && campsite != null && lottery != null) LeaveWaitingListDialog(
        campsiteName = campsite!!.name.ifBlank { "campsite" },
        onCancel = { leaving = false },
        onConfirm = { setSaving, setDialogError ->
            scope.launch {
                setSaving(true)
                setDialogError("")
                try {
                    lottery = lotteryService.leaveWaitingList(lottery!!.id)
                    leaving = false
                    Toast.makeText(context, "Left the waiting list", Toast.LENGTH_SHORT).show()
                } catch (exception: Exception) {
                    coroutineContext.ensureActive()
                    setDialogError(exception.lotteryMessage("Could not leave the waiting list."))
                } finally {
                    setSaving(false)
                }
            }
        }
    )
}

// Saves or deletes the current listing and keeps the draft open if the request fails.
@Composable
private fun EditOwnedCampsite(
    campsite: Campsite,
    service: CampsiteService,
    onCancel: () -> Unit,
    onSaved: (Campsite) -> Unit,
    onDeleted: () -> Unit
) {
    val original = remember(campsite) { CampsiteForm.fromCampsite(campsite) }
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun saveChange(failure: String, action: suspend () -> Unit) {
        if (saving) return
        saving = true
        error = ""
        scope.launch {
            try {
                action()
            } catch (exception: Exception) {
                coroutineContext.ensureActive()
                error = exception.lotteryMessage(failure)
            } finally {
                saving = false
            }
        }
    }

    CreateCampsiteScreen(
        saving = saving,
        saveError = error,
        onCancel = onCancel,
        onCreate = { form ->
            saveChange("Could not save campsite changes. Please try again.") {
                onSaved(service.updateCampsite(campsite.id, original, form))
            }
        },
        original = original,
        onDelete = {
            saveChange("Could not delete the campsite. Please try again.") {
                service.deleteOwnedCampsite(campsite.id)
                onDeleted()
            }
        },
        requiresRulesAcceptance = campsite.requireRulesAcceptance
    )
}

@Composable
private fun CampsiteHeading(site: Campsite, showStatus: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                site.name.ifBlank { "Unnamed campsite" },
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f)
            )
            site.averageRating?.let { rating ->
                Row(
                    Modifier.clip(PinedrawRadius.Pill).background(PinedrawColors.Glass).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Star, null, tint = PinedrawColors.Amber, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(String.format(Locale.CANADA, "%.1f", rating), style = MaterialTheme.typography.titleSmall)
                    if (site.ratingCount > 0) Text(
                        "  ${site.ratingCount}", style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextMuted
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Place, null, tint = PinedrawColors.TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(site.location.ifBlank { "Location not provided" }, style = MaterialTheme.typography.bodyMedium, color = PinedrawColors.TextSecondary)
        }
        if (showStatus) {
            StatusChip(
                if (site.published) "Published" else "Draft",
                color = if (site.published) PinedrawColors.Lime else PinedrawColors.TextMuted
            )
        }
    }
}

@Composable
private fun AmenityRow(amenities: List<String>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(PinedrawSpacing.Medium)) {
        itemsIndexed(amenities.filter { it.isNotBlank() }) { _, amenity ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(68.dp)) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(PinedrawColors.Glass)
                        .border(1.dp, PinedrawColors.Border, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(amenityIcon(amenity), null, tint = PinedrawColors.Text, modifier = Modifier.size(24.dp)) }
                Spacer(Modifier.height(6.dp))
                Text(amenity, style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun OwnerActions(
    onEdit: (() -> Unit)?,
    onQr: (() -> Unit)?,
    onMessageGuests: (() -> Unit)?,
    onCreateLottery: (() -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Small)) {
        if (onCreateLottery != null) PinedrawButton(
            "Create Lottery", onCreateLottery, Modifier.fillMaxWidth(), icon = Icons.Rounded.ConfirmationNumber
        )
        val secondary = listOfNotNull(
            onEdit?.let { Triple("Edit campsite", Icons.Rounded.Edit, it) },
            onQr?.let { Triple("QR code", Icons.Rounded.QrCode2, it) },
            onMessageGuests?.let { Triple("Message guests", Icons.AutoMirrored.Rounded.Chat, it) }
        )
        secondary.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(PinedrawSpacing.Small)) {
                row.forEach { (label, icon, action) ->
                    PinedrawButton(label, action, Modifier.weight(1f), style = ButtonStyle.Ghost, icon = icon, compact = true)
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LotteryDetailsActions(lottery: Lottery, entrantId: String) {
    val entered = entrantId in lottery.registered
    GlassCard(borderColor = if (entered) PinedrawColors.Lime.copy(alpha = 0.5f) else PinedrawColors.Border) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.ConfirmationNumber, filled = true)
            Spacer(Modifier.width(PinedrawSpacing.Medium))
            Column(Modifier.weight(1f)) {
                Text("Lottery", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Stays ${formatReservationDate(lottery.stayStartDate)} – ${formatReservationDate(lottery.stayEndDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = PinedrawColors.TextSecondary
                )
            }
            StatusChip(if (entered) "Entered" else "Open")
        }
        DetailRow("Registration closes", formatReservationDate(lottery.registrationEndDate))
        if (entered) {
            Text(
                lottery.registeredGroupSizes[entrantId]?.let { "You're in the draw · party of $it" } ?: "You're in the draw",
                style = MaterialTheme.typography.bodyMedium,
                color = PinedrawColors.Lime
            )
        }
    }
}

private fun Throwable.lotteryMessage(fallback: String): String {
    var current: Throwable = this
    while (current.cause != null && current.cause !== current) current = current.cause!!
    return current.message?.takeIf { it.isNotBlank() } ?: fallback
}

/**
 * Lets the owner edit the campsite policy entrants must accept (US 02.11.01).
 *
 * Save asks "Are you sure?" before saving, and Cancel discards the draft.
 *
 * @param campsiteId Firestore document ID of the campsite.
 * @param rules The policy currently saved.
 * @param onSaved Called with the trimmed policy after it is saved.
 * @param savePolicy Saves the policy; replaced in intent tests so no Firebase call is made.
 */
@Composable
internal fun OwnerPolicyEditor(
    campsiteId: String,
    rules: String,
    onSaved: (String) -> Unit,
    savePolicy: suspend (String) -> Unit = { CampsiteService().updateCampsitePolicy(campsiteId, it) }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable(campsiteId) { mutableStateOf(false) }
    var draft by rememberSaveable(campsiteId) { mutableStateOf(rules) }
    var confirming by rememberSaveable(campsiteId) { mutableStateOf(false) }
    var saving by remember(campsiteId) { mutableStateOf(false) }
    var error by remember(campsiteId) { mutableStateOf("") }

    if (!editing) {
        PinedrawButton(
            "Edit policy",
            onClick = {
                draft = rules
                error = ""
                editing = true
            },
            style = ButtonStyle.Ghost,
            icon = Icons.Rounded.Edit,
            compact = true
        )
        return
    }

    PinedrawTextField(
        value = draft, onValueChange = { draft = it }, enabled = !saving,
        label = "Rules for entrants", singleLine = false, minLines = 4
    )
    InlineMessage(error)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PinedrawSpacing.Small)) {
        PinedrawButton("Cancel", { editing = false }, Modifier.weight(1f), style = ButtonStyle.Ghost, enabled = !saving, compact = true)
        PinedrawButton(if (saving) "Saving..." else "Save", { confirming = true }, Modifier.weight(1f), enabled = !saving, compact = true)
    }

    if (confirming) PinedrawDialog(
        title = "Are you sure?",
        message = "Entrants will see this policy for your campsite.",
        icon = Icons.Rounded.Gavel,
        confirmText = "Proceed",
        onDismiss = { confirming = false },
        onConfirm = {
                confirming = false
                saving = true
                error = ""
                scope.launch {
                    try {
                        val saved = withTimeoutOrNull(20000) {
                            savePolicy(draft)
                            true
                        }
                        if (saved == null) error = "Could not save the policy. Please try again."
                        else {
                            editing = false
                            onSaved(draft.trim())
                            Toast.makeText(context, "Policy updated", Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: Exception) {
                        coroutineContext.ensureActive() // Stop normally when leaving the screen.
                        error = "Could not save the policy. Please try again."
                    } finally {
                        saving = false
                    }
                }
            }
    )
}

// Groups campsite details inside a titled glass card.
@Composable
private fun DetailCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = PinedrawColors.Lime, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(PinedrawSpacing.Small))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        content()
    }
}

// Shows one campsite label and its value.
@Composable
private fun DetailRow(label: String, value: String) = InfoRow(label, value)

// Shows amenities or tags as chips, or a message when none are listed.
@Composable
private fun DetailLabels(values: List<String>, emptyMessage: String) {
    val labels = values.filter { it.isNotBlank() }
    if (labels.isEmpty()) {
        if (emptyMessage.isNotBlank()) Text(emptyMessage, color = PinedrawColors.TextMuted)
    } else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { StatusChip(it, color = PinedrawColors.Sky, showDot = false) }
    }
}

/**
 * Loads a campsite photo. When there is no photo, or it can't be loaded, a landscape
 * illustration chosen from [seed] is shown instead so cards never look empty.
 */
@Composable
internal fun CampsitePhoto(
    url: String?,
    modifier: Modifier = Modifier,
    seed: String = url.orEmpty(),
    shape: Shape? = PinedrawRadius.Large
) {
    var loading by remember(url) { mutableStateOf(!url.isNullOrBlank()) }
    val bitmap by produceState<Bitmap?>(null, url) {
        value = null
        loading = !url.isNullOrBlank()
        if (!url.isNullOrBlank()) {
            value = withContext(Dispatchers.IO) {
                var connection: HttpURLConnection? = null
                try {
                    val address = URL(url)
                    require(address.protocol == "https")
                    connection = address.openConnection() as HttpURLConnection
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    val bytes = connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var count = input.read(buffer)
                        while (count != -1) {
                            require(output.size() + count <= 20 * 1024 * 1024)
                            output.write(buffer, 0, count)
                            count = input.read(buffer)
                        }
                        output.toByteArray()
                    }
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    options.inSampleSize = 1
                    while (options.outWidth / options.inSampleSize > 1200 || options.outHeight / options.inSampleSize > 1200)
                        options.inSampleSize *= 2 // Keep full-size photos from exhausting memory.
                    options.inJustDecodeBounds = false
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                } catch (_: Exception) {
                    null
                } finally {
                    connection?.disconnect()
                }
            }
        }
        loading = false
    }
    Box(
        modifier.then(if (shape != null) Modifier.clip(shape) else Modifier).background(PinedrawColors.Surface),
        contentAlignment = Alignment.Center
    ) {
        when {
            loading -> CircularProgressIndicator(Modifier.size(24.dp), color = PinedrawColors.Lime, strokeWidth = 2.dp)
            bitmap != null -> Image(bitmap!!.asImageBitmap(), "Campsite photo", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            else -> SceneImage(seed, Modifier.matchParentSize())
        }
    }
}
// Testing purposes functions
/**
 * Checks if a user can view a campsite.
 *
 * Published campsites can be viewed by anyone, while unpublished
 * campsites can only be viewed by their owner or an admin.
 *
 * @param campsite The campsite being checked.
 * @param userId ID of the current user.
 * @param asAdmin True if the user is viewing as an admin.
 * @return True if the user can view the campsite.
 */
fun canViewCampsite(campsite: Campsite, userId: String?, asAdmin: Boolean = false): Boolean {
    return campsite.published || campsite.isOwnedBy(userId) || asAdmin
}
/**
 * Checks if the owner controls should be displayed.
 *
 * Owner controls are only shown when the owner opens the
 * campsite outside of Discover.
 *
 * @param campsite The campsite being viewed.
 * @param userId ID of the current user.
 * @param fromDiscover True if the user opened the campsite from Discover.
 * @return True if the owner controls should be shown.
 */
fun canShowOwnerControls(
    campsite: Campsite,
    userId: String?,
    fromDiscover: Boolean
): Boolean {
    return !fromDiscover && campsite.isOwnedBy(userId)
}
/**
 * Gets the correct back button label based on where the user came from.
 *
 * @param fromDiscover True if the campsite was opened from Discover.
 * @param asAdmin True if the campsite was opened from the Admin Dashboard.
 * @return "Discover", "All Campsites" or "My Campsites".
 */
fun getCampsiteBackLabel(fromDiscover: Boolean, asAdmin: Boolean = false): String {
    if (fromDiscover) {
        return "Discover"
    }
    if (asAdmin) {
        return "All Campsites"
    }

    return "My Campsites"
}
