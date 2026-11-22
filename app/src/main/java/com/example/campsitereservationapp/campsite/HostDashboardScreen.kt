package com.example.campsitereservationapp.campsite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddHome
import androidx.compose.material.icons.rounded.Cabin
import androidx.compose.material.icons.rounded.SwapHoriz
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
 * Host mode home: register a new campsite or manage existing ones.
 *
 * @param onBack Goes back to the profile.
 * @param onRegisterClick Opens campsite registration.
 * @param onMyCampsitesClick Opens the user's campsites.
 */
@Composable
fun HostDashboardScreen(
    onBack: () -> Unit,
    onRegisterClick: () -> Unit,
    onMyCampsitesClick: () -> Unit
) {
    PinedrawScreen(title = "Host Dashboard", onBack = onBack, backLabel = "Back to Profile") {
        StatusChip("Host mode", color = PinedrawColors.Amber, icon = Icons.Rounded.SwapHoriz)
        Text("Share your campsite", style = MaterialTheme.typography.displayLarge, color = PinedrawColors.Text)
        Text(
            "List a site, run fair lottery draws and manage reservations in one place.",
            style = MaterialTheme.typography.bodyLarge,
            color = PinedrawColors.TextSecondary
        )
        HostAction(
            title = "Register Campsite",
            description = "Add photos, amenities, availability and rules.",
            icon = Icons.Rounded.AddHome,
            tint = PinedrawColors.Amber,
            onClick = onRegisterClick
        )
        HostAction(
            title = "My Campsites",
            description = "Edit listings, create lotteries and see bookings.",
            icon = Icons.Rounded.Cabin,
            tint = PinedrawColors.Lime,
            onClick = onMyCampsitesClick
        )
    }
}

@Composable
private fun HostAction(title: String, description: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    GlassCard(onClick = onClick, borderColor = tint.copy(alpha = 0.35f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint = tint, filled = true, size = 52.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = PinedrawColors.Text)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = PinedrawColors.TextSecondary)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = tint, modifier = Modifier.size(PinedrawDimens.IconSize))
        }
    }
}
