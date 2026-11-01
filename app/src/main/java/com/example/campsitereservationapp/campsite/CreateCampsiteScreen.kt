package com.example.campsitereservationapp.campsite

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import java.util.Locale

// Collects campsite details in four steps before saving.
@Composable
fun CreateCampsiteScreen(
    saving: Boolean,
    saveError: String,
    onCancel: () -> Unit,
    onCreate: (CampsiteForm) -> Unit
) {
    var form by rememberSaveable { mutableStateOf(CampsiteForm()) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf("") }
    var findingLocation by remember { mutableStateOf(false) }
    val errors = if (showErrors) form.errors(step) else emptyMap()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val scroll = rememberScrollState()
    val stepNames = listOf("Basics", "Location", "Photos", "Rules")
    val goBack: () -> Unit = {
        if (!saving) {
            if (step == 0) onCancel() else { step--; showErrors = false }
        }
    }
    BackHandler { goBack() }
    LaunchedEffect(step) { scroll.scrollTo(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (!saving) {
            val selected = (form.photos + uris.map { it.toString() }).distinct().take(10)
            selected.forEach {
                try {
                    context.contentResolver.takePersistableUriPermission(Uri.parse(it), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                    // Some photo providers only allow temporary access.
                }
            }
            form = form.copy(photos = selected)
        }
    }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(0f to Color(0xFF1E3A26), 0.45f to PinedrawColors.Background, 1f to PinedrawColors.Background))
            .statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = goBack, enabled = !saving) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Back")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("New campsite", style = MaterialTheme.typography.titleLarge)
                Text("Step ${step + 1} of 4", color = PinedrawColors.TextMuted, style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = onCancel, enabled = !saving) { Text("Cancel") }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            stepNames.forEachIndexed { index, title ->
                Column(Modifier.weight(1f)) {
                    Text("${index + 1} $title", style = MaterialTheme.typography.labelMedium,
                        color = if (index == step) PinedrawColors.Text else PinedrawColors.TextMuted)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(3.dp).background(if (index <= step) PinedrawColors.Lime else PinedrawColors.Glass))
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (step) {
                0 -> {
                    CampsiteField("Campsite name", form.name, errors["name"], saving) { form = form.copy(name = it) }
                    CampsiteField("Description & amenities", form.description, errors["description"], saving, multiline = true) {
                        form = form.copy(description = it)
                    }
                    Text("${form.description.length} / 600", color = PinedrawColors.TextMuted, style = MaterialTheme.typography.labelMedium)
                    CampsiteField("Maximum occupancy (optional)", form.occupancy, errors["occupancy"], saving, KeyboardType.Number) {
                        form = form.copy(occupancy = it)
                    }
                    Text("Leave blank for no maximum. People per reservation.", color = PinedrawColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    CampsiteField("Price per night (CAD)", form.price, errors["price"], saving, KeyboardType.Decimal) { form = form.copy(price = it) }
                    CampsiteField("Tags (separate with commas)", form.tags, null, saving) { form = form.copy(tags = it) }
                }
                1 -> {
                    CampsiteField("Address or location", form.location, errors["location"], saving || findingLocation) {
                        form = form.copy(location = it)
                        locationMessage = ""
                    }
                    OutlinedButton(enabled = !saving && !findingLocation && form.location.isNotBlank(), onClick = {
                        findingLocation = true
                        locationMessage = ""
                        focus.clearFocus()
                        scope.launch {
                            try {
                                val address = withContext(Dispatchers.IO) {
                                    @Suppress("DEPRECATION")
                                    Geocoder(context, Locale.getDefault()).getFromLocationName(form.location, 1)?.firstOrNull()
                                }
                                if (address == null) locationMessage = "Address not found. Enter and verify the campsite coordinates manually."
                                else {
                                    form = form.copy(latitude = address.latitude.toString(), longitude = address.longitude.toString())
                                    locationMessage = "Found: ${address.getAddressLine(0)}. Verify the pin before continuing."
                                }
                            } catch (_: Exception) {
                                coroutineContext.ensureActive()
                                locationMessage = "Address lookup unavailable. Enter coordinates manually."
                            } finally {
                                findingLocation = false
                            }
                        }
                    }) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (findingLocation) "Finding location…" else "Find coordinates from address")
                    }
                    if (locationMessage.isNotEmpty()) Text(locationMessage, color = PinedrawColors.TextSecondary)
                    CampsiteField("Latitude (-90 to 90)", form.latitude, errors["latitude"], saving || findingLocation, KeyboardType.Text) { form = form.copy(latitude = it) }
                    CampsiteField("Longitude (-180 to 180)", form.longitude, errors["longitude"], saving || findingLocation, KeyboardType.Text) { form = form.copy(longitude = it) }
                    Text("Use decimal degrees, including a minus sign for south or west. The coordinates locate the campsite on the map.", color = PinedrawColors.TextSecondary)
                    OutlinedButton(enabled = !saving && form.errors(1).isEmpty(), onClick = {
                        try {
                            val lat = form.latitude.toDouble()
                            val lon = form.longitude.toDouble()
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lon")))
                        } catch (_: ActivityNotFoundException) { locationMessage = "No map app or browser is available." }
                    }) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("View location on map")
                    }
                }
                2 -> {
                    Text("Promo photos", style = MaterialTheme.typography.titleLarge)
                    Text("Optional · up to 10 · first photo is the cover", color = PinedrawColors.TextMuted)
                    form.photos.forEachIndexed { index, uri ->
                        Card(colors = CardDefaults.cardColors(containerColor = PinedrawColors.Glass)) {
                            PhotoPreview(uri)
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (index == 0) "Cover photo" else "Photo ${index + 1}", Modifier.weight(1f))
                                TextButton(enabled = !saving, onClick = { form = form.copy(photos = form.photos - uri) }) { Text("Remove") }
                            }
                        }
                    }
                    OutlinedButton(enabled = !saving && form.photos.size < 10, onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add photos (${form.photos.size}/10)")
                    }
                    Text("Amenities", style = MaterialTheme.typography.titleLarge)
                    listOf("Cookhouse", "Tennis", "Fire pit", "Water", "Toilets", "Power", "Pets", "Wi-Fi")
                        .chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { amenity ->
                                    FilterChip(
                                        selected = amenity in form.amenities, enabled = !saving,
                                        modifier = Modifier.weight(1f), label = { Text(amenity) },
                                        onClick = {
                                            form = form.copy(amenities = if (amenity in form.amenities) form.amenities - amenity else form.amenities + amenity)
                                        }
                                    )
                                }
                            }
                        }
                }
                3 -> {
                    Text("Availability", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Every day" to (1..7).toList(), "Weekdays" to (1..5).toList(), "Weekends" to listOf(6, 7)).forEach { (label, days) ->
                            FilterChip(selected = form.availableDays.sorted() == days, enabled = !saving,
                                onClick = { form = form.copy(availableDays = days) }, label = { Text(label) })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, day ->
                            FilterChip(selected = index + 1 in form.availableDays, enabled = !saving,
                                modifier = Modifier.weight(1f), label = { Text(day) }, onClick = {
                                    val days = form.availableDays
                                    form = form.copy(availableDays = if (index + 1 in days) days - (index + 1) else days + (index + 1))
                                })
                        }
                    }
                    Text("Optional season dates; leave blank for no date restriction.", color = PinedrawColors.TextSecondary)
                    DateField("Available from", form.availableFrom, saving) { form = form.copy(availableFrom = it) }
                    DateField("Available until", form.availableUntil, saving) { form = form.copy(availableUntil = it) }
                    errors["availability"]?.let { Text(it, color = PinedrawColors.Coral) }
                    CampsiteField("Rules for entrants (optional)", form.rules, null, saving, multiline = true) { form = form.copy(rules = it) }
                    Text("Cancellation policy", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Flexible", "Moderate", "Strict").forEach { policy ->
                            FilterChip(selected = form.cancellationPolicy == policy, enabled = !saving,
                                onClick = { form = form.copy(cancellationPolicy = policy) }, label = { Text(policy) })
                        }
                    }
                    Text(when (form.cancellationPolicy) {
                        "Flexible" -> "Full refund up to 1 day before check-in."
                        "Moderate" -> "Full refund up to 7 days before check-in."
                        else -> "Full refund up to 14 days before check-in."
                    }, color = PinedrawColors.TextSecondary)
                    Text("Entrants must accept the rules", fontWeight = FontWeight.Bold)
                    Text("Always on for every reservation", color = PinedrawColors.TextMuted)
                    Text("Ready to create: ${form.name}\n${form.location}\n${form.photos.size} photos · ${form.amenities.size} amenities", color = PinedrawColors.TextSecondary)
                }
            }
            if (saveError.isNotEmpty()) Text(saveError, color = PinedrawColors.Coral)
            Spacer(Modifier.height(8.dp))
        }
        Button(
            enabled = !saving && !findingLocation,
            onClick = {
                focus.clearFocus()
                showErrors = true
                if (form.errors(step).isEmpty()) {
                    if (step < 3) { step++; showErrors = false }
                    else {
                        val invalidStep = (0..3).firstOrNull { form.errors(it).isNotEmpty() }
                        if (invalidStep != null) step = invalidStep else onCreate(form)
                    }
                }
            },
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
        ) {
            Box(Modifier.fillMaxSize().background(if (saving) Brush.horizontalGradient(listOf(PinedrawColors.Surface, PinedrawColors.Surface)) else PinedrawColors.PrimaryGradient), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (saving) "Creating campsite…" else if (step == 3) "Create campsite" else "Continue to ${stepNames[step + 1].lowercase()}", fontWeight = FontWeight.Bold)
                    if (!saving) {
                        Spacer(Modifier.width(8.dp))
                        Icon(if (step == 3) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// Shows a campsite input and its validation message.
@Composable
private fun CampsiteField(
    label: String, value: String, error: String?, saving: Boolean,
    keyboard: KeyboardType = KeyboardType.Text, multiline: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, enabled = !saving,
        label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium, singleLine = !multiline, minLines = if (multiline) 4 else 1,
        isError = error != null, supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (multiline) ImeAction.Default else ImeAction.Next),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = PinedrawColors.Glass, unfocusedContainerColor = PinedrawColors.Glass,
            unfocusedBorderColor = Color.White.copy(alpha = 0.08f)
        )
    )
}

// Lets the owner choose or clear an availability date.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, value: String, saving: Boolean, onChange: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val format = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.CANADA).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            enabled = !saving, modifier = Modifier.weight(1f),
            onClick = { picking = true }
        ) {
            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("$label: ${value.ifBlank { "Any date" }}")
        }
        if (value.isNotBlank()) TextButton(enabled = !saving, onClick = { onChange("") }) { Text("Clear") }
    }
    if (picking) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = value.takeIf { it.isNotBlank() }?.let { format.parse(it)?.time }
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            colors = DatePickerDefaults.colors(containerColor = PinedrawColors.Surface),
            confirmButton = {
                TextButton(enabled = dateState.selectedDateMillis != null, onClick = {
                    dateState.selectedDateMillis?.let { onChange(format.format(Date(it))) }
                    picking = false
                }) { Text("Select date") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } }
        ) {
            DatePicker(
                state = dateState,
                title = { Text(label, Modifier.padding(start = 24.dp, top = 16.dp)) },
                colors = DatePickerDefaults.colors(containerColor = PinedrawColors.Surface)
            )
        }
    }
}

// Shows a preview of a selected local photo.
@Composable
private fun PhotoPreview(uri: String) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, options) }
                options.inSampleSize = 1
                while (options.outWidth / options.inSampleSize > 800 || options.outHeight / options.inSampleSize > 800) options.inSampleSize *= 2
                options.inJustDecodeBounds = false
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, options) }
            } catch (_: Exception) { null }
        }
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Selected campsite photo", Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
    else Text("Photo preview unavailable. Remove and reselect if necessary.", Modifier.padding(16.dp), color = PinedrawColors.TextMuted)
}
