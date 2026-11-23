package com.example.campsitereservationapp.qr

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.example.campsitereservationapp.ui.components.ButtonStyle
import com.example.campsitereservationapp.ui.components.InlineMessage
import com.example.campsitereservationapp.ui.components.PinedrawButton
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawRadius
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

/**
 * Displays a campsite's QR code in a popup.
 *
 * @param campsiteId The ID of the campsite.
 * @param campsiteName The name of the campsite.
 * @param onClose Called when the user closes the popup.
 */
@Composable
fun CampsiteQrDialog(
    campsiteId: String,
    campsiteName: String,
    onClose: () -> Unit
) {

    // Generate the QR code for the selected campsite
    val qrCode = remember(campsiteId) {
        try {
            CampsiteQr.generateQrCode(campsiteId)
        } catch (e: Exception) {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = PinedrawColors.Surface,
        shape = PinedrawRadius.XLarge,
        icon = { Icon(Icons.Rounded.QrCode2, null, tint = PinedrawColors.Lime, modifier = Modifier.size(32.dp)) },
        title = {
            Text("Campsite QR Code", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(campsiteName, style = MaterialTheme.typography.titleMedium, color = PinedrawColors.Text, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                if (qrCode != null) {
                    Image(
                        bitmap = qrCode.asImageBitmap(),
                        contentDescription = "QR code for $campsiteName",
                        modifier = Modifier.fillMaxWidth().clip(PinedrawRadius.Large).background(Color.White).padding(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Scan this QR code to view the campsite.", style = MaterialTheme.typography.bodyMedium,
                        color = PinedrawColors.TextSecondary, textAlign = TextAlign.Center)
                } else {
                    InlineMessage("Could not generate QR code.")
                }
            }
        },
        confirmButton = {
            PinedrawButton("Close", onClose, Modifier.fillMaxWidth(), style = ButtonStyle.Ghost, compact = true)
        }
    )
}
