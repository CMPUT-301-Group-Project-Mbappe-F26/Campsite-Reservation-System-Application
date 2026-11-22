package com.example.campsitereservationapp.messaging

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import com.example.campsitereservationapp.service.MessageService
import com.example.campsitereservationapp.service.ReservationMessage
import com.example.campsitereservationapp.service.UserService
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import com.example.campsitereservationapp.ui.components.CircleIconButton
import com.example.campsitereservationapp.ui.components.EmptyState
import com.example.campsitereservationapp.ui.components.ErrorState
import com.example.campsitereservationapp.ui.components.GlassCard
import com.example.campsitereservationapp.ui.components.InitialsAvatar
import com.example.campsitereservationapp.ui.components.InlineMessage
import com.example.campsitereservationapp.ui.components.LoadingState
import com.example.campsitereservationapp.ui.components.PinedrawBackground
import com.example.campsitereservationapp.ui.components.StatusChip
import com.example.campsitereservationapp.ui.components.pinedrawFieldColors
import com.example.campsitereservationapp.ui.theme.PinedrawRadius
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import java.util.UUID
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
            Spacer(Modifier.width(12.dp))
            Text("Message guests", style = MaterialTheme.typography.titleLarge)
        }
        val entries = guests
        when {
            error -> MessageError("Could not load guests.", onRetry = { retry++ })
            entries == null -> MessageLoading()
            entries.isEmpty() -> EmptyState(Icons.AutoMirrored.Rounded.Chat, "No reservations yet.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Small)) {
                items(entries, key = { it.first.id }) { (reservation, name) ->
                    GlassCard(onClick = { onOpenChat(reservation.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InitialsAvatar(name, size = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.titleMedium)
                                Text(stayDates(reservation), style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary)
                            }
                            StatusChip(reservation.status,
                                color = if (reservation.status == RESERVATION_CANCELLED) PinedrawColors.Coral else PinedrawColors.Lime)
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
    val listState = rememberLazyListState()
    var booking by remember(reservationId, userId) { mutableStateOf<Reservation?>(null) }
    var contact by remember(reservationId, userId) { mutableStateOf("") }
    var messages by remember(reservationId, userId) { mutableStateOf<List<ReservationMessage>?>(null) }
    var ownerId by remember(reservationId, userId) { mutableStateOf("") }
    var loadError by remember(reservationId, userId) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(reservationId, userId, retry) {
        loadError = false
        booking = null
        messages = null
        contact = ""
        try {
            service.observeConversation(reservationId).collectLatest { conversation ->
                check(conversation.canRead(userId))
                booking = conversation.reservation
                ownerId = conversation.ownerId
                val isGuest = conversation.reservation.entrantId == userId
                val otherId = if (isGuest) ownerId else conversation.reservation.entrantId
                contact = if (isGuest) "Campsite owner" else "Guest"
                coroutineScope {
                    launch {
                        try {
                            val name = UserService().getUsers(listOf(otherId)).firstOrNull()?.name.orEmpty()
                            if (name.isNotBlank()) contact = name
                        } catch (_: Exception) {
                            coroutineContext.ensureActive()
                            // The conversation remains usable when the contact's profile is unavailable.
                        }
                    }
                    service.observeMessages(reservationId).collect { messages = it }
                }
            }
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
            if (contact.isNotBlank()) InitialsAvatar(contact, size = 42.dp)
            Column(Modifier.weight(1f)) {
                Text(contact.ifBlank { "Messages" }, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                booking?.let {
                    Text("${it.campsiteName.ifBlank { "Campsite reservation" }} · ${stayDates(it)}",
                        color = PinedrawColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
        booking?.let {
            val statusColor = if (it.status == RESERVATION_CANCELLED) PinedrawColors.Coral else PinedrawColors.Lime
            StatusChip("Reservation • ${it.status}", Modifier.align(Alignment.CenterHorizontally), color = statusColor)
        }
        when {
            loadError -> MessageError("Could not load this conversation.", onRetry = { retry++ })
            entries == null -> MessageLoading()
            else -> {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)) {
                    if (entries.isEmpty()) item {
                        Text(if (booking?.entrantId == userId) "Ask the owner about your stay." else "Send a message to your guest.",
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            color = PinedrawColors.TextSecondary, textAlign = TextAlign.Center)
                    }
                    items(entries, key = { it.id }) { message -> MessageBubble(message, message.senderId == userId) }
                }
                Text("Admins can review messages for safety.", Modifier.fillMaxWidth(),
                    color = PinedrawColors.TextMuted, style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center)
                ReservationMessageComposer(
                    reservationId = reservationId,
                    userId = userId,
                    blockedReason = booking?.let { ReservationConversation(it, ownerId).sendError(userId) },
                    onSend = { text, messageId -> service.sendMessage(reservationId, text, messageId) }
                )
            }
        }
    }
}

// Keeps the draft on failure and reuses its message ID if the user retries a send.
@Composable
internal fun ReservationMessageComposer(
    reservationId: String,
    userId: String,
    blockedReason: String? = null,
    onSend: suspend (String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(reservationId, userId) { mutableStateOf("") }
    var attemptId by rememberSaveable(reservationId, userId) { mutableStateOf<String?>(null) }
    var sending by remember(reservationId, userId) { mutableStateOf(false) }
    var sendError by remember(reservationId, userId) { mutableStateOf("") }
    if (blockedReason != null) InlineMessage(blockedReason, color = PinedrawColors.TextSecondary)
    InlineMessage(sendError)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = draft,
            onValueChange = {
                draft = it
                attemptId = null
                sendError = ""
            },
            modifier = Modifier.weight(1f),
            label = { Text("Message") },
            placeholder = { Text("Write a message…") },
            enabled = !sending && blockedReason == null,
            shape = PinedrawRadius.Large,
            colors = pinedrawFieldColors(),
            maxLines = 4,
            isError = draft.trim().length > MAX_MESSAGE_LENGTH,
            supportingText = {
                if (draft.trim().length > MAX_MESSAGE_LENGTH) Text("Keep your message under 1,000 characters.")
            }
        )
        FilledIconButton(
            modifier = Modifier.size(52.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = PinedrawColors.Lime, contentColor = PinedrawColors.Ink,
                disabledContainerColor = PinedrawColors.Glass, disabledContentColor = PinedrawColors.TextMuted
            ),
            enabled = !sending && blockedReason == null && messageTextError(draft) == null,
            onClick = {
                if (!sending && blockedReason == null && messageTextError(draft) == null) {
                    val text = draft.trim()
                    val messageId = attemptId ?: UUID.randomUUID().toString().also { attemptId = it }
                    sending = true
                    sendError = ""
                    scope.launch {
                        try {
                            onSend(text, messageId)
                            draft = ""
                            attemptId = null
                        } catch (_: Exception) {
                            coroutineContext.ensureActive()
                            sendError = "Couldn't send your message. Your draft is still here—try again."
                        } finally {
                            sending = false
                        }
                    }
                }
            }
        ) {
            if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = PinedrawColors.Ink)
            else Icon(Icons.AutoMirrored.Rounded.Send, "Send message")
        }
    }
}

// Draws an outgoing lime bubble or an incoming dark bubble, with its actual send time.
@Composable
private fun MessageBubble(message: ReservationMessage, outgoing: Boolean) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start) {
        val shape = if (outgoing) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
        val background = if (outgoing) PinedrawColors.PrimaryGradient
            else Brush.horizontalGradient(listOf(PinedrawColors.SurfaceRaised, PinedrawColors.SurfaceRaised))
        Text(message.text,
            Modifier.widthIn(max = 300.dp).clip(shape)
                .background(background).padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyLarge,
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
    PinedrawBackground {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(PinedrawSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

// Returns to the reservation or guest list.
@Composable
private fun MessageBack(onBack: () -> Unit) {
    CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
}

// Shows a retry action when the list or conversation fails to load.
@Composable
private fun MessageError(text: String, onRetry: () -> Unit) {
    ErrorState(text, onRetry = onRetry)
}

// Shows progress while the first result is loading.
@Composable
private fun MessageLoading() {
    LoadingState()
}

// Uses the same check-in and check-out labels as the reservation screen.
private fun stayDates(reservation: Reservation) =
    "${formatReservationDate(reservation.checkInDate)} – ${formatReservationDate(reservation.checkOutDate)}"
