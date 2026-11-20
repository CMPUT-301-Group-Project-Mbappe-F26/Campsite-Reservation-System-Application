package com.example.campsitereservationapp.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwnerNotificationTest {
    @Test
    fun validNotificationHasNoError() {
        assertNull(ownerNotificationError("Lottery update", "Registration closes tomorrow.", 4))
    }

    @Test
    fun titleAndMessageAreRequired() {
        assertEquals("Enter a notification title.", ownerNotificationError("  ", "Message", 1))
        assertEquals("Enter a notification message.", ownerNotificationError("Title", "  ", 1))
    }

    @Test
    fun notificationNeedsAtLeastOneEntrant() {
        assertEquals(
            "There are no entrants to notify.",
            ownerNotificationError("Title", "Message", 0)
        )
    }

    @Test
    fun longNotificationIsRejected() {
        assertEquals(
            "Title must be $MAX_NOTIFICATION_TITLE characters or less.",
            ownerNotificationError("A".repeat(MAX_NOTIFICATION_TITLE + 1), "Message", 1)
        )
        assertEquals(
            "Message must be $MAX_NOTIFICATION_MESSAGE characters or less.",
            ownerNotificationError("Title", "A".repeat(MAX_NOTIFICATION_MESSAGE + 1), 1)
        )
    }
}
