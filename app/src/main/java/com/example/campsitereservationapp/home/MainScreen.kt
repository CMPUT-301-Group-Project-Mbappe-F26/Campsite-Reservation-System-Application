package com.example.campsitereservationapp.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.campsitereservationapp.campsite.CreateCampsiteScreen
import com.example.campsitereservationapp.campsite.CampsiteRegistrationViewModel
import com.example.campsitereservationapp.campsite.HostDashboardScreen
import com.example.campsitereservationapp.campsite.MyCampsitesScreen
import com.example.campsitereservationapp.campsite.CampsiteDetailsScreen
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.lottery.LotteriesScreen
import com.example.campsitereservationapp.profile.EditProfileScreen
import com.example.campsitereservationapp.profile.ProfileScreen
import com.example.campsitereservationapp.reservation.MyReservationsScreen
import com.example.campsitereservationapp.reservation.ReservationDetailsScreen
import com.example.campsitereservationapp.reservation.ReserveScreen
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing
import kotlinx.coroutines.launch

// Handles the main tabs and host screen navigation.
@Composable
fun MainScreen(
    user: User,
    onSaveProfile: suspend (User) -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var hostMode by rememberSaveable { mutableStateOf(false) }
    var registering by rememberSaveable { mutableStateOf(false) }
    var showingCampsites by rememberSaveable { mutableStateOf(false) }
    var campsiteId by rememberSaveable { mutableStateOf<String?>(null) }
    var reservationId by rememberSaveable { mutableStateOf<String?>(null) }
    var reserveLotteryId by rememberSaveable { mutableStateOf<String?>(null) }
    val registration: CampsiteRegistrationViewModel = viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = PinedrawColors.Background,
        bottomBar = {
            if (!editing && !hostMode && reservationId == null && reserveLotteryId == null) {
                PinedrawNavBar(
                    tabs = listOf("Discover", "Lotteries", "Extra", "Profile"),
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
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
                hostMode && campsiteId != null -> CampsiteDetailsScreen(
                    campsiteId = campsiteId!!,
                    onBack = { campsiteId = null }
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
                        onCancel = { editing = false },
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
                    onBack = { reservationId = null }
                )

                reserveLotteryId != null -> ReserveScreen(
                    lotteryId = reserveLotteryId!!,
                    onBack = { reserveLotteryId = null },
                    onReserved = {
                        reserveLotteryId = null
                        selectedTab = 2 // Show the new reservation
                    }
                )

                selectedTab == 0 -> HomeScreen(
                    user = user,
                    onProfileClick = { selectedTab = 3 } // Open Profile tab
                )
                selectedTab == 1 -> LotteriesScreen(user = user)
                selectedTab == 2 -> MyReservationsScreen(
                    onReservationClick = { reservationId = it },
                    onReserveClick = { reserveLotteryId = it }
                )
                else -> ProfileScreen(
                    user = user,
                    onEditClick = { editing = true },
                    onHostClick = { hostMode = true }
                )
            }

            if (error.isNotEmpty()) {
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

// Shows the bottom navigation tabs.
@Composable
private fun PinedrawNavBar(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PinedrawColors.Background)
            .navigationBarsPadding()
            .padding(horizontal = PinedrawSpacing.Large, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(PinedrawColors.Surface)
                .border(1.dp, Color.White.copy(alpha = 0.06f), CircleShape)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icons = listOf(Icons.Default.Search, Icons.Default.Star, Icons.Default.MoreVert, Icons.Default.Person)
            tabs.forEachIndexed { index, title ->
                val selected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(CircleShape)
                        .then(
                            if (selected) Modifier.background(PinedrawColors.PrimaryGradient)
                            else Modifier
                        )
                        .clickable { onTabSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            icons[index], contentDescription = null, modifier = Modifier.size(20.dp),
                            tint = if (selected) PinedrawColors.Ink else PinedrawColors.TextSecondary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = title,
                            color = if (selected) PinedrawColors.Ink else PinedrawColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// Shows a message for a tab that is not ready yet.
@Composable
private fun EmptyTab(title: String, message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF1E3A26),
                    0.45f to PinedrawColors.Background,
                    1f to PinedrawColors.Background
                )
            )
            .statusBarsPadding()
            .padding(PinedrawSpacing.Screen),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(PinedrawColors.Glass)
                .border(1.dp, Color.White.copy(alpha = 0.06f), MaterialTheme.shapes.large)
                .padding(horizontal = PinedrawSpacing.Screen, vertical = PinedrawSpacing.Section),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = PinedrawColors.Lime
            )
            Spacer(Modifier.height(PinedrawSpacing.Small))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = PinedrawColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
