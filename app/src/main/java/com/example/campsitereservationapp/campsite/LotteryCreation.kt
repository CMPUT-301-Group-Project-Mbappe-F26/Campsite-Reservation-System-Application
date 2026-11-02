package com.example.campsitereservationapp.campsite

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Collects a lottery draft and saves it only when Start Lottery is pressed.
@Composable
fun LotteryCreation(campsite: Campsite, onCancel: () -> Unit) {
    var winners by rememberSaveable(campsite.id) { mutableStateOf("") }
    var registrationStart by rememberSaveable(campsite.id) { mutableStateOf("") }
    var registrationEnd by rememberSaveable(campsite.id) { mutableStateOf("") }
    var bookingStart by rememberSaveable(campsite.id) { mutableStateOf("") }
    var bookingEnd by rememberSaveable(campsite.id) { mutableStateOf("") }
    var stayStart by rememberSaveable(campsite.id) { mutableStateOf("") }
    var stayEnd by rememberSaveable(campsite.id) { mutableStateOf("") }
    val submission: LotteryCreationViewModel = viewModel(key = "lottery-${campsite.ownerId}-${campsite.id}")
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
            .padding(horizontal = 20.dp)
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Create Lottery", style = MaterialTheme.typography.headlineLarge)
            Text(campsite.name.ifBlank { "Unnamed campsite" },
                style = MaterialTheme.typography.titleLarge, color = PinedrawColors.Lime)
            Text("Set when entrants can join, when winners can book, and the stay dates on offer.",
                color = PinedrawColors.TextSecondary)

            LotterySection("Winners") {
                OutlinedTextField(
                    value = winners,
                    enabled = !submission.saving,
                    onValueChange = { value -> if (value.all { it in '0'..'9' }) winners = value },
                    label = { Text("Number of winners") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
            }
            Text(
                "Start dates begin at 00:00. End dates end at 11:59 on the selected date.",
                style = MaterialTheme.typography.bodySmall,
                color = PinedrawColors.TextMuted
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
                Text("Campsite dates available through this lottery.", color = PinedrawColors.TextSecondary)
                LotteryDateField("Stay start", stayStart, !submission.saving) { stayStart = it }
                LotteryDateField("Stay end", stayEnd, !submission.saving) { stayEnd = it }
            }
            if (submission.error.isNotEmpty()) {
                Text(submission.error, color = PinedrawColors.Coral)
            }
            Button(
                onClick = {
                    submission.submit(Lottery(
                        ownerId = campsite.ownerId,
                        campsiteId = campsite.id,
                        numberOfWinners = winners.toIntOrNull() ?: 0,
                        registrationStartDate = registrationStart,
                        registrationEndDate = registrationEnd,
                        bookingStartDate = bookingStart,
                        bookingEndDate = bookingEnd,
                        stayStartDate = stayStart,
                        stayEndDate = stayEnd
                    ))
                }, enabled = !submission.saving,
                modifier = Modifier.fillMaxWidth().height(56.dp),
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

@Composable
private fun LotterySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = PinedrawColors.Glass, contentColor = PinedrawColors.Text)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LotteryDateField(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = PinedrawColors.TextSecondary)
        OutlinedButton(onClick = { picking = true }, enabled = enabled, modifier = Modifier.fillMaxWidth(),
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
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value.takeIf { it.isNotBlank() }?.let { formatter.parse(it)?.time }
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            colors = DatePickerDefaults.colors(containerColor = PinedrawColors.Surface),
            confirmButton = {
                TextButton(enabled = state.selectedDateMillis != null, onClick = {
                    state.selectedDateMillis?.let { onChange(formatter.format(Date(it))) }
                    picking = false
                }) { Text("Select") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state, colors = DatePickerDefaults.colors(containerColor = PinedrawColors.Surface))
        }
    }
}
