package com.example.campsitereservationapp.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.ui.components.GlassCard
import com.example.campsitereservationapp.ui.components.IconBadge
import com.example.campsitereservationapp.ui.components.PinedrawScreen
import com.example.campsitereservationapp.ui.theme.PinedrawColors

private data class PolicyItem(val icon: ImageVector, val title: String, val description: String)

private val policies = listOf(
    PolicyItem(Icons.Rounded.Info, "Campsite Information", "Before booking, make sure to check the campsite's location, available dates, number of guests allowed, and any rules the owner has added. Different campsites can have different requirements. You can search for campsites and use filters to narrow down the results. If a campsite doesn't match your filters, it won't show up."),
    PolicyItem(Icons.Rounded.EventAvailable, "Campsite Availability", "Only available dates can be booked. If someone has already reserved certain dates, you won't be able to choose those same dates. Availability can also change when someone makes or cancels a reservation. Just because dates were available earlier doesn't mean they'll still be available later."),
    PolicyItem(Icons.Rounded.ConfirmationNumber, "Joining a Lottery", "You can enter a lottery while registration is open. When joining, you'll need to enter your group size, and it can't be more than the campsite allows. You also can't join the same lottery twice. Some lotteries have a limit on how many people can enter, so once the waiting list is full, no more entries will be accepted. You won't be able to join after registration closes either."),
    PolicyItem(Icons.Rounded.ExitToApp, "Leaving a Lottery", "If you don't want to participate anymore, you can leave the lottery before registration closes. The app will ask you to confirm before removing you from the waiting list. If you decide not to confirm, your entry stays there. Once registration closes, you can't leave through the normal option."),
    PolicyItem(Icons.Rounded.Casino, "Lottery Winners", "Winners are picked randomly after registration closes. Entering doesn't mean you're guaranteed to win. The number of winners depends on how the lottery was set up. If a winner declines their spot, another person may be selected from the remaining entrants. If there isn't anyone eligible left, another winner won't be picked."),
    PolicyItem(Icons.Rounded.CalendarMonth, "Booking After Winning", "If you win, you'll have a certain amount of time to make your reservation. You need to book before the deadline. You'll also have to choose dates that are still available, and your check-out date must be after your check-in date. For lotteries with multiple campsites, you can select one campsite to book. Campsites without available dates can't be selected."),
    PolicyItem(Icons.Rounded.DoNotDisturbOn, "Declining a Win", "You don't have to book if you win a lottery. If you decide to decline, the app will ask for confirmation first. Once you've declined, you can't go back and use that same win to make a reservation. Make sure you've decided before confirming."),
    PolicyItem(Icons.Rounded.EventBusy, "Reservations and Cancellations", "After booking, you can check your reservation details in the app, including your dates and number of guests. If you need to cancel, you can do so before the check-in date. The app will show the campsite's cancellation policy and ask you to confirm. Cancelling makes those reserved dates available again. Once your stay has started, the normal cancellation option is no longer available."),
    PolicyItem(Icons.Rounded.Gavel, "Campsite Rules", "Campsite owners can set their own rules, so make sure you read them before booking. Some campsites require you to accept the rules before you can confirm a reservation. Owners may also update their campsite information and policies. Always check the rules for the specific campsite you're booking."),
    PolicyItem(Icons.Rounded.SyncAlt, "Reservation Changes", "Campsite owners can reject reservations. If that happens, the reservation is cancelled and the dates become available again. You'll be notified about the change. You can check your active, past, and cancelled reservations in the app to see their current status."),
    PolicyItem(Icons.Rounded.Star, "Ratings and Reviews", "You can rate a campsite after you've completed your stay. Ratings are from 1 to 5 stars, and you can't rate campsites you haven't stayed at. The ratings submitted by users are used to calculate the campsite's average rating."),
    PolicyItem(Icons.Rounded.ManageAccounts, "Accounts and Notifications", "You can update your profile information and change your lottery notification settings. If you turn notifications off, you won't receive lottery-result notifications through that setting. Deleting your account is permanent and removes you from waiting lists and cancels active reservations. Campsites and accounts can also be removed by administrators. If a campsite is removed, its active lottery is closed and its waiting list is cleared. Removing a campsite owner can also affect their existing campsites and reservations."),
)

/**
 * Shows the policies and guidelines for using Pinedraw.
 *
 * @param onBack Returns to the Profile tab.
 */
@Composable
fun PolicyGuidelinesScreen(onBack: () -> Unit) {
    PinedrawScreen(title = "Policy & guidelines", onBack = onBack, backLabel = "Back to Settings") {
        Text(
            "Here are some things to know before using Pinedraw to enter lotteries or book campsites.",
            style = MaterialTheme.typography.bodyLarge,
            color = PinedrawColors.TextSecondary
        )
        policies.forEach { PolicySection(it) }
    }
}

/** One policy topic in a glass card. */
@Composable
private fun PolicySection(item: PolicyItem) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(item.icon, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = PinedrawColors.Text)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.description, style = MaterialTheme.typography.bodyMedium, color = PinedrawColors.TextSecondary)
        }
    }
}
