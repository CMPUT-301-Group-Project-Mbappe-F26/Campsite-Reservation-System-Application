package com.example.campsitereservationapp.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.ui.theme.PinedrawColors

// Shows account information and the switch to host mode.
@Composable
fun ProfileScreen(
    user: User,
    onEditClick: () -> Unit,
    onHostClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF1E3A26),
                    0.45f to PinedrawColors.Background,
                    1f to PinedrawColors.Background
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Profile",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            color = PinedrawColors.Text
        )

        Spacer(Modifier.height(20.dp))

        // User information
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .border(2.5.dp, PinedrawColors.Lime, CircleShape)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF2E8C86), Color(0xFF1F5C73)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.name.firstOrNull()?.uppercase() ?: "P",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PinedrawColors.Text
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = user.name.ifBlank { "Pinedraw Guest" },
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = PinedrawColors.Text
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = user.city.ifBlank { "Welcome to Pinedraw" },
                fontSize = 14.sp,
                color = PinedrawColors.TextSecondary
            )
        }

        Spacer(Modifier.height(24.dp))

        // Host mode
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onHostClick() }
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFF3A2F17), Color(0xFF1C2318)))
                )
                .border(1.dp, PinedrawColors.Amber.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Home, contentDescription = null, tint = PinedrawColors.Amber)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Switch to Host mode",
                    color = PinedrawColors.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "List campsites and run your own draws",
                    color = PinedrawColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
            Chevron(color = PinedrawColors.Amber)
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("ACCOUNT")
        OptionGroup {
            ProfileOption("Personal information", Icons.Default.Person, onClick = onEditClick)
            OptionDivider()
            ProfileOption("Notifications", Icons.Default.Notifications) {}
            OptionDivider()
            ProfileOption("Policy & guidelines", Icons.Default.Info) {}
            OptionDivider()
            ProfileOption("Sign-in & security", Icons.Default.Lock) {}
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("SETTINGS")
        OptionGroup {
            ProfileOption("Settings", Icons.Default.Settings) {}
        }
        Spacer(Modifier.height(16.dp))
    }
}

// Shows a heading above a group of profile options.
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = PinedrawColors.TextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
    )
}

// Groups profile options inside a rounded card.
@Composable
private fun OptionGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(PinedrawColors.Glass)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(24.dp)),
        content = content
    )
}

// Shows a clickable profile option.
@Composable
private fun ProfileOption(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = PinedrawColors.TextSecondary)
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            color = PinedrawColors.Text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Chevron(color = PinedrawColors.TextMuted)
    }
}

// Shows the arrow beside a profile option.
@Composable
private fun Chevron(color: Color) {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null, tint = color, modifier = Modifier.size(22.dp)
    )
}

// Separates profile options with a line.
@Composable
private fun OptionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 18.dp),
        thickness = 1.dp,
        color = Color.White.copy(alpha = 0.06f)
    )
}
