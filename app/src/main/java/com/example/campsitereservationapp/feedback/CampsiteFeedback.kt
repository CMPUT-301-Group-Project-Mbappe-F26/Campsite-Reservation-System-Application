package com.example.campsitereservationapp.feedback

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.home.getRatingStars
import com.example.campsitereservationapp.model.Campsite
import com.example.campsitereservationapp.model.CampsiteRating
import com.example.campsitereservationapp.model.CampsiteReview
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.model.formatReservationDate
import com.example.campsitereservationapp.service.FeedbackService
import com.example.campsitereservationapp.service.UserService
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Star
import com.example.campsitereservationapp.ui.components.ButtonStyle
import com.example.campsitereservationapp.ui.components.ErrorState
import com.example.campsitereservationapp.ui.components.GlassCard
import com.example.campsitereservationapp.ui.components.InitialsAvatar
import com.example.campsitereservationapp.ui.components.PinedrawButton
import com.example.campsitereservationapp.ui.components.PinedrawDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/*
 * The reviews shown on a campsite's details screen (US 01.13.02), and the review and rating
 * lists where an admin can remove ones that violate policy (US 03.09.01, US 03.10.01).
 *
 * [CampsiteReviewsSection] and [AdminRatingsSection] load the data from Firebase.
 * [FeedbackList] only draws it, so intent tests can check it without Firebase.
 *
 * Outstanding issues: the lists load once and do not update live, and entrants are not told
 * when an admin removes their rating or review.
 */

/**
 * One rating or review, ready to display.
 *
 * @property id Firestore document ID of the rating or review.
 * @property entrantName Name of the entrant who left it, or a placeholder when it is unknown.
 * @property date Day it was left, as yyyy-MM-dd.
 * @property text The review's text, or the rating drawn as stars.
 */
data class FeedbackEntry(val id: String, val entrantName: String, val date: String, val text: String)

/**
 * Builds the review list, newest first.
 *
 * @param reviews The campsite's reviews, in any order.
 * @param users Profiles loaded for the entrants; missing profiles are allowed.
 * @return One entry per review.
 */
fun reviewEntries(reviews: List<CampsiteReview>, users: List<User>): List<FeedbackEntry> =
    reviews.map { FeedbackEntry(it.id, entrantName(it.entrantId, users), it.date, it.text) }
        .sortedByDescending { it.date }

/**
 * Builds the admin's rating list, newest first.
 *
 * @param ratings The campsite's ratings, in any order.
 * @param users Profiles loaded for the entrants; missing profiles are allowed.
 * @return One entry per rating, with its stars drawn as text.
 */
fun ratingEntries(ratings: List<CampsiteRating>, users: List<User>): List<FeedbackEntry> =
    ratings.map {
        FeedbackEntry(it.id, entrantName(it.entrantId, users), it.date, getRatingStars(it.stars.toDouble()))
    }.sortedByDescending { it.date }

// Guests have no name, and deleted accounts have no profile.
private fun entrantName(id: String, users: List<User>): String {
    val user = users.firstOrNull { it.id == id } ?: return "Former entrant"
    return user.name.trim().ifBlank { "Guest entrant" }
}

/**
 * Loads and shows a campsite's reviews.
 *
 * @param campsiteId Firestore document ID of the campsite.
 * @param asAdmin True when an admin opened the campsite, which adds Remove buttons.
 */
@Composable
fun CampsiteReviewsSection(campsiteId: String, asAdmin: Boolean) {
    val service = remember { FeedbackService() }
    val userService = remember { UserService() }
    FeedbackSection(
        campsiteId = campsiteId, title = "Reviews", kind = "review", emptyText = "No reviews yet.",
        load = {
            val reviews = service.getCampsiteReviews(campsiteId)
            reviewEntries(reviews, userService.getUsers(reviews.map { it.entrantId }))
        },
        remove = if (asAdmin) { entry -> service.removeReview(entry.id) } else null
    )
}

/**
 * Loads and shows a campsite's ratings to an admin, who can remove them.
 *
 * @param campsiteId Firestore document ID of the campsite.
 * @param onCampsiteChanged Called with the campsite's new average after a rating is removed.
 */
@Composable
fun AdminRatingsSection(campsiteId: String, onCampsiteChanged: (Campsite) -> Unit) {
    val service = remember { FeedbackService() }
    val userService = remember { UserService() }
    FeedbackSection(
        campsiteId = campsiteId, title = "Ratings", kind = "rating", emptyText = "No ratings yet.",
        load = {
            val ratings = service.getCampsiteRatings(campsiteId)
            ratingEntries(ratings, userService.getUsers(ratings.map { it.entrantId }))
        },
        remove = { entry -> service.removeRating(entry.id)?.let(onCampsiteChanged) }
    )
}

// Loads the entries, and drops one from the list once it has been removed.
@Composable
private fun FeedbackSection(
    campsiteId: String,
    title: String,
    kind: String,
    emptyText: String,
    load: suspend () -> List<FeedbackEntry>,
    remove: (suspend (FeedbackEntry) -> Unit)?
) {
    val scope = rememberCoroutineScope()
    var entries by remember(campsiteId) { mutableStateOf<List<FeedbackEntry>>(emptyList()) }
    var loading by remember(campsiteId) { mutableStateOf(true) }
    var error by remember(campsiteId) { mutableStateOf("") }
    var removingId by remember(campsiteId) { mutableStateOf<String?>(null) }
    var message by remember(campsiteId) { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(campsiteId, refresh) {
        loading = true
        error = ""
        try {
            val loaded = withTimeoutOrNull(20000) { load() }
            if (loaded == null) error = "Could not load ${kind}s. Please try again."
            else entries = loaded
        } catch (e: Exception) {
            coroutineContext.ensureActive() // Stop normally when leaving the screen.
            Log.w("CampsiteFeedback", "Could not load ${kind}s for $campsiteId", e)
            error = "Could not load ${kind}s. Please try again."
        } finally {
            loading = false
        }
    }

    val onRemove: ((FeedbackEntry) -> Unit)? = if (remove == null) null else { entry ->
        scope.launch {
            removingId = entry.id
            message = ""
            try {
                remove(entry)
                entries = entries.filter { it.id != entry.id }
                message = "The $kind was removed."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("CampsiteFeedback", "Could not remove $kind ${entry.id}", e)
                message = "Could not remove this $kind. Please try again."
            } finally {
                removingId = null
            }
        }
    }

    when {
        loading -> Unit
        error.isNotEmpty() -> ErrorState(error, onRetry = { refresh++ })
        else -> FeedbackList(title, kind, entries, emptyText, onRemove, removingId, message)
    }
}

/**
 * Draws a campsite's reviews or ratings.
 *
 * When [onRemove] is given, each entry has a Remove button that asks to confirm first.
 *
 * @param title Heading, such as "Reviews".
 * @param kind What one entry is called, such as "review".
 * @param entries Entries to show, newest first.
 * @param emptyText Message shown when there are none.
 * @param onRemove Removes an entry after the admin confirms, or null to hide Remove.
 * @param removingId ID of the entry being removed; Remove buttons are disabled meanwhile.
 * @param message Result of the last removal, or blank.
 */
@Composable
fun FeedbackList(
    title: String,
    kind: String,
    entries: List<FeedbackEntry>,
    emptyText: String,
    onRemove: ((FeedbackEntry) -> Unit)? = null,
    removingId: String? = null,
    message: String = ""
) {
    var confirming by remember { mutableStateOf<FeedbackEntry?>(null) }

    GlassCard(modifier = Modifier.testTag("feedback-$kind")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (kind == "rating") Icons.Rounded.Star else Icons.Rounded.RateReview, null,
                tint = if (kind == "rating") StarColor else PinedrawColors.Lime, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (entries.isEmpty()) title else "$title (${entries.size})", style = MaterialTheme.typography.titleMedium)
        }
        if (entries.isEmpty()) Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = PinedrawColors.TextSecondary)
        entries.forEach { entry ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                InitialsAvatar(entry.entrantName, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(entry.entrantName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        formatReservationDate(entry.date),
                        color = PinedrawColors.TextMuted, style = MaterialTheme.typography.bodySmall
                    )
                    Text(entry.text, style = MaterialTheme.typography.bodyMedium,
                        color = if (kind == "rating") StarColor else PinedrawColors.Text)
                }
                if (onRemove != null) PinedrawButton(
                    if (removingId == entry.id) "Removing…" else "Remove",
                    onClick = { confirming = entry }, enabled = removingId == null,
                    style = ButtonStyle.SoftDestructive, compact = true
                )
            }
        }
        if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodyMedium, color = PinedrawColors.TextSecondary)
    }

    confirming?.let { entry ->
        PinedrawDialog(
            title = "Remove ${entry.entrantName}'s $kind?",
            message = if (kind == "rating") "The campsite's average rating will be updated. This cannot be undone."
                else "It will no longer appear on the campsite. This cannot be undone.",
            icon = Icons.Rounded.DeleteOutline,
            iconTint = PinedrawColors.Coral,
            confirmText = "Yes, remove",
            confirmStyle = ButtonStyle.Destructive,
            onDismiss = { confirming = null },
            onConfirm = { confirming = null; onRemove?.invoke(entry) }
        )
    }
}
