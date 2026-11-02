package com.example.campsitereservationapp.campsite

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.service.lotteryCapacityError
import com.example.campsitereservationapp.service.validateLottery
import com.example.campsitereservationapp.service.lotteryStayAvailabilityError
import com.example.campsitereservationapp.service.isLotteryStayDateSelectable
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.launch

/**
 * Displays the owner's lottery form using the app's Pinedraw theme.
 *
 * Collects winner capacity and registration, booking, and stay dates. Field values
 * survive configuration changes through rememberSaveable. Shared validation gates
 * Start Lottery; [LotteryCreationViewModel] handles saving and reports failures
 * inline. Success displays a confirmation and closes the form.
 *
 * Back and Cancel discard the local draft without saving. Editing and closing are
 * blocked while a submission is in progress. The caller must supply an owned campsite;
 * the service independently verifies ownership, capacity, and current campsite availability when saving.
 *
 * @param campsite Loaded campsite whose ID, owner, and maximum occupancy define the draft.
 * @param onCancel Closes the form on cancellation or successful creation.
 */
@Composable
fun LotteryCreation(campsite: Campsite, onCancel: () -> Unit) {
    var winners by rememberSaveable(campsite.id) { mutableStateOf("") }
    var useMaxCapacity by rememberSaveable(campsite.id) { mutableStateOf(false) }
    val maximum = campsite.maxOccupancy
    val winnerCount = if (useMaxCapacity) maximum ?: 0 else winners.toIntOrNull() ?: 0
    val capacityError = if (useMaxCapacity || winners.isNotBlank() || maximum == null || maximum <= 0)
        lotteryCapacityError(winnerCount, maximum, useMaxCapacity) else null
    var registrationStart by rememberSaveable(campsite.id) { mutableStateOf("") }
    var registrationEnd by rememberSaveable(campsite.id) { mutableStateOf("") }
    var bookingStart by rememberSaveable(campsite.id) { mutableStateOf("") }
    var bookingEnd by rememberSaveable(campsite.id) { mutableStateOf("") }
    var stayStart by rememberSaveable(campsite.id) { mutableStateOf("") }
    var stayEnd by rememberSaveable(campsite.id) { mutableStateOf("") }
    val draft = Lottery(
        id = "draft", ownerId = campsite.ownerId, campsiteId = campsite.id,
        numberOfWinners = winnerCount, useMaxCapacity = useMaxCapacity,
        registrationStartDate = registrationStart, registrationEndDate = registrationEnd,
        bookingStartDate = bookingStart, bookingEndDate = bookingEnd,
        stayStartDate = stayStart, stayEndDate = stayEnd
    )
    val validationError = runCatching { validateLottery(draft) }.exceptionOrNull()?.message
    val availabilityError = if (stayStart.isNotBlank() && stayEnd.isNotBlank())
        lotteryStayAvailabilityError(stayStart, stayEnd, campsite) else null
    val allDatesSelected = listOf(registrationStart, registrationEnd, bookingStart, bookingEnd, stayStart, stayEnd)
        .all { it.isNotBlank() }
    val submission: LotteryCreationViewModel = viewModel(key = "lottery-${campsite.ownerId}-${campsite.id}")
    LaunchedEffect(draft) { submission.clearError() }
    val context = LocalContext.current
    val close: () -> Unit = {
        if (!submission.saving) {
            submission.reset()
            onCancel()
        }
    }
    LaunchedEffect(submission.created) {
        if (submission.created) {
            Toast.makeText(context, "Lottery created successfully", Toast.LENGTH_LONG).show()
            submission.reset()
            onCancel()
        }
    }

    BackHandler { close() }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(
                0f to Color(0xFF1E3A26),
                0.45f to PinedrawColors.Background,
                1f to PinedrawColors.Background
            ))
            .statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(horizontal = PinedrawSpacing.Screen)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = close, enabled = !submission.saving) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Back")
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = close, enabled = !submission.saving) { Text("Cancel") }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Large)
        ) {
            Text("Create Lottery", style = MaterialTheme.typography.headlineLarge)
            Text(campsite.name.ifBlank { "Unnamed campsite" },
                style = MaterialTheme.typography.titleLarge, color = PinedrawColors.Lime)
            Text("Set when entrants can join, when winners can book, and the stay dates on offer.",
                color = PinedrawColors.TextSecondary)

            LotterySection("Winners") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Capacity", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Row(
                        Modifier.heightIn(min = 48.dp).toggleable(
                            value = useMaxCapacity,
                            enabled = !submission.saving && maximum != null && maximum > 0,
                            role = Role.Checkbox,
                            onValueChange = { useMaxCapacity = it }
                        ), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = useMaxCapacity, onCheckedChange = null,
                            enabled = !submission.saving && maximum != null && maximum > 0)
                        Text("Max", style = MaterialTheme.typography.labelMedium)
                    }
                    CapacityHelp()
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = if (useMaxCapacity) maximum?.toString().orEmpty() else winners,
                        enabled = !submission.saving,
                        readOnly = useMaxCapacity,
                        onValueChange = { value -> if (value.all { it in '0'..'9' }) winners = value },
                        label = { Text("Number of winners") },
                        isError = capacityError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = PinedrawColors.Glass,
                            unfocusedContainerColor = PinedrawColors.Glass,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.08f)
                        )
                    )
                }
                Text(
                    capacityError ?: "Campsite maximum: $maximum",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (capacityError != null) PinedrawColors.Coral else PinedrawColors.TextSecondary
                )
            }
            Text(
                "Registration and booking start at 00:00 and end at 23:59 (11:59 PM), Alberta time. Stay end is the checkout date.",
                style = MaterialTheme.typography.bodySmall,
                color = PinedrawColors.TextSecondary
            )
            LotterySection("Registration period") {
                Text("When entrants can join the waiting list.", color = PinedrawColors.TextSecondary)
                LotteryDateField("Registration start", registrationStart, !submission.saving) { registrationStart = it }
                LotteryDateField("Registration end", registrationEnd, !submission.saving) { registrationEnd = it }
            }
            LotterySection("Booking period") {
                Text("When lottery winners can make a reservation.", color = PinedrawColors.TextSecondary)
                LotteryDateField("Booking start", bookingStart, !submission.saving) { bookingStart = it }
                LotteryDateField("Booking end", bookingEnd, !submission.saving) { bookingEnd = it }
            }
            LotterySection("Stay period") {
                Text("Offer stay dates within this campsite's availability. The checkout date is not a booked night.",
                    color = PinedrawColors.TextSecondary)
                Text("Available nights: ${campsite.availableFrom.ifBlank { "Any start date" }} to ${campsite.availableUntil.ifBlank { "No end date" }}",
                    style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary)
                if (campsite.availableDays.isNotEmpty()) {
                    val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    Text(campsite.availableDays.distinct().sorted().mapNotNull { weekdays.getOrNull(it - 1) }.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary)
                }
                LotteryDateField("First offered night", stayStart, !submission.saving, campsite) { stayStart = it }
                LotteryDateField("Stay end (checkout)", stayEnd, !submission.saving, campsite, checkout = true) { stayEnd = it }
                if (availabilityError != null) {
                    Text(availabilityError, color = PinedrawColors.Coral, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
            if (submission.error.isNotEmpty()) {
                Text(submission.error, color = PinedrawColors.Coral,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (validationError != null && capacityError == null) {
                Text(validationError,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (allDatesSelected && winnerCount > 0) PinedrawColors.Coral else PinedrawColors.TextSecondary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            Button(
                onClick = { submission.submit(draft) },
                enabled = !submission.saving && capacityError == null && validationError == null && availabilityError == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PinedrawColors.Lime, contentColor = PinedrawColors.Ink
                )
            ) {
                if (submission.saving) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = PinedrawColors.Lime)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (submission.saving) "Creating Lottery…" else "Start Lottery", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Shows capacity-rule help in a themed question-mark tooltip.
 *
 * Supports pointer hover, long press, and explicit tap so the explanation is
 * available on touch devices as well as devices with a mouse.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CapacityHelp() {
    val state = rememberTooltipState()
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        state = state,
        tooltip = {
            PlainTooltip(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text("Enter a positive whole number strictly less than the campsite maximum. Select Max to use exactly the maximum.")
            }
        }
    ) {
        IconButton(
            onClick = { scope.launch { state.show() } },
            modifier = Modifier.semantics { contentDescription = "Capacity rules" }
        ) {
            Text("?", color = PinedrawColors.Lime, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Groups related lottery fields in a themed card with consistent spacing.
 *
 * @param title Visible section heading.
 * @param content Fields and supporting text rendered below the heading.
 */
@Composable
private fun LotterySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = PinedrawColors.Glass, contentColor = PinedrawColors.Text)
    ) {
        Column(Modifier.padding(PinedrawSpacing.Large), verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Medium)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

/**
 * Displays a labelled date button and its Material date-picker dialog.
 *
 * Uses UTC to convert picker milliseconds into a date-only string, avoiding
 * local time-zone shifts. UTC is a picker conversion detail, not a stored lottery
 * time zone. Cancelling the dialog leaves the value unchanged. Period ordering
 * is validated by the form and service, not by this picker.
 *
 * @param label Accessible field description and dialog title.
 * @param value Selected `yyyy-MM-dd` date, or an empty string before selection.
 * @param enabled Whether the user may open the date picker.
 * @param campsite Optional campsite whose season and weekdays constrain stay endpoints.
 * Registration and booking window pickers leave this null so advance booking remains possible.
 * @param checkout Whether this is a checkout endpoint rather than the first offered night.
 * @param onChange Receives the confirmed date as a `yyyy-MM-dd` string.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LotteryDateField(
    label: String, value: String, enabled: Boolean,
    campsite: Campsite? = null, checkout: Boolean = false,
    onChange: (String) -> Unit
) {
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = PinedrawColors.TextSecondary)
        OutlinedButton(onClick = { picking = true }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                contentDescription = "$label: ${value.ifBlank { "Select date" }}"
            },
            shape = MaterialTheme.shapes.medium) {
            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(value.ifBlank { "Select date" })
        }
    }
    if (picking) {
        val formatter = remember {
            SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
        }
        val selectable = remember(campsite, checkout) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = campsite == null ||
                    isLotteryStayDateSelectable(utcTimeMillis, campsite, checkout)
            }
        }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value.takeIf { it.isNotBlank() }?.let { formatter.parse(it)?.time },
            selectableDates = selectable
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            colors = DatePickerDefaults.colors(containerColor = PinedrawColors.Surface),
            confirmButton = {
                TextButton(enabled = state.selectedDateMillis?.let { selectable.isSelectableDate(it) } == true, onClick = {
                    state.selectedDateMillis?.let { onChange(formatter.format(Date(it))) }
                    picking = false
                }) { Text("Select") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state,
                title = { Text(label, Modifier.padding(start = PinedrawSpacing.Screen, top = PinedrawSpacing.Large)) },
                colors = DatePickerDefaults.colors(containerColor = PinedrawColors.Surface))
        }
    }
}
