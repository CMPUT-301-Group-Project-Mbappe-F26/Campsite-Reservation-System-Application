package com.example.campsitereservationapp.qr

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
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
        title = {
            Text("Campsite QR Code")
        },
        text = {
            Column {
                Text(campsiteName)
                Spacer(modifier = Modifier.height(16.dp))
                // Display the QR code if generated successfully
                if (qrCode != null) {
                    Image(
                        bitmap = qrCode.asImageBitmap(),
                        contentDescription = "QR code for $campsiteName",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Scan this QR code to view the campsite.")
                } else {
                    // Show an error if QR code not generated
                    Text("Could not generate QR code.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Close")
            }
        }
    )
}