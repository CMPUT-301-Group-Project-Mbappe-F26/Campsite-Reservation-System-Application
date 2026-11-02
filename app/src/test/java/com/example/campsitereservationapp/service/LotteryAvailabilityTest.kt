package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Campsite
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LotteryAvailabilityTest {
    private val campsite = Campsite(availableFrom = "2027-05-01", availableUntil = "2027-05-31")

    @Test
    fun acceptsPeriodInsideCampsiteAvailability() {
        assertNull(lotteryPeriodAvailabilityError("2027-05-10", "2027-05-12", campsite))
    }

    @Test
    fun acceptsExactInclusiveAvailabilityBoundaries() {
        assertNull(lotteryPeriodAvailabilityError("2027-05-01", "2027-05-31", campsite))
    }

    @Test
    fun rejectsStartBeforeCampsiteOpens() {
        assertNotNull(lotteryPeriodAvailabilityError("2027-04-30", "2027-05-12", campsite))
    }

    @Test
    fun rejectsEndAfterCampsiteCloses() {
        assertNotNull(lotteryPeriodAvailabilityError("2027-05-10", "2027-06-01", campsite))
    }

    @Test
    fun rejectsPeriodsEntirelyBeforeOrAfterAvailability() {
        assertNotNull(lotteryPeriodAvailabilityError("2027-04-01", "2027-04-02", campsite))
        assertNotNull(lotteryPeriodAvailabilityError("2027-06-01", "2027-06-02", campsite))
    }

    @Test
    fun blankAvailabilityBoundsRemainUnrestricted() {
        assertNull(lotteryPeriodAvailabilityError("2027-01-01", "2027-12-31", Campsite()))
    }

    @Test
    fun supportsAvailabilityWithOnlyOneBound() {
        assertNull(lotteryPeriodAvailabilityError("2027-05-01", "2028-01-01", campsite.copy(availableUntil = "")))
        assertNotNull(lotteryPeriodAvailabilityError("2027-04-30", "2027-05-01", campsite.copy(availableUntil = "")))
        assertNull(lotteryPeriodAvailabilityError("2026-01-01", "2027-05-31", campsite.copy(availableFrom = "")))
        assertNotNull(lotteryPeriodAvailabilityError("2027-05-31", "2027-06-01", campsite.copy(availableFrom = "")))
    }

    @Test
    fun rejectsInvalidOrReversedSelectedDates() {
        for (start in listOf("", "2027-5-01", "2027-02-29")) {
            assertNotNull(lotteryPeriodAvailabilityError(start, "2027-05-12", campsite))
        }
        assertNotNull(lotteryPeriodAvailabilityError("2027-05-12", "2027-05-10", campsite))
    }

    @Test
    fun rejectsInvalidOrReversedStoredCampsiteAvailability() {
        assertNotNull(lotteryPeriodAvailabilityError("2027-05-10", "2027-05-12", campsite.copy(availableFrom = "2027-02-29")))
        assertNotNull(lotteryPeriodAvailabilityError("2027-05-10", "2027-05-12", campsite.copy(availableUntil = "2027-13-01")))
        assertNotNull(lotteryPeriodAvailabilityError("2027-05-10", "2027-05-12", campsite.copy(availableFrom = "2027-06-01")))
    }

    @Test
    fun sameDayPeriodIsValidWithinSeasonBounds() {
        assertNull(lotteryPeriodAvailabilityError("2027-05-15", "2027-05-15", campsite))
    }
}
