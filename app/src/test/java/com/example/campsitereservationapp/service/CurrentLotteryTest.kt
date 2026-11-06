package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Lottery
import org.junit.Assert.*
import org.junit.Test

/*
 * Unit tests for currentLottery, which picks the lottery whose waiting list a campsite
 * owner sees (US 02.09.01).
 */
class CurrentLotteryTest {

    private val summerDraw = Lottery(
        id = "lottery-summer",
        ownerId = "owner-maria",
        campsiteId = "campsite-two-jack",
        numberOfWinners = 4,
        registrationStartDate = "2027-05-01",
        registrationEndDate = "2027-05-15",
        registered = listOf("entrant-amir", "entrant-jen")
    )

    private val springDraw = summerDraw.copy(
        id = "lottery-spring",
        registrationStartDate = "2027-03-01",
        registrationEndDate = "2027-03-15",
        drawCompleted = true
    )

    @Test
    fun noLotteriesMeansNoCurrentLottery() {
        assertNull(currentLottery(emptyList()))
    }

    @Test
    fun undrawnLotteryIsCurrent() {
        assertEquals("lottery-summer", currentLottery(listOf(summerDraw))?.id)
    }

    @Test
    fun drawnLotteryIsNotCurrent() {
        assertNull(currentLottery(listOf(springDraw)))
    }

    @Test
    fun drawnLotteriesAreSkipped() {
        assertEquals("lottery-summer", currentLottery(listOf(springDraw, summerDraw))?.id)
    }

    @Test
    fun earliestRegistrationIsPickedWhenSeveralAreRunning() {
        val fallDraw = summerDraw.copy(id = "lottery-fall", registrationStartDate = "2027-08-01",
            registrationEndDate = "2027-08-15")
        assertEquals("lottery-summer", currentLottery(listOf(fallDraw, summerDraw))?.id)
    }
}
