package com.example.campsitereservationapp.admin

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.campsite.rememberCampsiteOwnerId
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.service.CampsiteService
import com.example.campsitereservationapp.service.UserService
import com.example.campsitereservationapp.service.withOwnerRoles
import com.example.campsitereservationapp.service.adminCanRemoveProfile
import com.example.campsitereservationapp.ui.components.ButtonStyle
import com.example.campsitereservationapp.ui.components.CircleIconButton
import com.example.campsitereservationapp.ui.components.EmptyState
import com.example.campsitereservationapp.ui.components.ErrorState
import com.example.campsitereservationapp.ui.components.GlassCard
import com.example.campsitereservationapp.ui.components.InitialsAvatar
import com.example.campsitereservationapp.ui.components.LoadingState
import com.example.campsitereservationapp.ui.components.PinedrawButton
import com.example.campsitereservationapp.ui.components.PinedrawDialog
import com.example.campsitereservationapp.ui.components.PinedrawScreen
import com.example.campsitereservationapp.ui.components.PinedrawTextField
import com.example.campsitereservationapp.ui.components.StatusChip
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Lets an admin browse all registered profiles (US 03.05.01) and remove profiles (US 03.02.01).
 *
 * [AdminProfilesScreen] loads profiles from Firebase and removes them. [AdminProfileList] only
 * draws them, so intent tests can check it without Firebase.
 *
 * Outstanding issues: a removed user's sign-in account stays in Firebase Authentication; they
 * are signed out the next time the app checks their profile.
 */

/**
 * Loads every profile for the admin and removes one after the admin confirms.
 *
 * @param onBack Goes back to the Admin Dashboard.
 * @param onProfileClick Opens the selected profile's details.
 */
@Composable
fun AdminProfilesScreen(
    onBack: () -> Unit,
    onProfileClick: (String) -> Unit
) {
    val userService = remember { UserService() }
    val campsiteService = remember { CampsiteService() }
    val adminId = rememberCampsiteOwnerId().orEmpty()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<User>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var removingId by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh) {
        loading = true
        error = ""
        try {
            val result = withTimeoutOrNull(20000) {
                // Anyone who has listed a campsite is an owner (see withOwnerRoles).
                withOwnerRoles(userService.getAllUsers(), campsiteService.getAllCampsites())
            }
            if (result == null) error = "Could not load profiles. Please try again."
            else rows = result.sortedBy { it.name.ifBlank { "~" }.lowercase() }
        } catch (_: Exception) {
            coroutineContext.ensureActive() // Stop normally when leaving the screen.
            error = "Could not load profiles. Please try again."
        } finally {
            loading = false
        }
    }

    PinedrawScreen(
        title = "Profiles",
        onBack = onBack,
        backLabel = "Admin Dashboard",
        scrollable = false,
        actions = { CircleIconButton(Icons.Rounded.Refresh, "Refresh", { refresh++ }, enabled = !loading && removingId == null) }
    ) {
        when {
            loading -> LoadingState()
            error.isNotEmpty() -> ErrorState(error, onRetry = { refresh++ })
            else -> AdminProfileList(
                rows = rows,
                currentUserId = adminId,
                removingId = removingId,
                onProfileClick = onProfileClick,
                onRemove = { user ->
                    removingId = user.id
                    scope.launch {
                        val name = profileName(user)
                        val message = try {
                            val removed = withTimeoutOrNull(30000) { userService.adminDeleteProfile(user.id) }
                            if (removed == null) "Removing $name timed out. Please try again."
                            else {
                                rows = rows.filter { it.id != user.id }
                                "$name was removed."
                            }
                        } catch (exception: Exception) {
                            coroutineContext.ensureActive() // Stop normally when leaving the screen.
                            profileRemovalError(name, exception)
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
 * Shows every profile with a search box and a Remove button on each one except the admin's own.
 *
 * Remove opens a confirmation, and [onRemove] is only called after the admin confirms.
 * Shows "No profiles found" when there are none.
 *
 * @param rows Profiles to show.
 * @param currentUserId The signed-in admin, whose profile cannot be removed here.
 * @param removingId ID of a profile being removed; Remove buttons are disabled meanwhile.
 * @param onProfileClick Opens the tapped profile.
 * @param onRemove Removes the confirmed profile.
 */
@Composable
fun AdminProfileList(
    rows: List<User>,
    currentUserId: String,
    removingId: String?,
    onProfileClick: (String) -> Unit,
    onRemove: (User) -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    var confirming by remember { mutableStateOf<User?>(null) }
    val query = search.trim()
    val shown = rows.filter { user ->
        query.isBlank() || listOf(user.name, user.email, user.city).any { it.contains(query, ignoreCase = true) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Medium)) {
        PinedrawTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = "Search name, email or city",
            leadingIcon = Icons.Rounded.Search
        )
        Text(
            "${rows.size} ${if (rows.size == 1) "profile" else "profiles"}",
            style = MaterialTheme.typography.bodyMedium,
            color = PinedrawColors.TextMuted
        )
        if (shown.isEmpty()) {
            EmptyState(Icons.Rounded.Groups, "No profiles found")
        } else LazyColumn(
            verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Medium),
            contentPadding = PaddingValues(bottom = PinedrawSpacing.Screen)
        ) {
            items(shown, key = { it.id }) { user ->
                val self = user.id == currentUserId
                GlassCard(onClick = { onProfileClick(user.id) }, contentPadding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InitialsAvatar(profileName(user), size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(profileName(user), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                user.email.ifBlank { "No email" },
                                style = MaterialTheme.typography.bodySmall,
                                color = PinedrawColors.TextSecondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                RoleChip(user)
                                if (self) StatusChip("You", color = PinedrawColors.Sky, showDot = false)
                            }
                        }
                        if (!self) PinedrawButton(
                            if (removingId == user.id) "Removing..." else "Remove",
                            onClick = { confirming = user },
                            enabled = removingId == null && adminCanRemoveProfile(currentUserId, user.id),
                            style = ButtonStyle.SoftDestructive,
                            compact = true
                        )
                    }
                }
            }
        }
    }

    confirming?.let { user ->
        RemoveProfileDialog(user, onDismiss = { confirming = null }, onConfirm = {
            confirming = null
            onRemove(user)
        })
    }
}

/** Confirmation shared by the profile list and the profile details. */
@Composable
internal fun RemoveProfileDialog(user: User, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    PinedrawDialog(
        title = "Remove ${profileName(user)}?",
        message = "They will be taken off all waiting lists and their active reservations will be cancelled. This cannot be undone.",
        icon = Icons.Rounded.PersonRemove,
        iconTint = PinedrawColors.Coral,
        confirmText = "Remove",
        confirmStyle = ButtonStyle.Destructive,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

/** Admin, Campsite Owner or Entrant badge. */
@Composable
internal fun RoleChip(user: User) {
    when {
        user.isAdmin -> StatusChip("Admin", color = PinedrawColors.Coral, showDot = false)
        user.isOwner -> StatusChip("Campsite Owner", color = PinedrawColors.Amber, showDot = false)
        else -> StatusChip("Entrant", color = PinedrawColors.Lime, showDot = false)
    }
}

/** The profile's name, or a readable fallback for guests who never set one. */
internal fun profileName(user: User): String = user.name.ifBlank { "Unnamed profile" }

/**
 * Turns a failed removal into a message for the admin.
 *
 * @param name Name of the profile being removed.
 * @param exception What went wrong.
 */
internal fun profileRemovalError(name: String, exception: Exception): String =
    if (exception is IllegalStateException && !exception.message.isNullOrBlank()) exception.message!!
    else "Could not remove $name. Please try again."
