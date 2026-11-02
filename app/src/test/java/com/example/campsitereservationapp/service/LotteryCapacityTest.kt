package com.example.campsitereservationapp.service

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LotteryCapacityTest {
    @Test
    fun manualCapacityMustBeStrictlyBelowMaximum() {
        assertNull(lotteryCapacityError(winners = 7, maximum = 8, useMaxCapacity = false))
        assertNotNull(lotteryCapacityError(winners = 8, maximum = 8, useMaxCapacity = false))
        assertNotNull(lotteryCapacityError(winners = 9, maximum = 8, useMaxCapacity = false))
    }

    @Test
    fun maxSelectionRequiresExactlyTheCampsiteMaximum() {
        assertNull(lotteryCapacityError(winners = 8, maximum = 8, useMaxCapacity = true))
        assertNotNull(lotteryCapacityError(winners = 7, maximum = 8, useMaxCapacity = true))
        assertNotNull(lotteryCapacityError(winners = 9, maximum = 8, useMaxCapacity = true))
    }

    @Test
    fun rejectsNonPositiveCapacityWithOrWithoutMaxSelected() {
        for (useMax in listOf(false, true)) {
            assertNotNull(lotteryCapacityError(winners = 0, maximum = 8, useMaxCapacity = useMax))
            assertNotNull(lotteryCapacityError(winners = -1, maximum = 8, useMaxCapacity = useMax))
        }
    }

    @Test
    fun rejectsMissingOrInvalidCampsiteMaximum() {
        for (maximum in listOf(null, 0, -1)) {
            for (useMax in listOf(false, true)) {
                assertNotNull(lotteryCapacityError(winners = 1, maximum = maximum, useMaxCapacity = useMax))
            }
        }
    }

    @Test
    fun oneGuestCampsiteRequiresMaxSelected() {
        assertNull(lotteryCapacityError(winners = 1, maximum = 1, useMaxCapacity = true))
        assertNotNull(lotteryCapacityError(winners = 1, maximum = 1, useMaxCapacity = false))
    }

    @Test
    fun rejectsStaleMaximumAfterCampsiteCapacityChanges() {
        assertNotNull(lotteryCapacityError(winners = 8, maximum = 10, useMaxCapacity = true))
        assertNotNull(lotteryCapacityError(winners = 7, maximum = 6, useMaxCapacity = false))
    }
}
