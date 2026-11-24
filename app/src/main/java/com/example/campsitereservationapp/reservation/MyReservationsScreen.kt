package com.example.campsitereservationapp.reservation

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Cabin
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.model.RESERVATION_CANCELLED
import com.example.campsitereservationapp.model.RESERVATION_CONFIRMED
import com.example.campsitereservationapp.model.RESERVATION_REJECTED
import com.example.campsitereservationapp.ui.components.EmptyState
import com.example.campsitereservationapp.ui.components.ErrorState
import com.example.campsitereservationapp.ui.components.GlassCard
import com.example.campsitereservationapp.ui.components.IconBadge
import com.example.campsitereservationapp.ui.components.LoadingState
import com.example.campsitereservationapp.ui.components.PageHeader
import com.example.campsitereservationapp.ui.components.PinedrawBackground
import com.example.campsitereservationapp.ui.components.SceneImage
import com.example.campsitereservationapp.ui.components.SegmentedTabs
import com.example.campsitereservationapp.ui.components.StatusChip
import com.example.campsitereservationapp.ui.theme.PinedrawRadius
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing
import com.example.campsitereservationapp.campsite.rememberCampsiteOwnerId
import com.example.campsitereservationapp.feedback.StayFeedback
import com.example.campsitereservationapp.feedback.StayFeedbackActions
import com.example.campsitereservationapp.model.Reservation
import com.example.campsitereservationapp.model.activeReservations
import com.example.campsitereservationapp.model.formatReservationDate
import com.example.campsitereservationapp.model.pastReservations
import com.example.campsitereservationapp.model.reservationDateToday
import com.example.campsitereservationapp.service.CampsiteService
import com.example.campsitereservationapp.service.FeedbackService
import com.example.campsitereservationapp.service.ReservationService
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/*
 * My Reservations tab: the signed-in entrant's upcoming reservations (US 01.06.01) and past
 * stays (US 01.06.02), plus a card for each lottery spot they won but have not used yet,
 * which opens ReserveScreen. Past stays can be rated (US 01.13.01) and reviewed (US 01.13.02).
 *
 * [MyReservationsScreen] loads the data from Firebase. [MyReservationsContent] only draws
 * it, so intent tests can check the screen without Firebase.
 *
 * Outstanding issues: the list only reloads when the tab is reopened or after an error, and
 * cancelled reservations are not listed anywhere yet.
 */

/**
 * Loads and displays the signed-in entrant's reservations and unused lottery wins.
 *
 * @param onReservationClick Opens the details of the tapped reservation.
 * @param onReserveClick Opens the reserve screen for the tapped lottery win.
 */
@Composable
fun MyReservationsScreen(onReservationClick: (String) -> Unit, onReserveClick: (String) -> Unit) {
    val service = remember { ReservationService() }
    val campsiteService = remember { CampsiteService() }
    val feedbackService = remember { FeedbackService() }
    val entrantId = rememberCampsiteOwnerId()
    var wins by remember(entrantId) { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // Lottery ID and campsite name
    var reservations by remember(entrantId) { mutableStateOf<List<Reservation>>(emptyList()) }
    var past by remember(entrantId) { mutableStateOf<List<Reservation>>(emptyList()) }
    var feedback by remember(entrantId) { mutableStateOf<StayFeedback?>(null) }
    var loading by remember(entrantId) { mutableStateOf(true) }
    var error by remember(entrantId) { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(entrantId, refresh) {
        loading = true
        error = ""
        try {
            check(entrantId != null) { "Not signed in" }
            val result = withTimeoutOrNull(20000) { service.getMyReservations() }
            if (result == null) error = "Could not load your reservations. Please try again."
            else {
                val today = reservationDateToday()
                reservations = activeReservations(result, today)
                past = pastReservations(result, today)
            }
            // Past stays can still be shown if ratings and reviews cannot be loaded.
            feedback = try {
                withTimeoutOrNull(20000) {
                    StayFeedback(
                        stars = feedbackService.getMyRatings().associate { it.id to it.stars },
                        reviewed = feedbackService.getMyReviews().map { it.id }.toSet()
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("MyReservations", "Could not load ratings and reviews", e)
                null
            }
            // Reservations can still be shown if won lotteries cannot be loaded.
            wins = try {
                withTimeoutOrNull(20000) {
                    service.getMyPendingLotteries().map { lottery ->
                        lottery.id to campsiteService.getCampsite(lottery.campsiteId)?.name.orEmpty()
                    }
                }.orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        } catch (_: Exception) {
            coroutineContext.ensureActive() // Stop normally when leaving the screen.
            error = if (entrantId == null) "Sign in to view your reservations." else "Could not load your reservations. Please try again."
        } finally {
            loading = false
        }
    }

    // Saves a rating or review, and returns a message when it could not be saved.
    suspend fun save(what: String, stay: Reservation, action: suspend () -> String?): String? = try {
        action()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("MyReservations", "Could not save $what for ${stay.id}", e)
        "Could not save your $what. Check your connection and try again."
    }

    MyReservationsContent(
        wins, reservations, loading, error, onRetry = { refresh++ }, onReservationClick, onReserveClick, past = past,
        feedback = feedback,
        onRate = { stay, stars ->
            save("rating", stay) { feedbackService.rate(stay.id, stars) }.also { problem ->
                if (problem == null) feedback = feedback?.let { it.copy(stars = it.stars + (stay.id to stars)) }
            }
        },
        onReview = { stay, text ->
            save("review", stay) { feedbackService.postReview(stay.id, text) }.also { problem ->
                if (problem == null) feedback = feedback?.let { it.copy(reviewed = it.reviewed + stay.id) }
            }
        }
    )
}

/**
 * Draws the My Reservations tab from already loaded data.
 *
 * "Upcoming" and "Past" chips switch between the two lists. Each list shows an empty
 * message when it has nothing in it and nothing is loading. Unused lottery wins only
 * appear under Upcoming.
 *
 * @param wins Unused lottery wins, as lottery ID and campsite name.
 * @param reservations Active reservations, soonest first.
 * @param loading True while the reservations are loading.
 * @param error Message to show when loading failed, or blank.
 * @param onRetry Loads the reservations again after an error.
 * @param onReservationClick Opens the details of the tapped reservation.
 * @param onReserveClick Opens the reserve screen for the tapped lottery win.
 * @param past Stays that have ended, most recent first.
 * @param feedback What the entrant has rated and reviewed, or null to hide those buttons.
 * @param onRate Saves a rating of a past stay; returns a message when it failed, or null.
 * @param onReview Saves a review of a past stay; returns a message when it failed, or null.
 */
@Composable
fun MyReservationsContent(
    wins: List<Pair<String, String>>,
    reservations: List<Reservation>,
    loading: Boolean,
    error: String,
    onRetry: () -> Unit,
    onReservationClick: (String) -> Unit,
    onReserveClick: (String) -> Unit,
    past: List<Reservation> = emptyList(),
    feedback: StayFeedback? = null,
    onRate: suspend (Reservation, Int) -> String? = { _, _ -> null },
    onReview: suspend (Reservation, String) -> String? = { _, _ -> null }
) {
    var showPast by rememberSaveable { mutableStateOf(false) }
    val shown = if (showPast) past else reservations

    PinedrawBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = PinedrawSpacing.Gutter, end = PinedrawSpacing.Gutter, top = PinedrawSpacing.Large, bottom = PinedrawSpacing.Screen),
            verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Large)
        ) {
            item { PageHeader("My trips") }
            item {
                SegmentedTabs(
                    options = listOf("Upcoming", "Past"),
                    selectedIndex = if (showPast) 1 else 0,
                    onSelect = { showPast = it == 1 }
                )
            }
            // Only appears when the entrant has won a spot they have not used yet.
            if (!showPast) items(wins, key = { "win-${it.first}" }) { (lotteryId, campsiteName) ->
                GlassCard(
                    onClick = { onReserveClick(lotteryId) },
                    borderColor = PinedrawColors.Lime.copy(alpha = 0.6f),
                    container = PinedrawColors.Lime.copy(alpha = 0.08f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.EmojiEvents, filled = true)
                        Spacer(Modifier.width(PinedrawSpacing.Medium))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("You won a spot", style = MaterialTheme.typography.titleMedium, color = PinedrawColors.Lime)
                            Text(campsiteName.ifBlank { "Unnamed campsite" }, style = MaterialTheme.typography.bodyLarge, color = PinedrawColors.Text)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(PinedrawRadius.Pill).background(PinedrawColors.PrimaryGradient)
                            .padding(horizontal = PinedrawSpacing.Gutter, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("Choose your dates to reserve.", style = MaterialTheme.typography.labelLarge, color = PinedrawColors.Ink)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = PinedrawColors.Ink, modifier = Modifier.size(18.dp))
                    }
                }
            }

            when {
                loading -> item { LoadingState() }
                error.isNotEmpty() -> item { ErrorState(error, onRetry = onRetry) }
                shown.isEmpty() -> item {
                    EmptyState(
                        icon = if (showPast) Icons.Rounded.History else Icons.Rounded.Cabin,
                        title = if (showPast) "No past reservations" else "No active reservations",
                        message = if (showPast) "Stays you have finished will appear here." else "Campsites you reserve will appear here."
                    )
                }
                else -> items(shown, key = { it.id }) { reservation ->
                    TripCard(
                        reservation = reservation,
                        past = showPast,
                        onClick = { onReservationClick(reservation.id) }
                    ) {
                        if (showPast && feedback != null) StayFeedbackActions(reservation, feedback, onRate, onReview)
                    }
                }
            }
        }
    }
}

// One reservation: a landscape banner with its status, then the campsite and dates.
@Composable
private fun TripCard(
    reservation: Reservation,
    past: Boolean,
    onClick: () -> Unit,
    extra: @Composable () -> Unit
) {
    GlassCard(onClick = onClick, contentPadding = 12.dp) {
        Box(Modifier.fillMaxWidth().height(if (past) 110.dp else 160.dp)) {
            SceneImage(reservation.campsiteId.ifBlank { reservation.campsiteName }, Modifier.matchParentSize().clip(PinedrawRadius.Medium))
            Box(Modifier.padding(10.dp)) {
                // A past stay is still stored as Confirmed, so show that it is over.
                StatusChip(
                    if (past) "Completed" else reservation.status,
                    color = if (past) PinedrawColors.Text else statusColor(reservation.status),
                    icon = if (past || reservation.status == RESERVATION_CONFIRMED) Icons.Rounded.Check else null,
                    modifier = Modifier.clip(PinedrawRadius.Pill).background(PinedrawColors.Background.copy(alpha = 0.75f))
                )
            }
        }
        Column(Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                reservation.campsiteName.ifBlank { "Unnamed campsite" },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CalendarMonth, null, tint = PinedrawColors.TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${formatReservationDate(reservation.checkInDate)} – ${formatReservationDate(reservation.checkOutDate)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PinedrawColors.TextSecondary
                )
            }
            extra()
        }
    }
}

private fun statusColor(status: String) = when (status) {
    RESERVATION_CONFIRMED -> PinedrawColors.Lime
    RESERVATION_CANCELLED, RESERVATION_REJECTED -> PinedrawColors.Coral
    else -> PinedrawColors.TextSecondary
}
