package com.example.campsitereservationapp.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class SentNotification(
    val id: String,
    val title: String,
    val message: String,
    val recipientCount: Int,
    val campsiteName: String,
    val createdAt: Date?
)

internal fun newestNotificationsFirst(notifications: List<SentNotification>): List<SentNotification> =
    notifications.sortedByDescending { it.createdAt?.time ?: Long.MIN_VALUE }

internal class OwnerNotificationHistoryService(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    suspend fun getSentNotifications(): List<SentNotification> {
        val ownerId = auth.currentUser?.uid ?: error("Please sign in.")
        val result = db.collection("notifications")
            .whereEqualTo("senderId", ownerId)
            .get()
            .await()

        return newestNotificationsFirst(result.documents.map { document ->
            SentNotification(
                id = document.id,
                title = document.getString("title").orEmpty(),
                message = document.getString("message").orEmpty(),
                recipientCount = (document.get("recipientIds") as? List<*>)?.size ?: 0,
                campsiteName = document.getString("campsiteName").orEmpty(),
                createdAt = document.getTimestamp("createdAt")?.toDate()
            )
        })
    }
}

@Composable
internal fun SentNotificationHistory(
    campsiteId: String,
    onBack: () -> Unit,
    onNewNotification: () -> Unit,
    loadNotifications: suspend () -> List<SentNotification> = {
        OwnerNotificationHistoryService().getSentNotifications()
    }
) {
    var notifications by remember(campsiteId) { mutableStateOf<List<SentNotification>>(emptyList()) }
    var loading by remember(campsiteId) { mutableStateOf(true) }
    var error by remember(campsiteId) { mutableStateOf("") }
    var refresh by remember(campsiteId) { mutableIntStateOf(0) }

    LaunchedEffect(campsiteId, refresh) {
        loading = true
        error = ""
        try {
            notifications = loadNotifications()
        } catch (_: Exception) {
            error = "Could not load sent notifications."
        } finally {
            loading = false
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(PinedrawColors.Surface, RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HistoryIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Sent notifications",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            HistoryIconButton(onClick = onNewNotification) {
                Icon(Icons.Default.Add, contentDescription = "New notification")
            }
        }

        Surface(color = PinedrawColors.Lime, contentColor = PinedrawColors.Ink, shape = CircleShape) {
            Text(
                "All lotteries",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }

        when {
            loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PinedrawColors.Lime)
            }
            error.isNotEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error, color = PinedrawColors.Coral)
                TextButton(onClick = { refresh++ }) { Text("Try again") }
            }
            notifications.isEmpty() -> Text(
                "No sent notifications yet.",
                color = PinedrawColors.TextSecondary,
                modifier = Modifier.padding(vertical = 18.dp)
            )
            else -> notifications.forEach { SentNotificationCard(it) }
        }
    }
}

@Composable
private fun HistoryIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(PinedrawColors.Glass),
        content = content
    )
}

@Composable
private fun SentNotificationCard(notification: SentNotification) {
    val audience = if (notification.recipientCount == 1) "1 entrant" else "${notification.recipientCount} entrants"
    val date = notification.createdAt?.let {
        SimpleDateFormat("MMM d · h:mm a", Locale.CANADA).format(it)
    }.orEmpty()

    Surface(color = PinedrawColors.Glass, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(notification.title, fontWeight = FontWeight.Bold)
            Text(
                notification.message,
                color = PinedrawColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "$audience · ${notification.campsiteName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = PinedrawColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (date.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(date, style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextMuted)
                }
            }
        }
    }
}
