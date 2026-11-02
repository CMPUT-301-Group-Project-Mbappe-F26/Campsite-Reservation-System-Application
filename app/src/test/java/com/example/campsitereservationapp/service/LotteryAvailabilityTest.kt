package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.reservationDateToUtcMillis
import com.example.campsitereservationapp.model.reservationError
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun offeredStayFitsTheCampsiteSeason() {
        assertNull(lotteryStayAvailabilityError("2027-05-10", "2027-05-12", campsite))
        assertNotNull(lotteryStayAvailabilityError("2027-04-30", "2027-05-12", campsite))
        assertNotNull(lotteryStayAvailabilityError("2027-05-10", "2027-06-02", campsite))
    }

    @Test
    fun checkoutMayFollowTheLastAvailableNight() {
        assertNull(lotteryStayAvailabilityError("2027-05-01", "2027-06-01", campsite))
        assertNull(lotteryStayAvailabilityError("2027-05-31", "2027-06-01", campsite))
        assertNotNull(lotteryStayAvailabilityError("2027-06-01", "2027-06-02", campsite))
    }

    @Test
    fun offeredStayRequiresAtLeastOneNight() {
        assertNotNull(lotteryStayAvailabilityError("2027-05-10", "2027-05-10", campsite))
        assertNotNull(lotteryStayAvailabilityError("2027-05-12", "2027-05-10", campsite))
    }

    @Test
    fun offeredStaySupportsUnboundedAndSingleBoundSeasons() {
        assertNull(lotteryStayAvailabilityError("2027-01-01", "2028-01-01", Campsite()))
        assertNull(lotteryStayAvailabilityError("2027-05-01", "2028-01-01", campsite.copy(availableUntil = "")))
        assertNull(lotteryStayAvailabilityError("2026-01-01", "2027-06-01", campsite.copy(availableFrom = "")))
    }

    @Test
    fun firstAndLastOfferedNightsMustBeAvailableWeekdays() {
        val weekends = campsite.copy(availableDays = listOf(6, 7))
        assertNotNull(lotteryStayAvailabilityError("2027-05-03", "2027-05-10", weekends)) // Monday first night
        assertNotNull(lotteryStayAvailabilityError("2027-05-02", "2027-05-04", weekends)) // Monday last night
        assertNull(lotteryStayAvailabilityError("2027-05-01", "2027-05-03", weekends))
    }

    @Test
    fun oneLotteryCanOfferSeveralWeekendsWithoutOfferingClosedWeekdaysToReservations() {
        val weekends = campsite.copy(availableDays = listOf(6, 7))
        assertNull(lotteryStayAvailabilityError("2027-05-01", "2027-05-31", weekends))
        val offered = Lottery(
            stayStartDate = "2027-05-01", stayEndDate = "2027-05-31", numberOfWinners = 2,
            pending = listOf("winner"), bookingStartDate = "2027-04-08", bookingEndDate = "2027-04-30"
        )
        assertNotNull(reservationError(offered, weekends, "winner", "2027-05-03", "2027-05-04", true, "2027-04-10"))
        assertNull(reservationError(offered, weekends, "winner", "2027-05-01", "2027-05-03", true, "2027-04-10"))
    }

    @Test
    fun checkoutDayDoesNotNeedToBeAnAvailableWeekday() {
        assertNull(lotteryStayAvailabilityError("2027-05-02", "2027-05-03", campsite.copy(availableDays = listOf(7))))
    }

    @Test
    fun changedAvailabilityInvalidatesAnOldFormSelection() {
        assertNull(lotteryStayAvailabilityError("2027-05-10", "2027-05-12", campsite))
        assertNotNull(lotteryStayAvailabilityError("2027-05-10", "2027-05-12", campsite.copy(availableFrom = "2027-05-11")))
        assertNotNull(lotteryStayAvailabilityError("2027-05-10", "2027-05-12", campsite.copy(availableUntil = "2027-05-10")))
        assertNotNull(lotteryStayAvailabilityError("2027-05-10", "2027-05-12", campsite.copy(availableDays = listOf(6, 7))))
    }

    @Test
    fun reservationValidationUsesEditedCampsiteAvailabilityForExistingLotteries() {
        val offered = Lottery(
            stayStartDate = "2027-05-01", stayEndDate = "2027-06-01", numberOfWinners = 2,
            pending = listOf("winner"), bookingStartDate = "2027-04-08", bookingEndDate = "2027-04-30"
        )
        assertNull(reservationError(offered, campsite, "winner", "2027-05-10", "2027-05-12", true, "2027-04-10"))
        assertNotNull(reservationError(offered, campsite.copy(availableUntil = "2027-05-10"), "winner", "2027-05-10", "2027-05-12", true, "2027-04-10"))
    }

    @Test
    fun pickerDisablesDatesOutsideSeasonAndAllowsCheckoutAfterTheFinalNight() {
        fun selectable(date: String, checkout: Boolean = false) =
            isLotteryStayDateSelectable(reservationDateToUtcMillis(date)!!, campsite, checkout)
        assertFalse(selectable("2027-04-30"))
        assertTrue(selectable("2027-05-01"))
        assertTrue(selectable("2027-05-31"))
        assertFalse(selectable("2027-06-01"))
        assertFalse(selectable("2027-05-01", checkout = true))
        assertTrue(selectable("2027-06-01", checkout = true))
        assertFalse(selectable("2027-06-02", checkout = true))
    }

    @Test
    fun pickerDisablesClosedNightsAndUsesThePrecedingNightForCheckout() {
        val weekends = campsite.copy(availableDays = listOf(6, 7))
        assertFalse(isLotteryStayDateSelectable(reservationDateToUtcMillis("2027-05-03")!!, weekends, checkout = false))
        assertTrue(isLotteryStayDateSelectable(reservationDateToUtcMillis("2027-05-03")!!, weekends, checkout = true))
        assertFalse(isLotteryStayDateSelectable(reservationDateToUtcMillis("2027-05-04")!!, weekends, checkout = true))
    }

    @Test
    fun invalidStayDatesAndCampsiteWeekdaysAreRejected() {
        assertNotNull(lotteryStayAvailabilityError("2027-02-29", "2027-05-12", campsite))
        assertNotNull(lotteryStayAvailabilityError("2027-05-10", "2027-5-12", campsite))
        assertNotNull(lotteryStayAvailabilityError("2027-05-10", "2027-05-12", campsite.copy(availableDays = listOf(0, 8))))
    }
}
