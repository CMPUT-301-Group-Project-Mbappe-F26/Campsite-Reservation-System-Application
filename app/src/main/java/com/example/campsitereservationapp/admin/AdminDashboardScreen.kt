package com.example.campsitereservationapp.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campsitereservationapp.ui.theme.PinedrawColors

/*
 * Admin Dashboard, the starting point for admin tools (US 03.04.01).
 *
 * Only shown to users whose profile has isAdmin set. It opens [AdminCampsitesScreen].
 *
 * Outstanding issues: only campsite browsing and removal exist so far; profile, image
 * and log browsing (US 03.02 to 03.12) still need their own buttons here.
 */

// Shows the admin tools the signed-in admin can open.
@Composable
fun AdminDashboardScreen(
    onBack: () -> Unit,
    onCampsitesClick: () -> Unit
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
        TextButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Back to Profile", color = PinedrawColors.Lime)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Admin Dashboard",
            color = PinedrawColors.Text,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Review and moderate content across Pinedraw.",
            color = PinedrawColors.TextSecondary,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onCampsitesClick,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = PinedrawColors.Lime,
                contentColor = PinedrawColors.Ink
            )
        ) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("Campsites", fontWeight = FontWeight.Bold)
        }
    }
}
