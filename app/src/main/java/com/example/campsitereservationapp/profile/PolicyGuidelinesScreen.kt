package com.example.campsitereservationapp.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shows the policies and guidelines for the app.
 */
@Composable
fun PolicyGuidelinesScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Settings")
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Policy & Guidelines",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Here are some things to know before using Pinedraw to enter lotteries or book campsites."
        )
        Spacer(Modifier.height(24.dp))

        PolicySection(
            "1. Campsite Information",
            "Before booking, make sure to check the campsite's location, available dates, number of guests allowed, and any rules the owner has added. Different campsites can have different requirements. You can search for campsites and use filters to narrow down the results. If a campsite doesn't match your filters, it won't show up."
        )

        PolicySection(
            "2. Campsite Availability",
            "Only available dates can be booked. If someone has already reserved certain dates, you won't be able to choose those same dates. Availability can also change when someone makes or cancels a reservation. Just because dates were available earlier doesn't mean they'll still be available later."
        )

        PolicySection(
            "3. Joining a Lottery",
            "You can enter a lottery while registration is open. When joining, you'll need to enter your group size, and it can't be more than the campsite allows. You also can't join the same lottery twice. Some lotteries have a limit on how many people can enter, so once the waiting list is full, no more entries will be accepted. You won't be able to join after registration closes either."
        )

        PolicySection(
            "4. Leaving a Lottery",
            "If you don't want to participate anymore, you can leave the lottery before registration closes. The app will ask you to confirm before removing you from the waiting list. If you decide not to confirm, your entry stays there. Once registration closes, you can't leave through the normal option."
        )

        PolicySection(
            "5. Lottery Winners",
            "Winners are picked randomly after registration closes. Entering doesn't mean you're guaranteed to win. The number of winners depends on how the lottery was set up. If a winner declines their spot, another person may be selected from the remaining entrants. If there isn't anyone eligible left, another winner won't be picked."
        )

        PolicySection(
            "6. Booking After Winning",
            "If you win, you'll have a certain amount of time to make your reservation. You need to book before the deadline. You'll also have to choose dates that are still available, and your check-out date must be after your check-in date. For lotteries with multiple campsites, you can select one campsite to book. Campsites without available dates can't be selected."
        )

        PolicySection(
            "7. Declining a Win",
            "You don't have to book if you win a lottery. If you decide to decline, the app will ask for confirmation first. Once you've declined, you can't go back and use that same win to make a reservation. Make sure you've decided before confirming."
        )

        PolicySection(
            "8. Reservations and Cancellations",
            "After booking, you can check your reservation details in the app, including your dates and number of guests. If you need to cancel, you can do so before the check-in date. The app will show the campsite's cancellation policy and ask you to confirm. Cancelling makes those reserved dates available again. Once your stay has started, the normal cancellation option is no longer available."
        )

        PolicySection(
            "9. Campsite Rules",
            "Campsite owners can set their own rules, so make sure you read them before booking. Some campsites require you to accept the rules before you can confirm a reservation. Owners may also update their campsite information and policies. Always check the rules for the specific campsite you're booking."
        )

        PolicySection(
            "10. Reservation Changes",
            "Campsite owners can reject reservations. If that happens, the reservation is cancelled and the dates become available again. You'll be notified about the change. You can check your active, past, and cancelled reservations in the app to see their current status."
        )

        PolicySection(
            "11. Ratings and Reviews",
            "You can rate a campsite after you've completed your stay. Ratings are from 1 to 5 stars, and you can't rate campsites you haven't stayed at. The ratings submitted by users are used to calculate the campsite's average rating."
        )

        PolicySection(
            "12. Accounts and Notifications",
            "You can update your profile information and change your lottery notification settings. If you turn notifications off, you won't receive lottery-result notifications through that setting. Deleting your account is permanent and removes you from waiting lists and cancels active reservations. Campsites and accounts can also be removed by administrators. If a campsite is removed, its active lottery is closed and its waiting list is cleared. Removing a campsite owner can also affect their existing campsites and reservations."
        )

        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Displays a heading and its policy information.
 */
@Composable
private fun PolicySection(title: String, description: String) {
    Column(
        modifier = Modifier.padding(bottom = 24.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}