package com.example.campsitereservationapp.notification

import com.example.campsitereservationapp.service.UserService
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.util.Date

internal enum class NotificationLogType { SYSTEM, OWNER }

internal data class AdminNotificationLog(
    val id: String,
    val type: NotificationLogType,
    val sender: String,
    val recipients: List<String>,
    val title: String,
    val message: String,
    val campsite: String,
    val sentAt: Date?
)

internal fun newestNotificationLogsFirst(logs: List<AdminNotificationLog>): List<AdminNotificationLog> =
    logs.sortedByDescending { it.sentAt?.time ?: Long.MIN_VALUE }

internal fun filterNotificationLogs(
    logs: List<AdminNotificationLog>,
    search: String,
    type: NotificationLogType?
): List<AdminNotificationLog> {
    val query = search.trim()
    return logs.filter { log ->
        val matchesType = type == null || log.type == type
        // Recipient names are included so an admin can find one person in a group notification.
        val searchable = listOf(log.title, log.sender, log.message, log.campsite) + log.recipients
        matchesType && searchable.any { it.contains(query, ignoreCase = true) }
    }
}

internal class AdminNotificationLogService(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun getLogs(): List<AdminNotificationLog> {
        val documents = db.collection("notifications").get(Source.SERVER).await().documents
        val senderIds = documents.mapNotNull { it.getString("senderId") }
        val recipientIds = documents.flatMap { document ->
            (document.get("recipientIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
        }
        val names = UserService(db = db).getUsers(senderIds + recipientIds)
            .associate { it.id to it.name.ifBlank { it.id } }

        val logs = documents.map { document ->
            val kind = document.getString("kind").orEmpty()
            val senderId = document.getString("senderId").orEmpty()
            val recipients = (document.get("recipientIds") as? List<*>)
                ?.filterIsInstance<String>().orEmpty()
            val systemNotification = kind.isNotBlank()

            AdminNotificationLog(
                id = document.id,
                type = if (systemNotification) NotificationLogType.SYSTEM else NotificationLogType.OWNER,
                sender = if (systemNotification) "System" else names[senderId].orEmpty().ifBlank { senderId },
                recipients = recipients.map { names[it].orEmpty().ifBlank { it } },
                title = document.getString("title").orEmpty(),
                message = document.getString("message").orEmpty(),
                campsite = document.getString("campsiteName").orEmpty(),
                sentAt = document.getTimestamp("createdAt")?.toDate()
            )
        }
        return newestNotificationLogsFirst(logs)
    }
}
