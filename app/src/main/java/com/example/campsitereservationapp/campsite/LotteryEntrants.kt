package com.example.campsitereservationapp.campsite

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.model.Lottery
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.service.LotteryService
import com.example.campsitereservationapp.service.UserService
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Shows a campsite owner the entrants on their campsite's lottery waiting list (US 02.09.01).
 *
 * [LotteryEntrantsSection] loads the data and is shown on the owner's view of
 * [CampsiteDetailsScreen]. [LotteryEntrantList] only draws the list, so intent tests
 * can check it without Firebase.
 *
 * Outstanding issues: the list loads once and does not update live while entrants join.
 * Entrants can only join a waiting list once the lottery-engine branch is merged.
 */

/**
 * One entrant on a lottery waiting list, ready to display.
 *
 * @property id Firebase UID of the entrant.
 * @property name Name to show, or a placeholder when the profile has no name.
 * @property city The entrant's city, which may be blank.
 */
data class LotteryEntrant(val id: String, val name: String, val city: String = "")

/**
 * Matches waiting-list IDs to their profiles, keeping the order entrants joined in.
 *
 * Duplicate registrations are shown once. Entrants without a name are shown as
 * "Guest entrant", and IDs with no saved profile as "Unknown entrant".
 *
 * @param registered Entrant IDs in the order they joined the waiting list.
 * @param users Profiles loaded for those IDs; extra profiles are ignored.
 * @return One display row per distinct entrant.
 */
fun lotteryEntrants(registered: List<String>, users: List<User>): List<LotteryEntrant> {
    val byId = users.associateBy { it.id }
    return registered.distinct().map { id ->
        val user = byId[id]
        LotteryEntrant(
            id = id,
            name = when {
                user == null -> "Unknown entrant"
                user.name.isBlank() -> "Guest entrant"
                else -> user.name.trim()
            },
            city = user?.city.orEmpty().trim()
        )
    }
}

// Loads the campsite's current lottery and its entrants, and hides itself when there is no lottery.
@Composable
fun LotteryEntrantsSection(campsiteId: String) {
    val lotteryService = remember { LotteryService() }
    val userService = remember { UserService() }
    var lottery by remember(campsiteId) { mutableStateOf<Lottery?>(null) }
    var entrants by remember(campsiteId) { mutableStateOf<List<LotteryEntrant>>(emptyList()) }
    var loading by remember(campsiteId) { mutableStateOf(true) }
    var error by remember(campsiteId) { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(campsiteId, refresh) {
        loading = true
        error = ""
        try {
            val loaded = withTimeoutOrNull(20000) {
                val current = lotteryService.getCurrentLottery(campsiteId)
                val users = current?.let { userService.getUsers(it.registered) }.orEmpty()
                current to users
            }
            if (loaded == null) error = "Could not load the waiting list. Please try again."
            else {
                lottery = loaded.first
                entrants = loaded.first?.let { lotteryEntrants(it.registered, loaded.second) }.orEmpty()
            }
        } catch (_: Exception) {
            coroutineContext.ensureActive() // Stop normally when leaving the screen.
            error = "Could not load the waiting list. Please try again."
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
        lottery != null -> LotteryEntrantList(entrants)
    }
}

/**
 * Shows the entrants on a lottery waiting list, or a message when nobody has joined.
 *
 * @param entrants Entrants in the order they joined.
 */
@Composable
fun LotteryEntrantList(entrants: List<LotteryEntrant>) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("lotteryEntrants"), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = PinedrawColors.Glass, contentColor = PinedrawColors.Text)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Waiting list", style = MaterialTheme.typography.titleLarge)
            Text(
                "${entrants.size} ${if (entrants.size == 1) "entrant" else "entrants"}",
                color = PinedrawColors.TextMuted
            )
            if (entrants.isEmpty()) Text("No entrants have joined yet.", color = PinedrawColors.TextSecondary)
            entrants.forEachIndexed { index, entrant ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${index + 1}.", color = PinedrawColors.TextMuted)
                    Column(Modifier.weight(1f)) {
                        Text(entrant.name, fontWeight = FontWeight.SemiBold)
                        if (entrant.city.isNotBlank()) Text(entrant.city, color = PinedrawColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
