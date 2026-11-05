package com.example.campsitereservationapp.lottery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.model.formatReservationDate
import com.example.campsitereservationapp.service.LotteryListing
import com.example.campsitereservationapp.service.LotteryService
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Lists open campsite lotteries and lets the current entrant join one.
 *
 * The confirmation and success states follow the C01/C02 wiki mockups. The list is
 * refreshed in memory after a successful transaction so the same lottery immediately
 * shows Entered and cannot be submitted twice from the UI.
 */
@Composable
fun LotteriesScreen(user: User) {
    val service = remember { LotteryService() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var listings by remember { mutableStateOf<List<LotteryListing>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<LotteryListing?>(null) }

    LaunchedEffect(refresh) {
        loading = true
        loadError = ""
        try {
            listings = service.getOpenLotteryListings()
        } catch (_: Exception) {
            coroutineContext.ensureActive()
            loadError = "Could not load open lotteries. Please try again."
        } finally {
            loading = false
        }
    }

    Box(
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
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = PinedrawSpacing.Screen)) {
            Spacer(Modifier.height(PinedrawSpacing.Large))
            Text("Open lotteries", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(PinedrawSpacing.Small))
            Text(
                "Join a fair draw for your next campsite stay.",
                color = PinedrawColors.TextSecondary
            )
            Spacer(Modifier.height(PinedrawSpacing.Large))

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PinedrawColors.Lime)
                }
                loadError.isNotEmpty() -> LoadError(loadError, onRetry = { refresh++ })
                listings.isEmpty() -> EmptyLotteries()
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Large)
                ) {
                    items(listings, key = { it.lottery.id }) { listing ->
                        LotteryCard(
                            listing = listing,
                            currentUserId = user.id,
                            onJoin = { selected = listing }
                        )
                    }
                    item { Spacer(Modifier.height(PinedrawSpacing.Section)) }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(PinedrawSpacing.Large))
    }

    selected?.let { listing ->
        JoinWaitingListDialog(
            lottery = listing.lottery,
            campsite = listing.campsite,
            onCancel = { selected = null },
            onConfirm = { groupSize, setSaving, setError ->
                scope.launch {
                    setSaving(true)
                    setError("")
                    try {
                        val joined = service.joinWaitingList(listing.lottery.id, groupSize)
                        listings = listings.map {
                            if (it.lottery.id == joined.id) it.copy(lottery = joined) else it
                        }
                        selected = null
                        snackbar.showSnackbar("You're on the waiting list")
                    } catch (exception: Exception) {
                        coroutineContext.ensureActive()
                        setError(exception.deepestMessage())
                    } finally {
                        setSaving(false)
                    }
                }
            }
        )
    }
}

@Composable
private fun LotteryCard(
    listing: LotteryListing,
    currentUserId: String,
    onJoin: () -> Unit
) {
    val lottery = listing.lottery
    val campsite = listing.campsite
    val entered = currentUserId in lottery.registered || currentUserId in lottery.pending ||
        lottery.booked.any { it.entrantId == currentUserId }
    val owner = currentUserId == lottery.ownerId
    Card(
        modifier = Modifier.fillMaxWidth().border(
            width = 1.dp,
            color = if (entered) PinedrawColors.Lime.copy(alpha = 0.65f)
                else Color.White.copy(alpha = 0.08f),
            shape = MaterialTheme.shapes.large
        ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = PinedrawColors.Glass,
            contentColor = PinedrawColors.Text
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = PinedrawColors.Lime,
                    contentColor = PinedrawColors.Ink
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Star, contentDescription = null)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        campsite.name.ifBlank { "Campsite lottery" },
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(campsite.location, color = PinedrawColors.TextSecondary)
                }
                Surface(
                    shape = CircleShape,
                    color = PinedrawColors.Lime.copy(alpha = 0.16f),
                    contentColor = PinedrawColors.Lime
                ) {
                    Text(
                        if (entered) "Entered" else "Open",
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Text(
                "Stay ${formatReservationDate(lottery.stayStartDate)} – ${formatReservationDate(lottery.stayEndDate)}",
                color = PinedrawColors.TextSecondary
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LotteryStat("Registration closes", formatReservationDate(lottery.registrationEndDate))
                LotteryStat("Entrants", lottery.registered.distinct().size.toString())
                LotteryStat("Winners", lottery.numberOfWinners.toString())
            }
            if (entered) {
                val party = lottery.registeredGroupSizes[currentUserId]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = PinedrawColors.Lime,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        party?.let { "You're in the draw · party of $it" } ?: "You're in the draw",
                        color = PinedrawColors.Lime,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Button(
                onClick = onJoin,
                enabled = !entered && !owner,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PinedrawColors.Lime,
                    contentColor = PinedrawColors.Ink,
                    disabledContainerColor = PinedrawColors.Lime.copy(alpha = 0.12f),
                    disabledContentColor = PinedrawColors.Lime
                )
            ) {
                Text(
                    when {
                        entered -> "Entered"
                        owner -> "Your lottery"
                        else -> "Join waiting list"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LotteryStat(label: String, value: String) {
    Column {
        Text(label, color = PinedrawColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

/**
 * Confirms a waiting-list join and collects a party size without saving on cancel.
 *
 * [onConfirm] receives state callbacks so Firestore work can be owned by the screen's
 * coroutine while this dialog displays progress and a specific failure reason.
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

    AlertDialog(
        onDismissRequest = { if (!saving) onCancel() },
        containerColor = PinedrawColors.Surface,
        shape = MaterialTheme.shapes.extraLarge,
        icon = {
            Surface(
                modifier = Modifier.size(64.dp),
                color = PinedrawColors.Lime.copy(alpha = 0.16f),
                contentColor = PinedrawColors.Lime,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Star, null, Modifier.size(30.dp))
                }
            }
        },
        title = {
            Text(
                "Join the waiting list?",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Enter the draw for ${campsite.name.ifBlank { "this campsite" }}. " +
                        "It's free — ${lottery.numberOfWinners} ${if (lottery.numberOfWinners == 1) "winner is" else "winners are"} " +
                        "drawn at random when registration closes.",
                    color = PinedrawColors.TextSecondary,
                    textAlign = TextAlign.Center
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = Color.White.copy(alpha = 0.05f)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        DialogRow("Registration closes", formatReservationDate(lottery.registrationEndDate))
                        DialogRow(
                            "Booking window",
                            "${formatReservationDate(lottery.bookingStartDate)} – ${formatReservationDate(lottery.bookingEndDate)}"
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Party size", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Max $maximum for this site",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PinedrawColors.TextMuted
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { groupSize-- },
                                    enabled = !saving && groupSize > 1,
                                    modifier = Modifier.size(36.dp)
                                ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                                Text(
                                    groupSize.toString(),
                                    modifier = Modifier.width(36.dp),
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { groupSize++ },
                                    enabled = !saving && groupSize < maximum,
                                    modifier = Modifier.size(36.dp)
                                ) { Icon(Icons.Default.Add, "Increase party size") }
                            }
                        }
                    }
                }
                if (error.isNotBlank()) {
                    Text(
                        error,
                        color = PinedrawColors.Coral,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                enabled = !saving,
                shape = CircleShape
            ) { Text("Cancel") }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(groupSize, { saving = it }, { error = it })
                },
                enabled = !saving,
                shape = CircleShape
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = PinedrawColors.Ink
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (saving) "Joining..." else "Confirm", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun DialogRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = PinedrawColors.TextSecondary)
        Text(value, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

@Composable
private fun LoadError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = PinedrawColors.Coral, textAlign = TextAlign.Center)
            Spacer(Modifier.height(PinedrawSpacing.Small))
            TextButton(onClick = onRetry) { Text("Try again") }
        }
    }
}

@Composable
private fun EmptyLotteries() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No open lotteries", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(PinedrawSpacing.Small))
            Text(
                "Check back when a campsite opens registration.",
                color = PinedrawColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun Throwable.deepestMessage(): String {
    var current: Throwable = this
    while (current.cause != null && current.cause !== current) current = current.cause!!
    return current.message?.takeIf { it.isNotBlank() } ?: "Could not join the waiting list. Please try again."
}
