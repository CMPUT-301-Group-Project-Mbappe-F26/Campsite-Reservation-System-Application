package com.example.campsitereservationapp.campsite

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.model.RESERVATION_REJECTED
import com.example.campsitereservationapp.model.Reservation
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.model.activeReservations
import com.example.campsitereservationapp.model.cancelledReservations
import com.example.campsitereservationapp.model.formatReservationDate
import com.example.campsitereservationapp.model.pastReservations
import com.example.campsitereservationapp.model.reservationDateToday
import com.example.campsitereservationapp.service.ReservationService
import com.example.campsitereservationapp.service.UserService
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Shows a campsite owner the reservations made at their campsite, filtered into active
 * (US 02.07.01), past (US 02.07.02) and cancelled (US 02.07.03) stays, and lets the owner
 * reject an active reservation (US 02.08.01). The same data also fills the booked-dates
 * calendar ([BookedDatesCalendar], US 02.06.01).
 *
 * [CampsiteReservationsSection] loads the data and is shown on the owner's view of
 * [CampsiteDetailsScreen]. [CampsiteReservationList] only draws the lists, so intent tests
 * can check it without Firebase.
 *
 * Outstanding issues: the list loads once and does not update live, and the entrant is
 * not notified when their reservation is rejected (US 01.07.02).
 */

/**
 * One reservation at the owner's campsite, ready to display.
 *
 * @property reservation The reservation.
 * @property entrantName Name of the entrant who booked, or a placeholder when it is unknown.
 */
data class CampsiteBooking(val reservation: Reservation, val entrantName: String)

/**
 * A campsite's reservations split into the owner's three filters.
 *
 * @property active Confirmed stays that have not ended, soonest first.
 * @property past Confirmed stays that have ended, most recent first.
 * @property cancelled Cancelled reservations, latest check-in first.
 */
data class CampsiteBookings(
    val active: List<CampsiteBooking> = emptyList(),
    val past: List<CampsiteBooking> = emptyList(),
    val cancelled: List<CampsiteBooking> = emptyList()
)

/**
 * Splits a campsite's reservations into active, past and cancelled, and adds entrant names.
 *
 * Entrants without a name are shown as "Guest entrant", and IDs with no saved profile, such
 * as deleted accounts, as "Unknown entrant".
 *
 * @param reservations The campsite's reservations, in any order.
 * @param users Profiles loaded for the entrants; extra profiles are ignored.
 * @param today Today's date as yyyy-MM-dd.
 * @return The three lists, each sorted for display.
 */
fun campsiteBookings(reservations: List<Reservation>, users: List<User>, today: String): CampsiteBookings {
    val byId = users.associateBy { it.id }
    fun named(list: List<Reservation>) = list.map { reservation ->
        val user = byId[reservation.entrantId]
        CampsiteBooking(
            reservation,
            when {
                user == null -> "Unknown entrant"
                user.name.isBlank() -> "Guest entrant"
                else -> user.name.trim()
            }
        )
    }
    return CampsiteBookings(
        active = named(activeReservations(reservations, today)),
        past = named(pastReservations(reservations, today)),
        cancelled = named(cancelledReservations(reservations))
    )
}

// Loads the campsite's reservations and the entrants who made them, and rejects one when asked.
@Composable
fun CampsiteReservationsSection(campsiteId: String) {
    val reservationService = remember { ReservationService() }
    val userService = remember { UserService() }
    val scope = rememberCoroutineScope()
    var bookings by remember(campsiteId) { mutableStateOf(CampsiteBookings()) }
    var loading by remember(campsiteId) { mutableStateOf(true) }
    var error by remember(campsiteId) { mutableStateOf("") }
    var rejectingId by remember(campsiteId) { mutableStateOf<String?>(null) }
    var rejectMessage by remember(campsiteId) { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    // Rejects the reservation, then reloads so it moves from Active to Cancelled.
    fun reject(booking: CampsiteBooking) {
        scope.launch {
            rejectingId = booking.reservation.id
            rejectMessage = ""
            try {
                val problem = reservationService.reject(booking.reservation.id)
                rejectMessage = problem ?: "${booking.entrantName}'s reservation was rejected."
                if (problem == null) refresh++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("CampsiteReservations", "Could not reject reservation ${booking.reservation.id}", e)
                rejectMessage = "Could not reject this reservation. Check your connection and try again."
            } finally {
                rejectingId = null
            }
        }
    }

    LaunchedEffect(campsiteId, refresh) {
        loading = true
        error = ""
        try {
            val loaded = withTimeoutOrNull(20000) {
                val reservations = reservationService.getCampsiteReservations(campsiteId)
                val users = userService.getUsers(reservations.map { it.entrantId })
                campsiteBookings(reservations, users, reservationDateToday())
            }
            if (loaded == null) error = "Could not load reservations. Please try again."
            else bookings = loaded
        } catch (_: Exception) {
            coroutineContext.ensureActive() // Stop normally when leaving the screen.
            error = "Could not load reservations. Please try again."
        } finally {
            loading = false
        }
    }

    when {
        loading -> Unit
        error.isNotEmpty() -> Column {
            Text(error, color = PinedrawColors.Coral)
            TextButton(onClick = { refresh++ }) { Text("Try again") }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            BookedDatesCalendar(bookedNightNames(bookings), reservationDateToday())
            CampsiteReservationList(bookings, onReject = ::reject, rejectingId = rejectingId, message = rejectMessage)
        }
    }
}

/**
 * Shows a campsite's reservations with Active, Past and Cancelled filters.
 *
 * Active is selected first. Each filter shows how many reservations it holds, and a
 * message when it is empty. When [onReject] is given, each active reservation has a Reject
 * button that asks the owner to confirm first. Rejected reservations are listed under
 * Cancelled and marked as rejected.
 *
 * @param bookings The campsite's reservations, already split by [campsiteBookings].
 * @param onReject Rejects a reservation after the owner confirms, or null to hide Reject.
 * @param rejectingId ID of the reservation being rejected, whose button is disabled meanwhile.
 * @param message Result of the last reject attempt, or blank.
 */
@Composable
fun CampsiteReservationList(
    bookings: CampsiteBookings,
    onReject: ((CampsiteBooking) -> Unit)? = null,
    rejectingId: String? = null,
    message: String = ""
) {
    var filter by rememberSaveable { mutableStateOf("Active") }
    var confirming by remember { mutableStateOf<CampsiteBooking?>(null) }
    val shown = when (filter) {
        "Past" -> bookings.past
        "Cancelled" -> bookings.cancelled
        else -> bookings.active
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("campsiteReservations"), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = PinedrawColors.Glass, contentColor = PinedrawColors.Text)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Reservations", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Active" to bookings.active.size,
                    "Past" to bookings.past.size,
                    "Cancelled" to bookings.cancelled.size
                ).forEach { (name, count) ->
                    FilterChip(selected = filter == name, onClick = { filter = name }, label = { Text("$name ($count)") })
                }
            }
            if (shown.isEmpty()) Text(
                when (filter) {
                    "Past" -> "No past stays yet."
                    "Cancelled" -> "No cancelled reservations."
                    else -> "No active reservations."
                },
                color = PinedrawColors.TextSecondary
            )
            shown.forEach { booking ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(booking.entrantName, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${formatReservationDate(booking.reservation.checkInDate)} – " +
                                formatReservationDate(booking.reservation.checkOutDate),
                            color = PinedrawColors.TextSecondary, style = MaterialTheme.typography.bodySmall
                        )
                        if (filter == "Cancelled") Text(
                            if (booking.reservation.status == RESERVATION_REJECTED) "Rejected by you" else "Cancelled by entrant",
                            color = PinedrawColors.Coral, style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (filter == "Active" && onReject != null) TextButton(
                        onClick = { confirming = booking }, enabled = rejectingId == null
                    ) {
                        Text(
                            if (rejectingId == booking.reservation.id) "Rejecting…" else "Reject",
                            color = if (rejectingId == null) PinedrawColors.Coral else PinedrawColors.TextMuted
                        )
                    }
                }
            }
            if (message.isNotEmpty()) Text(message, color = PinedrawColors.TextSecondary)
        }
    }

    confirming?.let { booking ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("Reject ${booking.entrantName}'s reservation?") },
            text = {
                Text(
                    "Their stay from ${formatReservationDate(booking.reservation.checkInDate)} to " +
                        "${formatReservationDate(booking.reservation.checkOutDate)} will be cancelled and the " +
                        "dates released to other entrants. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirming = null; onReject?.invoke(booking) }) {
                    Text("Yes, reject", color = PinedrawColors.Coral)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text("Keep reservation") }
            }
        )
    }
}
