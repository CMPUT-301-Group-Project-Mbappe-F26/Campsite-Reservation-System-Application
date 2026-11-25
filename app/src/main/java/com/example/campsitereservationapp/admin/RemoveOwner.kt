package com.example.campsitereservationapp.admin

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.ui.components.ButtonStyle
import com.example.campsitereservationapp.ui.components.PinedrawButton
import com.example.campsitereservationapp.ui.components.PinedrawDialog
import com.example.campsitereservationapp.ui.theme.PinedrawColors

/*
 * The Remove owner button and its confirmation on an admin's view of a campsite owner's
 * profile (US 03.07.01). The removal itself is done by OwnerRemovalService.
 *
 * Outstanding issues: the warning cannot say how many campsites will be removed, because
 * the profile screen does not load the owner's campsites.
 */

/**
 * Shows Remove owner, and asks the admin to confirm before calling [onConfirm].
 *
 * @param user The campsite owner being viewed.
 * @param removing True while the removal is running; the button shows progress.
 * @param onConfirm Removes the owner after the admin confirms.
 */
@Composable
fun RemoveOwnerButton(user: User, removing: Boolean, onConfirm: () -> Unit) {
    var confirming by remember(user.id) { mutableStateOf(false) }
    PinedrawButton(
        if (removing) "Removing owner..." else "Remove owner",
        onClick = { confirming = true },
        modifier = Modifier.fillMaxWidth(),
        style = ButtonStyle.SoftDestructive,
        icon = Icons.Rounded.Gavel,
        enabled = !removing,
        loading = removing
    )
    if (confirming) {
        PinedrawDialog(
            title = "Remove ${profileName(user)} as an owner?",
            message = REMOVE_OWNER_WARNING,
            icon = Icons.Rounded.Gavel,
            iconTint = PinedrawColors.Coral,
            confirmText = "Remove owner",
            confirmStyle = ButtonStyle.Destructive,
            onDismiss = { confirming = false },
            onConfirm = {
                confirming = false
                onConfirm()
            }
        )
    }
}

/** Warning shown before an owner is removed. */
internal const val REMOVE_OWNER_WARNING =
    "All of their campsites will be removed. Upcoming reservations at those campsites will be " +
        "cancelled and the entrants notified. Their profile stays, but they will no longer be a " +
        "campsite owner. This cannot be undone."
