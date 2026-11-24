package com.example.campsitereservationapp.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.notification.AdminNotificationLog
import com.example.campsitereservationapp.notification.AdminNotificationLogService
import com.example.campsitereservationapp.notification.NotificationLogType
import com.example.campsitereservationapp.notification.filterNotificationLogs
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

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.surfaceBright,
                    0.60f to PinedrawColors.Background
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = PinedrawColors.Glass,
                    contentColor = PinedrawColors.Text
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Admin Dashboard")
            }
            Text("Notification logs", style = MaterialTheme.typography.headlineLarge)
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search title, sender or recipient") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = CircleShape
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
            failed -> {
                Text("Could not load notification logs.", color = PinedrawColors.Coral)
                TextButton(onClick = { refresh++ }) { Text("Try again") }
            }
            visibleLogs == null -> Box(
                Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PinedrawColors.Lime)
            }
            visibleLogs.isEmpty() -> Text(
                "No notifications found",
                color = PinedrawColors.TextSecondary
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(visibleLogs, key = { it.id }) { NotificationLogCard(it) }
            }
        }
    }
}

@Composable
private fun NotificationTypeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) }
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PinedrawColors.Surface)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (log.type == NotificationLogType.SYSTEM) "SYSTEM" else "OWNER",
                    color = PinedrawColors.Lime,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(date, color = PinedrawColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Text(log.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${log.sender} → $recipientText", color = PinedrawColors.TextSecondary)
            Text(log.message, color = PinedrawColors.Text)
            Text(log.campsite, color = PinedrawColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
