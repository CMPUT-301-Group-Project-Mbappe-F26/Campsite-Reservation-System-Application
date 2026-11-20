package com.example.campsitereservationapp.home

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.Locale

/**
 * Displays a dialog where users can select campsite filters.
 *
 * @param currentFilters The filters currently applied.
 * @param onApply Called when the user applies their filters.
 * @param onClose Called when the user closes the dialog.
 */
@Composable
fun CampsiteFilterDialog(
    currentFilters: CampsiteFilters,
    onApply: (CampsiteFilters) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    // Store temporary filter selections
    var location by remember {
        mutableStateOf(currentFilters.location)
    }
    var startDate by remember {
        mutableStateOf(currentFilters.startDate)
    }
    var endDate by remember {
        mutableStateOf(currentFilters.endDate)
    }
    var guests by remember {
        mutableIntStateOf(currentFilters.guests)
    }
    var dateError by remember {
        mutableStateOf("")
    }

    /**
     * Opens a calendar for selecting a date.
     */
    fun openCalendar(selectedDate: String, onDateSelected: (String) -> Unit) {
        val calendar = parseFilterDate(selectedDate)
            ?: Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val datePicker = DatePickerDialog(
            context,
            { _, selectedYear, selectedMonth, selectedDay ->
                // Save the selected date in yyyy-MM-dd format
                val date = String.format(
                    Locale.US,
                    "%04d-%02d-%02d",
                    selectedYear,
                    selectedMonth + 1,
                    selectedDay
                )
                onDateSelected(date)
                dateError = ""
            },
            year,
            month,
            day
        )
        datePicker.show()
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text("Filter Campsites")
        },

        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Location filter
                Text("Location", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = location,
                    onValueChange = {
                        location = it
                    },
                    label = {
                        Text("City or area")
                    },
                    placeholder = {
                        Text("e.g. Edmonton")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider()

                // Date filters
                Text("Dates", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = {
                        openCalendar(startDate) {
                            startDate = it
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (startDate.isEmpty()) {
                            "Select Start Date"
                        } else {
                            "Start: $startDate"
                        }
                    )
                }

                OutlinedButton(
                    onClick = {
                        openCalendar(endDate) {
                            endDate = it
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (endDate.isEmpty()) {
                            "Select End Date"
                        } else {
                            "End: $endDate"
                        }
                    )
                }

                // Remove selected dates
                if (startDate.isNotEmpty() || endDate.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            startDate = ""
                            endDate = ""
                            dateError = ""
                        }
                    ) {
                        Text("Clear Dates")
                    }
                }
                if (dateError.isNotEmpty()) {
                    Text(
                        text = dateError,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                HorizontalDivider()

                // Guest count filter
                Text("Number of Guests", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = {
                            if (guests > 0) {
                                guests--
                            }
                        }
                    ) {
                        Text("-")
                    }
                    Text(
                        text = if (guests == 0) "Any" else "$guests",
                        modifier = Modifier.padding(top = 12.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            guests++
                        }
                    ) {
                        Text("+")
                    }
                }
            }
        },

        confirmButton = {
            Button(
                onClick = {
                    // Make sure both dates are selected together
                    if (startDate.isEmpty() != endDate.isEmpty()) {
                        dateError = "Please select both dates."
                    } else if (
                        startDate.isNotEmpty() &&
                        startDate >= endDate
                    ) {
                        dateError = "End date must be after start date."
                    } else {
                        // Apply the selected filters
                        val filters = CampsiteFilters(
                            location = location.trim(),
                            startDate = startDate,
                            endDate = endDate,
                            guests = guests
                        )

                        onApply(filters)
                    }
                }
            ) {
                Text("Apply")
            }
        },

        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        // Reset all filters
                        location = ""
                        startDate = ""
                        endDate = ""
                        guests = 0
                        dateError = ""
                    }
                ) {
                    Text("Clear")
                }
                TextButton(onClick = onClose) {
                    Text("Cancel")
                }
            }
        }
    )
}