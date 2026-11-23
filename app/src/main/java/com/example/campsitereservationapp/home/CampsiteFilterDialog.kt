package com.example.campsitereservationapp.home

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.ui.components.ButtonStyle
import com.example.campsitereservationapp.ui.components.InlineMessage
import com.example.campsitereservationapp.ui.components.PinedrawButton
import com.example.campsitereservationapp.ui.components.PinedrawTextButton
import com.example.campsitereservationapp.ui.components.PinedrawTextField
import com.example.campsitereservationapp.ui.components.SelectChip
import com.example.campsitereservationapp.ui.components.Stepper
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing
import java.util.Calendar
import java.util.Locale

private val quickLocations = listOf("Banff", "Jasper", "Canmore", "Waterton", "Edmonton")

/**
 * Bottom sheet where users choose a location, dates and party size to filter campsites.
 *
 * @param currentFilters The filters currently applied.
 * @param onApply Called when the user applies their filters.
 * @param onClose Called when the user closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampsiteFilterDialog(
    currentFilters: CampsiteFilters,
    onApply: (CampsiteFilters) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var location by remember { mutableStateOf(currentFilters.location) }
    var startDate by remember { mutableStateOf(currentFilters.startDate) }
    var endDate by remember { mutableStateOf(currentFilters.endDate) }
    var guests by remember { mutableIntStateOf(currentFilters.guests) }
    var dateError by remember { mutableStateOf("") }

    fun openCalendar(selectedDate: String, onDateSelected: (String) -> Unit) {
        val calendar = parseFilterDate(selectedDate) ?: Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                onDateSelected(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day))
                dateError = ""
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PinedrawColors.Surface,
        contentColor = PinedrawColors.Text
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PinedrawSpacing.Screen)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Large)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                PinedrawTextButton("Reset", onClick = {
                    location = ""
                    startDate = ""
                    endDate = ""
                    guests = 0
                    dateError = ""
                })
            }

            Text("Location", style = MaterialTheme.typography.titleMedium)
            PinedrawTextField(
                value = location,
                onValueChange = { location = it },
                placeholder = "City, town or park",
                leadingIcon = Icons.Rounded.Place
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                quickLocations.forEach { place ->
                    SelectChip(
                        place,
                        selected = location.equals(place, ignoreCase = true),
                        onClick = { location = if (location.equals(place, ignoreCase = true)) "" else place }
                    )
                }
            }

            Text("Dates", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(PinedrawSpacing.Small)) {
                PinedrawButton(
                    text = startDate.ifEmpty { "Check-in" },
                    onClick = { openCalendar(startDate) { startDate = it } },
                    style = ButtonStyle.Ghost,
                    icon = Icons.Rounded.CalendarMonth,
                    modifier = Modifier.weight(1f)
                )
                PinedrawButton(
                    text = endDate.ifEmpty { "Check-out" },
                    onClick = { openCalendar(endDate) { endDate = it } },
                    style = ButtonStyle.Ghost,
                    icon = Icons.Rounded.CalendarMonth,
                    modifier = Modifier.weight(1f)
                )
            }
            InlineMessage(dateError)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Guests", style = MaterialTheme.typography.titleMedium)
                    Text("Matches max occupancy", style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary)
                }
                Stepper(
                    value = if (guests == 0) "Any" else "$guests",
                    onDecrement = { if (guests > 0) guests-- },
                    onIncrement = { guests++ },
                    canDecrement = guests > 0,
                    decrementLabel = "Fewer guests",
                    incrementLabel = "More guests"
                )
            }

            Spacer(Modifier.height(PinedrawSpacing.XSmall))
            PinedrawButton(
                text = "Show campsites",
                onClick = {
                    if (startDate.isEmpty() != endDate.isEmpty()) {
                        dateError = "Please select both dates."
                    } else if (startDate.isNotEmpty() && startDate >= endDate) {
                        dateError = "End date must be after start date."
                    } else {
                        onApply(CampsiteFilters(location.trim(), startDate, endDate, guests))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(PinedrawSpacing.Medium))
        }
    }
}
