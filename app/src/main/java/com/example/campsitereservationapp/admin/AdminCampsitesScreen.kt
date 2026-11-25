package com.example.campsitereservationapp.admin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.service.CampsiteService
import com.example.campsitereservationapp.service.UserService
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import androidx.compose.material.icons.rounded.Cabin
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.example.campsitereservationapp.ui.components.ButtonStyle
import com.example.campsitereservationapp.ui.components.CircleIconButton
import com.example.campsitereservationapp.ui.components.EmptyState
import com.example.campsitereservationapp.ui.components.ErrorState
import com.example.campsitereservationapp.ui.components.GlassCard
import com.example.campsitereservationapp.ui.components.LoadingState
import com.example.campsitereservationapp.ui.components.PinedrawButton
import com.example.campsitereservationapp.ui.components.PinedrawDialog
import com.example.campsitereservationapp.ui.components.PinedrawScreen
import com.example.campsitereservationapp.ui.components.SceneImage
import com.example.campsitereservationapp.ui.components.StatusChip
import com.example.campsitereservationapp.ui.theme.PinedrawRadius
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Lets an admin browse every registered campsite (US 03.04.01) and remove one (US 03.01.01).
 *
 * [AdminCampsitesScreen] loads campsites and owner names from Firebase and handles removal.
 * [AdminCampsiteList] only draws the list and the confirmation dialog, so intent tests can
 * check it without Firebase.
 *
 * Outstanding issues: the list does not update live; it reloads after a removal or on Refresh.
 */

/**
 * One campsite as shown in the admin list.
 *
 * @property id Firestore document ID of the campsite.
 * @property name Campsite name, or a placeholder when blank.
 * @property location Campsite location, or a placeholder when blank.
 * @property ownerName Owner's display name, or a placeholder when the profile is missing or unnamed.
 * @property published Whether entrants can currently see the campsite.
 */
data class AdminCampsiteRow(
    val id: String,
    val name: String,
    val location: String,
    val ownerName: String,
    val published: Boolean
)

/**
 * Builds the admin list rows from campsites and their owners' profiles, sorted by name.
 *
 * @param campsites Every registered campsite.
 * @param owners Profiles loaded for the campsites' owners; missing owners are allowed.
 * @return One row per campsite, sorted alphabetically by name, ignoring case.
 */
fun adminCampsiteRows(campsites: List<Campsite>, owners: List<User>): List<AdminCampsiteRow> {
    val ownersById = owners.associateBy { it.id }
    return campsites.map { site ->
        val owner = ownersById[site.ownerId]
        AdminCampsiteRow(
            id = site.id,
            name = site.name.trim().ifBlank { "Unnamed campsite" },
            location = site.location.trim().ifBlank { "Location not provided" },
            ownerName = when {
                owner == null -> "Unknown owner"
                owner.name.isBlank() -> "Unnamed owner"
                else -> owner.name.trim()
            },
            published = site.published
        )
    }.sortedBy { it.name.lowercase() }
}

/**
 * Loads all campsites for the admin and removes one after the admin confirms.
 *
 * @param onBack Goes back to the previous screen.
 * @param onCampsiteClick Opens the selected campsite.
 */
@Composable
fun AdminCampsitesScreen(
    onBack: () -> Unit,
    onCampsiteClick: (String) -> Unit
) {
    val campsiteService = remember { CampsiteService() }
    val userService = remember { UserService() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<AdminCampsiteRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var removingId by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh) {
        loading = true
        error = ""
        try {
            val result = withTimeoutOrNull(20000) {
                val campsites = campsiteService.getAllCampsites()
                adminCampsiteRows(campsites, userService.getUsers(campsites.map { it.ownerId }))
            }
            if (result == null) error = "Could not load campsites. Please try again."
            else rows = result
        } catch (_: Exception) {
            coroutineContext.ensureActive() // Stop normally when leaving the screen.
            error = "Could not load campsites. Please try again."
        } finally {
            loading = false
        }
    }

    PinedrawScreen(
        title = "All campsites",
        onBack = onBack,
        backLabel = "Admin Dashboard",
        scrollable = false,
        actions = { CircleIconButton(Icons.Rounded.Refresh, "Refresh", { refresh++ }, enabled = !loading && removingId == null) }
    ) {
        when {
            loading -> LoadingState()
            error.isNotEmpty() -> ErrorState(error, onRetry = { refresh++ })
            else -> AdminCampsiteList(
                rows = rows,
                removingId = removingId,
                onCampsiteClick = onCampsiteClick,
                onRemove = { row ->
                    removingId = row.id
                    scope.launch {
                        val message = try {
                            val removed = withTimeoutOrNull(20000) { campsiteService.removeCampsite(row.id) }
                            if (removed == null) "Could not remove ${row.name}. Please try again."
                            else {
                                rows = rows.filter { it.id != row.id }
                                "${row.name} was removed"
                            }
                        } catch (_: Exception) {
                            coroutineContext.ensureActive() // Stop normally when leaving the screen.
                            "Could not remove ${row.name}. Please try again."
                        } finally {
                            removingId = null
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }
}

/**
 * Shows the admin campsite list with a Remove button on each campsite.
 *
 * Remove opens a confirmation dialog, and [onRemove] is only called after the admin
 * confirms. Shows "No campsites found" when [rows] is empty.
 *
 * @param rows Campsites to show.
 * @param removingId ID of a campsite currently being removed; Remove buttons are disabled meanwhile.
 * @param onCampsiteClick Opens the details of the tapped campsite.
 * @param onRemove Removes the confirmed campsite.
 */
@Composable
fun AdminCampsiteList(
    rows: List<AdminCampsiteRow>,
    removingId: String?,
    onCampsiteClick: (String) -> Unit,
    onRemove: (AdminCampsiteRow) -> Unit
) {
    var confirming by remember { mutableStateOf<AdminCampsiteRow?>(null) }

    if (rows.isEmpty()) {
        EmptyState(Icons.Rounded.Cabin, "No campsites found")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Medium)) {
        Text(style = MaterialTheme.typography.bodyMedium, color = PinedrawColors.TextMuted, text = "${rows.size} ${if (rows.size == 1) "campsite" else "campsites"}")
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Medium),
            contentPadding = PaddingValues(bottom = PinedrawSpacing.Screen)
        ) {
            items(rows, key = { it.id }) { row ->
                GlassCard(onClick = { onCampsiteClick(row.id) }, contentPadding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SceneImage(row.id, Modifier.size(64.dp).clip(PinedrawRadius.Medium))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(row.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(row.location, style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary, maxLines = 1)
                            Text("Owner: ${row.ownerName}", style = MaterialTheme.typography.bodySmall,
                                color = PinedrawColors.TextSecondary, maxLines = 1)
                            StatusChip(if (row.published) "Published" else "Draft",
                                color = if (row.published) PinedrawColors.Lime else PinedrawColors.TextMuted)
                        }
                        PinedrawButton(
                            if (removingId == row.id) "Removing..." else "Remove",
                            onClick = { confirming = row }, enabled = removingId == null,
                            style = ButtonStyle.SoftDestructive, compact = true
                        )
                    }
                }
            }
        }
    }

    confirming?.let { row ->
        PinedrawDialog(
            title = "Remove ${row.name}?",
            message = "Entrants will no longer see this campsite. Any active lottery will be closed and its waiting list cleared.",
            icon = Icons.Rounded.DeleteOutline,
            iconTint = PinedrawColors.Coral,
            confirmText = "Remove",
            confirmStyle = ButtonStyle.Destructive,
            onDismiss = { confirming = null },
            onConfirm = {
                confirming = null
                onRemove(row)
            }
        )
    }
}
