package com.example.campsitereservationapp.messaging

import com.example.campsitereservationapp.model.RESERVATION_CONFIRMED
import com.example.campsitereservationapp.model.Reservation

/** Both sides use the reservation ID as their conversation ID. */
internal data class ReservationConversation(val reservation: Reservation, val ownerId: String) {
    fun canRead(userId: String): Boolean = userId.isNotBlank() && ownerId.isNotBlank() &&
        (userId == reservation.entrantId || userId == ownerId)

    fun sendError(userId: String): String? = when {
        !canRead(userId) -> "You can only message about your own reservations."
        userId == ownerId -> null
        reservation.status != RESERVATION_CONFIRMED -> "This reservation is no longer confirmed. You can still read your messages."
        else -> null
    }
}

internal fun canMessageOwner(reservation: Reservation): Boolean = reservation.status == RESERVATION_CONFIRMED

internal const val MAX_MESSAGE_LENGTH = 1000

internal fun messageTextError(draft: String): String? = when {
    draft.isBlank() -> "Write a message first."
    draft.trim().length > MAX_MESSAGE_LENGTH -> "Keep your message under 1,000 characters."
    else -> null
}
