package com.example.campsitereservationapp.admin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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

// Loads all campsites for the admin and removes one after the admin confirms.
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

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(0f to Color(0xFF1E3A26), 0.45f to PinedrawColors.Background, 1f to PinedrawColors.Background))
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Admin Dashboard")
            }
            TextButton(onClick = { refresh++ }, enabled = !loading && removingId == null) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Refresh")
            }
        }
        Text("All campsites", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Every campsite registered on Pinedraw.", color = PinedrawColors.TextSecondary)
        Spacer(Modifier.height(16.dp))

        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PinedrawColors.Lime)
            }
            error.isNotEmpty() -> Column(Modifier.padding(vertical = 24.dp)) {
                Text(error, color = PinedrawColors.Coral)
                TextButton(onClick = { refresh++ }) { Text("Try again") }
            }
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
        Card(
            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = PinedrawColors.Glass, contentColor = PinedrawColors.Text)
        ) {
            Text("No campsites found", Modifier.padding(24.dp), style = MaterialTheme.typography.titleLarge)
        }
        return
    }

    Column {
        Text("${rows.size} ${if (rows.size == 1) "campsite" else "campsites"}", color = PinedrawColors.TextMuted)
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(rows, key = { it.id }) { row ->
                Card(
                    onClick = { onCampsiteClick(row.id) },
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = PinedrawColors.Glass, contentColor = PinedrawColors.Text)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(row.name, fontWeight = FontWeight.Bold)
                            Text(row.location, color = PinedrawColors.TextSecondary)
                            Text("Owner: ${row.ownerName}", style = MaterialTheme.typography.bodySmall,
                                color = PinedrawColors.TextSecondary)
                            Text(if (row.published) "Published" else "Draft",
                                color = if (row.published) PinedrawColors.Lime else PinedrawColors.TextMuted)
                        }
                        TextButton(onClick = { confirming = row }, enabled = removingId == null) {
                            Text(if (removingId == row.id) "Removing..." else "Remove", color = PinedrawColors.Coral,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    confirming?.let { row ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("Remove ${row.name}?") },
            text = { Text("Entrants will no longer see this campsite. Any active lottery will be closed and its waiting list cleared.") },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Cancel") } },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    onRemove(row)
                }) { Text("Remove", color = PinedrawColors.Coral) }
            }
        )
    }
}
