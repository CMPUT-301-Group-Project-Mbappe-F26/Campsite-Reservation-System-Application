package com.example.campsitereservationapp.admin

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.campsite.rememberCampsiteOwnerId
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.profile.ProfileAvatar
import com.example.campsitereservationapp.service.CampsiteService
import com.example.campsitereservationapp.service.OwnerRemovalService
import com.example.campsitereservationapp.service.UserService
import com.example.campsitereservationapp.service.adminCanRemoveOwner
import com.example.campsitereservationapp.service.adminCanRemoveProfile
import com.example.campsitereservationapp.service.ownerRemovalSummary
import com.example.campsitereservationapp.service.withOwnerRoles
import com.example.campsitereservationapp.ui.components.ButtonStyle
import com.example.campsitereservationapp.ui.components.ErrorState
import com.example.campsitereservationapp.ui.components.GlassCard
import com.example.campsitereservationapp.ui.components.InfoRow
import com.example.campsitereservationapp.ui.components.LoadingState
import com.example.campsitereservationapp.ui.components.OverlineLabel
import com.example.campsitereservationapp.ui.components.PinedrawButton
import com.example.campsitereservationapp.ui.components.PinedrawScreen
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shows one profile's details to an admin, with a Remove button unless it is the admin's own,
 * and a Remove owner button on other campsite owners' profiles (US 03.07.01).
 *
 * @param userId ID of the profile to show.
 * @param onBack Goes back to the profile list.
 * @param onRemoved Called after the profile is removed; defaults to going back.
 */
@Composable
fun AdminProfileScreen(
    userId: String,
    onBack: () -> Unit,
    onRemoved: () -> Unit = onBack
) {
    val userService = remember { UserService() }
    val ownerRemovalService = remember { OwnerRemovalService() }
    val campsiteService = remember { CampsiteService() }
    val adminId = rememberCampsiteOwnerId().orEmpty()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var user by remember(userId) { mutableStateOf<User?>(null) }
    var loading by remember(userId) { mutableStateOf(true) }
    var error by remember(userId) { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var confirming by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var removingOwner by remember { mutableStateOf(false) }

    LaunchedEffect(userId, refresh) {
        loading = true
        error = ""
        try {
            val result = withTimeoutOrNull(20000) {
                // Anyone who has listed a campsite is an owner (see withOwnerRoles).
                withOwnerRoles(userService.getUsers(listOf(userId)), campsiteService.getAllCampsites())
            }
            when {
                result == null -> error = "Could not load this profile. Please try again."
                result.isEmpty() -> error = "This profile no longer exists."
                else -> user = result.first()
            }
        } catch (_: Exception) {
            coroutineContext.ensureActive() // Stop normally when leaving the screen.
            error = "Could not load this profile. Please try again."
        } finally {
            loading = false
        }
    }

    PinedrawScreen(title = "Profile", onBack = onBack, backLabel = "All profiles") {
        val shown = user
        when {
            loading -> LoadingState()
            error.isNotEmpty() -> ErrorState(error, onRetry = { refresh++ })
            shown != null -> AdminProfileDetails(
                user = shown,
                canRemove = adminCanRemoveProfile(adminId, shown.id),
                removing = removing,
                onRemove = { confirming = true },
                canRemoveOwner = adminCanRemoveOwner(adminId, shown),
                removingOwner = removingOwner,
                onRemoveOwner = {
                    removingOwner = true
                    scope.launch {
                        val name = profileName(shown)
                        try {
                            val result = withTimeoutOrNull(30000) { ownerRemovalService.removeOwner(shown.id) }
                            if (result == null) {
                                Toast.makeText(context, "Removing $name as an owner timed out. Please try again.", Toast.LENGTH_LONG).show()
                            } else {
                                user = shown.copy(isOwner = false)
                                Toast.makeText(context, ownerRemovalSummary(name, result), Toast.LENGTH_LONG).show()
                            }
                        } catch (exception: Exception) {
                            coroutineContext.ensureActive()
                            Toast.makeText(context, ownerRemovalError(name, exception), Toast.LENGTH_LONG).show()
                        } finally {
                            removingOwner = false
                        }
                    }
                }
            )
        }
    }

    val target = user
    if (confirming && target != null) RemoveProfileDialog(target, onDismiss = { confirming = false }, onConfirm = {
        confirming = false
        removing = true
        scope.launch {
            val name = profileName(target)
            try {
                val removed = withTimeoutOrNull(30000) { userService.adminDeleteProfile(target.id) }
                if (removed == null) {
                    Toast.makeText(context, "Removing $name timed out. Please try again.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "$name was removed.", Toast.LENGTH_LONG).show()
                    onRemoved()
                }
            } catch (exception: Exception) {
                coroutineContext.ensureActive()
                Toast.makeText(context, profileRemovalError(name, exception), Toast.LENGTH_LONG).show()
            } finally {
                removing = false
            }
        }
    })
}

/**
 * Draws a profile's photo, role, account and contact details, and the Remove button.
 *
 * @param user The profile to show.
 * @param canRemove False for the admin's own profile, which hides Remove.
 * @param removing True while the profile is being removed.
 * @param onRemove Asks to remove the profile.
 * @param canRemoveOwner True for another user's campsite owner profile, which shows Remove owner.
 * @param removingOwner True while the owner is being removed.
 * @param onRemoveOwner Removes the owner, called only after the admin confirms.
 */
@Composable
fun AdminProfileDetails(
    user: User,
    canRemove: Boolean,
    removing: Boolean,
    onRemove: () -> Unit,
    canRemoveOwner: Boolean = false,
    removingOwner: Boolean = false,
    onRemoveOwner: () -> Unit = {}
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileAvatar(name = profileName(user), imageUrl = user.profileImageUrl, modifier = Modifier.size(96.dp))
        Text(profileName(user), style = MaterialTheme.typography.headlineMedium, color = PinedrawColors.Text)
        RoleChip(user)
    }
    OverlineLabel("Account")
    GlassCard {
        InfoRow("ID", user.id)
        InfoRow("Notifications", if (user.notificationsEnabled) "On" else "Off")
    }
    OverlineLabel("Contact")
    GlassCard {
        InfoRow("Email", user.email.ifBlank { "Not provided" })
        InfoRow("Phone", user.phone.ifBlank { "Not provided" })
        InfoRow("City", user.city.ifBlank { "Not provided" })
    }
    if (canRemoveOwner) RemoveOwnerButton(user, removing = removingOwner, onConfirm = onRemoveOwner)
    if (canRemove) {
        PinedrawButton(
            if (removing) "Removing..." else "Remove profile",
            onClick = onRemove,
            modifier = Modifier.fillMaxWidth(),
            style = ButtonStyle.SoftDestructive,
            icon = Icons.Rounded.PersonRemove,
            loading = removing
        )
    } else {
        Text(
            "This is your profile. You can't remove it from here.",
            style = MaterialTheme.typography.bodyMedium,
            color = PinedrawColors.TextMuted
        )
    }
}

/**
 * Turns a failed owner removal into a message for the admin.
 *
 * @param name Name of the owner being removed.
 * @param exception What went wrong.
 */
internal fun ownerRemovalError(name: String, exception: Exception): String =
    if (exception is IllegalStateException && !exception.message.isNullOrBlank()) exception.message!!
    else "Could not remove $name as an owner. Please try again."
