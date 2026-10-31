package com.example.campsitereservationapp.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.profile.EditProfileScreen
import com.example.campsitereservationapp.profile.ProfileScreen
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    user: User,
    onSaveProfile: suspend (User) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = PinedrawColors.Background,
        bottomBar = {
            if (!editing) {
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
                                } catch (e: Exception) {
                                    error = "Could not save profile"
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    )
                }

                selectedTab == 0 -> HomeScreen(
                    user = user,
                    onProfileClick = { selectedTab = 3 } // Open Profile tab
                )
                selectedTab == 1 -> EmptyTab(
                    "Lotteries",
                    "Your campsite draws will appear here."
                )
                selectedTab == 2 -> EmptyTab(
                    "Extra",
                    "This is an extra tab if needed"
                )
                else -> ProfileScreen(
                    user = user,
                    onEditClick = { editing = true }
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

// Floating pill-shaped tab bar; the selected tab is highlighted in lime
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
            tabs.forEachIndexed { index, title ->
                val selected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(CircleShape)
                        .then(
                            if (selected) Modifier.background(PinedrawColors.PrimaryGradient)
                            else Modifier
                        )
                        .clickable { onTabSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (selected) PinedrawColors.Ink else PinedrawColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

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