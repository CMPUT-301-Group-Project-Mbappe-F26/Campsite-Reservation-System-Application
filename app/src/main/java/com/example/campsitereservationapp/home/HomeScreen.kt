package com.example.campsitereservationapp.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.ui.theme.PinedrawColors

// Shows the welcome screen and profile shortcut.
@Composable
fun HomeScreen(
    user: User,
    onProfileClick: () -> Unit
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
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Welcome back,",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PinedrawColors.TextSecondary
                )

                Text(
                    text = user.name.ifBlank { "Explorer" },
                    style = MaterialTheme.typography.headlineLarge,
                    color = PinedrawColors.Text
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .border(2.dp, PinedrawColors.Lime, CircleShape)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2E8C86), Color(0xFF1F5C73))
                        )
                    )
                    .clickable { onProfileClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.name.firstOrNull()?.uppercase() ?: "P",
                    style = MaterialTheme.typography.titleLarge,
                    color = PinedrawColors.Text
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Find your next adventure.",
            style = MaterialTheme.typography.headlineLarge,
            color = PinedrawColors.Lime
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Discover campsites and explore new places.",
            style = MaterialTheme.typography.bodyLarge,
            color = PinedrawColors.TextSecondary
        )
    }
}
