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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF1E3A26),
                    0.45f to PinedrawColors.Background,
                    1f to PinedrawColors.Background
                )
            )
            .statusBarsPadding()
            .padding(horizontal = PinedrawSpacing.Screen)
    ) {
        Spacer(Modifier.height(PinedrawSpacing.Large))
        Text("Lotteries", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(PinedrawSpacing.Large))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PinedrawColors.Lime)
            }
            error.isNotEmpty() -> LoadError(error, onRetry = { refresh++ })
            listings.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No open lotteries", color = PinedrawColors.TextSecondary)
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Large)) {
                items(listings, key = { it.lottery.id }) { listing ->
                    LotteryCard(
                        listing = listing,
                        currentUserId = user.id,
                        onClick = { onCampsiteClick(listing.campsite.id) }
                    )
                }
                item { Spacer(Modifier.height(PinedrawSpacing.Section)) }
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
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().border(
            1.dp,
            if (entered) PinedrawColors.Lime.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.08f),
            MaterialTheme.shapes.large
        ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = PinedrawColors.Glass,
            contentColor = PinedrawColors.Text
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        listing.campsite.name.ifBlank { "Campsite lottery" },
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(listing.campsite.location, color = PinedrawColors.TextSecondary)
                }
                Text(
                    if (entered) "Entered" else "Open",
                    color = PinedrawColors.Lime,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "${formatReservationDate(lottery.stayStartDate)} – ${formatReservationDate(lottery.stayEndDate)}",
                color = PinedrawColors.TextSecondary
            )
        }
    }
}

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
            DialogIcon(PinedrawColors.Lime) {
                Icon(Icons.Default.Star, contentDescription = null)
            }
        },
        title = { Text("Join the waiting list?", textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Enter the draw for ${campsite.name.ifBlank { "this campsite" }}.",
                    color = PinedrawColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                DialogRow("Draw", formatReservationDate(lottery.registrationEndDate))
                DialogRow(
                    "Booking window",
                    "${formatReservationDate(lottery.stayStartDate)} – ${formatReservationDate(lottery.stayEndDate)}"
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Party size", fontWeight = FontWeight.SemiBold)
                        Text("Max $maximum", color = PinedrawColors.TextMuted)
                    }
                    Row(
                        Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.06f)).padding(4.dp),
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
                if (error.isNotBlank()) Text(error, color = PinedrawColors.Coral)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel, enabled = !saving, shape = CircleShape) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(groupSize, { saving = it }, { error = it }) },
                enabled = !saving,
                shape = CircleShape
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (saving) "Joining..." else "Confirm", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun LeaveWaitingListDialog(
    campsiteName: String,
    onCancel: () -> Unit,
    onConfirm: (setSaving: (Boolean) -> Unit, setError: (String) -> Unit) -> Unit
) {
    var saving by remember(campsiteName) { mutableStateOf(false) }
    var error by remember(campsiteName) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!saving) onCancel() },
        containerColor = PinedrawColors.Surface,
        shape = MaterialTheme.shapes.extraLarge,
        icon = {
            DialogIcon(PinedrawColors.Coral) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
            }
        },
        title = { Text("Leave the waiting list?", textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Leave the $campsiteName draw? You can rejoin while registration is open.",
                    color = PinedrawColors.TextSecondary,
                    textAlign = TextAlign.Center
                )
                if (error.isNotBlank()) Text(error, color = PinedrawColors.Coral)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel, enabled = !saving, shape = CircleShape) {
                Text("Stay in draw")
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm({ saving = it }, { error = it }) },
                enabled = !saving,
                shape = CircleShape
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (saving) "Leaving..." else "Leave", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun DialogIcon(color: Color, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.size(64.dp),
        color = color.copy(alpha = 0.16f),
        contentColor = color,
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
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
            Text(message, color = PinedrawColors.Coral)
            TextButton(onClick = onRetry) { Text("Try again") }
        }
    }
}
