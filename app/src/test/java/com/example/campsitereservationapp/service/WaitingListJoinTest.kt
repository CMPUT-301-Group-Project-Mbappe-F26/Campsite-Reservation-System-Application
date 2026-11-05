package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.Reservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class WaitingListJoinTest {
    private val lottery = Lottery(
        id = "lottery",
        ownerId = "owner",
        campsiteId = "campsite",
        numberOfWinners = 5,
        registrationStartDate = "2027-04-01",
        registrationEndDate = "2027-04-07",
        bookingStartDate = "2027-04-08",
        bookingEndDate = "2027-04-15",
        stayStartDate = "2027-05-01",
        stayEndDate = "2027-05-03"
    )
    private val campsite = Campsite(id = "campsite", maxOccupancy = 6, published = true)
    private val duringRegistration = instant("2027-04-05 12:00")

    @Test
    fun acceptsPositivePartyAtCampsiteMaximumDuringRegistration() {
        assertNull(waitingListJoinError(lottery, campsite, "entrant", 6, duringRegistration))
    }

    @Test
    fun registrationStartAndEndDatesAreInclusive() {
        assertNull(waitingListJoinError(lottery, campsite, "entrant", 1, instant("2027-04-01 00:00")))
        assertNull(waitingListJoinError(lottery, campsite, "entrant", 1, instant("2027-04-07 23:59")))
    }

    @Test
    fun rejectsInvalidOrOversizedParty() {
        assertEquals(
            "Party size must be at least 1.",
            waitingListJoinError(lottery, campsite, "entrant", 0, duringRegistration)
        )
        assertEquals(
            "Party size cannot exceed this campsite's maximum occupancy of 6.",
            waitingListJoinError(lottery, campsite, "entrant", 7, duringRegistration)
        )
    }

    @Test
    fun rejectsBeforeOpeningAndAfterClosing() {
        assertEquals(
            "Registration for this lottery has not opened yet.",
            waitingListJoinError(lottery, campsite, "entrant", 1, instant("2027-03-31 23:59"))
        )
        assertEquals(
            "Registration for this lottery has closed.",
            waitingListJoinError(lottery, campsite, "entrant", 1, instant("2027-04-08 00:00"))
        )
    }

    @Test
    fun rejectsDuplicateAcrossEveryParticipantState() {
        assertEquals(
            "You are already on this lottery's waiting list.",
            waitingListJoinError(lottery.copy(registered = listOf("entrant")), campsite, "entrant", 1, duringRegistration)
        )
        assertEquals(
            "You are already on this lottery's waiting list.",
            waitingListJoinError(lottery.copy(pending = listOf("entrant")), campsite, "entrant", 1, duringRegistration)
        )
        assertEquals(
            "You are already on this lottery's waiting list.",
            waitingListJoinError(
                lottery.copy(booked = listOf(Reservation(entrantId = "entrant"))),
                campsite,
                "entrant",
                1,
                duringRegistration
            )
        )
    }

    @Test
    fun rejectsFullWaitingListUsingDistinctEntrants() {
        val full = lottery.copy(
            waitingListCapacity = 2,
            registered = listOf("first", "first", "second")
        )
        assertEquals(
            "This lottery's waiting list is full.",
            waitingListJoinError(full, campsite, "entrant", 1, duringRegistration)
        )
    }

    @Test
    fun rejectsOwnerAndCompletedDraw() {
        assertEquals(
            "Campsite owners cannot enter their own lottery.",
            waitingListJoinError(lottery, campsite, "owner", 1, duringRegistration)
        )
        assertEquals(
            "This lottery has already been drawn.",
            waitingListJoinError(lottery.copy(drawCompleted = true), campsite, "entrant", 1, duringRegistration)
        )
    }

    @Test
    fun albertaPermanentOffsetChangesDateAtUtcMinusSix() {
        val utc = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        assertEquals("2026-11-03", lotteryAlbertaDate(utc.parse("2026-11-04 05:59")!!.time))
        assertEquals("2026-11-04", lotteryAlbertaDate(utc.parse("2026-11-04 06:00")!!.time))
    }

    private fun instant(value: String): Long = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("GMT-06:00")
    }.parse(value)!!.time
}
