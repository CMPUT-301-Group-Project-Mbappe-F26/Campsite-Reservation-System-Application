package com.example.campsitereservationapp.feedback

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.home.getRatingStars
import com.example.campsitereservationapp.model.REVIEW_MAX_LENGTH
import com.example.campsitereservationapp.model.Reservation
import com.example.campsitereservationapp.model.ratingError
import com.example.campsitereservationapp.model.reviewError
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.StarOutline
import com.example.campsitereservationapp.ui.components.ButtonStyle
import com.example.campsitereservationapp.ui.components.InlineMessage
import com.example.campsitereservationapp.ui.components.PinedrawButton
import com.example.campsitereservationapp.ui.components.PinedrawDialog
import com.example.campsitereservationapp.ui.components.PinedrawTextField
import kotlinx.coroutines.launch

/*
 * The Rate (US 01.13.01) and Write review (US 01.13.02) buttons and dialogs shown on each
 * finished stay in My Reservations.
 *
 * Saving is passed in, so intent tests can check the buttons and dialogs without Firebase.
 *
 * Outstanding issues: a rating or review cannot be changed after it is submitted.
 */

/** Colour of rating stars, matching the campsite list and details screens. */
internal val StarColor = Color(0xFFFFC107)

/**
 * What the signed-in entrant has already rated and reviewed.
 *
 * @property stars Stars given for each rated stay, by reservation ID.
 * @property reviewed IDs of the reservations that have a review.
 */
data class StayFeedback(
    val stars: Map<String, Int> = emptyMap(),
    val reviewed: Set<String> = emptySet()
)

/**
 * Shows the rating and review actions for one finished stay.
 *
 * Each button is replaced by what the entrant left once it is submitted. A dialog stays
 * open with a message when saving fails, so nothing typed is lost.
 *
 * @param stay The finished stay.
 * @param feedback What the entrant has already rated and reviewed.
 * @param onRate Saves a rating; returns a message when it failed, or null when it was saved.
 * @param onReview Saves a review; returns a message when it failed, or null when it was saved.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StayFeedbackActions(
    stay: Reservation,
    feedback: StayFeedback,
    onRate: suspend (Reservation, Int) -> String?,
    onReview: suspend (Reservation, String) -> String?
) {
    var rating by rememberSaveable(stay.id) { mutableStateOf(false) }
    var reviewing by rememberSaveable(stay.id) { mutableStateOf(false) }
    val name = stay.campsiteName.ifBlank { "this campsite" }
    val stars = feedback.stars[stay.id]

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        if (stars == null) PinedrawButton("Rate", { rating = true }, style = ButtonStyle.Ghost, icon = Icons.Rounded.StarOutline, compact = true)
        else Text("Your rating: ${getRatingStars(stars.toDouble())}", style = MaterialTheme.typography.bodyMedium, color = StarColor)
        if (stay.id !in feedback.reviewed) PinedrawButton("Write review", { reviewing = true }, style = ButtonStyle.Ghost, icon = Icons.Rounded.RateReview, compact = true)
        else Text("Review posted", style = MaterialTheme.typography.bodyMedium, color = PinedrawColors.TextSecondary)
    }

    if (rating) RatingDialog(name, onSubmit = { onRate(stay, it) }, onClose = { rating = false })
    if (reviewing) ReviewDialog(name, onSubmit = { onReview(stay, it) }, onClose = { reviewing = false })
}

// Asks for 1 to 5 stars.
@Composable
private fun RatingDialog(campsiteName: String, onSubmit: suspend (Int) -> String?, onClose: () -> Unit) {
    var stars by rememberSaveable { mutableIntStateOf(0) }
    FeedbackDialog(
        title = "Rate $campsiteName", canSubmit = ratingError(stars) == null,
        onSubmit = { onSubmit(stars) }, onClose = onClose
    ) { saving ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            (1..5).forEach { number ->
                TextButton(
                    onClick = { stars = number }, enabled = !saving,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = if (number == 1) "1 star" else "$number stars"
                        selected = number == stars
                    }
                ) {
                    Text(
                        if (number <= stars) "★" else "☆",
                        color = StarColor, style = MaterialTheme.typography.headlineLarge
                    )
                }
            }
        }
    }
}

// Asks for the text of a review.
@Composable
private fun ReviewDialog(campsiteName: String, onSubmit: suspend (String) -> String?, onClose: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    FeedbackDialog(
        title = "Review $campsiteName", canSubmit = reviewError(text) == null,
        onSubmit = { onSubmit(text) }, onClose = onClose
    ) { saving ->
        PinedrawTextField(
            value = text, onValueChange = { text = it }, enabled = !saving,
            label = "Your review", singleLine = false,
            minLines = 3, maxLines = 8, isError = text.trim().length > REVIEW_MAX_LENGTH,
            supportingText = "${text.trim().length} / $REVIEW_MAX_LENGTH. Your name and the date will be shown."
        )
    }
}

// Submits and closes, or stays open with a message when saving fails.
@Composable
private fun FeedbackDialog(
    title: String,
    canSubmit: Boolean,
    onSubmit: suspend () -> String?,
    onClose: () -> Unit,
    content: @Composable (saving: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    PinedrawDialog(
        title = title,
        icon = Icons.Rounded.RateReview,
        iconTint = StarColor,
        confirmText = if (saving) "Submitting…" else "Submit",
        confirmEnabled = canSubmit && !saving,
        loading = saving,
        onDismiss = onClose,
        onConfirm = {
            saving = true
            error = ""
            scope.launch {
                val problem = try { onSubmit() } finally { saving = false }
                if (problem == null) onClose() else error = problem
            }
        }
    ) {
        content(saving)
        InlineMessage(error)
    }
}
