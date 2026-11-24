package com.example.campsitereservationapp.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.service.MessageService
import com.example.campsitereservationapp.service.OwnerMessageLog
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Search
import com.example.campsitereservationapp.ui.components.EmptyState
import com.example.campsitereservationapp.ui.components.ErrorState
import com.example.campsitereservationapp.ui.components.GlassCard
import com.example.campsitereservationapp.ui.components.LoadingState
import com.example.campsitereservationapp.ui.components.PinedrawScreen
import com.example.campsitereservationapp.ui.components.PinedrawTextField
import com.example.campsitereservationapp.ui.theme.PinedrawRadius
import kotlinx.coroutines.CancellationException
import java.text.DateFormat

// Shows the owner message log and filters it by name, campsite or message text.
@Composable
internal fun AdminMessageLogsScreen(onBack: () -> Unit) {
    val service = remember { MessageService() }
    var logs by remember { mutableStateOf<List<OwnerMessageLog>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(refresh) {
        failed = false
        try {
            logs = service.getOwnerMessageLogs()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            failed = true
        }
    }

    PinedrawScreen(
        title = "Message Logs",
        onBack = onBack,
        backLabel = "Back to Admin Dashboard",
        scrollable = false,
        modifier = Modifier.imePadding()
    ) {
        PinedrawTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = "Search people, campsite or text",
            leadingIcon = Icons.Rounded.Search
        )
        val query = search.trim()
        val entries = logs?.filter { log ->
            listOf(log.owner, log.entrant, log.campsite, log.text).any { it.contains(query, ignoreCase = true) }
        }
        when {
            failed -> ErrorState("Could not load message logs.", onRetry = { refresh++ })
            entries == null -> LoadingState()
            entries.isEmpty() -> EmptyState(Icons.AutoMirrored.Rounded.Chat, "No messages found")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(entries, key = { it.id }) { OwnerMessageCard(it) }
            }
        }
    }
}

// Shows who sent the message, who received it and the reservation's campsite.
@Composable
private fun OwnerMessageCard(log: OwnerMessageLog) {
    GlassCard(spacing = 8.dp) {
        run {
            Text("${log.owner} → ${log.entrant}", style = MaterialTheme.typography.titleMedium)
            Text(log.campsite, style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary)
            Surface(shape = PinedrawRadius.Medium, color = PinedrawColors.Background.copy(alpha = 0.6f)) {
                Text(log.text, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = PinedrawColors.Text)
            }
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(log.sentAt.toDate()),
                color = PinedrawColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
