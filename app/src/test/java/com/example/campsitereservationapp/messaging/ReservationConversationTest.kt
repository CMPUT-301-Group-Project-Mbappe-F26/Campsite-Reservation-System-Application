package com.example.campsitereservationapp.messaging

import com.example.campsitereservationapp.model.*
import org.junit.Assert.*
import org.junit.Test

class ReservationConversationTest {
    private val booking = Reservation(id = "booking", entrantId = "guest", campsiteId = "site")

    @Test
    fun confirmedEntrantCanStartAConversationWithoutAnOwnerMessage() {
        val conversation = ReservationConversation(booking, "owner")
        assertTrue(canMessageOwner(booking))
        assertTrue(conversation.canRead("guest"))
        assertNull(conversation.sendError("guest"))
        assertNull(conversation.sendError("owner"))
    }

    @Test
    fun unrelatedAccountsAndMissingOwnersCannotUseTheConversation() {
        val conversation = ReservationConversation(booking, "owner")
        listOf("stranger", "", "other-owner").forEach {
            assertFalse(conversation.canRead(it))
            assertNotNull(conversation.sendError(it))
        }
        assertFalse(ReservationConversation(booking, "").canRead("guest"))
    }

    @Test
    fun cancelledAndRejectedBookingsKeepHistoryButDisableEntrantSending() {
        listOf(RESERVATION_CANCELLED, RESERVATION_REJECTED).forEach { status ->
            val changed = booking.copy(status = status)
            val conversation = ReservationConversation(changed, "owner")
            assertFalse(canMessageOwner(changed))
            assertTrue(conversation.canRead("guest"))
            assertNotNull(conversation.sendError("guest"))
            // Preserve the existing owner's ability to discuss a cancellation or rejection.
            assertNull(conversation.sendError("owner"))
        }
    }

    @Test
    fun pastConfirmedReservationsStillAllowQuestionsAboutTheStay() {
        assertTrue(canMessageOwner(booking.copy(checkOutDate = "2020-01-01")))
    }

    @Test
    fun messageValidationRejectsBlankAndOversizedMessages() {
        assertNotNull(messageTextError(" \n\t"))
        assertNotNull(messageTextError("a".repeat(1001)))
        assertNull(messageTextError("a".repeat(1000)))
        assertNull(messageTextError("  What time is check-in?  "))
    }
}
