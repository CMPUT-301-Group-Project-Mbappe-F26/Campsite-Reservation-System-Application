package com.example.campsitereservationapp.home

import android.util.Log
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cabin
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Cabin
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.campsitereservationapp.ui.theme.PinedrawDimens
import com.example.campsitereservationapp.ui.theme.PinedrawRadius
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.campsitereservationapp.admin.AdminCampsitesScreen
import com.example.campsitereservationapp.admin.AdminDashboardScreen
import com.example.campsitereservationapp.admin.AdminMessageLogsScreen
import com.example.campsitereservationapp.admin.AdminNotificationLogsScreen
import com.example.campsitereservationapp.campsite.CreateCampsiteScreen
import com.example.campsitereservationapp.campsite.CampsiteRegistrationViewModel
import com.example.campsitereservationapp.campsite.HostDashboardScreen
import com.example.campsitereservationapp.campsite.MyCampsitesScreen
import com.example.campsitereservationapp.campsite.CampsiteDetailsScreen
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.lottery.LotteriesScreen
import com.example.campsitereservationapp.messaging.CampsiteMessagesScreen
import com.example.campsitereservationapp.messaging.ReservationMessagesScreen
import com.example.campsitereservationapp.profile.EditProfileScreen
import com.example.campsitereservationapp.profile.ProfileScreen
import com.example.campsitereservationapp.reservation.MyReservationsScreen
import com.example.campsitereservationapp.reservation.ReservationDetailsScreen
import com.example.campsitereservationapp.reservation.ReserveScreen
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import com.example.campsitereservationapp.profile.PolicyGuidelinesScreen

/**
 * Shows the main screens and handles navigation between them.
 *
 * @param user The current user.
 * @param onSaveProfile Saves changes to the user's profile.
 * @param onDeleteProfile Deletes the current profile and returns to Welcome.
 * @param onSaveProfileImage Saves a selected photo, or removes it when null.
 * @param openLotteryId The lottery to open from a notification, if any.
 * @param onLotteryOpened Called after the lottery is opened.
 */
@Composable
fun MainScreen(
    user: User,
    onSaveProfile: suspend (User) -> Unit,
    onDeleteProfile: suspend () -> Unit,
    onSaveProfileImage: suspend (Uri?) -> Unit,
    openLotteryId: String? = null,
    onLotteryOpened: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var showingPolicies by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var deletingProfile by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var hostMode by rememberSaveable { mutableStateOf(false) }
    var registering by rememberSaveable { mutableStateOf(false) }
    var showingCampsites by rememberSaveable { mutableStateOf(false) }
    var campsiteId by rememberSaveable { mutableStateOf<String?>(null) }
    var entrantCampsiteId by rememberSaveable { mutableStateOf<String?>(null) }  // selected from Discover or Lotteries
    var searchText by rememberSaveable { mutableStateOf("") }  // Keep the search text when opening and closing campsite details
    var reservationId by rememberSaveable { mutableStateOf<String?>(null) }
    var showingCampsiteMessages by rememberSaveable { mutableStateOf(false) }
    var messageReservationId by rememberSaveable { mutableStateOf<String?>(null) }
    var reserveLotteryId by rememberSaveable { mutableStateOf<String?>(null) }
    var adminMode by rememberSaveable { mutableStateOf(false) }
    var showingAdminCampsites by rememberSaveable { mutableStateOf(false) }
    var showingMessageLogs by rememberSaveable { mutableStateOf(false) }
    var showingNotificationLogs by rememberSaveable { mutableStateOf(false) }
    var adminCampsiteId by rememberSaveable { mutableStateOf<String?>(null) }  // selected from the admin list
    val registration: CampsiteRegistrationViewModel = viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanCampsite = rememberCampsiteScanner(onCampsiteFound = { entrantCampsiteId = it })
    LaunchedEffect(openLotteryId) {
        if (!openLotteryId.isNullOrBlank()) {
            reserveLotteryId = openLotteryId
            onLotteryOpened()
        }
    }
    LaunchedEffect(registration.createdId) {
        if (registration.createdId != null) {
            registering = false
            showingCampsites = true
            campsiteId = null
            Toast.makeText(context, "Campsite created successfully", Toast.LENGTH_LONG).show()
            registration.reset()
        }
    }
    BackHandler(enabled = hostMode && !registering) {
        when {
            campsiteId != null -> campsiteId = null
            showingCampsites -> showingCampsites = false
            else -> hostMode = false
        }
        error = ""
    }
    BackHandler(enabled = reservationId != null) { reservationId = null }
    BackHandler(enabled = reserveLotteryId != null) { reserveLotteryId = null }
    BackHandler(enabled = entrantCampsiteId != null) { entrantCampsiteId = null }  // Return to Discover after leaving campsite details
    BackHandler(enabled = adminMode) {
        when {
            showingNotificationLogs -> showingNotificationLogs = false
            showingMessageLogs -> showingMessageLogs = false
            adminCampsiteId != null -> adminCampsiteId = null
            showingAdminCampsites -> showingAdminCampsites = false
            else -> adminMode = false
        }
    }
    BackHandler(enabled = showingPolicies) {
        showingPolicies = false
        selectedTab = 3
    }
    BackHandler(enabled = !showingPolicies && (showingCampsiteMessages || messageReservationId != null)) {
        if (messageReservationId != null) messageReservationId = null
        else showingCampsiteMessages = false
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = PinedrawColors.Background,
        bottomBar = {
            // Hide bottom navigation when some other screen is open
            if (!editing && !hostMode && !adminMode && messageReservationId == null && reservationId == null && reserveLotteryId == null && entrantCampsiteId == null) {
                PinedrawNavBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    onScanClick = scanCampsite
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                showingPolicies -> PolicyGuidelinesScreen(
                    onBack = {
                        showingPolicies = false
                        selectedTab = 3
                    }
                )
                messageReservationId != null -> ReservationMessagesScreen(
                    reservationId = messageReservationId!!,
                    userId = user.id,
                    onBack = { messageReservationId = null }
                )
                hostMode && campsiteId != null && showingCampsiteMessages -> CampsiteMessagesScreen(
                    campsiteId = campsiteId!!,
                    onBack = { showingCampsiteMessages = false },
                    onOpenChat = { messageReservationId = it }
                )
                registering -> CreateCampsiteScreen(
                    saving = registration.saving,
                    saveError = registration.error,
                    onCancel = {
                        if (!registration.saving) {
                            registering = false
                            showingCampsites = false
                            registration.reset()
                        }
                    },
                    onCreate = registration::create
                )
                adminMode && adminCampsiteId != null -> CampsiteDetailsScreen(
                    campsiteId = adminCampsiteId!!,
                    onBack = { adminCampsiteId = null },
                    asAdmin = true
                )
                adminMode && showingAdminCampsites -> AdminCampsitesScreen(
                    onBack = { showingAdminCampsites = false },
                    onCampsiteClick = { adminCampsiteId = it }
                )
                adminMode && user.isAdmin && showingMessageLogs -> AdminMessageLogsScreen(
                    onBack = { showingMessageLogs = false }
                )
                adminMode && user.isAdmin && showingNotificationLogs -> AdminNotificationLogsScreen(
                    onBack = { showingNotificationLogs = false }
                )
                adminMode -> AdminDashboardScreen(
                    onBack = { adminMode = false },
                    onCampsitesClick = { showingAdminCampsites = true },
                    onMessageLogsClick = { if (user.isAdmin) showingMessageLogs = true },
                    onNotificationLogsClick = { if (user.isAdmin) showingNotificationLogs = true }
                )
                hostMode && campsiteId != null -> CampsiteDetailsScreen(
                    campsiteId = campsiteId!!,
                    onBack = { campsiteId = null },
                    onDeleted = { campsiteId = null },
                    onMessageGuests = { showingCampsiteMessages = true }
                )
                hostMode && showingCampsites -> MyCampsitesScreen(
                    onBack = { showingCampsites = false },
                    onCampsiteClick = { campsiteId = it },
                    onRegisterClick = {
                        registration.reset()
                        showingCampsites = false
                        registering = true
                    }
                )
                hostMode -> HostDashboardScreen(
                    onBack = {
                        hostMode = false
                        error = ""
                    },
                    onRegisterClick = {
                        registration.reset()
                        showingCampsites = false
                        registering = true
                    },
                    onMyCampsitesClick = { showingCampsites = true }
                )
                editing -> {
                    EditProfileScreen(
                        user = user,
                        saving = saving,
                        deleting = deletingProfile,
                        onCancel = {
                            editing = false
                            error = ""
                        },
                        onDelete = {
                            if (!saving && !deletingProfile) {
                                deletingProfile = true
                                error = ""
                                scope.launch {
                                    try {
                                        onDeleteProfile()
                                    } catch (exception: Exception) {
                                        coroutineContext.ensureActive()
                                        val code = (exception as? FirebaseFirestoreException)?.code
                                        error = when (code) {
                                            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                                                "Firebase denied profile deletion. Contact the app team."
                                            FirebaseFirestoreException.Code.UNAVAILABLE ->
                                                "Could not reach Firebase. Check your connection and try again."
                                            else -> "Could not finish deleting your profile. Try again."
                                        }
                                        Log.w("ProfileDeletion", "Deletion failed: ${code ?: exception.javaClass.simpleName}")
                                    } finally {
                                        deletingProfile = false
                                    }
                                }
                            }
                        },
                        onSave = { updatedUser ->
                            saving = true
                            error = ""

                            scope.launch {
                                try {
                                    onSaveProfile(updatedUser) // Save through UserService
                                    editing = false
                                } catch (_: Exception) {
                                    error = "Could not save profile"
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    )
                }

                reservationId != null -> ReservationDetailsScreen(
                    reservationId = reservationId!!,
                    onBack = { reservationId = null },
                    onOpenMessages = { messageReservationId = reservationId }
                )

                reserveLotteryId != null -> ReserveScreen(
                    lotteryId = reserveLotteryId!!,
                    onBack = { reserveLotteryId = null },
                    onReserved = {
                        reserveLotteryId = null
                        selectedTab = 2 // Show the new reservation
                    }
                )
                // Open campsite selected from Discover or Lotteries.
                entrantCampsiteId != null -> CampsiteDetailsScreen(
                    campsiteId = entrantCampsiteId!!,
                    entrantId = user.id,
                    onBack = { entrantCampsiteId = null },
                    fromDiscover = selectedTab == 0
                )

                selectedTab == 0 -> HomeScreen(
                    user = user,
                    onProfileClick = { selectedTab = 3 }, // Open Profile tab
                    onCampsiteClick = { entrantCampsiteId = it },
                    searchText = searchText,  // Remember the previous search
                    onSearchTextChange = { searchText = it }
                )
                selectedTab == 1 -> LotteriesScreen(
                    user = user,
                    onCampsiteClick = { entrantCampsiteId = it }
                )
                selectedTab == 2 -> MyReservationsScreen(
                    onReservationClick = { reservationId = it },
                    onReserveClick = { reserveLotteryId = it }
                )
                else -> ProfileScreen(
                    user = user,
                    onEditClick = { if (!saving) editing = true },
                    onHostClick = { hostMode = true },
                    onAdminClick = { if (user.isAdmin) adminMode = true },
                    onPolicyClick = { showingPolicies = true },
                    savingNotifications = saving,
                    onSaveImage = { uri ->
                        check(!saving && !deletingProfile) { "Please wait for your profile to finish saving." }
                        saving = true
                        error = ""
                        try {
                            onSaveProfileImage(uri)
                        } finally {
                            saving = false
                        }
                    },
                    onNotificationsChanged = { enabled ->
                        if (!saving) {
                            saving = true
                            error = ""
                            scope.launch {
                                try {
                                    onSaveProfile(user.copy(notificationsEnabled = enabled))
                                } catch (exception: kotlinx.coroutines.CancellationException) {
                                    throw exception
                                } catch (_: Exception) {
                                    error = "Could not save notification settings. Try again."
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    }
                )
            }

            if (error.isNotEmpty()) {
                LaunchedEffect(error) {
                    kotlinx.coroutines.delay(4000)
                    error = ""
                }
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(PinedrawSpacing.Large),
                    shape = MaterialTheme.shapes.medium,
                    containerColor = PinedrawColors.Surface,
                    contentColor = PinedrawColors.Coral
                ) {
                    Text(error, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private data class NavTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val navTabs = listOf(
    NavTab("Discover", Icons.Outlined.Home, Icons.Rounded.Home),
    NavTab("Lotteries", Icons.Outlined.ConfirmationNumber, Icons.Rounded.ConfirmationNumber),
    NavTab("Trips", Icons.Outlined.Cabin, Icons.Rounded.Cabin),
    NavTab("Profile", Icons.Outlined.Person, Icons.Rounded.Person)
)

// Floating bottom dock: two tabs on each side of a round QR scan button.
@Composable
private fun PinedrawNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onScanClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PinedrawColors.Background)
            .navigationBarsPadding()
            .padding(horizontal = PinedrawSpacing.Large, vertical = PinedrawSpacing.Small)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(PinedrawDimens.NavBarHeight)
                .clip(PinedrawRadius.Pill)
                .background(PinedrawColors.Surface)
                .border(PinedrawDimens.BorderWidth, PinedrawColors.Border, PinedrawRadius.Pill)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navTabs.forEachIndexed { index, tab ->
                if (index == 2) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(PinedrawColors.PrimaryGradient)
                                .clickable(onClick = onScanClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scan QR code", tint = PinedrawColors.Ink)
                        }
                    }
                }
                val selected = selectedTab == index
                val color = if (selected) PinedrawColors.Lime else PinedrawColors.TextMuted
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(PinedrawRadius.Large)
                        .clickable { onTabSelected(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        if (selected) tab.selectedIcon else tab.icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(PinedrawDimens.IconSize)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        color = color,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
