package com.example.campsitereservationapp.campsite

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.campsitereservationapp.model.*
import com.example.campsitereservationapp.service.*
import com.example.campsitereservationapp.ui.theme.PinedrawColors

/** Loads collaboration controls only from an owned campsite's details screen. */
@Composable
fun LotteryCollaborationSection(campsite: Campsite, refresh: Int, onChanged: () -> Unit) {
    val controller: LotteryCollaborationViewModel = viewModel(key = "collaboration-${campsite.id}-${campsite.ownerId}")
    LaunchedEffect(campsite.id, refresh) { controller.load(campsite.id) }
    if (controller.loading) CircularProgressIndicator(Modifier.size(20.dp))
    if (controller.error.isNotBlank()) {
        Text(controller.error, color = PinedrawColors.Coral)
        TextButton(onClick = { controller.load(campsite.id) }) { Text("Refresh collaboration") }
    }
    if (controller.message.isNotBlank()) Text(controller.message, color = PinedrawColors.Lime)
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
    Card(Modifier.fillMaxWidth().testTag("lottery-collaboration-${lottery.id}"),
        colors = CardDefaults.cardColors(containerColor = PinedrawColors.Glass, contentColor = PinedrawColors.Text),
        shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (invited) "Shared lottery invitation" else "Lottery collaboration", style = MaterialTheme.typography.titleLarge)
            Text(if (coordinator) "You coordinate this lottery." else "You manage only ${ownedCampsite.name}.", color = PinedrawColors.TextSecondary)
            Text("Registration: ${lottery.registrationStartDate} – ${lottery.registrationEndDate}")
            Text("Booking: ${lottery.bookingStartDate} – ${lottery.bookingEndDate}")
            Text("Stay: ${lottery.stayStartDate} – ${lottery.stayEndDate} (checkout)")
            Text("Winners: ${lottery.numberOfWinners}")
            Text("Accepted campsites", style = MaterialTheme.typography.labelLarge)
            lottery.offeredCampsiteIds().forEach { id ->
                Text(entry.campsites.find { it.id == id }?.name ?: "Unavailable campsite", color = PinedrawColors.TextSecondary)
            }
            if (!editable) Text("The lottery has been drawn or booked. Membership can no longer change.", color = PinedrawColors.TextSecondary)
            if (invited) {
                Text("Your campsite is not listed until you accept these dates and the winner limit.", color = PinedrawColors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = editable && !saving, onClick = { confirmation = CollaborationConfirmation(ownedCampsite.id, LotteryCollaborationAction.ACCEPT) }) { Text("Accept invitation") }
                    TextButton(enabled = editable && !saving, onClick = { confirmation = CollaborationConfirmation(ownedCampsite.id, LotteryCollaborationAction.DECLINE) }) { Text("Decline") }
                }
            }
            if (accepted && ownedCampsite.id != lottery.campsiteId) {
                OutlinedButton(enabled = editable && !saving, onClick = { confirmation = CollaborationConfirmation(ownedCampsite.id, LotteryCollaborationAction.WITHDRAW) }) {
                    Text("Withdraw my campsite")
                }
            }
            if (coordinator) {
                lottery.campsiteInvitations.forEach { (id, _) ->
                    Text("Pending: ${entry.campsites.find { it.id == id }?.name ?: "Unavailable campsite"}")
                    TextButton(enabled = editable && !saving, onClick = { confirmation = CollaborationConfirmation(id, LotteryCollaborationAction.DECLINE) }) { Text("Cancel invitation") }
                }
                OutlinedButton(enabled = editable && !saving && lottery.offeredCampsiteIds().size + lottery.campsiteInvitations.size < MAX_LOTTERY_CAMPSITES,
                    onClick = { inviting = true; candidateId = null; onLoadCandidates() }) { Text("Invite another owner's campsite") }
            }
        }
    }
    if (inviting) AlertDialog(onDismissRequest = { inviting = false }, title = { Text("Invite a campsite") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Its owner must accept before the campsite is listed.")
            Box {
                OutlinedButton(onClick = { choosing = true }) { Text(available.find { it.id == candidateId }?.name ?: "Choose a campsite") }
                DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                    available.forEach { site -> DropdownMenuItem(text = { Text("${site.name} · ${site.location}") }, onClick = { candidateId = site.id; choosing = false }) }
                }
            }
            if (loadingCandidates) CircularProgressIndicator(Modifier.size(20.dp))
            else if (available.isEmpty()) Text("No other published campsites are available to invite yet.")
        } },
        dismissButton = { TextButton(onClick = { inviting = false }) { Text("Cancel") } },
        confirmButton = { TextButton(enabled = available.any { it.id == candidateId } && !saving, onClick = {
            inviting = false
            onAction(requireNotNull(candidateId), LotteryCollaborationAction.INVITE)
        }) { Text("Send invitation") } })
    confirmation?.let { pending ->
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text("Are you sure?") },
            text = { Text(when (pending.action) {
                LotteryCollaborationAction.ACCEPT -> "Accept the displayed dates and winner limit, and add your campsite?"
                LotteryCollaborationAction.WITHDRAW -> "Withdraw only your campsite? Remaining capacity may reduce the winner limit. Entrants will be notified."
                else -> "Remove this pending invitation? No campsite will be added."
            }) },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } },
            confirmButton = { TextButton(enabled = !saving, onClick = { confirmation = null; onAction(pending.campsiteId, pending.action) }) { Text("Proceed") } })
    }
}
