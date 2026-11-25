package com.example.campsitereservationapp.service

import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.CampsiteRating
import com.example.campsitereservationapp.model.CampsiteReview
import com.example.campsitereservationapp.model.Reservation
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.model.ratingError
import com.example.campsitereservationapp.model.reservationDateToday
import com.example.campsitereservationapp.model.reviewError
import com.example.campsitereservationapp.model.stayFeedbackError
import com.example.campsitereservationapp.model.withRating
import com.example.campsitereservationapp.model.withoutRating
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.tasks.await

/**
 * Saves and loads campsite ratings and reviews in Firestore (US 01.13.01, US 01.13.02), and
 * lets an admin remove them (US 03.09.01, US 03.10.01).
 *
 * Ratings are stored at `ratings/{reservationId}` and reviews at `reviews/{reservationId}`.
 * Adding or removing a rating also updates the campsite's average in the same transaction,
 * so the two cannot disagree. The checks come from Feedback.kt.
 *
 * Outstanding issues: ratings and reviews are kept when their campsite is removed.
 */
class FeedbackService {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Loads the ratings the signed-in entrant has left.
     *
     * @return The entrant's ratings, in no particular order.
     * @throws IllegalStateException If no user is signed in.
     */
    suspend fun getMyRatings(): List<CampsiteRating> = ratings("entrantId", myId())

    /**
     * Loads the reviews the signed-in entrant has posted.
     *
     * @return The entrant's reviews, in no particular order.
     * @throws IllegalStateException If no user is signed in.
     */
    suspend fun getMyReviews(): List<CampsiteReview> = reviews("entrantId", myId())

    /**
     * Loads every rating of one campsite. Firestore rules only allow this for admins.
     *
     * @param campsiteId Firestore document ID of the campsite.
     * @return The campsite's ratings, in no particular order.
     */
    suspend fun getCampsiteRatings(campsiteId: String): List<CampsiteRating> = ratings("campsiteId", campsiteId)

    /**
     * Loads every review of one campsite.
     *
     * @param campsiteId Firestore document ID of the campsite.
     * @return The campsite's reviews, in no particular order.
     */
    suspend fun getCampsiteReviews(campsiteId: String): List<CampsiteReview> = reviews("campsiteId", campsiteId)

    /**
     * Rates a campsite for a finished stay and updates its average (US 01.13.01).
     *
     * @param reservationId Firestore document ID of the stay.
     * @param stars Number of stars, from 1 to 5.
     * @return A message explaining why it was not saved, or null when it was.
     * @throws IllegalStateException If no user is signed in.
     */
    suspend fun rate(reservationId: String, stars: Int): String? {
        ratingError(stars)?.let { return it }
        val uid = myId()
        val today = reservationDateToday()
        val ratingRef = db.collection("ratings").document(reservationId)
        return db.runTransaction { transaction ->
            val stay = readStay(transaction, reservationId)
            val problem = stayError(stay, uid, today)
            if (stay == null || problem != null) return@runTransaction problem
            val campsiteRef = db.collection("campsites").document(stay.campsiteId)
            val campsite = transaction.get(campsiteRef).toObject(Campsite::class.java)
                ?: return@runTransaction "This campsite is no longer listed."
            if (transaction.get(ratingRef).exists()) return@runTransaction "You have already rated this stay."

            val rating = CampsiteRating(reservationId, stay.campsiteId, uid, stars, today)
            transaction.set(ratingRef, rating)
            transaction.update(campsiteRef, ratingFields(campsite.withRating(rating)))
            null
        }.await()
    }

    /**
     * Posts a review of a campsite for a finished stay (US 01.13.02).
     *
     * @param reservationId Firestore document ID of the stay.
     * @param text The review; spaces around it are removed.
     * @return A message explaining why it was not saved, or null when it was.
     * @throws IllegalStateException If no user is signed in.
     */
    suspend fun postReview(reservationId: String, text: String): String? {
        reviewError(text)?.let { return it }
        val uid = myId()
        val today = reservationDateToday()
        val reviewRef = db.collection("reviews").document(reservationId)
        return db.runTransaction { transaction ->
            val stay = readStay(transaction, reservationId)
            val problem = stayError(stay, uid, today)
            if (stay == null || problem != null) return@runTransaction problem
            if (!transaction.get(db.collection("campsites").document(stay.campsiteId)).exists())
                return@runTransaction "This campsite is no longer listed."
            if (transaction.get(reviewRef).exists()) return@runTransaction "You have already reviewed this stay."

            transaction.set(reviewRef, CampsiteReview(reservationId, stay.campsiteId, uid, text.trim(), today))
            null
        }.await()
    }

    /**
     * Removes a rating as an admin and updates the campsite's average (US 03.09.01).
     *
     * @param ratingId Firestore document ID of the rating.
     * @return The campsite with its new average, or null if the rating or campsite is already gone.
     * @throws IllegalStateException If the signed-in user is not an admin.
     */
    suspend fun removeRating(ratingId: String): Campsite? {
        val uid = myId()
        val ratingRef = db.collection("ratings").document(ratingId)
        return db.runTransaction { transaction ->
            checkAdmin(transaction, uid)
            val rating = transaction.get(ratingRef).toObject(CampsiteRating::class.java)
                ?: return@runTransaction null
            val campsiteRef = db.collection("campsites").document(rating.campsiteId)
            val updated = transaction.get(campsiteRef).toObject(Campsite::class.java)?.withoutRating(rating)
            transaction.delete(ratingRef)
            if (updated != null) transaction.update(campsiteRef, ratingFields(updated))
            updated?.copy(id = campsiteRef.id)
        }.await()
    }

    /**
     * Removes a review as an admin (US 03.10.01).
     *
     * @param reviewId Firestore document ID of the review.
     * @throws IllegalStateException If the signed-in user is not an admin.
     */
    suspend fun removeReview(reviewId: String) {
        val uid = myId()
        db.runTransaction { transaction ->
            checkAdmin(transaction, uid)
            transaction.delete(db.collection("reviews").document(reviewId))
            Unit
        }.await()
    }

    private fun myId(): String = auth.currentUser?.uid ?: error("Not signed in")

    private suspend fun ratings(field: String, value: String): List<CampsiteRating> =
        db.collection("ratings").whereEqualTo(field, value).get().await()
            .documents.mapNotNull { doc -> doc.toObject(CampsiteRating::class.java)?.copy(id = doc.id) }

    private suspend fun reviews(field: String, value: String): List<CampsiteReview> =
        db.collection("reviews").whereEqualTo(field, value).get().await()
            .documents.mapNotNull { doc -> doc.toObject(CampsiteReview::class.java)?.copy(id = doc.id) }

    private fun readStay(transaction: Transaction, reservationId: String): Reservation? =
        transaction.get(db.collection("reservations").document(reservationId)).toObject(Reservation::class.java)

    private fun stayError(stay: Reservation?, uid: String, today: String): String? =
        if (stay == null) "This reservation could not be found." else stayFeedbackError(stay, uid, today)

    private fun checkAdmin(transaction: Transaction, uid: String) {
        val user = transaction.get(db.collection("users").document(uid)).toObject(User::class.java)
        check(user?.isAdmin == true) { "Only admins can remove ratings and reviews." }
    }

    // The only campsite fields a rating may change.
    private fun ratingFields(campsite: Campsite): Map<String, Any?> = mapOf(
        "averageRating" to campsite.averageRating,
        "ratingCount" to campsite.ratingCount,
        "ratingTotal" to campsite.ratingTotal,
        "lastRatingId" to campsite.lastRatingId
    )
}
