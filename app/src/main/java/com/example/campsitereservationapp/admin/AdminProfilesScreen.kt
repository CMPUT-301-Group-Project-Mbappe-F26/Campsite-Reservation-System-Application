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
 * Lets an admin browse all registered profiles (US 03.05.01), remove profiles (US 03.02.01),
 * and remove campsite owners (US 03.07.01).
 *
 * [AdminProfilesScreen] loads profiles and details from Firebase and handles removal.
 * [AdminProfileList] (insert description here)
 *
 * Outstanding issues: (insert issues here)
 */

@Composable
fun adminProfileScreen() {
    var loading by remember { mutableStateOf(true) }
    val userService = remember { UserService() }
    var error by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<User>>(emptyList()) }

    LaunchedEffect(Unit) {
        loading = true
        error = ""
        rows = emptyList()
        try {
            val result = withTimeoutOrNull(20000) {
                val users: List<User> = userService.getAllUsers()
                users
            }
            if (result == null) error = "Profile retrieval timed out."
            else rows = result
        } catch (_: Exception) {
            coroutineContext.ensureActive() // Stop normally when leaving the screen.
            error = "Failed to retrieve profiles."
        } finally {
            loading = false
        }
    }
    if (loading) {
        CircularProgressIndicator()
    } else if (error.isNotEmpty()) {
        Text(error)
    } else if (rows.isEmpty()) {
        Text("No profiles found.")
    } else {
        LazyColumn {
            items(rows) { user ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(text = user.name)
                        Text(text = user.email)
                        Text(
                            text = when {
                                user.isAdmin -> "Admin"
                                user.isOwner -> "Campsite Owner"
                                else -> "Entrant"
                            }
                        )
                    }
                }
            }
        }
    }
}