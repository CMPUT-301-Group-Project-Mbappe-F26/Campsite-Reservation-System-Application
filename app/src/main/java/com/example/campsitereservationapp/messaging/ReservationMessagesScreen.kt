package com.example.campsitereservationapp.messaging

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.model.Reservation
import com.example.campsitereservationapp.model.RESERVATION_CANCELLED
import com.example.campsitereservationapp.model.formatReservationDate
import com.example.campsitereservationapp.service.CampsiteService
import com.example.campsitereservationapp.service.MAX_MESSAGE_LENGTH
import com.example.campsitereservationapp.service.MessageService
import com.example.campsitereservationapp.service.ReservationMessage
import com.example.campsitereservationapp.service.ReservationService
import com.example.campsitereservationapp.service.UserService
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.text.DateFormat

// Shows the guests who booked this campsite and opens the selected reservation's chat.
@Composable
internal fun CampsiteMessagesScreen(campsiteId: String, onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val service = remember { MessageService() }
    var guests by remember(campsiteId) { mutableStateOf<List<Pair<Reservation, String>>?>(null) }
    var error by remember(campsiteId) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(campsiteId, retry) {
        error = false
        try {
            val reservations = service.getCampsiteReservations(campsiteId)
            val names = UserService().getUsers(reservations.map { it.entrantId }).associate { it.id to it.name }
            guests = reservations.map { it to names[it.entrantId].orEmpty().ifBlank { "Guest" } }
        } catch (_: Exception) {
            coroutineContext.ensureActive()
            error = true
        }
    }
    MessagePage {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MessageBack(onBack)
            Text("Message guests", style = MaterialTheme.typography.headlineLarge)
        }
        val entries = guests
        when {
            error -> MessageError("Could not load guests.", onRetry = { retry++ })
            entries == null -> MessageLoading()
            entries.isEmpty() -> Text("No reservations yet.", color = PinedrawColors.TextSecondary)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it.first.id }) { (reservation, name) ->
                    Card(
                        onClick = { onOpenChat(reservation.id) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PinedrawColors.Surface)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(name, style = MaterialTheme.typography.titleLarge)
                            Text(stayDates(reservation), color = PinedrawColors.TextSecondary)
                            Text("Reservation • ${reservation.status}", color = PinedrawColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

// Loads the reservation and shows a live conversation between its guest and owner.
@Composable
internal fun ReservationMessagesScreen(reservationId: String, userId: String, onBack: () -> Unit) {
    val service = remember { MessageService() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var booking by remember(reservationId, userId) { mutableStateOf<Reservation?>(null) }
    var contact by remember(reservationId, userId) { mutableStateOf("") }
    var messages by remember(reservationId, userId) { mutableStateOf<List<ReservationMessage>?>(null) }
    var draft by rememberSaveable(reservationId, userId) { mutableStateOf("") }
    var loadError by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(reservationId, userId, retry) {
        loadError = false
        try {
            val reservation = checkNotNull(ReservationService().getReservation(reservationId))
            val campsite = CampsiteService().getCampsite(reservation.campsiteId)
            val isGuest = reservation.entrantId == userId
            check(isGuest || campsite?.ownerId == userId)
            val otherId = if (isGuest) campsite?.ownerId else reservation.entrantId
            contact = UserService().getUsers(listOfNotNull(otherId)).firstOrNull()?.name.orEmpty()
                .ifBlank { if (isGuest) "Campsite owner" else "Guest" }
            booking = reservation
            service.observeMessages(reservationId).collect { messages = it }
        } catch (_: Exception) {
            coroutineContext.ensureActive()
            loadError = true
        }
    }
    val entries = messages
    LaunchedEffect(entries?.lastOrNull()?.id) {
        if (!entries.isNullOrEmpty()) listState.scrollToItem(entries.lastIndex)
    }
    MessagePage {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MessageBack(onBack)
            if (contact.isNotBlank()) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(PinedrawColors.Teal),
                    contentAlignment = Alignment.Center
                ) {
                    Text(contact.take(1).uppercase(), color = PinedrawColors.Ink, style = MaterialTheme.typography.titleLarge)
                }
            }
            Column {
                Text(contact.ifBlank { "Messages" }, style = MaterialTheme.typography.titleLarge)
                booking?.let {
                    Text(it.campsiteName.ifBlank { "Campsite reservation" }, color = PinedrawColors.TextSecondary)
                    Text(stayDates(it), color = PinedrawColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        booking?.let {
            val statusColor = if (it.status == RESERVATION_CANCELLED) PinedrawColors.Coral else PinedrawColors.Lime
            Surface(Modifier.align(Alignment.CenterHorizontally), shape = CircleShape, color = statusColor.copy(alpha = 0.12f)) {
                Text("Reservation • ${it.status}", Modifier.padding(horizontal = 14.dp, vertical = 6.dp), color = statusColor)
            }
        }
        when {
            loadError -> MessageError("Could not load this conversation.", onRetry = { retry++ })
            entries == null -> MessageLoading()
            else -> {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)) {
                    if (entries.isEmpty()) item {
                        Text("No messages yet. Start the conversation.",
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            color = PinedrawColors.TextSecondary, textAlign = TextAlign.Center)
                    }
                    items(entries, key = { it.id }) { message -> MessageBubble(message, message.senderId == userId) }
                }
                Text("Admins can review messages for safety.", Modifier.fillMaxWidth(),
                    color = PinedrawColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center)
                if (sendError) Text("Could not send your message. Try again.", color = PinedrawColors.Coral)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= MAX_MESSAGE_LENGTH) draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message") },
                        enabled = !sending,
                        shape = MaterialTheme.shapes.large,
                        maxLines = 4
                    )
                    FilledIconButton(
                        enabled = !sending && draft.isNotBlank(),
                        onClick = {
                            if (!sending && draft.isNotBlank()) {
                                sending = true
                                sendError = false
                                scope.launch {
                                    try {
                                        service.sendMessage(reservationId, draft)
                                        draft = ""
                                    } catch (_: Exception) {
                                        coroutineContext.ensureActive()
                                        sendError = true
                                    } finally {
                                        sending = false
                                    }
                                }
                            }
                        }
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "Send message") }
                }
            }
        }
    }
}

// Draws an outgoing lime bubble or an incoming dark bubble, with its actual send time.
@Composable
private fun MessageBubble(message: ReservationMessage, outgoing: Boolean) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start) {
        val shape = MaterialTheme.shapes.large
        val background = if (outgoing) PinedrawColors.PrimaryGradient
            else Brush.horizontalGradient(listOf(PinedrawColors.Surface, PinedrawColors.Surface))
        Text(message.text,
            Modifier.fillMaxWidth(0.80f).clip(shape)
                .background(background).padding(horizontal = 14.dp, vertical = 10.dp),
            color = if (outgoing) PinedrawColors.Ink else PinedrawColors.Text)
        message.sentAt?.let {
            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(it.toDate()),
                Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                color = PinedrawColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

// Applies the shared Pinedraw gradient and leaves space for system bars and the keyboard.
@Composable
private fun MessagePage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(
                0f to MaterialTheme.colorScheme.surfaceBright, 0.60f to PinedrawColors.Background
            )).statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

// Returns to the reservation or guest list.
@Composable
private fun MessageBack(onBack: () -> Unit) {
    FilledTonalIconButton(
        onClick = onBack,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = PinedrawColors.Glass, contentColor = PinedrawColors.Text
        )
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
    }
}

// Shows a retry action when the list or conversation fails to load.
@Composable
private fun MessageError(text: String, onRetry: () -> Unit) {
    Text(text, color = PinedrawColors.Coral)
    TextButton(onClick = onRetry) { Text("Try again") }
}

// Shows progress while the first result is loading.
@Composable
private fun MessageLoading() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

// Uses the same check-in and check-out labels as the reservation screen.
private fun stayDates(reservation: Reservation) =
    "${formatReservationDate(reservation.checkInDate)} – ${formatReservationDate(reservation.checkOutDate)}"
