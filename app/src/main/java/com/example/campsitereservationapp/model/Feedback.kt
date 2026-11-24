package com.example.campsitereservationapp.model

/*
 * The rating and review models and the rules for leaving them (US 01.13.01, US 01.13.02),
 * and how a campsite's average changes when a rating is added or an admin removes one
 * (US 03.09.01).
 *
 * The functions here do no database work, so the screens, FeedbackService and unit tests
 * all share the same rules.
 *
 * Outstanding issues: ratings and reviews cannot be edited, and once an admin removes one
 * the entrant can leave another for the same stay.
 */

/** Longest review an entrant can post, in characters. */
const val REVIEW_MAX_LENGTH = 1000

/**
 * An entrant's star rating for one finished stay, stored at `ratings/{reservationId}`.
 *
 * Using the reservation's ID allows only one rating per stay.
 *
 * @property id Firestore document ID, which is the reservation's ID.
 * @property campsiteId Document ID of the rated campsite.
 * @property entrantId Firebase UID of the entrant who stayed.
 * @property stars Number of stars, from 1 to 5.
 * @property date Day the rating was left, as yyyy-MM-dd.
 */
data class CampsiteRating(
    val id: String = "",
    val campsiteId: String = "",
    val entrantId: String = "",
    val stars: Int = 0,
    val date: String = ""
)

/**
 * An entrant's written review of one finished stay, stored at `reviews/{reservationId}`.
 *
 * Using the reservation's ID allows only one review per stay.
 *
 * @property id Firestore document ID, which is the reservation's ID.
 * @property campsiteId Document ID of the reviewed campsite.
 * @property entrantId Firebase UID of the entrant who stayed.
 * @property text What the entrant wrote.
 * @property date Day the review was posted, as yyyy-MM-dd.
 */
data class CampsiteReview(
    val id: String = "",
    val campsiteId: String = "",
    val entrantId: String = "",
    val text: String = "",
    val date: String = ""
)

/**
 * Checks whether an entrant can rate or review a stay.
 *
 * Only the entrant who stayed can, and only after a confirmed stay has ended.
 *
 * @param reservation The stay to rate or review.
 * @param entrantId Firebase UID of the signed-in entrant.
 * @param today Today's date as yyyy-MM-dd.
 * @return A message suitable for display, or null if the stay can be rated or reviewed.
 */
fun stayFeedbackError(reservation: Reservation, entrantId: String, today: String): String? = when {
    reservation.entrantId != entrantId -> "This reservation does not belong to your account."
    reservation.status != RESERVATION_CONFIRMED -> "You can only rate and review campsites you stayed at."
    reservation.checkOutDate >= today -> "You can rate and review this campsite after your stay."
    else -> null
}

/**
 * Checks the number of stars in a rating.
 *
 * @param stars Stars the entrant chose.
 * @return A message suitable for display, or null if the rating is valid.
 */
fun ratingError(stars: Int): String? = if (stars in 1..5) null else "Choose from 1 to 5 stars."

/**
 * Checks the text of a review.
 *
 * @param text Text the entrant wrote; spaces around it are ignored.
 * @return A message suitable for display, or null if the review can be posted.
 */
fun reviewError(text: String): String? = when {
    text.isBlank() -> "Write something before submitting."
    text.trim().length > REVIEW_MAX_LENGTH -> "Reviews can be at most $REVIEW_MAX_LENGTH characters."
    else -> null
}

/**
 * Adds a rating to a campsite's average.
 *
 * @receiver The campsite being rated.
 * @param rating The new rating; its stars must be valid (see [ratingError]).
 * @return The campsite with its count, total and average updated.
 */
fun Campsite.withRating(rating: CampsiteRating): Campsite = copy(
    ratingCount = ratingCount + 1,
    ratingTotal = ratingTotal + rating.stars,
    averageRating = (ratingTotal + rating.stars).toDouble() / (ratingCount + 1),
    lastRatingId = rating.id
)

/**
 * Takes a removed rating out of a campsite's average.
 *
 * @receiver The campsite that was rated.
 * @param rating The rating being removed.
 * @return The campsite with its count, total and average updated. The average is null
 * when no ratings are left.
 */
fun Campsite.withoutRating(rating: CampsiteRating): Campsite {
    val count = ratingCount - 1
    if (count <= 0) return copy(ratingCount = 0, ratingTotal = 0, averageRating = null)
    val total = ratingTotal - rating.stars
    return copy(ratingCount = count, ratingTotal = total, averageRating = total.toDouble() / count)
}
