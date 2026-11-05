package com.example.campsitereservationapp.campsite

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.service.CampsiteService
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Loads a campsite and displays its details, with extra options for its owner.
 *
 * Hides other users' unpublished campsites. Create Lottery is shown to the owner and
 * opens [LotteryCreation] for the loaded campsite; closing that form returns here.
 * Loading failures display an error with a retry action.
 *
 * @param campsiteId Firestore document ID of the campsite to load.
 * @param onBack Returns to the owner's campsite list.
 */
// Loads and shows a campsite, with extra options when the signed-in user owns it.
@Composable
fun CampsiteDetailsScreen(campsiteId: String, onBack: () -> Unit, fromDiscover: Boolean = false) {
    val service = remember { CampsiteService() }
    var openLottery by remember(campsiteId) { mutableStateOf<Lottery?>(null) } // Store the campsite's open lottery
    var lotteryStatus by remember(campsiteId) { mutableStateOf("Checking lottery...") }  // Lottery status message
    val ownerId = rememberCampsiteOwnerId()
    var campsite by remember(campsiteId, ownerId) { mutableStateOf<Campsite?>(null) }
    var loading by remember(campsiteId, ownerId) { mutableStateOf(true) }
    var error by remember(campsiteId, ownerId) { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var creatingLottery by rememberSaveable(campsiteId, ownerId) { mutableStateOf(false) }

    LaunchedEffect(campsiteId, ownerId, refresh) {
        loading = true
        error = ""
        campsite = null
        try {
            check(ownerId != null) { "Not signed in" }
            val result = withTimeoutOrNull(20000) { service.getCampsite(campsiteId) }
            if (result == null) error = "This campsite is unavailable or could not be loaded. Please try again."
            else if (!canViewCampsite(result, ownerId))
                error = "This campsite is unavailable or could not be loaded. Please try again."
            else campsite = result
        } catch (_: Exception) {
            coroutineContext.ensureActive() // Stop normally when leaving the screen.
            error = "Could not load this campsite. Please try again."
        } finally {
            loading = false
        }
    }
    // If selected campsite has an open lottery
    LaunchedEffect(campsiteId) {
        try {
            openLottery = service.getOpenLottery(campsiteId)
            // Update status
            if (openLottery != null) {
                lotteryStatus = "Registration open"
            } else {
                lotteryStatus = "No open lottery"
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            lotteryStatus = "Could not load lottery information"
        }
    }

    val ownedCampsite = campsite?.takeIf { ownerId != null && it.ownerId == ownerId }
    if (creatingLottery && !loading && error.isEmpty() && ownedCampsite != null) {
        LotteryCreation(campsite = ownedCampsite, onCancel = { creatingLottery = false })
        return
    }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(0f to Color(0xFF1E3A26), 0.45f to PinedrawColors.Background, 1f to PinedrawColors.Background))
            .statusBarsPadding().navigationBarsPadding()
    ) {
        TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 12.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(getCampsiteBackLabel(fromDiscover))  // Returns either discover or my campsites
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PinedrawColors.Lime)
            }
            error.isNotEmpty() -> Column(Modifier.padding(24.dp)) {
                Text(error, color = PinedrawColors.Coral)
                TextButton(onClick = { refresh++ }) { Text("Try again") }
            }
            else -> campsite?.let { site ->
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CampsitePhoto(site.imageUrls.firstOrNull(), Modifier.fillMaxWidth().height(230.dp))
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(site.name.ifBlank { "Unnamed campsite" }, style = MaterialTheme.typography.headlineLarge)
                        if (canShowOwnerControls(site, ownerId, fromDiscover)) Button(
                            onClick = { creatingLottery = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PinedrawColors.Lime,
                                contentColor = PinedrawColors.Ink
                            )
                        ) {
                            Text("Create Lottery", fontWeight = FontWeight.Bold)
                        }
                        Surface(
                            color = if (site.published) PinedrawColors.Lime.copy(alpha = 0.15f) else PinedrawColors.Glass,
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(if (site.published) "Published" else "Draft", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = if (site.published) PinedrawColors.Lime else PinedrawColors.TextMuted,
                                fontWeight = FontWeight.Bold)
                        }
                        Text("Overview", style = MaterialTheme.typography.titleLarge)
                        HorizontalDivider(color = PinedrawColors.Amber)
                        DetailCard {
                            DetailRow("Location", site.location.ifBlank { "Not provided" })
                            DetailRow("Coordinates", String.format(Locale.CANADA, "%.5f, %.5f", site.latitude, site.longitude))
                            DetailRow("Max occupancy", site.maxOccupancy?.let { "$it guests" } ?: "No maximum")
                            DetailRow("Price", String.format(Locale.CANADA, "$%.2f CAD / night", site.pricePerNight))

                            if (site.averageRating != null) {  // Display campsite rating if available
                                val rating = site.averageRating
                                val fullStars = rating.toInt().coerceIn(0, 5)
                                val stars = "\u2605".repeat(fullStars) + "\u2606".repeat(5 - fullStars)

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stars,
                                        color = Color(0xFFFFC107)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))
                                    // Rating
                                    Text(text = "$rating / 5")
                                }
                            } else {
                                Text(  // No rating
                                    text = "No ratings yet",
                                    color = PinedrawColors.TextSecondary
                                )
                            }
                        }

                        Text(  // Display lottery information
                            text = "Lottery Information",
                            style = MaterialTheme.typography.titleLarge
                        )

                        DetailCard {  // Show whether registration is open
                            Text(
                                text = lotteryStatus,
                                color = PinedrawColors.TextSecondary
                            )

                            if (openLottery != null) {  // Show registration dates if a lottery is open
                                DetailRow(
                                    "Registration starts",
                                    openLottery!!.registrationStartDate
                                )

                                DetailRow(
                                    "Registration ends",
                                    openLottery!!.registrationEndDate
                                )
                            }
                        }

                        DetailCard {
                            Text("Description", style = MaterialTheme.typography.titleLarge)
                            Text(site.description.ifBlank { "No description provided." }, color = PinedrawColors.TextSecondary)
                            Text("Amenities", fontWeight = FontWeight.Bold)
                            DetailLabels(site.amenities, "No amenities listed.")
                            Text("Tags", fontWeight = FontWeight.Bold)
                            DetailLabels(site.tags, "No tags listed.")
                        }
                        if (site.imageUrls.size > 1) {
                            Text("Photos", style = MaterialTheme.typography.titleLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                itemsIndexed(site.imageUrls.drop(1)) { _, url ->
                                    CampsitePhoto(url, Modifier.width(260.dp).height(180.dp))
                                }
                            }
                        }
                        DetailCard {
                            Text("Availability", style = MaterialTheme.typography.titleLarge)
                            val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                            val available = site.availableDays.distinct().sorted().mapNotNull { days.getOrNull(it - 1) }
                            Text(available.joinToString(" · ").ifBlank { "Availability not specified." }, color = PinedrawColors.TextSecondary)
                            DetailRow("From", site.availableFrom.ifBlank { "Any date" })
                            DetailRow("Until", site.availableUntil.ifBlank { "Any date" })
                        }
                        DetailCard {
                            Text("Rules for entrants", style = MaterialTheme.typography.titleLarge)
                            Text(site.rules.ifBlank { "No additional rules provided." }, color = PinedrawColors.TextSecondary)
                            DetailRow("Cancellation policy", site.cancellationPolicy.ifBlank { "Not specified" })
                            if (site.requireRulesAcceptance) Text("Entrants must accept the rules.", color = PinedrawColors.TextMuted)
                            if (canShowOwnerControls(site, ownerId, fromDiscover)) OwnerPolicyEditor(
                                campsiteId = site.id,
                                rules = site.rules,
                                onSaved = { campsite = site.copy(rules = it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// Lets the owner edit the campsite policy, saving only after they confirm.
@Composable
private fun OwnerPolicyEditor(campsiteId: String, rules: String, onSaved: (String) -> Unit) {
    val service = remember { CampsiteService() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable(campsiteId) { mutableStateOf(false) }
    var draft by rememberSaveable(campsiteId) { mutableStateOf(rules) }
    var confirming by rememberSaveable(campsiteId) { mutableStateOf(false) }
    var saving by remember(campsiteId) { mutableStateOf(false) }
    var error by remember(campsiteId) { mutableStateOf("") }

    if (!editing) {
        TextButton(onClick = {
            draft = rules
            error = ""
            editing = true
        }) { Text("Edit policy") }
        return
    }

    OutlinedTextField(
        value = draft, onValueChange = { draft = it }, enabled = !saving,
        label = { Text("Rules for entrants") }, modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium, minLines = 4,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = PinedrawColors.Glass, unfocusedContainerColor = PinedrawColors.Glass,
            unfocusedBorderColor = Color.White.copy(alpha = 0.08f)
        )
    )
    if (error.isNotEmpty()) Text(error, color = PinedrawColors.Coral)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { editing = false }, enabled = !saving) { Text("Cancel") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = { confirming = true }, enabled = !saving, shape = MaterialTheme.shapes.large) {
            Text(if (saving) "Saving..." else "Save", fontWeight = FontWeight.Bold)
        }
    }

    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text("Are you sure?") },
        text = { Text("Entrants will see this policy for your campsite.") },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        confirmButton = {
            TextButton(onClick = {
                confirming = false
                saving = true
                error = ""
                scope.launch {
                    try {
                        val saved = withTimeoutOrNull(20000) {
                            service.updateCampsitePolicy(campsiteId, draft)
                            true
                        }
                        if (saved == null) error = "Could not save the policy. Please try again."
                        else {
                            editing = false
                            onSaved(draft.trim())
                            Toast.makeText(context, "Policy updated", Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: Exception) {
                        coroutineContext.ensureActive() // Stop normally when leaving the screen.
                        error = "Could not save the policy. Please try again."
                    } finally {
                        saving = false
                    }
                }
            }) { Text("Proceed") }
        }
    )
}

// Groups campsite details inside a rounded card.
@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = PinedrawColors.Glass, contentColor = PinedrawColors.Text)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

// Shows one campsite label and its value.
@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = PinedrawColors.TextSecondary)
        Text(value, Modifier.weight(1.3f), fontWeight = FontWeight.SemiBold)
    }
}

// Shows amenities or tags, or a message when none are listed.
@Composable
private fun DetailLabels(values: List<String>, emptyMessage: String) {
    val labels = values.filter { it.isNotBlank() }
    if (labels.isEmpty()) Text(emptyMessage, color = PinedrawColors.TextMuted)
    else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { label ->
            Surface(color = PinedrawColors.Surface, contentColor = PinedrawColors.Text, shape = MaterialTheme.shapes.medium) {
                Text(label, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// Loads a campsite photo or shows a placeholder.
@Composable
internal fun CampsitePhoto(url: String?, modifier: Modifier = Modifier) {
    var loading by remember(url) { mutableStateOf(!url.isNullOrBlank()) }
    val bitmap by produceState<Bitmap?>(null, url) {
        value = null
        loading = !url.isNullOrBlank()
        if (!url.isNullOrBlank()) {
            value = withContext(Dispatchers.IO) {
                var connection: HttpURLConnection? = null
                try {
                    val address = URL(url)
                    require(address.protocol == "https")
                    connection = address.openConnection() as HttpURLConnection
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    val bytes = connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var count = input.read(buffer)
                        while (count != -1) {
                            require(output.size() + count <= 20 * 1024 * 1024)
                            output.write(buffer, 0, count)
                            count = input.read(buffer)
                        }
                        output.toByteArray()
                    }
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    options.inSampleSize = 1
                    while (options.outWidth / options.inSampleSize > 1200 || options.outHeight / options.inSampleSize > 1200)
                        options.inSampleSize *= 2 // Keep full-size photos from exhausting memory.
                    options.inJustDecodeBounds = false
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                } catch (_: Exception) {
                    null
                } finally {
                    connection?.disconnect()
                }
            }
        }
        loading = false
    }
    Box(modifier.clip(MaterialTheme.shapes.large).background(PinedrawColors.Surface), contentAlignment = Alignment.Center) {
        when {
            loading -> CircularProgressIndicator(Modifier.size(24.dp), color = PinedrawColors.Lime)
            bitmap != null -> Image(bitmap!!.asImageBitmap(), "Campsite photo", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            else -> Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Place, contentDescription = null, tint = PinedrawColors.TextMuted, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(6.dp))
                Text(if (url.isNullOrBlank()) "No photo" else "Photo unavailable",
                    color = PinedrawColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
// Testing purposes functions
/**
 * Checks if a user can view a campsite.
 *
 * Published campsites can be viewed by anyone, while unpublished
 * campsites can only be viewed by their owner.
 *
 * @param campsite The campsite being checked.
 * @param userId ID of the current user.
 * @return True if the user can view the campsite.
 */
fun canViewCampsite(campsite: Campsite, userId: String?): Boolean {
    return campsite.published || campsite.isOwnedBy(userId)
}
/**
 * Checks if the owner controls should be displayed.
 *
 * Owner controls are only shown when the owner opens the
 * campsite outside of Discover.
 *
 * @param campsite The campsite being viewed.
 * @param userId ID of the current user.
 * @param fromDiscover True if the user opened the campsite from Discover.
 * @return True if the owner controls should be shown.
 */
fun canShowOwnerControls(
    campsite: Campsite,
    userId: String?,
    fromDiscover: Boolean
): Boolean {
    return !fromDiscover && campsite.isOwnedBy(userId)
}
/**
 * Gets the correct back button label based on where the user came from.
 *
 * @param fromDiscover True if the campsite was opened from Discover.
 * @return "Discover" or "My Campsites".
 */
fun getCampsiteBackLabel(fromDiscover: Boolean): String {
    if (fromDiscover) {
        return "Discover"
    }

    return "My Campsites"
}
