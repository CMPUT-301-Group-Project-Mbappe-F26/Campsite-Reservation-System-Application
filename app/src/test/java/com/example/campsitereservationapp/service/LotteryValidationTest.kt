package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Lottery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LotteryValidationTest {
    private val valid = Lottery(
        id = "lottery", ownerId = "owner", campsiteId = "campsite", numberOfWinners = 5,
        registrationStartDate = "2027-04-01", registrationEndDate = "2027-04-07",
        bookingStartDate = "2027-04-08", bookingEndDate = "2027-04-15",
        stayStartDate = "2027-05-01", stayEndDate = "2027-05-03"
    )

    @Test
    fun acceptsBookingStartingTheDayAfterRegistrationEnds() {
        validateLottery(valid)
    }

    @Test
    fun rejectsMissingOrImpossibleDates() {
        assertThrows(IllegalArgumentException::class.java) { validateLottery(valid.copy(registrationStartDate = "")) }
        assertThrows(IllegalArgumentException::class.java) { validateLottery(valid.copy(registrationStartDate = "2027-02-29")) }
    }

    @Test
    fun rejectsNonPositiveWinnerCount() {
        assertThrows(IllegalArgumentException::class.java) { validateLottery(valid.copy(numberOfWinners = 0)) }
        assertThrows(IllegalArgumentException::class.java) { validateLottery(valid.copy(numberOfWinners = -1)) }
    }

    @Test
    fun rejectsReversedPeriodsAndZeroNightStay() {
        assertThrows(IllegalArgumentException::class.java) { validateLottery(valid.copy(registrationEndDate = "2027-03-31")) }
        assertThrows(IllegalArgumentException::class.java) { validateLottery(valid.copy(bookingEndDate = "2027-04-07")) }
        assertThrows(IllegalArgumentException::class.java) { validateLottery(valid.copy(stayEndDate = valid.stayStartDate)) }
    }

    @Test
    fun rejectsRegistrationAndBookingSharingADay() {
        assertRegistrationConflict(valid.copy(bookingStartDate = valid.registrationEndDate))
    }

    @Test
    fun rejectsBookingStartingDuringRegistration() {
        assertRegistrationConflict(valid.copy(bookingStartDate = "2027-04-05"))
    }

    @Test
    fun rejectsBookingEntirelyWithinRegistration() {
        assertRegistrationConflict(valid.copy(bookingStartDate = "2027-04-02", bookingEndDate = "2027-04-06"))
    }

    @Test
    fun rejectsBookingEnclosingRegistration() {
        assertRegistrationConflict(valid.copy(bookingStartDate = "2027-03-31"))
    }

    @Test
    fun rejectsIdenticalRegistrationAndBookingPeriods() {
        assertRegistrationConflict(valid.copy(
            bookingStartDate = valid.registrationStartDate,
            bookingEndDate = valid.registrationEndDate
        ))
    }

    @Test
    fun rejectsBookingEntirelyBeforeRegistrationEvenWithoutOverlap() {
        assertRegistrationConflict(valid.copy(bookingStartDate = "2027-03-20", bookingEndDate = "2027-03-30"))
    }

    @Test
    fun rejectsSingleDayRegistrationAndBookingOnTheSameDate() {
        assertRegistrationConflict(valid.copy(
            registrationStartDate = "2027-04-07", registrationEndDate = "2027-04-07",
            bookingStartDate = "2027-04-07", bookingEndDate = "2027-04-07"
        ))
    }

    private fun assertRegistrationConflict(lottery: Lottery) {
        val exception = assertThrows(IllegalArgumentException::class.java) { validateLottery(lottery) }
        assertEquals(
            "Booking must start on a later date than registration ends. The periods cannot share a date or overlap.",
            exception.message
        )
    }

    @Test
    fun rejectsBookingDuringTheStay() {
        assertThrows(IllegalArgumentException::class.java) {
            validateLottery(valid.copy(bookingEndDate = valid.stayStartDate))
        }
    }
}
