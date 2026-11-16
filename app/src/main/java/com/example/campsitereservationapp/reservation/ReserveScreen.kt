package com.example.campsitereservationapp.reservation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.example.campsitereservationapp.model.reservationDateToday
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/*
 * Lets a lottery winner pick available dates and reserve the campsite (US 01.04.01), agree
 * to the campsite's rules first when the owner requires it (US 02.11.01), or decline their
 * spot (US 01.04.03).
 *
 * [ReserveViewModel] loads eligibility and availability. [LotteryReservationFlow] lets
 * multi-campsite winners choose an available campsite before [ReserveFlow] presents its
 * dates and rules. The views receive actions as callbacks so they can be tested without Firebase.
 *
 * Outstanding issues: declining does not yet redraw the spot for the next entrant
 * (US 02.05.01). Notifications and My Reservations can open this screen.
 */

/**
 * Loads the lottery and campsite a winner is reserving. Notifications can open this with a lottery ID.
 *
 * @param lotteryId Firestore document ID of the lottery the entrant won.
 * @param onBack Returns to the previous screen.
 * @param onReserved Called after the reservation is confirmed.
 */
@Composable
fun ReserveScreen(lotteryId: String, onBack: () -> Unit, onReserved: () -> Unit) {
    val entrantId = rememberCampsiteOwnerId()
    val controller: ReserveViewModel = viewModel(key = "reserve-$lotteryId-$entrantId")
    LaunchedEffect(lotteryId, entrantId) { controller.load(lotteryId) }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(0f to Color(0xFF1E3A26), 0.45f to PinedrawColors.Background, 1f to PinedrawColors.Background))
            .statusBarsPadding().navigationBarsPadding()
    ) {
        val selection = controller.selection
        when {
            controller.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PinedrawColors.Lime)
            }
            selection == null -> Column {
                ReserveBackButton(onBack)
                Column(Modifier.padding(24.dp)) {
                    Text(controller.error, color = PinedrawColors.Coral)
                    TextButton(onClick = { controller.load(lotteryId) }) { Text("Try again") }
                }
            }
            else -> LotteryReservationFlow(selection, controller::reserve, controller::decline,
                onBack, onReserved, onRefresh = { controller.load(lotteryId) })
        }
    }
}

/**
 * Takes the winner from picking dates to agreeing to the rules and confirming.
 *
 * Unavailable dates are greyed out. Continue is only enabled once every night is open, and
 * Reserve only once the rules are accepted when the campsite requires it.
 *
 * @param lottery The lottery the entrant won.
 * @param campsite The campsite being reserved.
 * @param onReserve Books the check-in date, check-out date and rules acceptance; returns an error message or null.
 * @param onDecline Gives up the spot; returns an error message or null.
 * @param onBack Returns to the previous screen, including after declining.
 * @param onReserved Called after the reservation is confirmed.
 * @param initialCheckIn Check-in date to preselect as yyyy-MM-dd, or null.
 * @param initialCheckOut Check-out date to preselect as yyyy-MM-dd, or null.
 * @param blockedNights Occupied nights across related lotteries; null uses the lottery's own bookings.
 * @param firstAvailableNight First open night used to choose the calendar's initial month.
 * @param backLabel Text for returning to campsite selection, or the parent screen for a single campsite.
 * @param onDeclined Returns to the parent screen after releasing the entire lottery opportunity.
 * @param today Current booking-calendar date; supplied explicitly by tests.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColumnScope.ReserveFlow(
    lottery: Lottery,
    campsite: Campsite,
    onReserve: suspend (checkIn: String, checkOut: String, rulesAccepted: Boolean) -> String?,
    onDecline: suspend () -> String?,
    onBack: () -> Unit,
    onReserved: () -> Unit,
    initialCheckIn: String? = null,
    initialCheckOut: String? = null,
    blockedNights: Set<String>? = null,
    firstAvailableNight: String = lottery.stayStartDate,
    backLabel: String = "Back",
    onDeclined: () -> Unit = onBack,
    today: String = reservationDateToday()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirming by rememberSaveable { mutableStateOf(false) }
    var rulesAccepted by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    fun nightOpen(date: String) = date >= today && isNightOpen(date, lottery, campsite, blockedNights)
    BackHandler(enabled = confirming || saving) { if (!saving) confirming = false }
    val selectableDates = remember(lottery, campsite, blockedNights, today) {
        object : SelectableDates {
            // A taken night can still be a check-out day when the night before it is open.
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                nightOpen(reservationDateFromUtcMillis(utcTimeMillis)) ||
                    nightOpen(reservationDateFromUtcMillis(utcTimeMillis - DAY_MILLIS))
        }
    }
    val picker = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialCheckIn?.let(::reservationDateToUtcMillis),
        initialSelectedEndDateMillis = initialCheckOut?.let(::reservationDateToUtcMillis),
        initialDisplayedMonthMillis = reservationDateToUtcMillis(firstAvailableNight),
        selectableDates = selectableDates
    )
    val checkIn = picker.selectedStartDateMillis?.let(::reservationDateFromUtcMillis)
    val checkOut = picker.selectedEndDateMillis?.let(::reservationDateFromUtcMillis)
    val nights = if (checkIn != null && checkOut != null) reservationNights(checkIn, checkOut) else emptyList()
    val datesOpen = nights.isNotEmpty() && nights.all { nightOpen(it) }

    if (!confirming) {
        ReserveBackButton(onBack, backLabel)
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
                enabled = datesOpen && !saving, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large
            ) { Text("Continue", fontWeight = FontWeight.Bold) }
            if (error.isNotEmpty()) Text(error, color = PinedrawColors.Coral)
            DeclineSpotAction(onDecline, onDeclined, enabled = !saving, onBusyChanged = { saving = it })
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
                            onReserve(checkIn.orEmpty(), checkOut.orEmpty(), rulesAccepted) ?: ""
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
private fun ReserveBackButton(onBack: () -> Unit, label: String = "Back") {
    TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 12.dp)) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
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
