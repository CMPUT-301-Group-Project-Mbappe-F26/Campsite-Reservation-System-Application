package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.*
import org.junit.Assert.*
import org.junit.Test

/** Owner consent in isolation and its interactions with editing, capacity, joining, and winner selection. */
class LotteryCollaborationTest {
    private val primary = Campsite(id = "primary", ownerId = "coordinator", maxOccupancy = 8, published = true)
    private val partner = primary.copy(id = "partner", ownerId = "partner-owner", maxOccupancy = 4)
    private val base = Lottery(id = "draw", ownerId = "coordinator", campsiteId = "primary", numberOfWinners = 3,
        registrationStartDate = "2027-04-01", registrationEndDate = "2027-04-07",
        bookingStartDate = "2027-04-08", bookingEndDate = "2027-04-14",
        stayStartDate = "2027-05-01", stayEndDate = "2027-05-04")
    private fun invited() = inviteLotteryCampsite(base, partner, "coordinator")
    private fun accepted() = acceptLotteryCampsite(invited(), partner, "partner-owner")

    @Test fun invitationsAreNotOfferingsBeforeConsent() {
        val pending = invited()
        assertEquals(listOf("primary"), pending.offeredCampsiteIds())
        assertEquals(mapOf("partner" to "partner-owner"), pending.campsiteInvitations)
        assertFalse(pending.includesAcceptedCampsite(partner))
        assertEquals(1, pending.listingRevision.toInt())
    }

    @Test fun onlyCoordinatorCanInviteAnotherPublishedOwnersCampsite() {
        assertThrows(IllegalStateException::class.java) { inviteLotteryCampsite(base, partner, "intruder") }
        assertThrows(IllegalArgumentException::class.java) { inviteLotteryCampsite(base, primary.copy(id = "own"), "coordinator") }
        assertThrows(IllegalArgumentException::class.java) { inviteLotteryCampsite(base, partner.copy(published = false), "coordinator") }
    }

    @Test fun invalidAvailabilityDuplicatesAndCapacityAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { inviteLotteryCampsite(base, partner.copy(availableFrom = "2027-06-01"), "coordinator") }
        assertThrows(IllegalArgumentException::class.java) { inviteLotteryCampsite(invited(), partner, "coordinator") }
        val full = base.copy(campsiteInvitations = (1..7).associate { "invited-$it" to "owner-$it" })
        assertThrows(IllegalArgumentException::class.java) { inviteLotteryCampsite(full, partner, "coordinator") }
    }

    @Test fun onlyAddressedCurrentOwnerCanAccept() {
        assertThrows(IllegalStateException::class.java) { acceptLotteryCampsite(invited(), partner, "coordinator") }
        assertThrows(IllegalStateException::class.java) { acceptLotteryCampsite(invited(), partner.copy(ownerId = "new-owner"), "new-owner") }
        assertThrows(IllegalStateException::class.java) { acceptLotteryCampsite(base, partner, "partner-owner") }
    }

    @Test fun acceptanceAddsExactlyOneCampsiteAndPreservesTheAgreedWinnerCount() {
        val current = acceptLotteryCampsite(inviteLotteryCampsite(base.copy(numberOfWinners = 8, useMaxCapacity = true), partner, "coordinator"), partner, "partner-owner")
        assertEquals(listOf("primary", "partner"), current.offeredCampsiteIds())
        assertEquals("partner-owner", current.campsiteOwnerId("partner"))
        assertTrue(current.campsiteInvitations.isEmpty())
        assertEquals(8, current.numberOfWinners)
        assertFalse(current.useMaxCapacity)
        assertNull(lotteryListingError(current, listOf(primary, partner)))
    }

    @Test fun anotherOwnerCannotAcceptForOrWithdrawSomeoneElsesCampsite() {
        assertThrows(IllegalStateException::class.java) { withdrawLotteryCampsite(accepted(), "partner", "coordinator", listOf(primary)) }
        assertThrows(IllegalStateException::class.java) { declineLotteryCampsite(invited(), "partner", "intruder") }
    }

    @Test fun invitedOwnerCanDeclineWithoutEverGrantingMembership() {
        val declined = declineLotteryCampsite(invited(), "partner", "partner-owner")
        assertTrue(declined.campsiteInvitations.isEmpty())
        assertEquals(listOf("primary"), declined.offeredCampsiteIds())
    }

    @Test fun closingARemovedSingleOwnerCampsiteClearsItsPendingInvitations() {
        val closed = closeLotteryForRemovedCampsite(invited())
        assertTrue(closed.campsiteInvitations.isEmpty())
        assertTrue(closed.drawCompleted)
    }

    @Test fun coordinatorCanCancelOnlyPendingInvitations() {
        assertTrue(declineLotteryCampsite(invited(), "partner", "coordinator").campsiteInvitations.isEmpty())
        assertThrows(IllegalStateException::class.java) { declineLotteryCampsite(accepted(), "partner", "coordinator") }
    }

    @Test fun contributorCanWithdrawOnlyTheirOwnCampsite() {
        val withdrawn = withdrawLotteryCampsite(accepted(), "partner", "partner-owner", listOf(primary))
        assertEquals(listOf("primary"), withdrawn.offeredCampsiteIds())
        assertTrue(withdrawn.campsiteOwners.isEmpty())
        assertEquals(3, withdrawn.numberOfWinners)
        assertThrows(IllegalArgumentException::class.java) { withdrawLotteryCampsite(base, "primary", "coordinator", emptyList()) }
    }

    @Test fun withdrawalClampsCapacityWithoutChangingDatesOrEntrants() {
        val shared = accepted().copy(numberOfWinners = 10, registered = listOf("entrant"))
        val withdrawn = withdrawLotteryCampsite(shared, "partner", "partner-owner", listOf(primary))
        assertEquals(8, withdrawn.numberOfWinners)
        assertTrue(withdrawn.useMaxCapacity)
        assertEquals(shared.registered, withdrawn.registered)
        assertEquals(shared.stayStartDate, withdrawn.stayStartDate)
        assertNull(lotteryListingError(withdrawn, listOf(primary)))
    }

    @Test fun everyMembershipMutationIsBlockedAfterDrawing() {
        val drawn = accepted().copy(drawCompleted = true)
        assertThrows(IllegalStateException::class.java) { withdrawLotteryCampsite(drawn, "partner", "partner-owner", listOf(primary)) }
        assertThrows(IllegalStateException::class.java) { inviteLotteryCampsite(drawn, partner.copy(id = "third"), "coordinator") }
        assertThrows(IllegalStateException::class.java) { acceptLotteryCampsite(invited().copy(drawCompleted = true), partner, "partner-owner") }
        assertThrows(IllegalStateException::class.java) { declineLotteryCampsite(invited().copy(drawCompleted = true), "partner", "partner-owner") }
    }

    @Test fun coordinatorCannotRemoveOthersThroughTheOrdinaryEditForm() {
        val current = accepted()
        assertThrows(IllegalArgumentException::class.java) { updatedLotteryListing(current, current.copy(campsiteIds = listOf("primary"))) }
    }

    @Test fun changedTermsRequireConsentAgainButWaitlistEditsDoNot() {
        val current = accepted().copy(registered = listOf("entrant"))
        val changed = updatedLotteryListing(current, current.copy(stayEndDate = "2027-05-05"))
        assertEquals(listOf("primary"), changed.offeredCampsiteIds())
        assertEquals(mapOf("partner" to "partner-owner"), changed.campsiteInvitations)
        assertEquals(current.registered, changed.registered)
        assertTrue(acceptLotteryCampsite(changed, partner, "partner-owner").includesAcceptedCampsite(partner))
        val unchanged = updatedLotteryListing(current, current.copy(waitingListCapacity = 20))
        assertTrue(unchanged.includesAcceptedCampsite(partner))
    }

    @Test fun coordinatorCanUseCombinedWinnerCapacityWithoutChangingAgreedDates() {
        val shared = accepted()
        val updated = updatedLotteryListing(shared, shared.copy(numberOfWinners = 12, useMaxCapacity = true))
        assertTrue(updated.includesAcceptedCampsite(partner))
        assertNull(lotteryListingError(updated, listOf(primary, partner)))
        val newDates = updatedLotteryListing(shared.copy(numberOfWinners = 8),
            shared.copy(numberOfWinners = 8, useMaxCapacity = true, stayEndDate = "2027-05-05"))
        assertEquals(listOf("primary"), newDates.offeredCampsiteIds())
        assertNull(lotteryListingError(newDates, listOf(primary)))
    }

    @Test fun collaboratorsCannotEnterTheirSharedLotteryOrAcceptAsAnEntrant() {
        val instant = java.text.SimpleDateFormat("yyyy-MM-dd").parse("2027-04-04")!!.time
        assertEquals("Campsite owners cannot enter their own lottery.", waitingListJoinError(accepted(), partner, "partner-owner", 1, instant))
        assertThrows(IllegalStateException::class.java) { acceptLotteryCampsite(invited().copy(registered = listOf("partner-owner")), partner, "partner-owner") }
    }

    @Test fun winnersCanReserveAnyAcceptedOwnersCampsiteButNotPendingOrTransferredOnes() {
        val shared = accepted().copy(pending = listOf("winner"))
        assertNull(reservationCampsiteError(shared, partner, "winner"))
        assertNotNull(reservationCampsiteError(invited().copy(pending = listOf("winner")), partner, "winner"))
        assertNotNull(reservationCampsiteError(shared, partner.copy(ownerId = "new-owner"), "winner"))
        assertTrue(reservationCampsiteChoice("partner", partner, shared, "winner", emptySet(), "2027-04-09").selectable)
    }
}
