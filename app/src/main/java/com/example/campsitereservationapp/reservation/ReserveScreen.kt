package com.example.campsitereservationapp.reservation

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.campsite.rememberCampsiteOwnerId
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.formatReservationDate
import com.example.campsitereservationapp.model.isNightOpen
import com.example.campsitereservationapp.model.reservationDateFromUtcMillis
import com.example.campsitereservationapp.model.reservationDateToUtcMillis
import com.example.campsitereservationapp.model.reservationNights
import com.example.campsitereservationapp.service.CampsiteService
import com.example.campsitereservationapp.service.ReservationService
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

// Loads the lottery and campsite a winner is reserving. Notifications can open this with a lottery ID.
@Composable
fun ReserveScreen(lotteryId: String, onBack: () -> Unit, onReserved: () -> Unit) {
    val reservationService = remember { ReservationService() }
    val campsiteService = remember { CampsiteService() }
    val entrantId = rememberCampsiteOwnerId()
    var lottery by remember(lotteryId, entrantId) { mutableStateOf<Lottery?>(null) }
    var campsite by remember(lotteryId, entrantId) { mutableStateOf<Campsite?>(null) }
    var loading by remember(lotteryId, entrantId) { mutableStateOf(true) }
    var error by remember(lotteryId, entrantId) { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(lotteryId, entrantId, refresh) {
        loading = true
        error = ""
        lottery = null
        campsite = null
        try {
            check(entrantId != null) { "Not signed in" }
            val result = withTimeoutOrNull(20000) {
                val found = reservationService.getLottery(lotteryId)
                found to found?.let { campsiteService.getCampsite(it.campsiteId) }
            }
            if (result?.first == null || result.second == null) error = "This lottery is unavailable or could not be loaded. Please try again."
            else if (entrantId !in result.first!!.pending) error = "You do not have a spot to reserve in this lottery."
            else {
                lottery = result.first
                campsite = result.second
            }
        } catch (_: Exception) {
            coroutineContext.ensureActive() // Stop normally when leaving the screen.
            error = "Could not load this lottery. Please try again."
        } finally {
            loading = false
        }
    }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(0f to Color(0xFF1E3A26), 0.45f to PinedrawColors.Background, 1f to PinedrawColors.Background))
            .statusBarsPadding().navigationBarsPadding()
    ) {
        val loadedLottery = lottery
        val loadedCampsite = campsite
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PinedrawColors.Lime)
            }
            loadedLottery == null || loadedCampsite == null -> Column {
                ReserveBackButton(onBack)
                Column(Modifier.padding(24.dp)) {
                    Text(error, color = PinedrawColors.Coral)
                    TextButton(onClick = { refresh++ }) { Text("Try again") }
                }
            }
            else -> ReserveFlow(loadedLottery, loadedCampsite, reservationService, onBack, onReserved)
        }
    }
}

// Takes the winner from picking dates to agreeing to the rules and confirming.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.ReserveFlow(
    lottery: Lottery,
    campsite: Campsite,
    service: ReservationService,
    onBack: () -> Unit,
    onReserved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirming by rememberSaveable { mutableStateOf(false) }
    var rulesAccepted by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val selectableDates = remember(lottery, campsite) {
        object : SelectableDates {
            // A taken night can still be a check-out day when the night before it is open.
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                isNightOpen(reservationDateFromUtcMillis(utcTimeMillis), lottery, campsite) ||
                    isNightOpen(reservationDateFromUtcMillis(utcTimeMillis - DAY_MILLIS), lottery, campsite)
        }
    }
    val picker = rememberDateRangePickerState(
        initialDisplayedMonthMillis = reservationDateToUtcMillis(lottery.stayStartDate),
        selectableDates = selectableDates
    )
    val checkIn = picker.selectedStartDateMillis?.let(::reservationDateFromUtcMillis)
    val checkOut = picker.selectedEndDateMillis?.let(::reservationDateFromUtcMillis)
    val nights = if (checkIn != null && checkOut != null) reservationNights(checkIn, checkOut) else emptyList()
    val datesOpen = nights.isNotEmpty() && nights.all { isNightOpen(it, lottery, campsite) }

    if (!confirming) {
        ReserveBackButton(onBack)
        Text(campsite.name.ifBlank { "Unnamed campsite" }, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Pick your dates. Dates that are greyed out are not available.", Modifier.padding(horizontal = 20.dp), color = PinedrawColors.TextSecondary)
        DateRangePicker(
            state = picker, modifier = Modifier.weight(1f), title = null, showModeToggle = false,
            colors = DatePickerDefaults.colors(containerColor = Color.Transparent)
        )
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (nights.isNotEmpty() && !datesOpen) Text("Some of these dates are not available. Please choose others.", color = PinedrawColors.Coral)
            Button(
                onClick = {
                    error = ""
                    confirming = true
                },
                enabled = datesOpen, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large
            ) { Text("Continue", fontWeight = FontWeight.Bold) }
        }
        return
    }

    TextButton(onClick = { confirming = false }, enabled = !saving, modifier = Modifier.padding(horizontal = 12.dp)) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Change dates")
    }
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Review & confirm", style = MaterialTheme.typography.headlineLarge)
        ReserveCard {
            Text(campsite.name.ifBlank { "Unnamed campsite" }, style = MaterialTheme.typography.titleLarge)
            ReserveRow("Check-in", formatReservationDate(checkIn.orEmpty()))
            ReserveRow("Check-out", formatReservationDate(checkOut.orEmpty()))
            ReserveRow("Nights", nights.size.toString())
            ReserveRow("Total", String.format(Locale.CANADA, "$%.2f CAD", campsite.pricePerNight * nights.size))
        }
        ReserveCard {
            Text("Campsite rules & policies", style = MaterialTheme.typography.titleLarge)
            Text(campsite.rules.ifBlank { "No additional rules provided." }, color = PinedrawColors.TextSecondary)
            ReserveRow("Cancellation policy", campsite.cancellationPolicy.ifBlank { "Not specified" })
            if (campsite.requireRulesAcceptance) Row(
                Modifier.fillMaxWidth().clickable(enabled = !saving) { rulesAccepted = !rulesAccepted },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = rulesAccepted, onCheckedChange = null, enabled = !saving)
                Spacer(Modifier.width(12.dp))
                Text("I've read and agree to the campsite rules and cancellation policy.")
            }
        }
        if (error.isNotEmpty()) Text(error, color = PinedrawColors.Coral)
        Button(
            onClick = {
                saving = true
                error = ""
                scope.launch {
                    try {
                        val result = withTimeoutOrNull(20000) {
                            service.reserve(lottery.id, checkIn.orEmpty(), checkOut.orEmpty(), rulesAccepted) ?: ""
                        }
                        when {
                            result == null -> error = "Could not confirm your reservation. Please try again."
                            result.isNotEmpty() -> error = result
                            else -> {
                                Toast.makeText(context, "Reservation confirmed", Toast.LENGTH_LONG).show()
                                onReserved()
                            }
                        }
                    } catch (_: Exception) {
                        coroutineContext.ensureActive() // Stop normally when leaving the screen.
                        error = "Could not confirm your reservation. Please try again."
                    } finally {
                        saving = false
                    }
                }
            },
            enabled = !saving && (rulesAccepted || !campsite.requireRulesAcceptance),
            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large
        ) { Text(if (saving) "Reserving..." else "Reserve", fontWeight = FontWeight.Bold) }
    }
}

// Returns to the screen the winner came from.
@Composable
private fun ReserveBackButton(onBack: () -> Unit) {
    TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 12.dp)) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Back")
    }
}

// Groups reservation review details inside a rounded card.
@Composable
private fun ReserveCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = PinedrawColors.Glass, contentColor = PinedrawColors.Text)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

// Shows one review label and its value.
@Composable
private fun ReserveRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = PinedrawColors.TextSecondary)
        Text(value, Modifier.weight(1.3f), fontWeight = FontWeight.SemiBold)
    }
}
