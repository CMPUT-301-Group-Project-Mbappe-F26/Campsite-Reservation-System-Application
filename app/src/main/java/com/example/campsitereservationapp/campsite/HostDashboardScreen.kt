package com.example.campsitereservationapp.campsite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campsitereservationapp.ui.theme.PinedrawColors

/**
 * Shows the host registration and campsite list buttons.
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
            text = "Host Dashboard",
            color = PinedrawColors.Text,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Manage your campsites and listings.",
            color = PinedrawColors.TextSecondary,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onRegisterClick,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = PinedrawColors.Lime,
                contentColor = PinedrawColors.Ink
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("Register Campsite", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onMyCampsitesClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PinedrawColors.Lime),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = CircleShape
        ) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("My Campsites", fontWeight = FontWeight.Bold)
        }
    }
}
