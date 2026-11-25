package com.example.campsitereservationapp.profile

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.ui.components.IconBadge
import com.example.campsitereservationapp.ui.components.ListGroup
import com.example.campsitereservationapp.ui.components.ListRow
import com.example.campsitereservationapp.ui.components.OverlineLabel
import com.example.campsitereservationapp.ui.components.PageHeader
import com.example.campsitereservationapp.ui.components.PinedrawBackground
import com.example.campsitereservationapp.ui.components.RowDivider
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawDimens
import com.example.campsitereservationapp.ui.theme.PinedrawRadius
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing

/**
 * Profile tab: photo, name, the switch to Host mode, account settings and, for admins,
 * a link to the Admin Dashboard.
 */
@Composable
fun ProfileScreen(
    user: User,
    onEditClick: () -> Unit,
    onHostClick: () -> Unit = {},
    onAdminClick: () -> Unit = {},
    onPolicyClick: () -> Unit = {},
    savingNotifications: Boolean = false,
    onNotificationsChanged: (Boolean) -> Unit = {},
    onSaveImage: suspend (Uri?) -> Unit = {}
) {
    PinedrawBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PinedrawSpacing.Gutter, vertical = PinedrawSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Gutter)
        ) {
            PageHeader("Profile")

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ProfileImageEditor(user = user, enabled = !savingNotifications, onSaveImage = onSaveImage)
                Spacer(Modifier.height(PinedrawSpacing.Small))
                Text(
                    user.name.ifBlank { "Pinedraw Guest" },
                    style = MaterialTheme.typography.headlineMedium,
                    color = PinedrawColors.Text,
                    textAlign = TextAlign.Center
                )
                if (user.city.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(user.city, style = MaterialTheme.typography.bodyMedium, color = PinedrawColors.TextSecondary)
                }
            }

            HostModeCard(onHostClick)

            if (user.isAdmin) {
                Column(verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Small)) {
                    OverlineLabel("Admin")
                    ListGroup {
                        ListRow("Admin Dashboard", Icons.Rounded.AdminPanelSettings, onAdminClick, tint = PinedrawColors.Coral)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Small)) {
                OverlineLabel("Account")
                ListGroup {
                    ListRow(
                        "Personal information", Icons.Rounded.Person, onEditClick,
                        enabled = !savingNotifications
                    )
                    RowDivider()
                    ListRow(
                        "Lottery notifications", Icons.Rounded.Notifications, onClick = null,
                        subtitle = "Draw results and owner updates",
                        trailing = {
                            Switch(
                                checked = user.notificationsEnabled,
                                onCheckedChange = onNotificationsChanged,
                                enabled = !savingNotifications,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = PinedrawColors.Text,
                                    checkedTrackColor = PinedrawColors.Lime,
                                    uncheckedThumbColor = PinedrawColors.TextSecondary,
                                    uncheckedTrackColor = PinedrawColors.Glass,
                                    uncheckedBorderColor = PinedrawColors.Border
                                ),
                                modifier = Modifier.semantics { contentDescription = "Lottery notifications" }
                            )
                        }
                    )
                    RowDivider()
                    ListRow("Policy & guidelines", Icons.Rounded.Description, onPolicyClick)
                }
            }
            Spacer(Modifier.height(PinedrawSpacing.Small))
        }
    }
}

// Amber card that switches the app into Host mode.
@Composable
private fun HostModeCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PinedrawRadius.Large)
            .background(Brush.horizontalGradient(listOf(Color(0xFF3A2F17), Color(0xFF1C2318))))
            .border(PinedrawDimens.BorderWidth, PinedrawColors.Amber.copy(alpha = 0.45f), PinedrawRadius.Large)
            .clickable(onClick = onClick)
            .padding(PinedrawDimens.CardPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(Icons.Rounded.SwapHoriz, tint = PinedrawColors.Amber, filled = true)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Switch to Host mode", style = MaterialTheme.typography.titleMedium, color = PinedrawColors.Text)
            Text("List campsites and run your own draws", style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary)
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = PinedrawColors.Amber,
            modifier = Modifier.size(PinedrawDimens.IconSize)
        )
    }
}
