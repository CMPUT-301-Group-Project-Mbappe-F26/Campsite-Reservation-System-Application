package com.example.campsitereservationapp.reservation

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(0f to Color(0xFF1E3A26), 0.45f to PinedrawColors.Background, 1f to PinedrawColors.Background))
            .statusBarsPadding().padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("My Reservations", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            if (showPast) "Campsite stays you have finished." else "Your upcoming campsite stays.",
            color = PinedrawColors.TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showPast, onClick = { showPast = false }, label = { Text("Upcoming") })
            FilterChip(selected = showPast, onClick = { showPast = true }, label = { Text("Past") })
        }
        Spacer(Modifier.height(16.dp))
        // Only appears when the entrant has won a spot they have not used yet.
        if (!showPast) wins.forEach { (lotteryId, campsiteName) ->
            Card(
                onClick = { onReserveClick(lotteryId) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = PinedrawColors.Glass, contentColor = PinedrawColors.Text)
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("You won a spot", color = PinedrawColors.Lime, fontWeight = FontWeight.Bold)
                        Text(campsiteName.ifBlank { "Unnamed campsite" }, fontWeight = FontWeight.Bold)
                        Text("Choose your dates to reserve.", color = PinedrawColors.TextSecondary)
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                        modifier = Modifier.size(20.dp), tint = PinedrawColors.TextMuted
                    )
                }
            }
        }

        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PinedrawColors.Lime)
            }
            error.isNotEmpty() -> Column(Modifier.padding(vertical = 24.dp)) {
                Text(error, color = PinedrawColors.Coral)
                TextButton(onClick = onRetry) { Text("Try again") }
            }
            shown.isEmpty() -> Card(
                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = PinedrawColors.Glass, contentColor = PinedrawColors.Text)
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        if (showPast) "No past reservations" else "No active reservations",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (showPast) "Stays you have finished will appear here." else "Campsites you reserve will appear here.",
                        color = PinedrawColors.TextSecondary
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(shown, key = { it.id }) { reservation ->
                    Card(
                        onClick = { onReservationClick(reservation.id) },
                        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = PinedrawColors.Glass, contentColor = PinedrawColors.Text)
                    ) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(reservation.campsiteName.ifBlank { "Unnamed campsite" }, fontWeight = FontWeight.Bold)
                                Text(
                                    "${formatReservationDate(reservation.checkInDate)} – ${formatReservationDate(reservation.checkOutDate)}",
                                    color = PinedrawColors.TextSecondary
                                )
                                // A past stay is still stored as Confirmed, so show that it is over.
                                Text(
                                    if (showPast) "Completed" else reservation.status,
                                    color = if (showPast) PinedrawColors.TextMuted else PinedrawColors.Lime,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (showPast && feedback != null) {
                                    StayFeedbackActions(reservation, feedback, onRate, onReview)
                                }
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                                modifier = Modifier.size(20.dp), tint = PinedrawColors.TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}
