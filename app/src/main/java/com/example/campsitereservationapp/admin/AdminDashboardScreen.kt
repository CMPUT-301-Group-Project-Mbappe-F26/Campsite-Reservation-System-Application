package com.example.campsitereservationapp.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Cabin
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.ui.components.GlassCard
import com.example.campsitereservationapp.ui.components.IconBadge
import com.example.campsitereservationapp.ui.components.PinedrawScreen
import com.example.campsitereservationapp.ui.components.StatusChip
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawDimens

/**
 * Shows the admin tools the signed-in admin can open.
 *
 * @param onBack Goes back to the previous screen.
 * @param onCampsitesClick Opens the campsite management screen.
 * @param onMessageLogsClick Opens the owner message logs.
 * @param onNotificationLogsClick Opens the notification audit log.
 * @param onProfilesClick Opens the profile management screen.
 * @param onImagesClick Opens the uploaded images screen.
 */
@Composable
fun AdminDashboardScreen(
    onBack: () -> Unit,
    onCampsitesClick: () -> Unit,
    onMessageLogsClick: (() -> Unit)? = null,
    onNotificationLogsClick: (() -> Unit)? = null,
    onProfilesClick: (() -> Unit)? = null,
    onImagesClick: (() -> Unit)? = null
) {
    PinedrawScreen(title = "Admin Dashboard", onBack = onBack, backLabel = "Back to Profile") {
        StatusChip("Admin console", color = PinedrawColors.Coral, icon = Icons.Rounded.Shield)
        Text("Moderation", style = MaterialTheme.typography.displayLarge, color = PinedrawColors.Text)
        Text(
            "Review and moderate content across Pinedraw.",
            style = MaterialTheme.typography.bodyLarge,
            color = PinedrawColors.TextSecondary
        )
        AdminAction("Campsites", "Browse every listing and remove ones that break policy.", Icons.Rounded.Cabin, PinedrawColors.Lime, onCampsitesClick)
        if (onProfilesClick != null) {
            AdminAction("Profiles", "Browse every account and remove ones that break policy.", Icons.Rounded.Groups, PinedrawColors.Teal, onProfilesClick)
        }
        if (onImagesClick != null) {
            AdminAction("Images", "Browse uploaded campsite and profile photos.", Icons.Rounded.Image, PinedrawColors.Coral, onImagesClick)
        }
        if (onMessageLogsClick != null) {
            AdminAction("Message Logs", "Review conversations between owners and guests.", Icons.AutoMirrored.Rounded.Chat, PinedrawColors.Sky, onMessageLogsClick)
        }
        if (onNotificationLogsClick != null) {
            AdminAction("Notification Logs", "Review all sent notifications.", Icons.Rounded.Notifications, PinedrawColors.Amber, onNotificationLogsClick)
        }
    }
}

@Composable
private fun AdminAction(title: String, description: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    GlassCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint = tint, size = 52.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = PinedrawColors.Text)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = PinedrawColors.TextSecondary)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = PinedrawColors.TextMuted, modifier = Modifier.size(PinedrawDimens.IconSize))
        }
    }
}
