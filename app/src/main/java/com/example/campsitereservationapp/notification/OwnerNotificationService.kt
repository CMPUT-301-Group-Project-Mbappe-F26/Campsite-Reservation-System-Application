package com.example.campsitereservationapp.notification

import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.offeredCampsiteIds
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

internal const val MAX_NOTIFICATION_TITLE = 80
internal const val MAX_NOTIFICATION_MESSAGE = 300

/** Returns an error for an invalid owner notification, or null when it can be sent. */
internal fun ownerNotificationError(title: String, message: String, recipientCount: Int): String? = when {
    title.isBlank() -> "Enter a notification title."
    title.trim().length > MAX_NOTIFICATION_TITLE -> "Title must be $MAX_NOTIFICATION_TITLE characters or less."
    message.isBlank() -> "Enter a notification message."
    message.trim().length > MAX_NOTIFICATION_MESSAGE -> "Message must be $MAX_NOTIFICATION_MESSAGE characters or less."
    recipientCount <= 0 -> "There are no entrants to notify."
    else -> null
}

/** Saves a notification for everyone currently registered in an owner's lottery. */
internal class OwnerNotificationService(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    suspend fun send(campsiteId: String, lotteryId: String, title: String, message: String) {
        require(campsiteId.isNotBlank() && '/' !in campsiteId) { "Invalid campsite." }
        require(lotteryId.isNotBlank() && '/' !in lotteryId) { "Invalid lottery." }

        val senderId = auth.currentUser?.uid ?: error("Please sign in before sending a notification.")
        val cleanTitle = title.trim()
        val cleanMessage = message.trim()
        val campsiteDocument = db.collection("campsites").document(campsiteId)
        val lotteryDocument = db.collection("lotteries").document(lotteryId)
        val notificationDocument = db.collection("notifications").document()

        db.runTransaction { transaction ->
            val campsite = transaction.get(campsiteDocument).toObject(Campsite::class.java)
                ?.copy(id = campsiteId) ?: error("This campsite is no longer available.")
            val lottery = transaction.get(lotteryDocument).toObject(Lottery::class.java)
                ?.copy(id = lotteryId) ?: error("This lottery is no longer available.")

            check(auth.currentUser?.uid == senderId) { "Your account changed. Please try again." }
            check(campsite.ownerId == senderId && lottery.ownerId == senderId) {
                "You can only notify entrants for your own campsite."
            }
            check(!lottery.drawCompleted && campsiteId in lottery.offeredCampsiteIds()) {
                "This campsite is not participating in an active lottery."
            }

            val recipients = lottery.registered.filter { it.isNotBlank() }.distinct()
            val validationError = ownerNotificationError(cleanTitle, cleanMessage, recipients.size)
            require(validationError == null) { validationError.orEmpty() }

            transaction.set(notificationDocument, mapOf(
                "senderId" to senderId,
                "recipientIds" to recipients,
                "title" to cleanTitle,
                "message" to cleanMessage,
                "campsiteId" to campsiteId,
                "campsiteName" to campsite.name,
                "lotteryId" to lotteryId,
                "createdAt" to FieldValue.serverTimestamp()
            ))
        }.await()
    }
}
