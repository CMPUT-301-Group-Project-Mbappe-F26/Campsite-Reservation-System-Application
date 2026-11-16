package com.example.campsitereservationapp.model

import org.junit.Assert.*
import org.junit.Test

/** Regression coverage for shared-lottery selection, occupied dates, and campsite eligibility. */
class LotteryReservationSelectionTest {
    private val today = "2027-06-01"
    private val site = Campsite(id = "first", ownerId = "owner", name = "First", published = true, maxOccupancy = 6)
    private val lottery = Lottery(id = "draw", ownerId = "owner", campsiteId = "first",
        campsiteIds = listOf("first", "second"), numberOfWinners = 3, pending = listOf("winner"),
        registeredGroupSizes = mapOf("winner" to 4), stayStartDate = "2027-07-01", stayEndDate = "2027-07-04")
    private fun choice(site: Campsite? = this.site, lottery: Lottery = this.lottery, blocked: Set<String> = emptySet(),
        today: String = this.today) = reservationCampsiteChoice("first", site, lottery, "winner", blocked, today)

    @Test fun campsiteWithAnyOpenNightCanBeSelected() {
        val result = choice(blocked = setOf("2027-07-01", "2027-07-02"))
        assertTrue(result.selectable)
        assertEquals("2027-07-03", result.firstAvailableNight)
    }

    @Test fun campsiteWithoutOpenNightsIsDisabled() {
        val result = choice(blocked = setOf("2027-07-01", "2027-07-02", "2027-07-03"))
        assertFalse(result.selectable)
        assertEquals("No available dates in this lottery.", result.unavailableReason)
    }

    @Test fun seasonAndWeekdaysConstrainSelection() {
        assertFalse(choice(site.copy(availableFrom = "2027-08-01")).selectable)
        assertFalse(choice(site.copy(availableUntil = "2027-06-30")).selectable)
        assertFalse(choice(site.copy(availableDays = listOf(1))).selectable) // Thu/Fri/Sat stay, no Monday.
        val friday = choice(site.copy(availableDays = listOf(5)))
        assertTrue(friday.selectable)
        assertEquals("2027-07-02", friday.firstAvailableNight)
    }

    @Test fun lastOfferedNightCanBeBookedWithCheckoutOutsideTheSeason() {
        val result = choice(site.copy(availableFrom = "2027-07-03", availableUntil = "2027-07-03"))
        assertTrue(result.selectable)
        assertEquals("2027-07-03", result.firstAvailableNight)
        assertFalse(isNightOpen("2027-07-04", lottery, site, emptySet()))
    }

    @Test fun missingUnpublishedTransferredAndUnlistedCampsitesAreDisabled() {
        assertFalse(choice(null).selectable)
        assertFalse(choice(site.copy(published = false)).selectable)
        assertFalse(choice(site.copy(ownerId = "another-owner")).selectable)
        assertFalse(choice(site.copy(id = "outside-lottery")).selectable)
    }

    @Test fun partyMustFitAnIndividualCampsite() {
        assertFalse(choice(site.copy(maxOccupancy = 3)).selectable)
        assertTrue(choice(site.copy(maxOccupancy = 4)).selectable)
        assertFalse(choice(site.copy(maxOccupancy = null)).selectable)
    }

    @Test fun losingAndFullyBookedLotteriesCannotProvideChoices() {
        assertFalse(choice(lottery = lottery.copy(pending = emptyList())).selectable)
        assertFalse(choice(lottery = lottery.copy(numberOfWinners = 1,
            booked = listOf(Reservation(entrantId = "another-winner")))).selectable)
    }

    @Test fun pastNightsAreNotAvailableButTodayCanBe() {
        assertFalse(choice(today = "2027-07-04").selectable)
        assertEquals("2027-07-03", choice(today = "2027-07-03").firstAvailableNight)
    }

    @Test fun invalidAndEmptyStayPeriodsDoNotOfferDates() {
        assertFalse(choice(lottery = lottery.copy(stayStartDate = "", stayEndDate = "")).selectable)
        assertFalse(choice(lottery = lottery.copy(stayEndDate = lottery.stayStartDate)).selectable)
    }

    @Test fun occupiedNightsAreCombinedAcrossLotteriesAndScopedToOneCampsite() {
        val first = Reservation(id = "r1", entrantId = "one", campsiteId = "first",
            checkInDate = "2027-07-01", checkOutDate = "2027-07-02")
        val second = first.copy(id = "r2", entrantId = "two", checkInDate = "2027-07-02", checkOutDate = "2027-07-04")
        val otherSite = first.copy(id = "r3", campsiteId = "second")
        val related = listOf(lottery.copy(booked = listOf(first, otherSite)), lottery.copy(id = "other", booked = listOf(second)))
        val occupied = occupiedCampsiteNights("first", related)
        assertEquals(setOf("2027-07-01", "2027-07-02", "2027-07-03"), occupied)
        assertFalse(choice(blocked = occupied).selectable)
        assertEquals(setOf("2027-07-01"), occupiedCampsiteNights("second", related))
    }

    @Test fun cancelledRejectedAndReleasedBookingsDoNotBlockNights() {
        val reservation = Reservation(entrantId = "cancelled", campsiteId = "first",
            checkInDate = "2027-07-01", checkOutDate = "2027-07-04")
        for (state in listOf(lottery.copy(booked = listOf(reservation.copy(status = RESERVATION_CANCELLED))),
            lottery.copy(booked = listOf(reservation.copy(status = RESERVATION_REJECTED))),
            lottery.copy(booked = listOf(reservation), cancelledEntrantIds = listOf("cancelled")))) {
            assertTrue(occupiedCampsiteNights("first", listOf(state)).isEmpty())
        }
    }

    @Test fun legacyBookingsWithoutCampsiteIdsBelongOnlyToTheirPrimaryCampsite() {
        val legacy = lottery.copy(campsiteIds = emptyList(), booked = listOf(
            Reservation(entrantId = "old", checkInDate = "2027-07-01", checkOutDate = "2027-07-02")))
        assertEquals(setOf("2027-07-01"), occupiedCampsiteNights("first", listOf(legacy)))
        assertTrue(occupiedCampsiteNights("second", listOf(legacy)).isEmpty())
    }

    @Test fun selectionAndPickerUseTheSameBlockedNights() {
        val result = choice(blocked = setOf("2027-07-01"))
        assertFalse(isNightOpen("2027-07-01", lottery, site, result.blockedNights))
        assertTrue(isNightOpen(requireNotNull(result.firstAvailableNight), lottery, site, result.blockedNights))
    }
}
