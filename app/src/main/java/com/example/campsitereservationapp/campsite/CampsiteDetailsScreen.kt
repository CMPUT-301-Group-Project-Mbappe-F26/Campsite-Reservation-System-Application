package com.example.campsitereservationapp.campsite

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.service.CampsiteService
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// Loads and shows a campsite belonging to the current owner.
@Composable
fun CampsiteDetailsScreen(campsiteId: String, onBack: () -> Unit) {
    val service = remember { CampsiteService() }
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
            else if (result.ownerId != ownerId || FirebaseAuth.getInstance().currentUser?.uid != ownerId)
                error = "This campsite does not belong to your account."
            else campsite = result
        } catch (_: Exception) {
            coroutineContext.ensureActive() // Stop normally when leaving the screen.
            error = "Could not load this campsite. Please try again."
        } finally {
            loading = false
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
            Text("My Campsites")
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
                        Button(
                            onClick = { creatingLottery = true },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
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
                        }
                    }
                }
            }
        }
    }
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
