package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.*

/** Common terms for which a contributing owner must explicitly give consent again after a change. */
internal fun lotteryTermsChanged(before: Lottery, after: Lottery): Boolean =
    listOf(before.registrationStartDate, before.registrationEndDate, before.bookingStartDate, before.bookingEndDate,
        before.stayStartDate, before.stayEndDate) !=
        listOf(after.registrationStartDate, after.registrationEndDate, after.bookingStartDate, after.bookingEndDate,
            after.stayStartDate, after.stayEndDate)

/** Adds an invitation without granting membership or exposing the campsite to winners. */
internal fun inviteLotteryCampsite(lottery: Lottery, campsite: Campsite, actorId: String): Lottery {
    requireLotteryEditable(lottery)
    check(actorId == lottery.ownerId) { "Only the lottery coordinator can invite campsites." }
    require(campsite.ownerId.isNotBlank() && campsite.ownerId != actorId && campsite.published) {
        "Choose another owner's published campsite."
    }
    require(campsite.id !in lottery.offeredCampsiteIds() && campsite.id !in lottery.campsiteInvitations) {
        "This campsite is already listed or invited."
    }
    require(lottery.offeredCampsiteIds().size + lottery.campsiteInvitations.size < MAX_LOTTERY_CAMPSITES) {
        "A lottery can include at most $MAX_LOTTERY_CAMPSITES listed or invited campsites."
    }
    val error = lotteryStayAvailabilityError(lottery.stayStartDate, lottery.stayEndDate, campsite)
    require(error == null) { error.orEmpty() }
    require((campsite.maxOccupancy ?: 0) > 0) { "The invited campsite needs a positive maximum occupancy." }
    return lottery.copy(campsiteInvitations = lottery.campsiteInvitations + (campsite.id to campsite.ownerId),
        listingRevision = lottery.listingRevision + 1)
}

/** Accepts only the addressed owner's current campsite; acceptance preserves the agreed winner count. */
internal fun acceptLotteryCampsite(lottery: Lottery, campsite: Campsite, actorId: String): Lottery {
    requireLotteryEditable(lottery)
    check(campsite.ownerId == actorId && lottery.campsiteInvitations[campsite.id] == actorId) {
        "Only the invited campsite's current owner can accept."
    }
    check(actorId !in lottery.registered && actorId !in lottery.pending && lottery.booked.none { it.entrantId == actorId }) {
        "Leave this lottery's waiting list before accepting as a campsite owner."
    }
    require(campsite.published) { "Publish your campsite before accepting." }
    require(campsite.id !in lottery.offeredCampsiteIds()) { "This campsite is already listed." }
    require(lottery.offeredCampsiteIds().size < MAX_LOTTERY_CAMPSITES) { "This lottery is full." }
    return lottery.copy(campsiteIds = lottery.offeredCampsiteIds() + campsite.id,
        campsiteOwners = lottery.campsiteOwners + (campsite.id to actorId),
        campsiteInvitations = lottery.campsiteInvitations - campsite.id, useMaxCapacity = false,
        listingRevision = lottery.listingRevision + 1)
}

/** An invited owner can decline their invitation; the coordinator can cancel an unsatisfied invitation. */
internal fun declineLotteryCampsite(lottery: Lottery, campsiteId: String, actorId: String): Lottery {
    requireLotteryEditable(lottery)
    val invitedOwner = lottery.campsiteInvitations[campsiteId] ?: error("This invitation is no longer pending.")
    check(actorId == invitedOwner || actorId == lottery.ownerId) { "You cannot change another owner's invitation." }
    return lottery.copy(campsiteInvitations = lottery.campsiteInvitations - campsiteId,
        listingRevision = lottery.listingRevision + 1)
}

/**
 * Withdraws only the actor's additional campsite. If capacity shrinks, clamps winners to
 * the remaining occupancy and uses Max when equal. The primary campsite anchors coordination.
 */
internal fun withdrawLotteryCampsite(lottery: Lottery, campsiteId: String, actorId: String, remaining: List<Campsite>): Lottery {
    requireLotteryEditable(lottery)
    check(campsiteId in lottery.offeredCampsiteIds() && lottery.campsiteOwnerId(campsiteId) == actorId) {
        "You can only withdraw your own campsite."
    }
    require(campsiteId != lottery.campsiteId) { "The coordinator's primary campsite cannot be withdrawn." }
    val ids = lottery.offeredCampsiteIds() - campsiteId
    require(remaining.map { it.id }.toSet() == ids.toSet()) { "Reload the remaining campsites." }
    val maximum = remaining.sumOf { it.maxOccupancy ?: 0 }
    require(maximum > 0) { "The remaining campsites need positive capacity." }
    val winners = minOf(lottery.numberOfWinners, maximum)
    return lottery.copy(campsiteIds = ids, campsiteOwners = lottery.campsiteOwners - campsiteId,
        numberOfWinners = winners, useMaxCapacity = winners == maximum,
        listingRevision = lottery.listingRevision + 1)
}
