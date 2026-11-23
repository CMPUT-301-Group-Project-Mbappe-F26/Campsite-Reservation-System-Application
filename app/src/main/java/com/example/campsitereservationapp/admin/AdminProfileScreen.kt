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
import androidx.compose.ui.text.font.FontStyle
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
 * Displays details of a given user.
 *
 * @param userId The ID of the user whose details will be displayed.
 * @param onBack Goes back to the profiles screen.
 */
@Composable
fun AdminProfileScreen(
    userId: String,
    onBack: () -> Unit
) {
    val userService = remember { UserService() }
    var row by remember { mutableStateOf<List<User>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var removingId by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var user by remember {mutableStateOf(User("", "", "", "", "", false, false, false))}

    LaunchedEffect(Unit) {
        loading = true
        error = ""
        row = emptyList()
        try {
            val result = withTimeoutOrNull(20000) {
                val user: List<User> = userService.getUsers(listOf(userId))
                user
            }
            if (result == null) error = "Profile detail retrieval timed out."
            else {
                row = result
                user = row[0]
            }
        } catch (_: Exception) {
            coroutineContext.ensureActive() // Stop normally when leaving the screen.
            error = "Failed to retrieve profile details."
        } finally {
            loading = false
        }
    }
    if (loading) {
        CircularProgressIndicator()
    } else if (error.isNotEmpty()) {
        Text(error)
    } else if (row.isEmpty()) {
        Text("No profile details found.")
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
                    Text("All profiles")
                }
                TextButton(onClick = { refresh++ }, enabled = !loading && removingId == null) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh")
                }
            }

            when {
                loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PinedrawColors.Lime)
                }
                error.isNotEmpty() -> Column(Modifier.padding(vertical = 24.dp)) {
                    Text(error, color = PinedrawColors.Coral)
                    TextButton(onClick = { refresh++ }) { Text("Try again") }
                }
                else -> Column {
                    Text(text = when {
                        user.name != "" -> user.name
                        else -> "(No username)"
                        },
                        fontWeight = FontWeight.Bold,
                        fontStyle = when {
                            user.name == "" -> FontStyle.Italic
                            else -> FontStyle.Normal
                        },
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(text = "Account information", style = MaterialTheme.typography.headlineSmall)
                    Card(
                        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "ID",
                                    color = PinedrawColors.TextSecondary,
                                    modifier = Modifier.width(100.dp)
                                )
                                Text(
                                    text = user.id
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Role",
                                    color = PinedrawColors.TextSecondary,
                                    modifier = Modifier.width(100.dp)
                                )
                                Text(
                                    text = when {
                                        user.isAdmin -> "Admin"
                                        user.isOwner -> "Campsite Owner"
                                        else -> "Entrant"
                                    }
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Notifications",
                                    color = PinedrawColors.TextSecondary,
                                    modifier = Modifier.width(100.dp)
                                )
                                Text(
                                    text = when {
                                        user.notificationsEnabled -> "Enabled"
                                        else -> "Disabled"
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(text = "Personal information", style = MaterialTheme.typography.headlineSmall)
                    Card(
                        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Email",
                                    color = PinedrawColors.TextSecondary,
                                    modifier = Modifier.width(100.dp)
                                )
                                Text(
                                    text = user.email
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Phone number",
                                    color = PinedrawColors.TextSecondary,
                                    modifier = Modifier.width(100.dp)
                                )
                                Text(
                                    text = user.phone
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "City",
                                    color = PinedrawColors.TextSecondary,
                                    modifier = Modifier.width(100.dp)
                                )
                                Text(
                                    text = user.city
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}