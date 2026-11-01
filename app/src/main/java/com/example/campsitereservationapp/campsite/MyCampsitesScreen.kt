package com.example.campsitereservationapp.campsite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.service.CampsiteService
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

// Loads and displays the signed-in owner's campsites.
@Composable
fun MyCampsitesScreen(
    onBack: () -> Unit,
    onCampsiteClick: (String) -> Unit,
    onRegisterClick: () -> Unit
) {
    val service = remember { CampsiteService() }
    val ownerId = rememberCampsiteOwnerId()
    var campsites by remember(ownerId) { mutableStateOf<List<Campsite>>(emptyList()) }
    var loading by remember(ownerId) { mutableStateOf(true) }
    var error by remember(ownerId) { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(ownerId, refresh) {
        loading = true
        error = ""
        try {
            check(ownerId != null) { "Not signed in" }
            val result = withTimeoutOrNull(20000) { service.getMyCampsites() }
            if (result == null) error = "Could not load your campsites. Please try again."
            else campsites = result.filter { it.ownerId == ownerId }
        } catch (_: Exception) {
            coroutineContext.ensureActive() // Stop normally when leaving the screen.
            error = if (ownerId == null) "Sign in to view your campsites." else "Could not load your campsites. Please try again."
        } finally {
            loading = false
        }
    }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(0f to Color(0xFF1E3A26), 0.45f to PinedrawColors.Background, 1f to PinedrawColors.Background))
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Host Dashboard")
            }
            TextButton(onClick = { refresh++ }, enabled = !loading) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Refresh")
            }
        }
        Text("Your campsites", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Manage the places you share.", color = PinedrawColors.TextSecondary)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRegisterClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Register Campsite", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))

        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PinedrawColors.Lime)
            }
            error.isNotEmpty() -> Column(Modifier.padding(vertical = 24.dp)) {
                Text(error, color = PinedrawColors.Coral)
                TextButton(onClick = { refresh++ }) { Text("Try again") }
            }
            campsites.isEmpty() -> Card(
                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = PinedrawColors.Glass, contentColor = PinedrawColors.Text)
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text("No campsites yet", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("Register your first campsite to see it here.", color = PinedrawColors.TextSecondary)
                }
            }
            else -> {
                Text("${campsites.size} ${if (campsites.size == 1) "campsite" else "campsites"}", color = PinedrawColors.TextMuted)
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(campsites, key = { it.id }) { campsite ->
                        Card(
                            onClick = { onCampsiteClick(campsite.id) },
                            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = PinedrawColors.Glass, contentColor = PinedrawColors.Text)
                        ) {
                            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                CampsitePhoto(campsite.imageUrls.firstOrNull(), Modifier.size(96.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(campsite.name.ifBlank { "Unnamed campsite" }, fontWeight = FontWeight.Bold)
                                    Text(campsite.location.ifBlank { "Location not provided" }, color = PinedrawColors.TextSecondary)
                                    Text(if (campsite.published) "Published" else "Draft",
                                        color = if (campsite.published) PinedrawColors.Lime else PinedrawColors.TextMuted)
                                    Text(
                                        "${campsite.maxOccupancy?.let { "Sleeps $it" } ?: "No guest maximum"} · ${String.format(Locale.CANADA, "$%.2f / night", campsite.pricePerNight)}",
                                        style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                                    modifier = Modifier.size(20.dp).align(Alignment.CenterVertically), tint = PinedrawColors.TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Keeps the owner ID updated when the signed-in account changes.
@Composable
internal fun rememberCampsiteOwnerId(): String? {
    val auth = remember { FirebaseAuth.getInstance() }
    var ownerId by remember { mutableStateOf(auth.currentUser?.uid) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { ownerId = it.currentUser?.uid }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    return ownerId
}
