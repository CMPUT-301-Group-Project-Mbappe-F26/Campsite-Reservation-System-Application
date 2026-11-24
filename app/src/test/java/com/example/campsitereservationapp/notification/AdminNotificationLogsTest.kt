package com.example.campsitereservationapp.notification

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class AdminNotificationLogsTest {
    private val ownerLog = AdminNotificationLog(
        id = "owner",
        type = NotificationLogType.OWNER,
        sender = "Daniel Roy",
        recipients = listOf("Maya Thompson", "Amir Khan"),
        title = "Road closure on Hwy 40",
        message = "Use the Hwy 16 detour.",
        campsite = "Patricia Lake",
        sentAt = Date(1000)
    )
    private val systemLog = AdminNotificationLog(
        id = "system",
        type = NotificationLogType.SYSTEM,
        sender = "System",
        recipients = listOf("Maya Thompson"),
        title = "Reservation confirmed",
        message = "Your booking is confirmed.",
        campsite = "Two Jack Lake",
        sentAt = Date(2000)
    )

    @Test
    fun logsAreNewestFirst() {
        assertEquals(listOf(systemLog, ownerLog), newestNotificationLogsFirst(listOf(ownerLog, systemLog)))
    }

    @Test
    fun searchChecksTheRequiredLogFields() {
        val logs = listOf(ownerLog, systemLog)

        assertEquals(listOf(ownerLog), filterNotificationLogs(logs, "Daniel", null))
        assertEquals(listOf(ownerLog), filterNotificationLogs(logs, "Amir", null))
        assertEquals(listOf(systemLog), filterNotificationLogs(logs, "Two Jack", null))
        assertEquals(listOf(systemLog), filterNotificationLogs(logs, "booking is confirmed", null))
    }

    @Test
    fun typeFilterShowsOnlyTheSelectedKind() {
        assertEquals(
            listOf(ownerLog),
            filterNotificationLogs(listOf(ownerLog, systemLog), "", NotificationLogType.OWNER)
        )
    }
}
