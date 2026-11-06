package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Lottery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class WaitingListLeaveTest {
    private val lottery = Lottery(
        id = "lottery",
        ownerId = "owner",
        campsiteId = "campsite",
        registrationStartDate = "2027-04-01",
        registrationEndDate = "2027-04-07",
        registered = listOf("entrant"),
        registeredGroupSizes = mapOf("entrant" to 4)
    )

    @Test
    fun allowsLeavingThroughRegistrationEnd() {
        assertNull(waitingListLeaveError(lottery, "entrant", instant("2027-04-07 23:59")))
    }

    @Test
    fun rejectsLeavingAfterRegistrationCloses() {
        assertEquals(
            "Registration has closed. You can no longer leave the waiting list.",
            waitingListLeaveError(lottery, "entrant", instant("2027-04-08 00:00"))
        )
    }

    @Test
    fun rejectsEntrantWhoIsNotRegistered() {
        assertEquals(
            "You are not on this lottery's waiting list.",
            waitingListLeaveError(lottery, "other", instant("2027-04-05 12:00"))
        )
    }

    private fun instant(value: String): Long = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("GMT-06:00")
    }.parse(value)!!.time
}
