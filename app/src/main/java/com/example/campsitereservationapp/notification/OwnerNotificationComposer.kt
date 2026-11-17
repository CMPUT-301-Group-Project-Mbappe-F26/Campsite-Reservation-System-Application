package com.example.campsitereservationapp.notification

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun OwnerNotificationComposer(
    campsiteId: String,
    campsiteName: String,
    lottery: Lottery,
    sendNotification: suspend (String, String) -> Unit = { title, message ->
        OwnerNotificationService().send(campsiteId, lottery.id, title, message)
    }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recipientCount = lottery.registered.filter { it.isNotBlank() }.distinct().size
    var editing by rememberSaveable(campsiteId, lottery.id) { mutableStateOf(false) }
    var title by rememberSaveable(campsiteId, lottery.id) { mutableStateOf("") }
    var message by rememberSaveable(campsiteId, lottery.id) { mutableStateOf("") }
    var confirming by rememberSaveable(campsiteId, lottery.id) { mutableStateOf(false) }
    var sending by remember(campsiteId, lottery.id) { mutableStateOf(false) }
    var error by remember(campsiteId, lottery.id) { mutableStateOf("") }

    fun closeForm() {
        editing = false
        confirming = false
        title = ""
        message = ""
        error = ""
    }

    if (!editing) {
        OutlinedButton(
            onClick = { editing = true },
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape
        ) {
            Text("Notify entrants", fontWeight = FontWeight.SemiBold)
        }
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = PinedrawColors.Surface,
            contentColor = PinedrawColors.Text
        )
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = ::closeForm,
                    enabled = !sending,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(PinedrawColors.Glass)
                        .testTag("closeNotification")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
                Spacer(Modifier.width(14.dp))
                Text("New notification", style = MaterialTheme.typography.titleLarge)
            }

            Surface(color = PinedrawColors.Glass, shape = CircleShape) {
                Text(
                    campsiteName,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text("Send to", style = MaterialTheme.typography.labelMedium, color = PinedrawColors.TextSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecipientOption("Everyone", "$recipientCount entrants", true, Modifier.weight(1f))
                RecipientOption("Winners", "After the draw", false, Modifier.weight(1f))
                RecipientOption("Custom", "Pick people", false, Modifier.weight(1f))
            }

            Text("Title", style = MaterialTheme.typography.labelMedium, color = PinedrawColors.TextSecondary)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(MAX_NOTIFICATION_TITLE); error = "" },
                enabled = !sending,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("notificationTitle")
            )

            Text("Message", style = MaterialTheme.typography.labelMedium, color = PinedrawColors.TextSecondary)
            OutlinedTextField(
                value = message,
                onValueChange = { message = it.take(MAX_NOTIFICATION_MESSAGE); error = "" },
                enabled = !sending,
                minLines = 4,
                supportingText = { Text("${message.length} / $MAX_NOTIFICATION_MESSAGE") },
                modifier = Modifier.fillMaxWidth().testTag("notificationMessage")
            )

            Text("Preview", style = MaterialTheme.typography.labelMedium, color = PinedrawColors.TextSecondary)
            NotificationPreview(title, message)

            if (error.isNotEmpty()) Text(error, color = PinedrawColors.Coral)
            Button(
                onClick = {
                    val validationError = ownerNotificationError(title, message, recipientCount)
                    if (validationError == null) confirming = true else error = validationError
                },
                enabled = !sending,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PinedrawColors.Lime,
                    contentColor = PinedrawColors.Ink
                )
            ) {
                val entrants = if (recipientCount == 1) "entrant" else "entrants"
                Text(
                    if (sending) "Sending..." else "Send to $recipientCount $entrants",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text("Are you sure?") },
        text = { Text("Send this notification to everyone in the waiting list?") },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        confirmButton = {
            TextButton(onClick = {
                confirming = false
                sending = true
                error = ""
                scope.launch {
                    try {
                        val sent = withTimeoutOrNull(20000) {
                            sendNotification(title.trim(), message.trim())
                            true
                        }
                        if (sent == null) error = "Could not send the notification. Please try again."
                        else {
                            closeForm()
                            Toast.makeText(context, "Notification sent", Toast.LENGTH_SHORT).show()
                        }
                    } catch (exception: Exception) {
                        coroutineContext.ensureActive()
                        error = exception.message?.takeIf { it.isNotBlank() }
                            ?: "Could not send the notification. Please try again."
                    } finally {
                        sending = false
                    }
                }
            }) { Text("Proceed") }
        }
    )
}

@Composable
private fun RecipientOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) PinedrawColors.Lime else Color.Transparent
    Column(
        modifier
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .background(PinedrawColors.Glass, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(
            title,
            fontWeight = FontWeight.Bold,
            color = if (selected) PinedrawColors.Text else PinedrawColors.TextMuted
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = PinedrawColors.TextMuted,
            maxLines = 1
        )
    }
}

@Composable
private fun NotificationPreview(title: String, message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.12f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("PINEDRAW", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("now", style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary)
            }
            Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = PinedrawColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
