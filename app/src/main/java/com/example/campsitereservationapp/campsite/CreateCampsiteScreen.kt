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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Save
import com.example.campsitereservationapp.ui.components.ButtonStyle
import com.example.campsitereservationapp.ui.components.CircleIconButton
import com.example.campsitereservationapp.ui.components.GlassCard
import com.example.campsitereservationapp.ui.components.InlineMessage
import com.example.campsitereservationapp.ui.components.OverlineLabel
import com.example.campsitereservationapp.ui.components.PinedrawBackground
import com.example.campsitereservationapp.ui.components.PinedrawButton
import com.example.campsitereservationapp.ui.components.PinedrawDialog
import com.example.campsitereservationapp.ui.components.PinedrawTextButton
import com.example.campsitereservationapp.ui.components.PinedrawTextField
import com.example.campsitereservationapp.ui.components.SelectChip
import com.example.campsitereservationapp.ui.components.amenityIcon
import com.example.campsitereservationapp.ui.theme.PinedrawRadius
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing
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
import java.text.ParsePosition
import java.util.Date
import java.util.TimeZone
import java.util.Locale

/**
 * Four-step wizard (basics, location, photos, rules) used to register or edit a campsite.
 */
@Composable
fun CreateCampsiteScreen(
    saving: Boolean,
    saveError: String,
    onCancel: () -> Unit,
    onCreate: (CampsiteForm) -> Unit,
    original: CampsiteForm? = null,
    onDelete: (() -> Unit)? = null,
    requiresRulesAcceptance: Boolean = true
) {
    val editing = original != null
    var form by rememberSaveable(original) { mutableStateOf(original ?: CampsiteForm()) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var confirmingSave by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf("") }
    var findingLocation by remember { mutableStateOf(false) }
    val errors = if (showErrors) form.errors(step) else emptyMap()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val scroll = rememberScrollState()
    val stepNames = listOf("Basics", "Location", "Photos", "Rules")
    val hasChanges = original == null || form.hasChangesFrom(original)
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
            uris.forEach {
                try {
                    context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                    // Some photo providers only allow temporary access.
                }
            }
            form = form.copy(photos = selected)
        }
    }

    PinedrawBackground {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = PinedrawSpacing.Gutter)
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = PinedrawSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", goBack, enabled = !saving)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (editing) "Edit campsite" else "New campsite", style = MaterialTheme.typography.titleLarge)
                Text("Step ${step + 1} of 4", color = PinedrawColors.TextMuted, style = MaterialTheme.typography.labelMedium)
            }
            PinedrawTextButton("Cancel", onCancel, color = PinedrawColors.TextSecondary, enabled = !saving)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = PinedrawSpacing.Medium), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            stepNames.forEachIndexed { index, title ->
                Column(Modifier.weight(1f)) {
                    Text("${index + 1} $title", style = MaterialTheme.typography.labelMedium,
                        color = if (index == step) PinedrawColors.Lime else if (index < step) PinedrawColors.Text else PinedrawColors.TextMuted)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(PinedrawRadius.Pill).background(if (index <= step) PinedrawColors.Lime else PinedrawColors.Glass))
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
                    Text("${form.description.length} / 600", color = PinedrawColors.TextMuted, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.End))
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
                    PinedrawButton(
                        if (findingLocation) "Finding location…" else "Find coordinates from address",
                        modifier = Modifier.fillMaxWidth(), style = ButtonStyle.Ghost, icon = Icons.Rounded.MyLocation,
                        enabled = !saving && !findingLocation && form.location.isNotBlank(), loading = findingLocation, onClick = {
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
                    })
                    InlineMessage(locationMessage, color = PinedrawColors.Sky)
                    CampsiteField("Latitude (-90 to 90)", form.latitude, errors["latitude"], saving || findingLocation, KeyboardType.Text) { form = form.copy(latitude = it) }
                    CampsiteField("Longitude (-180 to 180)", form.longitude, errors["longitude"], saving || findingLocation, KeyboardType.Text) { form = form.copy(longitude = it) }
                    Text("Use decimal degrees, with a minus sign for south or west.", style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary)
                    PinedrawButton("View location on map", modifier = Modifier.fillMaxWidth(), style = ButtonStyle.Ghost,
                        icon = Icons.Rounded.Map, enabled = !saving && form.errors(1).isEmpty(), onClick = {
                        try {
                            val lat = form.latitude.toDouble()
                            val lon = form.longitude.toDouble()
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lon")))
                        } catch (_: ActivityNotFoundException) { locationMessage = "No map app or browser is available." }
                    })
                }
                2 -> {
                    Text("Promo photos", style = MaterialTheme.typography.titleLarge)
                    Text("Optional · up to 10 · first photo is the cover", style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextMuted)
                    form.photos.forEachIndexed { index, uri ->
                        GlassCard(contentPadding = 10.dp, spacing = 6.dp) {
                            if (Uri.parse(uri).scheme == "https") {
                                CampsitePhoto(uri, Modifier.fillMaxWidth().height(180.dp), shape = PinedrawRadius.Medium)
                            } else {
                                PhotoPreview(uri)
                            }
                            Row(Modifier.fillMaxWidth().padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (index == 0) "Cover photo" else "Photo ${index + 1}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                PinedrawTextButton("Remove", { form = form.copy(photos = form.photos - uri) }, color = PinedrawColors.Coral, enabled = !saving)
                            }
                        }
                    }
                    PinedrawButton("Add photos (${form.photos.size}/10)", modifier = Modifier.fillMaxWidth(), style = ButtonStyle.Ghost,
                        icon = Icons.Rounded.AddPhotoAlternate, enabled = !saving && form.photos.size < 10, onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                    Text("Amenities", style = MaterialTheme.typography.titleLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf("Cookhouse", "Tennis", "Fire pit", "Water", "Toilets", "Power", "Pets", "Wi-Fi") +
                            original?.amenities.orEmpty()).distinct().forEach { amenity ->
                            SelectChip(
                                amenity, selected = amenity in form.amenities, enabled = !saving, icon = amenityIcon(amenity),
                                onClick = {
                                    form = form.copy(amenities = if (amenity in form.amenities) form.amenities - amenity else form.amenities + amenity)
                                }
                            )
                        }
                    }
                }
                3 -> {
                    Text("Availability", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Every day" to (1..7).toList(), "Weekdays" to (1..5).toList(), "Weekends" to listOf(6, 7)).forEach { (label, days) ->
                            SelectChip(label, selected = form.availableDays.sorted() == days, enabled = !saving,
                                onClick = { form = form.copy(availableDays = days) })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, day ->
                            val selected = index + 1 in form.availableDays
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                                    .background(if (selected) PinedrawColors.Lime else PinedrawColors.Glass)
                                    .clickable(enabled = !saving) {
                                        val days = form.availableDays
                                        form = form.copy(availableDays = if (index + 1 in days) days - (index + 1) else days + (index + 1))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(day, style = MaterialTheme.typography.titleSmall, color = if (selected) PinedrawColors.Ink else PinedrawColors.Text)
                            }
                        }
                    }
                    Text("Season dates (optional)", style = MaterialTheme.typography.titleMedium)
                    DateField("Available from", form.availableFrom, saving) { form = form.copy(availableFrom = it) }
                    DateField("Available until", form.availableUntil, saving) { form = form.copy(availableUntil = it) }
                    errors["availability"]?.let { InlineMessage(it) }
                    CampsiteField("Rules for entrants (optional)", form.rules, null, saving, multiline = true) { form = form.copy(rules = it) }
                    Text("Cancellation policy", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Flexible", "Moderate", "Strict").forEach { policy ->
                            SelectChip(policy, selected = form.cancellationPolicy == policy, enabled = !saving,
                                onClick = { form = form.copy(cancellationPolicy = policy) })
                        }
                    }
                    Text(when (form.cancellationPolicy) {
                        "Flexible" -> "Full refund up to 1 day before check-in."
                        "Moderate" -> "Full refund up to 7 days before check-in."
                        else -> "Full refund up to 14 days before check-in."
                    }, style = MaterialTheme.typography.bodyMedium, color = PinedrawColors.TextSecondary)
                    if (requiresRulesAcceptance) {
                        GlassCard(spacing = 2.dp) {
                            Text("Entrants must accept the rules", style = MaterialTheme.typography.titleSmall)
                            Text("Always on for every reservation", style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextMuted)
                        }
                    }
                    errors["policy"]?.let { InlineMessage(it) }
                    if (!editing) GlassCard(borderColor = PinedrawColors.Lime.copy(alpha = 0.4f), spacing = 4.dp) {
                        OverlineLabel("Ready to create", color = PinedrawColors.Lime)
                        Text(form.name.ifBlank { "Unnamed campsite" }, style = MaterialTheme.typography.titleMedium)
                        Text(form.location, style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary)
                        Text("${form.photos.size} photos · ${form.amenities.size} amenities", style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        InlineMessage(saveError)
        if (editing) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    PinedrawTextButton("Delete campsite", { confirmingDelete = true }, color = PinedrawColors.Coral,
                        enabled = !saving, icon = Icons.Rounded.DeleteOutline)
                }
                Spacer(Modifier.weight(1f))
                if (step < 3) {
                    PinedrawTextButton("Save changes", onClick = {
                        focus.clearFocus()
                        val invalidStep = (0..3).firstOrNull { form.errors(it).isNotEmpty() }
                        if (invalidStep != null) {
                            step = invalidStep
                            showErrors = true
                        } else confirmingSave = true
                    }, enabled = hasChanges && !saving && !findingLocation)
                }
            }
        }
        val canContinue = !saving && !findingLocation && (step < 3 || hasChanges)
        PinedrawButton(
            when {
                saving -> if (editing) "Updating campsite…" else "Creating campsite…"
                step < 3 -> "Continue to ${stepNames[step + 1].lowercase()}"
                editing -> "Save changes"
                else -> "Create campsite"
            },
            enabled = canContinue,
            loading = saving,
            icon = if (step == 3) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
            modifier = Modifier.fillMaxWidth().padding(vertical = PinedrawSpacing.Medium),
            onClick = {
                focus.clearFocus()
                showErrors = true
                if (form.errors(step).isEmpty()) {
                    if (step < 3) { step++; showErrors = false }
                    else {
                        val invalidStep = (0..3).firstOrNull { form.errors(it).isNotEmpty() }
                        if (invalidStep != null) step = invalidStep
                        else if (editing) confirmingSave = true
                        else onCreate(form)
                    }
                }
            }
        )
    }
    }
    if (confirmingSave) PinedrawDialog(
        title = "Save campsite changes?",
        message = "Are you sure you want to update this campsite?",
        icon = Icons.Rounded.Save,
        confirmText = "Save changes",
        confirmEnabled = !saving && hasChanges,
        onDismiss = { confirmingSave = false },
        onConfirm = {
            confirmingSave = false
            onCreate(form)
        }
    )
    if (confirmingDelete && onDelete != null) PinedrawDialog(
        title = "Delete campsite?",
        message = "Are you sure? This permanently removes the listing and closes its active lotteries. Existing reservations are kept.",
        icon = Icons.Rounded.DeleteOutline,
        iconTint = PinedrawColors.Coral,
        confirmText = "Delete campsite",
        confirmStyle = ButtonStyle.Destructive,
        confirmEnabled = !saving,
        onDismiss = { confirmingDelete = false },
        onConfirm = {
            confirmingDelete = false
            onDelete()
        }
    )
}

// Shows a campsite input and its validation message.
@Composable
private fun CampsiteField(
    label: String, value: String, error: String?, saving: Boolean,
    keyboard: KeyboardType = KeyboardType.Text, multiline: Boolean = false,
    onChange: (String) -> Unit
) {
    PinedrawTextField(
        value = value, onValueChange = onChange, enabled = !saving,
        label = label, singleLine = !multiline, minLines = if (multiline) 4 else 1,
        isError = error != null, supportingText = error,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (multiline) ImeAction.Default else ImeAction.Next)
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
        PinedrawButton("$label: ${value.ifBlank { "Any date" }}", { picking = true }, Modifier.weight(1f),
            style = ButtonStyle.Ghost, icon = Icons.Rounded.CalendarMonth, enabled = !saving)
        if (value.isNotBlank()) PinedrawTextButton("Clear", { onChange("") }, color = PinedrawColors.TextSecondary, enabled = !saving)
    }
    if (picking) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = format.parse(value, ParsePosition(0))?.time
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
