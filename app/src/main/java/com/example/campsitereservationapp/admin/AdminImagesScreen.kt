package com.example.campsitereservationapp.admin

import androidx.compose.runtime.Composable
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
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.service.UserService
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Lets an admin browse all registered profiles (US 03.05.01), remove profiles (US 03.02.01),
 * and remove campsite owners (US 03.07.01).
 *
 * [AdminProfilesScreen] loads profiles and details from Firebase and handles removal.
 *
 * Outstanding issues: (insert issues here)
 */

/**
 * Loads all profiles for the admin and removes one after the admin confirms.
 *
 * @param onBack Goes back to the previous screen.
 * @param onImageClick Opens the selected image.
 */
@Composable
fun AdminImagesScreen(
    onBack: () -> Unit,
    onImageClick: (String) -> Unit
) {
    val userService = remember { UserService() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<User>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var removingId by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var confirming by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        error = ""
        rows = emptyList()
        try {
            val result = withTimeoutOrNull(20000) {
                val users: List<User> = userService.getAllUsers()
                users
            }
            if (result == null) error = "Image retrieval timed out."
            else rows = result
        } catch (_: Exception) {
            coroutineContext.ensureActive() // Stop normally when leaving the screen.
            error = "Failed to retrieve images."
        } finally {
            loading = false
        }
    }
    if (loading) {
        CircularProgressIndicator()
    } else if (error.isNotEmpty()) {
        Text(error)
    } else if (rows.isEmpty()) {
        Text("No images found.")
    } else {
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
            Text("All images", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text("Every images uploaded to Pinedraw.", color = PinedrawColors.TextSecondary)
            Spacer(Modifier.height(16.dp))

            when {
                loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PinedrawColors.Lime)
                }
                error.isNotEmpty() -> Column(Modifier.padding(vertical = 24.dp)) {
                    Text(error, color = PinedrawColors.Coral)
                    TextButton(onClick = { refresh++ }) { Text("Try again") }
                }
                else -> Column {
                    Text("${rows.size} ${if (rows.size == 1) "image" else "images"}", color = PinedrawColors.TextMuted)
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(rows) { user ->
                            Card(
                                onClick = { onImageClick(user.id) },
                                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = PinedrawColors.Glass, contentColor = PinedrawColors.Text)
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(text = when {
                                            user.name != "" -> "Username: ${user.name}"
                                            else -> "No username"
                                        },
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(text = when {
                                            user.email != "" -> "Email: ${user.email}"
                                            else -> "No registered email"
                                        },
                                            color = PinedrawColors.TextSecondary
                                        )
                                        Text(
                                            text = when {
                                                user.isAdmin -> "Admin"
                                                user.isOwner -> "Campsite Owner"
                                                else -> "Entrant"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PinedrawColors.TextSecondary
                                        )
                                    }

                                    TextButton(onClick = { confirming = user }, enabled = removingId == null) {
                                        Text(if (removingId == user.id) "Removing..." else "Remove", color = PinedrawColors.Coral,
                                            fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    confirming?.let { user ->
                        AlertDialog(
                            onDismissRequest = { confirming = null },
                            title = { if (user.name != "") {Text("Remove ${user.name}?")} else {Text("Remove user?")} },
                            text = { Text("This user will be removed. They will be taken off all waiting lists and their active reservations will be cancelled.") },
                            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Cancel") } },
                            confirmButton = {
                                TextButton(onClick = {
                                    confirming = null
                                    removingId = user.id
                                    scope.launch {
                                        val message = try {
                                            val removed = withTimeoutOrNull(20000) { userService.adminDeleteProfile(user.id) }
                                            if (removed == null) {
                                                if (user.name != "") {
                                                    "Could not remove ${user.name}. Please try again."
                                                }
                                                else {
                                                    "Could not remove image. Please try again."
                                                }
                                            }
                                            else {
                                                rows = rows.filter { it.id != user.id }
                                                if (user.name != "") {
                                                    "${user.name} was removed"
                                                }
                                                else {
                                                    "Image was removed"
                                                }
                                            }
                                        } catch (_: Exception) {
                                            coroutineContext.ensureActive() // Stop normally when leaving the screen.
                                            if (user.name != "") {
                                                "Could not remove ${user.name}. Please try again."
                                            }
                                            else {
                                                "Could not remove image. Please try again."
                                            }
                                        } finally {
                                            removingId = null
                                        }
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    }
                                }) { Text("Remove", color = PinedrawColors.Coral) }
                            }
                        )
                    }
                }
            }
        }
    }
}