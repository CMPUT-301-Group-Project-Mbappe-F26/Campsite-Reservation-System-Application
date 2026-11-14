package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Reservation
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

internal const val MAX_MESSAGE_LENGTH = 1000

internal data class ReservationMessage(
    val id: String,
    val senderId: String,
    val text: String,
    val sentAt: Timestamp?
)

internal data class OwnerMessageLog(
    val id: String,
    val owner: String,
    val entrant: String,
    val campsite: String,
    val text: String,
    val sentAt: Timestamp
)

internal class MessageService(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    // Loads owner messages across reservations and resolves the guest and owner names.
    suspend fun getOwnerMessageLogs(): List<OwnerMessageLog> {
        val messages = db.collectionGroup("messages").get(Source.SERVER).await().documents
        val byReservation = messages.mapNotNull { message ->
            val reservation = message.reference.parent.parent ?: return@mapNotNull null
            if (reservation.parent.path == "reservations") reservation to message else null
        }.groupBy({ it.first }, { it.second })
        val logs = byReservation.flatMap { (reference, messages) ->
            val booking = reference.get(Source.SERVER).await().toObject(Reservation::class.java)
                ?: return@flatMap emptyList()
            messages.mapNotNull { message ->
                // Firestore already verifies that each sender is the owner or the booked entrant.
                val sender = message.getString("senderId") ?: return@mapNotNull null
                val sentAt = message.getTimestamp("sentAt") ?: return@mapNotNull null
                if (sender == booking.entrantId) return@mapNotNull null
                OwnerMessageLog(
                    message.reference.path, sender, booking.entrantId,
                    booking.campsiteName.ifBlank { booking.campsiteId },
                    message.getString("text").orEmpty(), sentAt
                )
            }
        }
        val names = UserService(auth, db).getUsers(logs.flatMap { listOf(it.owner, it.entrant) })
            .associate { it.id to it.name }
        return logs.sortedByDescending { it.sentAt }.map { log ->
            log.copy(
                owner = names[log.owner].orEmpty().ifBlank { "Owner (${log.owner})" },
                entrant = names[log.entrant].orEmpty().ifBlank { "Guest (${log.entrant})" }
            )
        }
    }

    // Loads reservations for one owned campsite, including previous stays.
    suspend fun getCampsiteReservations(campsiteId: String): List<Reservation> =
        db.collection("reservations").whereEqualTo("campsiteId", campsiteId).get(Source.SERVER).await()
            .documents.mapNotNull { it.toObject(Reservation::class.java)?.copy(id = it.id) }
            .sortedByDescending { it.checkInDate }

    // Receives new messages as they arrive and stops listening when the chat closes.
    fun observeMessages(reservationId: String) = callbackFlow {
        val listener = messages(reservationId).orderBy("sentAt")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) close(error)
                else if (snapshot != null && !snapshot.metadata.isFromCache) trySend(snapshot.documents
                    .filterNot { it.metadata.hasPendingWrites() }
                    .map { doc ->
                        ReservationMessage(
                            doc.id, doc.getString("senderId").orEmpty(),
                            doc.getString("text").orEmpty(), doc.getTimestamp("sentAt")
                        )
                    })
            }
        awaitClose { listener.remove() }
    }

    // Saves a message with the signed-in sender and the server's time.
    suspend fun sendMessage(reservationId: String, draft: String) {
        val sender = checkNotNull(auth.currentUser).uid
        val text = draft.trim()
        require(text.isNotEmpty() && text.length <= MAX_MESSAGE_LENGTH)
        val booking = db.collection("reservations").document(reservationId)
        val message = messages(reservationId).document()
        // A transaction fails offline instead of leaving a message queued after the chat closes.
        db.runTransaction { transaction ->
            check(transaction.get(booking).exists()) { "Reservation is unavailable" }
            transaction.set(message, mapOf(
                "senderId" to sender,
                "text" to text,
                "sentAt" to FieldValue.serverTimestamp()
            ))
            Unit
        }.await()
    }

    // Keeps each transcript under the reservation it belongs to.
    private fun messages(reservationId: String) =
        db.collection("reservations").document(reservationId).collection("messages")
}
