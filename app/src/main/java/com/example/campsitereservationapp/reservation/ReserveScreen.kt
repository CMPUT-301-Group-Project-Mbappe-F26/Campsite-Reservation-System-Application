package com.example.campsitereservationapp.reservation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
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
import com.example.campsitereservationapp.ui.components.PinedrawTextButton
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
import com.example.campsitereservationapp.campsite.rememberCampsiteOwnerId
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.formatReservationDate
import com.example.campsitereservationapp.model.isNightOpen
import com.example.campsitereservationapp.model.reservationDateFromUtcMillis
import com.example.campsitereservationapp.model.reservationDateToUtcMillis
import com.example.campsitereservationapp.model.reservationNights
import com.example.campsitereservationapp.model.reservationDateToday
import com.example.campsitereservationapp.notification.reservationCsv
import com.example.campsitereservationapp.notification.writeReservationCsv
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

    PinedrawBackground {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        val selection = controller.selection
        when {
            controller.loading -> LoadingState(Modifier.fillMaxSize())
            selection == null -> Column {
                ReserveBackButton(onBack)
                ErrorState(controller.error, onRetry = { controller.load(lotteryId) })
            }
            else -> LotteryReservationFlow(selection, controller::reserve, controller::decline,
                onBack, onReserved, onRefresh = { controller.load(lotteryId) },
                groupSize = selection.lottery.registeredGroupSizes[entrantId] ?: 1)
        }
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
    today: String = reservationDateToday(),
    groupSize: Int = 1,
    onDownloadCsv: ((String) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var confirming by rememberSaveable { mutableStateOf(false) }
    var reservationConfirmed by rememberSaveable { mutableStateOf(false) }
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

    if (reservationConfirmed) {
        ReservationConfirmation(
            campsite = campsite,
            checkIn = checkIn.orEmpty(),
            checkOut = checkOut.orEmpty(),
            groupSize = groupSize,
            total = campsite.pricePerNight * nights.size,
            onDownloadCsv = onDownloadCsv,
            onDone = onReserved
        )
        return
    }

    if (!confirming) {
        ReserveBackButton(onBack, backLabel)
        Column(Modifier.padding(horizontal = PinedrawSpacing.Gutter), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StepLabel("Step 1 of 2 · Dates")
            Text(campsite.name.ifBlank { "Unnamed campsite" }, style = MaterialTheme.typography.headlineLarge)
            Text("Pick your dates. Dates that are greyed out are not available.", style = MaterialTheme.typography.bodyMedium, color = PinedrawColors.TextSecondary)
        }
        DateRangePicker(
            state = picker, modifier = Modifier.weight(1f), title = null, headline = null, showModeToggle = false,
            colors = pinedrawDatePickerColors()
        )
        Column(
            Modifier.background(PinedrawColors.Surface).padding(horizontal = PinedrawSpacing.Gutter, vertical = PinedrawSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Small)
        ) {
            if (nights.isNotEmpty() && !datesOpen) InlineMessage("Some of these dates are not available. Please choose others.")
            if (nights.isNotEmpty() && datesOpen) Text(
                "${formatReservationDate(checkIn.orEmpty())} → ${formatReservationDate(checkOut.orEmpty())} · ${nights.size} ${if (nights.size == 1) "night" else "nights"}",
                style = MaterialTheme.typography.bodyMedium, color = PinedrawColors.TextSecondary
            )
            PinedrawButton(
                "Continue",
                onClick = {
                    error = ""
                    confirming = true
                },
                enabled = datesOpen && !saving, modifier = Modifier.fillMaxWidth()
            )
            InlineMessage(error)
            DeclineSpotAction(onDecline, onDeclined, enabled = !saving, onBusyChanged = { saving = it })
        }
        return
    }

    ReserveBackButton({ confirming = false }, "Change dates", enabled = !saving)
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PinedrawSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Gutter)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StepLabel("Step 2 of 2 · Confirm")
            Text("Review & confirm", style = MaterialTheme.typography.headlineLarge)
        }
        ReserveCard {
            Text(campsite.name.ifBlank { "Unnamed campsite" }, style = MaterialTheme.typography.titleLarge)
            ReserveRow("Check-in", formatReservationDate(checkIn.orEmpty()))
            ReserveRow("Check-out", formatReservationDate(checkOut.orEmpty()))
            ReserveRow("Nights", nights.size.toString())
            ReserveRow("Total", String.format(Locale.CANADA, "$%.2f CAD", campsite.pricePerNight * nights.size))
        }
        ReserveCard {
            Text("Campsite rules & policies", style = MaterialTheme.typography.titleMedium)
            Text(campsite.rules.ifBlank { "No additional rules provided." }, style = MaterialTheme.typography.bodyLarge, color = PinedrawColors.TextSecondary)
            ReserveRow("Cancellation policy", campsite.cancellationPolicy.ifBlank { "Not specified" })
            if (campsite.requireRulesAcceptance) Row(
                Modifier.fillMaxWidth().clickable(enabled = !saving) { rulesAccepted = !rulesAccepted },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = rulesAccepted, onCheckedChange = null, enabled = !saving,
                    colors = CheckboxDefaults.colors(checkedColor = PinedrawColors.Lime, checkmarkColor = PinedrawColors.Ink, uncheckedColor = PinedrawColors.TextSecondary)
                )
                Spacer(Modifier.width(12.dp))
                Text("I've read and agree to the campsite rules and cancellation policy.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        InlineMessage(error)
        PinedrawButton(
            if (saving) "Reserving..." else "Reserve",
            icon = Icons.Rounded.Check,
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
                            else -> reservationConfirmed = true
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
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(PinedrawSpacing.Large))
    }
}

/** Confirmation shown after the reservation has been saved (US 01.05.01 and US 01.05.02). */
@Composable
internal fun ColumnScope.ReservationConfirmation(
    campsite: Campsite,
    checkIn: String,
    checkOut: String,
    groupSize: Int,
    total: Double,
    onDownloadCsv: ((String) -> Unit)? = null,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val csv = remember(campsite, checkIn, checkOut, groupSize, total) {
        reservationCsv(campsite, checkIn, checkOut, groupSize, total)
    }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null && !writeReservationCsv(csv) { context.contentResolver.openOutputStream(uri) }) {
            Toast.makeText(context, "Could not download CSV", Toast.LENGTH_LONG).show()
        }
    }
    BackHandler(onBack = onDone)
    Row(Modifier.fillMaxWidth().padding(PinedrawSpacing.Large), horizontalArrangement = Arrangement.End) {
        CircleIconButton(Icons.Rounded.Close, "Close", onDone)
    }
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PinedrawSpacing.Screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(Modifier.size(96.dp).background(PinedrawColors.Lime.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(70.dp).background(PinedrawColors.PrimaryGradient, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = PinedrawColors.Ink, modifier = Modifier.size(40.dp))
            }
        }
        Text("You’re going camping!", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Text("Your reservation is confirmed.", style = MaterialTheme.typography.bodyLarge, color = PinedrawColors.TextSecondary)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = PinedrawRadius.Large,
            colors = CardDefaults.cardColors(containerColor = PinedrawColors.Text, contentColor = PinedrawColors.Ink)
        ) {
            Column(Modifier.padding(PinedrawSpacing.Gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(campsite.name.ifBlank { "Unnamed campsite" }, style = MaterialTheme.typography.titleLarge)
                if (campsite.location.isNotBlank()) Text(campsite.location, style = MaterialTheme.typography.bodyMedium,
                    color = PinedrawColors.Ink.copy(alpha = 0.65f))
                HorizontalDivider(color = PinedrawColors.Ink.copy(alpha = 0.12f))
                ReceiptRow("Guests", if (groupSize == 1) "1 guest" else "$groupSize guests")
                ReceiptRow("Check-in", formatReservationDate(checkIn))
                ReceiptRow("Check-out", formatReservationDate(checkOut))
                ReceiptRow("Total paid", String.format(Locale.CANADA, "$%.2f CAD", total))
            }
        }
        PinedrawButton(
            "Download CSV",
            onClick = {
                if (onDownloadCsv != null) onDownloadCsv(csv)
                else fileLauncher.launch("reservation.csv")
            },
            modifier = Modifier.fillMaxWidth(),
            style = ButtonStyle.Ghost,
            icon = Icons.Rounded.Download
        )
        PinedrawButton("View my trip", onDone, Modifier.fillMaxWidth())
        Spacer(Modifier.height(PinedrawSpacing.Medium))
    }
}

// Returns to the screen the winner came from.
@Composable
internal fun ReserveBackButton(onBack: () -> Unit, label: String = "Back", enabled: Boolean = true) {
    PinedrawTextButton(
        label, onBack,
        modifier = Modifier.padding(horizontal = PinedrawSpacing.Small, vertical = PinedrawSpacing.XSmall),
        color = PinedrawColors.Text,
        icon = Icons.AutoMirrored.Rounded.ArrowBack,
        enabled = enabled
    )
}

@Composable
internal fun StepLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = PinedrawColors.Lime)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun pinedrawDatePickerColors() = DatePickerDefaults.colors(
    containerColor = Color.Transparent,
    weekdayContentColor = PinedrawColors.TextMuted,
    subheadContentColor = PinedrawColors.TextSecondary,
    dayContentColor = PinedrawColors.Text,
    disabledDayContentColor = PinedrawColors.TextMuted.copy(alpha = 0.45f),
    selectedDayContainerColor = PinedrawColors.Lime,
    selectedDayContentColor = PinedrawColors.Ink,
    todayContentColor = PinedrawColors.Lime,
    todayDateBorderColor = PinedrawColors.Lime,
    dayInSelectionRangeContainerColor = PinedrawColors.Lime.copy(alpha = 0.2f),
    dayInSelectionRangeContentColor = PinedrawColors.Text
)

// Groups reservation review details inside a glass card.
@Composable
private fun ReserveCard(content: @Composable ColumnScope.() -> Unit) = GlassCard(content = content)

// Shows one review label and its value.
@Composable
private fun ReserveRow(label: String, value: String) = InfoRow(label, value)

// Label and value on the light receipt card.
@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = PinedrawColors.Ink.copy(alpha = 0.6f))
        Text(value, Modifier.weight(1.3f), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End)
    }
}
