package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.offeredCampsiteIds
import com.example.campsitereservationapp.model.Reservation
import com.example.campsitereservationapp.model.isNightOpen
import org.junit.Assert.*
import org.junit.Test

class LotteryListingRulesTest {
    private val site = Campsite(id = "a", ownerId = "owner", name = "First", maxOccupancy = 8,
        availableFrom = "2027-05-01", availableUntil = "2027-05-31")
    private val lottery = Lottery(id = "draw", ownerId = "owner", campsiteId = "a", numberOfWinners = 5,
        registrationStartDate = "2027-04-01", registrationEndDate = "2027-04-03",
        bookingStartDate = "2027-04-04", bookingEndDate = "2027-04-07",
        stayStartDate = "2027-05-01", stayEndDate = "2027-05-03")

    @Test fun legacySingleCampsiteRemainsReadable() {
        assertEquals(listOf("a"), lottery.offeredCampsiteIds())
        assertNull(lotteryListingError(lottery, listOf(site)))
    }

    @Test fun duplicateCampsitesAreRejectedBySubmissionValidation() {
        val duplicate = lottery.copy(campsiteIds = listOf("a", "a"))
        assertEquals("Duplicate campsites are not allowed.", lotteryListingError(duplicate, listOf(site)))
        assertThrows(IllegalArgumentException::class.java) { validateLottery(duplicate) }
    }

    @Test fun campsiteCountMatchesTheFirestoreOwnershipBudget() {
        val sites = (1..9).map { site.copy(id = "site-$it") }
        val draft = lottery.copy(campsiteId = sites.first().id, campsiteIds = sites.map { it.id })
        assertNotNull(lotteryListingError(draft, sites))
        assertThrows(IllegalArgumentException::class.java) { validateLottery(draft) }
        assertNull(lotteryListingError(draft.copy(campsiteIds = sites.take(8).map { it.id }), sites.take(8)))
    }

    @Test fun everyOfferingMustExistAndBelongToOwner() {
        val shared = lottery.copy(campsiteIds = listOf("a", "b"))
        assertNotNull(lotteryListingError(shared, listOf(site)))
        assertNotNull(lotteryListingError(shared, listOf(site, site.copy(id = "b", ownerId = "other"))))
        assertNull(lotteryListingError(shared, listOf(site, site.copy(id = "b"))))
    }

    @Test fun everyOfferingMustSupportTheStayPeriod() {
        val shared = lottery.copy(campsiteIds = listOf("a", "b"))
        assertNotNull(lotteryListingError(shared, listOf(site, site.copy(id = "b", availableFrom = "2027-05-02"))))
    }

    @Test fun sharedWinnerMaximumUsesCombinedOccupancy() {
        val sites = listOf(site, site.copy(id = "b", maxOccupancy = 2))
        val shared = lottery.copy(campsiteIds = listOf("a", "b"), numberOfWinners = 9)
        assertNull(lotteryListingError(shared, sites))
        assertNotNull(lotteryListingError(shared.copy(numberOfWinners = 10), sites))
        assertNull(lotteryListingError(shared.copy(numberOfWinners = 10, useMaxCapacity = true), sites))
        assertNotNull(lotteryListingError(shared.copy(numberOfWinners = 11, useMaxCapacity = true), sites))
    }

    @Test fun waitlistLimitIsOptionalButMustExceedWinnersWhenSet() {
        assertNull(lotteryWaitlistCapacityError(0, 5))
        assertNull(lotteryWaitlistCapacityError(6, 5))
        assertNotNull(lotteryWaitlistCapacityError(5, 5))
        assertNotNull(lotteryWaitlistCapacityError(4, 5))
        assertNotNull(lotteryWaitlistCapacityError(-1, 5))
        validateLottery(lottery.copy(waitingListCapacity = 0))
        assertThrows(IllegalArgumentException::class.java) { validateLottery(lottery.copy(waitingListCapacity = 5)) }
    }

    @Test fun noticesIncludeAllDistinctParticipatingEntrants() {
        assertEquals(listOf("one", "two"), lottery.copy(registered = listOf("one", "one"),
            pending = listOf("two")).participantIds())
    }

    @Test fun updatingPreservesConcurrentEntrantsAndTheirPartySizes() {
        val current = lottery.copy(registered = listOf("new-entrant"), registeredGroupSizes = mapOf("new-entrant" to 3))
        val updated = updatedLotteryListing(current, lottery.copy(numberOfWinners = 6))
        assertEquals(current.registered, updated.registered)
        assertEquals(current.registeredGroupSizes, updated.registeredGroupSizes)
        assertEquals(6, updated.numberOfWinners)
        assertEquals(1L, updated.listingRevision)
        assertFalse(updated.drawCompleted)
    }

    @Test fun cannotLowerWaitlistBelowCurrentEnrollment() {
        val current = lottery.copy(registered = (1..8).map { "entrant-$it" })
        assertThrows(IllegalArgumentException::class.java) {
            updatedLotteryListing(current, lottery.copy(waitingListCapacity = 7))
        }
        assertEquals(0, updatedLotteryListing(current, lottery.copy(waitingListCapacity = 0)).waitingListCapacity)
    }

    @Test fun drawingOrBookingPreventsBothOwnerMutations() {
        for (state in listOf(lottery.copy(drawCompleted = true), lottery.copy(pending = listOf("winner")),
            lottery.copy(booked = listOf(Reservation(entrantId = "winner"))))) {
            assertThrows(IllegalStateException::class.java) { requireLotteryEditable(state) }
            assertThrows(IllegalStateException::class.java) { updatedLotteryListing(state, lottery) }
        }
    }

    @Test fun oneCampsiteBookingDoesNotBlockAnotherOffering() {
        val shared = lottery.copy(campsiteIds = listOf("a", "b"), booked = listOf(
            Reservation(entrantId = "winner", campsiteId = "a", checkInDate = "2027-05-01", checkOutDate = "2027-05-03")))
        assertFalse(isNightOpen("2027-05-01", shared, site))
        assertTrue(isNightOpen("2027-05-01", shared, site.copy(id = "b")))
    }
}
