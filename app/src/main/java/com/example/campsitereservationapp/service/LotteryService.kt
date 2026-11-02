package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Lottery
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

class LotteryService {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun newLotteryId(): String = db.collection("lotteries").document().id

    // Verifies ownership against the stored campsite before creating the lottery.
    suspend fun createLottery(lottery: Lottery): String {
        validateLottery(lottery)
        val uid = auth.currentUser?.uid ?: error("Please sign in before creating a lottery.")
        check(lottery.ownerId == uid) { "This campsite does not belong to your account." }
        val campsite = db.collection("campsites").document(lottery.campsiteId)
        val document = db.collection("lotteries").document(lottery.id)
        db.runTransaction { transaction ->
            val site = transaction.get(campsite)
            val existing = transaction.get(document)
            check(site.exists()) { "This campsite is no longer available." }
            check(site.getString("ownerId") == uid && auth.currentUser?.uid == uid) {
                "This campsite does not belong to your account."
            }
            if (existing.exists()) {
                // A retry after a delayed response must not create a second lottery.
                check(existing.toObject(Lottery::class.java) == lottery) {
                    "This lottery was already created. Close the form before starting another."
                }
            } else {
                transaction.set(document, lottery)
            }
        }.await()
        return document.id
    }
}

// Date-only periods include both endpoints; booking must finish before the stay starts.
internal fun validateLottery(lottery: Lottery) {
    require(lottery.id.isNotBlank() && lottery.ownerId.isNotBlank() && lottery.campsiteId.isNotBlank()) {
        "The lottery must belong to a campsite and its owner."
    }
    require(lottery.numberOfWinners > 0) { "Enter a positive whole number of winners." }
    val dates = listOf(lottery.registrationStartDate, lottery.registrationEndDate,
        lottery.bookingStartDate, lottery.bookingEndDate, lottery.stayStartDate, lottery.stayEndDate)
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
    require(dates.all { date ->
        date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) &&
            runCatching { parser.parse(date)?.let { parser.format(it) == date } == true }.getOrDefault(false)
    }) { "Select all registration, booking, and stay dates." }
    require(lottery.registrationStartDate <= lottery.registrationEndDate) {
        "Registration must end on or after its start date."
    }
    require(lottery.bookingStartDate <= lottery.bookingEndDate) {
        "Booking must end on or after its start date."
    }
    require(lottery.stayStartDate < lottery.stayEndDate) {
        "The stay end date must be after its start date."
    }
    // Strictly separate calendar dates: sharing an endpoint is also an overlap.
    require(lottery.registrationEndDate < lottery.bookingStartDate) {
        "Booking must start on a later date than registration ends. The periods cannot share a date or overlap."
    }
    require(lottery.bookingEndDate < lottery.stayStartDate) {
        "Booking must end before the stay starts."
    }
}
