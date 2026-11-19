package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.offeredCampsiteIds
import com.example.campsitereservationapp.model.MAX_LOTTERY_CAMPSITES
import com.example.campsitereservationapp.model.campsiteOwnerId

/** Shared listing rules used by the form and the authoritative Firestore transaction. */
internal fun lotteryListingError(lottery: Lottery, campsites: List<Campsite>): String? {
    val ids = lottery.offeredCampsiteIds()
    if (ids.isEmpty() || ids.any { it.isBlank() || '/' in it }) return "Select at least one campsite."
    if (ids.size > MAX_LOTTERY_CAMPSITES) return "A lottery can include at most $MAX_LOTTERY_CAMPSITES campsites."
    if (ids.size + lottery.campsiteInvitations.size > MAX_LOTTERY_CAMPSITES) return "Listed and invited campsites cannot exceed $MAX_LOTTERY_CAMPSITES."
    if (ids.distinct().size != ids.size) return "Duplicate campsites are not allowed."
    if (lottery.campsiteId !in ids) return "The primary campsite must be included."
    if (campsites.map { it.id }.toSet() != ids.toSet()) return "One or more campsites are no longer available."
    if (campsites.any { it.ownerId != lottery.campsiteOwnerId(it.id) }) return "A campsite's owner has not accepted or its ownership changed."
    if (lottery.campsiteOwnerId(lottery.campsiteId) != lottery.ownerId) return "The primary campsite must belong to the lottery coordinator."
    val maximum = campsites.sumOf { it.maxOccupancy?.takeIf { capacity -> capacity > 0 } ?: 0 }
    if (campsites.any { (it.maxOccupancy ?: 0) <= 0 }) return "Each campsite needs a positive maximum occupancy."
    lotteryCapacityError(lottery.numberOfWinners, maximum, lottery.useMaxCapacity)?.let { return it }
    for (site in campsites) {
        lotteryStayAvailabilityError(lottery.stayStartDate, lottery.stayEndDate, site)?.let {
            return "${site.name}: $it"
        }
    }
    return null
}

/** Zero means unlimited; a configured limit must leave room for more entrants than winners. */
internal fun lotteryWaitlistCapacityError(capacity: Int, winners: Int): String? = when {
    capacity < 0 -> "Enter a positive whole number for the waiting-list limit."
    capacity != 0 && capacity <= winners -> "The waiting-list limit must be greater than the number of winners."
    else -> null
}

/** All participating entrants receive listing change notices, including already booked winners. */
internal fun Lottery.participantIds(): List<String> =
    (registered + pending + booked.map { it.entrantId }).filter { it.isNotBlank() }.distinct()

/** Applies editable fields only; concurrent joins and all draw/reservation state stay intact. */
internal fun updatedLotteryListing(current: Lottery, draft: Lottery): Lottery {
    requireLotteryEditable(current)
    require(draft.waitingListCapacity == 0 || draft.waitingListCapacity >= current.registered.distinct().size) {
        "The waiting-list limit cannot be smaller than the current number of entrants."
    }
    val foreign = current.offeredCampsiteIds().filter { current.campsiteOwnerId(it) != current.ownerId }
    require(draft.offeredCampsiteIds().containsAll(foreign)) { "You cannot remove another owner's accepted campsite." }
    val reapprove = lotteryTermsChanged(current, draft)
    val ids = draft.offeredCampsiteIds().filterNot { reapprove && it in foreign }
    val owners = current.campsiteOwners.filterKeys { it in ids }
    return current.copy(
        campsiteId = draft.campsiteId.takeIf { it in ids && (owners[it] ?: current.ownerId) == current.ownerId }
            ?: ids.firstOrNull { (owners[it] ?: current.ownerId) == current.ownerId }
            ?: error("Keep at least one campsite belonging to the lottery coordinator."),
        numberOfWinners = draft.numberOfWinners, useMaxCapacity = draft.useMaxCapacity,
        waitingListCapacity = draft.waitingListCapacity,
        registrationStartDate = draft.registrationStartDate, registrationEndDate = draft.registrationEndDate,
        bookingStartDate = draft.bookingStartDate, bookingEndDate = draft.bookingEndDate,
        stayStartDate = draft.stayStartDate, stayEndDate = draft.stayEndDate,
        campsiteIds = ids,
        campsiteOwners = owners,
        campsiteInvitations = if (reapprove) current.campsiteInvitations + foreign.associateWith { current.campsiteOwnerId(it) }
            else current.campsiteInvitations,
        listingRevision = current.listingRevision + 1
    )
}

/** Prevents editing the offering after winners or reservations have already been allocated. */
internal fun requireLotteryEditable(lottery: Lottery) {
    check(!lottery.drawCompleted && lottery.pending.isEmpty() && lottery.booked.isEmpty()) {
        "A lottery cannot be updated or deleted after it has been drawn or booked."
    }
}
