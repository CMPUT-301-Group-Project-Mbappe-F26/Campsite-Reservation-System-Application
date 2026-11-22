package com.example.campsitereservationapp.campsite

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.campsitereservationapp.model.*
import com.example.campsitereservationapp.service.*
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cabin
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import com.example.campsitereservationapp.ui.components.ButtonStyle
import com.example.campsitereservationapp.ui.components.GlassCard
import com.example.campsitereservationapp.ui.components.InfoRow
import com.example.campsitereservationapp.ui.components.InlineMessage
import com.example.campsitereservationapp.ui.components.OverlineLabel
import com.example.campsitereservationapp.ui.components.PinedrawButton
import com.example.campsitereservationapp.ui.components.PinedrawDialog
import com.example.campsitereservationapp.ui.components.PinedrawTextButton

/** Loads collaboration controls only from an owned campsite's details screen. */
@Composable
fun LotteryCollaborationSection(campsite: Campsite, refresh: Int, onChanged: () -> Unit) {
    val controller: LotteryCollaborationViewModel = viewModel(key = "collaboration-${campsite.id}-${campsite.ownerId}")
    LaunchedEffect(campsite.id, refresh) { controller.load(campsite.id) }
    if (controller.loading) CircularProgressIndicator(Modifier.size(20.dp))
    if (controller.error.isNotBlank()) {
        InlineMessage(controller.error)
        PinedrawTextButton("Refresh collaboration", { controller.load(campsite.id) }, icon = Icons.Rounded.Refresh)
    }
    InlineMessage(controller.message, color = PinedrawColors.Lime)
    controller.listings.forEach { entry ->
        key(entry.lottery.id) { LotteryCollaborationCard(entry, campsite, controller.candidates, controller.saving || controller.loading,
            onLoadCandidates = controller::loadCandidates,
            onAction = { id, action -> controller.change(entry.lottery, id, action, onChanged) },
            loadingCandidates = controller.loadingCandidates) }
    }
}

/** Confirmation requested by a role-scoped owner action. */
private data class CollaborationConfirmation(val campsiteId: String, val action: LotteryCollaborationAction)

/**
 * Renders shared dates, accepted campsites and invitations. The coordinator invites;
 * only the displayed campsite's owner can accept or withdraw that campsite.
 * Actions are callbacks so role visibility and confirmation can be tested without Firebase.
 */
@Composable
internal fun LotteryCollaborationCard(
    entry: LotteryCollaborationListing,
    ownedCampsite: Campsite,
    candidates: List<Campsite>,
    saving: Boolean,
    onLoadCandidates: () -> Unit,
    onAction: (String, LotteryCollaborationAction) -> Unit,
    loadingCandidates: Boolean = false
) {
    val lottery = entry.lottery
    val coordinator = lottery.ownerId == ownedCampsite.ownerId
    val editable = runCatching { requireLotteryEditable(lottery) }.isSuccess
    val invited = lottery.campsiteInvitations[ownedCampsite.id] == ownedCampsite.ownerId
    val accepted = lottery.includesAcceptedCampsite(ownedCampsite)
    var inviting by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    var candidateId by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<CollaborationConfirmation?>(null) }
    val available = candidates.filter { it.ownerId != ownedCampsite.ownerId && it.id !in lottery.offeredCampsiteIds() && it.id !in lottery.campsiteInvitations }
    GlassCard(modifier = Modifier.testTag("lottery-collaboration-${lottery.id}"), spacing = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Handshake, null, tint = PinedrawColors.Lime, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (invited) "Shared lottery invitation" else "Lottery collaboration", style = MaterialTheme.typography.titleMedium)
        }
        Text(if (coordinator) "You coordinate this lottery." else "You manage only ${ownedCampsite.name}.",
            style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary)
        InfoRow("Registration", "${lottery.registrationStartDate} – ${lottery.registrationEndDate}")
        InfoRow("Booking", "${lottery.bookingStartDate} – ${lottery.bookingEndDate}")
        InfoRow("Stay", "${lottery.stayStartDate} – ${lottery.stayEndDate} (checkout)")
        InfoRow("Winners", "${lottery.numberOfWinners}")
        OverlineLabel("Accepted campsites")
        lottery.offeredCampsiteIds().forEach { id ->
            Text(entry.campsites.find { it.id == id }?.name ?: "Unavailable campsite", style = MaterialTheme.typography.bodyMedium, color = PinedrawColors.Text)
        }
        if (!editable) InlineMessage("The lottery has been drawn or booked. Membership can no longer change.", color = PinedrawColors.TextSecondary)
        if (invited) {
            Text("Your campsite is not listed until you accept these dates and the winner limit.", style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PinedrawButton("Accept invitation", { confirmation = CollaborationConfirmation(ownedCampsite.id, LotteryCollaborationAction.ACCEPT) },
                    Modifier.weight(1f), enabled = editable && !saving, compact = true)
                PinedrawButton("Decline", { confirmation = CollaborationConfirmation(ownedCampsite.id, LotteryCollaborationAction.DECLINE) },
                    style = ButtonStyle.Ghost, enabled = editable && !saving, compact = true)
            }
        }
        if (accepted && ownedCampsite.id != lottery.campsiteId) {
            PinedrawButton("Withdraw my campsite", { confirmation = CollaborationConfirmation(ownedCampsite.id, LotteryCollaborationAction.WITHDRAW) },
                Modifier.fillMaxWidth(), style = ButtonStyle.SoftDestructive, enabled = editable && !saving, compact = true)
        }
        if (coordinator) {
            lottery.campsiteInvitations.forEach { (id, _) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pending: ${entry.campsites.find { it.id == id }?.name ?: "Unavailable campsite"}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    PinedrawTextButton("Cancel invitation", { confirmation = CollaborationConfirmation(id, LotteryCollaborationAction.DECLINE) },
                        color = PinedrawColors.Coral, enabled = editable && !saving)
                }
            }
            PinedrawButton("Invite another owner's campsite", { inviting = true; candidateId = null; onLoadCandidates() },
                Modifier.fillMaxWidth(), style = ButtonStyle.Ghost, icon = Icons.Rounded.PersonAdd, compact = true,
                enabled = editable && !saving && lottery.offeredCampsiteIds().size + lottery.campsiteInvitations.size < MAX_LOTTERY_CAMPSITES)
        }
    }
    if (inviting) PinedrawDialog(
        title = "Invite a campsite",
        message = "Its owner must accept before the campsite is listed.",
        icon = Icons.Rounded.PersonAdd,
        confirmText = "Send invitation",
        confirmEnabled = available.any { it.id == candidateId } && !saving,
        onDismiss = { inviting = false },
        onConfirm = {
            inviting = false
            onAction(requireNotNull(candidateId), LotteryCollaborationAction.INVITE)
        }
    ) {
            Box {
                PinedrawButton(available.find { it.id == candidateId }?.name ?: "Choose a campsite", { choosing = true },
                    Modifier.fillMaxWidth(), style = ButtonStyle.Ghost, icon = Icons.Rounded.Cabin, compact = true)
                DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                    available.forEach { site -> DropdownMenuItem(text = { Text("${site.name} · ${site.location}") }, onClick = { candidateId = site.id; choosing = false }) }
                }
            }
            if (loadingCandidates) CircularProgressIndicator(Modifier.size(20.dp), color = PinedrawColors.Lime)
            else if (available.isEmpty()) Text("No other published campsites are available to invite yet.", style = MaterialTheme.typography.bodySmall)
    }
    confirmation?.let { pending ->
        PinedrawDialog(
            title = "Are you sure?",
            message = when (pending.action) {
                LotteryCollaborationAction.ACCEPT -> "Accept the displayed dates and winner limit, and add your campsite?"
                LotteryCollaborationAction.WITHDRAW -> "Withdraw only your campsite? Remaining capacity may reduce the winner limit. Entrants will be notified."
                else -> "Remove this pending invitation? No campsite will be added."
            },
            icon = Icons.Rounded.Handshake,
            confirmText = "Proceed",
            confirmEnabled = !saving,
            onDismiss = { confirmation = null },
            onConfirm = { confirmation = null; onAction(pending.campsiteId, pending.action) }
        )
    }
}
