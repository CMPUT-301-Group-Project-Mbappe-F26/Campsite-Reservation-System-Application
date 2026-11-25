package com.example.campsitereservationapp.campsite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cabin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.service.CampsiteService
import com.example.campsitereservationapp.ui.components.ButtonStyle
import com.example.campsitereservationapp.ui.components.CircleIconButton
import com.example.campsitereservationapp.ui.components.EmptyState
import com.example.campsitereservationapp.ui.components.ErrorState
import com.example.campsitereservationapp.ui.components.GlassCard
import com.example.campsitereservationapp.ui.components.LoadingState
import com.example.campsitereservationapp.ui.components.PinedrawButton
import com.example.campsitereservationapp.ui.components.PinedrawScreen
import com.example.campsitereservationapp.ui.components.StatusChip
import com.example.campsitereservationapp.ui.components.formatPrice
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawRadius
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Loads and displays the signed-in owner's campsites.
 *
 * @param onBack Goes back to the previous screen.
 * @param onCampsiteClick Opens the selected campsite.
 * @param onRegisterClick Opens the campsite registration screen.
 */
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

    PinedrawScreen(
        title = "Your campsites",
        onBack = onBack,
        backLabel = "Host Dashboard",
        scrollable = false,
        actions = { CircleIconButton(Icons.Rounded.Refresh, "Refresh", { refresh++ }, enabled = !loading) }
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Medium),
            contentPadding = PaddingValues(top = 4.dp, bottom = PinedrawSpacing.Screen)
        ) {
            item {
                PinedrawButton("Register Campsite", onRegisterClick, Modifier.fillMaxWidth(), style = ButtonStyle.Host, icon = Icons.Rounded.Add)
            }
            when {
                loading -> item { LoadingState() }
                error.isNotEmpty() -> item { ErrorState(error, onRetry = { refresh++ }) }
                campsites.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Rounded.Cabin,
                        title = "No campsites yet",
                        message = "Register your first campsite to see it here."
                    )
                }
                else -> {
                    item {
                        Text(style = MaterialTheme.typography.bodyMedium, color = PinedrawColors.TextMuted, text = "${campsites.size} ${if (campsites.size == 1) "campsite" else "campsites"}")
                    }
                    items(campsites, key = { it.id }) { campsite ->
                        GlassCard(onClick = { onCampsiteClick(campsite.id) }, contentPadding = 12.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CampsitePhoto(campsite.imageUrls.firstOrNull(), Modifier.size(92.dp), seed = campsite.id, shape = PinedrawRadius.Medium)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(
                                        campsite.name.ifBlank { "Unnamed campsite" },
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    StatusChip(
                                        if (campsite.published) "Published" else "Draft",
                                        color = if (campsite.published) PinedrawColors.Lime else PinedrawColors.TextMuted
                                    )
                                    Text(
                                        "${campsite.maxOccupancy?.let { "Sleeps $it" } ?: "No guest maximum"} · ${formatPrice(campsite.pricePerNight)} / night",
                                        style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null,
                                    modifier = Modifier.size(20.dp), tint = PinedrawColors.TextMuted
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
