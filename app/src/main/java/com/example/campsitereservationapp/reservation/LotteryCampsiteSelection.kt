package com.example.campsitereservationapp.reservation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.model.LotteryReservationSelection
import com.example.campsitereservationapp.model.ReservationCampsiteChoice
import com.example.campsitereservationapp.model.formatReservationDate
import com.example.campsitereservationapp.model.offeredCampsiteIds
import com.example.campsitereservationapp.model.reservationDateToday
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Presents US 01.04.02's campsite list before dates. Single-campsite lotteries retain
 * their direct calendar flow. Changing campsites recreates the date/rules state so
 * values selected for one campsite cannot accidentally be submitted for another.
 */
@Composable
internal fun ColumnScope.LotteryReservationFlow(
    selection: LotteryReservationSelection,
    onReserve: suspend (String, String, String, Boolean) -> String?,
    onDecline: suspend () -> String?,
    onBack: () -> Unit,
    onReserved: () -> Unit,
    onRefresh: () -> Unit,
    today: String = reservationDateToday(),
    groupSize: Int = 1
) {
    val multiple = selection.lottery.offeredCampsiteIds().distinct().size > 1
    var chosenId by rememberSaveable(selection.lottery.id) {
        mutableStateOf(if (!multiple) selection.choices.singleOrNull()?.takeIf { it.selectable }?.campsiteId else null)
    }
    val chosen = selection.choices.find { it.campsiteId == chosenId && it.selectable }
    BackHandler(enabled = multiple && chosen != null) { chosenId = null }
    if (chosen?.campsite == null) {
        CampsiteSelection(selection.choices, onChoose = { chosenId = it }, onDecline, onBack, onRefresh)
    } else {
        key(chosen.campsiteId) {
            ReserveFlow(selection.lottery, chosen.campsite,
                onReserve = { checkIn, checkOut, accepted -> onReserve(chosen.campsiteId, checkIn, checkOut, accepted) },
                onDecline = onDecline,
                onBack = { if (multiple) chosenId = null else onBack() },
                onReserved = onReserved, blockedNights = chosen.blockedNights,
                firstAvailableNight = requireNotNull(chosen.firstAvailableNight),
                backLabel = if (multiple) "Change campsite" else "Back", onDeclined = onBack,
                today = today, groupSize = groupSize)
        }
    }
}

/** Shows every offering and a clear reason when a campsite cannot be selected. */
@Composable
private fun ColumnScope.CampsiteSelection(
    choices: List<ReservationCampsiteChoice>,
    onChoose: (String) -> Unit,
    onDecline: suspend () -> String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val selected = choices.find { it.campsiteId == selectedId && it.selectable }
    BackHandler(enabled = busy) { /* Keep the screen while declining is in progress. */ }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack, enabled = !busy) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Text("Back")
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onRefresh, enabled = !busy) { Text("Refresh") }
    }
    Column(Modifier.padding(horizontal = PinedrawSpacing.Screen), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Choose a campsite", style = MaterialTheme.typography.headlineLarge)
        Text("Select one campsite from your lottery, then choose your dates.", color = PinedrawColors.TextSecondary)
        if (choices.none { it.selectable }) Text(
            "No campsites have available dates for your party. You can refresh or decline your spot.",
            color = PinedrawColors.Coral)
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(PinedrawSpacing.Screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(choices, key = { it.campsiteId }) { choice ->
            CampsiteChoiceCard(choice, selectedId == choice.campsiteId, !busy) { selectedId = choice.campsiteId }
        }
    }
    Column(Modifier.padding(PinedrawSpacing.Screen), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { selected?.let { onChoose(it.campsiteId) } }, enabled = selected != null && !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.large) {
            Text("Choose dates", fontWeight = FontWeight.Bold)
        }
        DeclineSpotAction(onDecline, onBack, enabled = !busy, onBusyChanged = { busy = it })
    }
}

/** A disabled card stays visible with its reason; only eligible cards and radio buttons accept input. */
@Composable
private fun CampsiteChoiceCard(choice: ReservationCampsiteChoice, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val site = choice.campsite
    Card(onClick = onSelect, enabled = enabled && choice.selectable,
        modifier = Modifier.fillMaxWidth().testTag("campsite-choice-${choice.campsiteId}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = PinedrawColors.Glass, contentColor = PinedrawColors.Text,
            disabledContainerColor = PinedrawColors.Glass, disabledContentColor = PinedrawColors.TextMuted)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(site?.name?.ifBlank { "Unnamed campsite" } ?: "Unavailable campsite",
                    style = MaterialTheme.typography.titleLarge)
                if (site != null) {
                    Text(site.location, color = PinedrawColors.TextSecondary)
                    Text("Up to ${site.maxOccupancy ?: 0} guests · " + String.format(Locale.CANADA, "$%.2f / night", site.pricePerNight),
                        style = MaterialTheme.typography.bodySmall)
                }
                Text(choice.unavailableReason ?: "Available from ${formatReservationDate(choice.firstAvailableNight.orEmpty())}",
                    color = if (choice.selectable) PinedrawColors.Lime else PinedrawColors.Coral,
                    style = MaterialTheme.typography.bodySmall)
            }
            RadioButton(selected = selected && choice.selectable, onClick = onSelect, enabled = enabled && choice.selectable)
        }
    }
}

/** Shared decline confirmation, usable even when every campsite is unavailable. */
@Composable
internal fun DeclineSpotAction(
    onDecline: suspend () -> String?, onDeclined: () -> Unit,
    enabled: Boolean = true, onBusyChanged: (Boolean) -> Unit = {}
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text("None of these work?", color = PinedrawColors.TextSecondary)
        TextButton(onClick = { confirming = true }, enabled = enabled && !busy) {
            Text(if (busy) "Declining..." else "Decline spot", color = PinedrawColors.Coral)
        }
    }
    if (error.isNotEmpty()) Text(error, color = PinedrawColors.Coral)
    if (confirming) AlertDialog(onDismissRequest = { confirming = false },
        title = { Text("Decline this spot?") },
        text = { Text("You will give up this reservation opportunity. This cannot be undone.") },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("Keep my spot") } },
        confirmButton = { TextButton(onClick = {
            confirming = false
            busy = true
            onBusyChanged(true)
            error = ""
            scope.launch {
                try {
                    val result = withTimeoutOrNull(20_000) { onDecline() ?: "" }
                    when {
                        result == null -> error = "Could not decline your spot. Please try again."
                        result.isNotEmpty() -> error = result
                        else -> {
                            Toast.makeText(context, "Spot declined", Toast.LENGTH_LONG).show()
                            onDeclined()
                        }
                    }
                } catch (_: Exception) {
                    coroutineContext.ensureActive()
                    error = "Could not decline your spot. Please try again."
                } finally {
                    busy = false
                    onBusyChanged(false)
                }
            }
        }) { Text("Decline", color = PinedrawColors.Coral) } })
}
