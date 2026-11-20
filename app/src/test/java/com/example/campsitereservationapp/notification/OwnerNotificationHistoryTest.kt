package com.example.campsitereservationapp.notification

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class OwnerNotificationHistoryTest {
    @Test
    fun notificationsAreNewestFirst() {
        val older = SentNotification("1", "Older", "Message", 2, "Camp", Date(1000))
        val newer = SentNotification("2", "Newer", "Message", 3, "Camp", Date(2000))

        assertEquals(listOf(newer, older), newestNotificationsFirst(listOf(older, newer)))
    }

    @Test
    fun notificationsWithoutDatesGoLast() {
        val dated = SentNotification("1", "Dated", "Message", 2, "Camp", Date(1000))
        val missingDate = SentNotification("2", "No date", "Message", 2, "Camp", null)

        assertEquals(listOf(dated, missingDate), newestNotificationsFirst(listOf(missingDate, dated)))
    }
}
