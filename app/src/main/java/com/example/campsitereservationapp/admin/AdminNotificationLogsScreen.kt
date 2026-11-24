package com.example.campsitereservationapp.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.notification.AdminNotificationLog
import com.example.campsitereservationapp.notification.AdminNotificationLogService
import com.example.campsitereservationapp.notification.NotificationLogType
import com.example.campsitereservationapp.notification.filterNotificationLogs
import com.example.campsitereservationapp.ui.components.EmptyState
import com.example.campsitereservationapp.ui.components.ErrorState
import com.example.campsitereservationapp.ui.components.GlassCard
import com.example.campsitereservationapp.ui.components.LoadingState
import com.example.campsitereservationapp.ui.components.PinedrawScreen
import com.example.campsitereservationapp.ui.components.PinedrawTextField
import com.example.campsitereservationapp.ui.components.SelectChip
import com.example.campsitereservationapp.ui.components.StatusChip
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Locale

// Notification audit screen for issue #58. It follows the notification log wiki mockup.
@Composable
internal fun AdminNotificationLogsScreen(
    onBack: () -> Unit,
    loadLogs: suspend () -> List<AdminNotificationLog> = {
        AdminNotificationLogService().getLogs()
    }
) {
    var logs by remember { mutableStateOf<List<AdminNotificationLog>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf<NotificationLogType?>(null) }

    LaunchedEffect(refresh) {
        failed = false
        try {
            logs = loadLogs()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            failed = true
        }
    }

    PinedrawScreen(
        title = "Notification logs",
        onBack = onBack,
        backLabel = "Back to Admin Dashboard",
        scrollable = false,
        modifier = Modifier.imePadding()
    ) {
        PinedrawTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = "Search title, sender or recipient",
            leadingIcon = Icons.Rounded.Search
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NotificationTypeChip("All", selectedType == null) { selectedType = null }
            NotificationTypeChip("System", selectedType == NotificationLogType.SYSTEM) {
                selectedType = NotificationLogType.SYSTEM
            }
            NotificationTypeChip("Owner broadcasts", selectedType == NotificationLogType.OWNER) {
                selectedType = NotificationLogType.OWNER
            }
        }

        val visibleLogs = logs?.let { filterNotificationLogs(it, search, selectedType) }
        when {
            failed -> ErrorState("Could not load notification logs.", onRetry = { refresh++ })
            visibleLogs == null -> LoadingState()
            visibleLogs.isEmpty() -> EmptyState(Icons.Rounded.Notifications, "No notifications found")
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(visibleLogs, key = { it.id }) { NotificationLogCard(it) }
            }
        }
    }
}

@Composable
private fun NotificationTypeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    SelectChip(
        text = text,
        selected = selected,
        onClick = onClick
    )
}

// Keeps every acceptance-criteria field together in one simple log card.
@Composable
private fun NotificationLogCard(log: AdminNotificationLog) {
    val date = log.sentAt?.let {
        SimpleDateFormat("MMM d · HH:mm:ss", Locale.CANADA).format(it)
    }.orEmpty()
    val recipientText = when (log.recipients.size) {
        0 -> "No recipients"
        1 -> log.recipients.first()
        else -> "${log.recipients.size} entrants"
    }

    GlassCard(spacing = 6.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatusChip(
                if (log.type == NotificationLogType.SYSTEM) "SYSTEM" else "OWNER",
                color = if (log.type == NotificationLogType.SYSTEM) PinedrawColors.Lime else PinedrawColors.Amber
            )
            Text(date, color = PinedrawColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Text(log.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("${log.sender} → $recipientText", color = PinedrawColors.TextSecondary)
        Text(log.message, color = PinedrawColors.Text)
        Text(log.campsite, color = PinedrawColors.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}
