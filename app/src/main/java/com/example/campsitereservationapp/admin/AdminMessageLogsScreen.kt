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
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.service.MessageService
import com.example.campsitereservationapp.service.OwnerMessageLog
import com.example.campsitereservationapp.ui.theme.PinedrawColors
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

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to MaterialTheme.colorScheme.surfaceBright, 0.60f to PinedrawColors.Background
        )).statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = PinedrawColors.Glass, contentColor = PinedrawColors.Text
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Admin Dashboard")
            }
            Text("Message Logs", style = MaterialTheme.typography.headlineLarge)
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search people, campsite or text") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = CircleShape
        )
        val query = search.trim()
        val entries = logs?.filter { log ->
            listOf(log.owner, log.entrant, log.campsite, log.text).any { it.contains(query, ignoreCase = true) }
        }
        when {
            failed -> {
                Text("Could not load message logs.", color = PinedrawColors.Coral)
                TextButton(onClick = { refresh++ }) { Text("Try again") }
            }
            entries == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            entries.isEmpty() -> Text("No messages found", color = PinedrawColors.TextSecondary)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(entries, key = { it.id }) { OwnerMessageCard(it) }
            }
        }
    }
}

// Shows who sent the message, who received it and the reservation's campsite.
@Composable
private fun OwnerMessageCard(log: OwnerMessageLog) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = PinedrawColors.Surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${log.owner} → ${log.entrant}", style = MaterialTheme.typography.titleLarge)
            Text(log.campsite, color = PinedrawColors.TextSecondary)
            Surface(shape = MaterialTheme.shapes.medium, color = PinedrawColors.Background) {
                Text(log.text, Modifier.fillMaxWidth().padding(12.dp), color = PinedrawColors.Text)
            }
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(log.sentAt.toDate()),
                color = PinedrawColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
