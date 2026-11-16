package com.example.campsitereservationapp.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.campsitereservationapp.MainActivity
import com.example.campsitereservationapp.R
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.model.formatReservationDate
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

enum class LotteryResult { WON, LOST }  // Results of a lottery for a user

/**
 * Checks if a user won or lost a lottery.
 *
 * @param lottery The lottery being checked.
 * @param userId The ID of the user.
 * @return The user's lottery result.
 */
fun lotteryResult(lottery: Lottery, userId: String): LotteryResult? {
    if (!lottery.drawCompleted || userId !in lottery.registered) return null
    if (userId in lottery.pending) return LotteryResult.WON
    if (lottery.booked.any { it.entrantId == userId }) return null
    return LotteryResult.LOST
}

/**
 * Handles notifications about lottery results.
 *
 * @param context The app context.
 */
class LotteryNotificationService(private val context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val seen = context.getSharedPreferences("lottery_notifications", Context.MODE_PRIVATE)

    // Watches lottery results until the activity removes the listener.
    fun listen(user: User): ListenerRegistration? {
        if (!user.notificationsEnabled) return null
        createChannel()
        val loading = mutableSetOf<String>()
        var listening = true
        // Separate durable notices survive deletion of the lottery document.
        val notices = db.collection("lotteryNotices")
            .whereArrayContains("recipientIds", user.id)
            .addSnapshotListener { snapshot, _ ->
                if (!listening) return@addSnapshotListener
                snapshot?.documents?.forEach { document ->
                    val key = "${user.id}_notice_${document.id}"
                    if (!seen.getBoolean(key, false) && showNotice(
                            document.id, document.getString("title").orEmpty(),
                            document.getString("message").orEmpty()
                        )) seen.edit().putBoolean(key, true).apply()
                }
            }
        val registration = db.collection("lotteries")
            .whereArrayContains("registered", user.id)
            .addSnapshotListener { snapshot, _ ->
                if (!listening) return@addSnapshotListener
                snapshot?.documents?.forEach { document ->
                    val lottery = document.toObject(Lottery::class.java)?.copy(id = document.id)
                        ?: return@forEach
                    val result = lotteryResult(lottery, user.id) ?: return@forEach
                    val key = "${document.id}_${result.name}"
                    if (seen.getBoolean(key, false) || !loading.add(key)) return@forEach

                    db.collection("campsites").document(lottery.campsiteId).get()
                        .addOnSuccessListener { campsite ->
                            val name = campsite.getString("name").orEmpty()
                            if (listening && name.isNotBlank() && show(result, lottery, name)) {
                                seen.edit().putBoolean(key, true).apply()
                            }
                            loading.remove(key)
                        }
                        .addOnFailureListener { loading.remove(key) }
                }
            }
        return ListenerRegistration {
            // A campsite lookup may still finish after notifications are turned off.
            listening = false
            registration.remove()
            notices.remove()
        }
    }

    /** Delivers a persisted owner change notice once on this device, respecting OS permission. */
    private fun showNotice(id: String, title: String, message: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return false
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(context, id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle(title).setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent).setAutoCancel(true).build()
        NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
        return true
    }

    private fun show(result: LotteryResult, lottery: Lottery, campsiteName: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (result == LotteryResult.WON) putExtra(EXTRA_LOTTERY_ID, lottery.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            lottery.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title: String
        val message: String
        if (result == LotteryResult.WON) {
            title = "You won the $campsiteName draw!"
            message = "Reserve before 11:59 PM on ${formatReservationDate(lottery.bookingEndDate)}."
        } else {
            title = "Not selected — $campsiteName"
            message = "Thanks for entering."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(lottery.id.hashCode(), notification)
        return true
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lottery results",
                NotificationManager.IMPORTANCE_HIGH
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val EXTRA_LOTTERY_ID = "lottery_id"
        private const val CHANNEL_ID = "lottery_results"
    }
}
