package com.example.campsitereservationapp.lottery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.campsite.CampsitePhoto
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.model.formatReservationDate
import com.example.campsitereservationapp.service.LotteryListing
import com.example.campsitereservationapp.service.LotteryService
import com.example.campsitereservationapp.ui.components.ButtonStyle
import com.example.campsitereservationapp.ui.components.EmptyState
import com.example.campsitereservationapp.ui.components.ErrorState
import com.example.campsitereservationapp.ui.components.GlassCard
import com.example.campsitereservationapp.ui.components.InfoRow
import com.example.campsitereservationapp.ui.components.InlineMessage
import com.example.campsitereservationapp.ui.components.LoadingState
import com.example.campsitereservationapp.ui.components.PageHeader
import com.example.campsitereservationapp.ui.components.PinedrawBackground
import com.example.campsitereservationapp.ui.components.PinedrawDialog
import com.example.campsitereservationapp.ui.components.SegmentedTabs
import com.example.campsitereservationapp.ui.components.StatusChip
import com.example.campsitereservationapp.ui.components.Stepper
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawRadius
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing
import kotlinx.coroutines.ensureActive

/**
 * Shows the available lotteries and lets users view their campsites.
 *
 * @param user The current user.
 * @param onCampsiteClick Opens the selected campsite.
 */
@Composable
fun LotteriesScreen(user: User, onCampsiteClick: (String) -> Unit) {
    val service = remember { LotteryService() }
    var listings by remember { mutableStateOf<List<LotteryListing>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh) {
        loading = true
        error = ""
        try {
            listings = service.getOpenLotteryListings()
        } catch (_: Exception) {
            coroutineContext.ensureActive()
            error = "Could not load lotteries."
        } finally {
            loading = false
        }
    }

    var showEntered by rememberSaveable { mutableStateOf(false) }
    val entered = listings.filter { user.id in it.lottery.registered }
    val shown = if (showEntered) entered else listings

    PinedrawBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = PinedrawSpacing.Gutter, end = PinedrawSpacing.Gutter, top = PinedrawSpacing.Large, bottom = PinedrawSpacing.Screen),
            verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Large)
        ) {
            item {
                PageHeader(
                    "Lotteries",
                    subtitle = when {
                        loading || error.isNotEmpty() -> null
                        entered.isEmpty() -> "${listings.size} open ${if (listings.size == 1) "draw" else "draws"}"
                        else -> "${listings.size} open · ${entered.size} entered"
                    }
                )
            }
            item {
                SegmentedTabs(
                    options = listOf("Open · ${listings.size}", "Entered · ${entered.size}"),
                    selectedIndex = if (showEntered) 1 else 0,
                    onSelect = { showEntered = it == 1 }
                )
            }
            when {
                loading -> item { LoadingState() }
                error.isNotEmpty() -> item { ErrorState(error, onRetry = { refresh++ }) }
                shown.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Rounded.ConfirmationNumber,
                        title = if (showEntered) "No entries yet" else "No open lotteries",
                        message = if (showEntered) "Join a draw from any campsite with an open lottery."
                        else "Check back soon. New draws open all season."
                    )
                }
                else -> items(shown, key = { it.lottery.id }) { listing ->
                    LotteryCard(
                        listing = listing,
                        currentUserId = user.id,
                        onClick = { onCampsiteClick(listing.campsite.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LotteryCard(
    listing: LotteryListing,
    currentUserId: String,
    onClick: () -> Unit
) {
    val lottery = listing.lottery
    val entered = currentUserId in lottery.registered
    GlassCard(
        onClick = onClick,
        borderColor = if (entered) PinedrawColors.Lime.copy(alpha = 0.5f) else PinedrawColors.Border
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CampsitePhoto(listing.campsite.imageUrls.firstOrNull(), Modifier.size(76.dp), seed = listing.campsite.id, shape = PinedrawRadius.Medium)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    listing.campsites.joinToString(", ") { it.name.ifBlank { "Campsite" } },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listing.campsite.location.ifBlank { "Alberta" },
                    style = MaterialTheme.typography.bodySmall,
                    color = PinedrawColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusChip(
                    if (entered) "Entered" else "Open",
                    color = PinedrawColors.Lime,
                    icon = if (entered) Icons.Rounded.CheckCircle else null
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CalendarMonth, null, tint = PinedrawColors.TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "${formatReservationDate(lottery.stayStartDate)} – ${formatReservationDate(lottery.stayEndDate)}",
                style = MaterialTheme.typography.bodyMedium,
                color = PinedrawColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${lottery.registered.size} joined",
                style = MaterialTheme.typography.bodySmall,
                color = PinedrawColors.TextMuted
            )
        }
    }
}

/**
 * Shows a dialog for joining a lottery waiting list.
 *
 * @param lottery The lottery being joined.
 * @param campsite The campsite for the lottery.
 * @param onCancel Closes the dialog.
 * @param onConfirm Joins the waiting list with the selected group size.
 */
@Composable
fun JoinWaitingListDialog(
    lottery: Lottery,
    campsite: Campsite,
    onCancel: () -> Unit,
    onConfirm: (
        groupSize: Int,
        setSaving: (Boolean) -> Unit,
        setError: (String) -> Unit
    ) -> Unit
) {
    val maximum = campsite.maxOccupancy?.coerceAtLeast(1) ?: 1
    var groupSize by rememberSaveable(lottery.id) { mutableIntStateOf(1) }
    var saving by remember(lottery.id) { mutableStateOf(false) }
    var error by remember(lottery.id) { mutableStateOf("") }

    PinedrawDialog(
        title = "Join the waiting list?",
        message = "Enter the draw for ${campsite.name.ifBlank { "this campsite" }}.",
        icon = Icons.Rounded.ConfirmationNumber,
        confirmText = if (saving) "Joining..." else "Confirm",
        loading = saving,
        onDismiss = onCancel,
        onConfirm = { onConfirm(groupSize, { saving = it }, { error = it }) }
    ) {
        GlassCard(spacing = 10.dp, contentPadding = 14.dp) {
            InfoRow("Draw", formatReservationDate(lottery.registrationEndDate))
            InfoRow(
                "Booking window",
                "${formatReservationDate(lottery.stayStartDate)} – ${formatReservationDate(lottery.stayEndDate)}"
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Party size", style = MaterialTheme.typography.titleMedium, color = PinedrawColors.Text)
                Text("Max $maximum", style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextMuted)
            }
            Stepper(
                value = groupSize.toString(),
                onDecrement = { groupSize-- },
                onIncrement = { groupSize++ },
                canDecrement = !saving && groupSize > 1,
                canIncrement = !saving && groupSize < maximum,
                decrementLabel = "Decrease party size",
                incrementLabel = "Increase party size"
            )
        }
        InlineMessage(error)
    }
}

/**
 * Shows a dialog to confirm leaving a lottery waiting list.
 *
 * @param campsiteName The name of the campsite.
 * @param onCancel Closes the dialog.
 * @param onConfirm Removes the user from the waiting list.
 */
@Composable
fun LeaveWaitingListDialog(
    campsiteName: String,
    onCancel: () -> Unit,
    onConfirm: (setSaving: (Boolean) -> Unit, setError: (String) -> Unit) -> Unit
) {
    var saving by remember(campsiteName) { mutableStateOf(false) }
    var error by remember(campsiteName) { mutableStateOf("") }
    PinedrawDialog(
        title = "Leave the waiting list?",
        message = "Leave the $campsiteName draw? You can rejoin while registration is open.",
        icon = Icons.AutoMirrored.Rounded.Logout,
        iconTint = PinedrawColors.Coral,
        dismissText = "Stay in draw",
        confirmText = if (saving) "Leaving..." else "Leave",
        confirmStyle = ButtonStyle.Destructive,
        loading = saving,
        onDismiss = onCancel,
        onConfirm = { onConfirm({ saving = it }, { error = it }) }
    ) {
        InlineMessage(error)
    }
}
