package com.example.campsitereservationapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.campsitereservationapp.auth.OnboardingScreen
import com.example.campsitereservationapp.auth.WelcomeScreen
import com.example.campsitereservationapp.auth.hasCompletedOnboarding
import com.example.campsitereservationapp.auth.markOnboardingComplete
import com.example.campsitereservationapp.home.MainScreen
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.notification.LotteryNotificationService
import com.example.campsitereservationapp.service.UserService
import com.example.campsitereservationapp.service.ProfileImageService
import com.example.campsitereservationapp.profile.prepareProfileImage
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawTheme
import com.google.firebase.auth.FirebaseAuth
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Main activity that starts the application.
 */
class MainActivity : ComponentActivity() {
    private var notificationLotteryId by mutableStateOf<String?>(null)
    /**
    * Sets up the app screens and loads the signed-in profile.
    * @param savedInstanceState The saved activity state, if available.
    */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationLotteryId = intent.getStringExtra(LotteryNotificationService.EXTRA_LOTTERY_ID)
        enableEdgeToEdge()
        setContent {
            PinedrawTheme {
                val userService = remember { UserService() }
                val profileImageService = remember { ProfileImageService() }
                val notificationService = remember { LotteryNotificationService(this@MainActivity) }
                val scope = rememberCoroutineScope()
                var user by remember { mutableStateOf<User?>(null) }
                var loading by remember { mutableStateOf(false) }
                var checkingUser by remember { mutableStateOf(true) }
                var notificationPermissionGranted by remember {
                    mutableStateOf(
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                    )
                }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { notificationPermissionGranted = it }

                LaunchedEffect(Unit) {
                    if (FirebaseAuth.getInstance().currentUser != null) {
                        try {
                            user = userService.loadCurrentUser()
                        } catch (_: Exception) {
                            Toast.makeText(
                                this@MainActivity,
                                "Could not load profile",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    checkingUser = false
                }

                LaunchedEffect(user?.id, user?.notificationsEnabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        user?.notificationsEnabled == true &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                DisposableEffect(user?.id, user?.notificationsEnabled, notificationPermissionGranted) {
                    val listener = if (notificationPermissionGranted) {
                        user?.let { notificationService.listen(it) }
                    } else null
                    onDispose { listener?.remove() }
                }

                var onboarded by remember { mutableStateOf(hasCompletedOnboarding(this@MainActivity)) }

                when {
                    checkingUser -> {
                        Box(
                            modifier = Modifier.fillMaxSize().background(PinedrawColors.Background),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = PinedrawColors.Lime)
                        }
                    }
                    user == null && !onboarded -> {
                        OnboardingScreen(onFinish = {
                            markOnboardingComplete(this@MainActivity)
                            onboarded = true
                        })
                    }
                    user == null -> {
                        WelcomeScreen(
                            loading = loading,
                            onGuestClick = {
                                if (loading) return@WelcomeScreen
                                loading = true
                                scope.launch {
                                    try {
                                        user = userService.getOrCreateUser()
                                    } catch (_: Exception) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Could not continue as guest. Check your connection and try again.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } finally {
                                        loading = false
                                    }
                                }
                            }
                        )
                    }
                    else -> {
                        MainScreen(
                            user = user!!,
                            openLotteryId = notificationLotteryId,
                            onLotteryOpened = { notificationLotteryId = null },
                            onSaveProfileImage = { uri ->
                                val userId = checkNotNull(user).id
                                val jpeg = uri?.let {
                                    withContext(Dispatchers.IO) { prepareProfileImage(this@MainActivity, it) }
                                }
                                val updatedUser = profileImageService.save(userId, jpeg)
                                if (user?.id == userId) user = updatedUser
                            },
                            onDeleteProfile = {
                                userService.deleteProfile()
                                notificationLotteryId = null
                                intent.removeExtra(LotteryNotificationService.EXTRA_LOTTERY_ID)
                                NotificationManagerCompat.from(this@MainActivity).cancelAll()
                                user = null
                                Toast.makeText(this@MainActivity, "Profile deleted", Toast.LENGTH_SHORT).show()
                            },
                            onSaveProfile = { updatedUser ->
                                user = userService.updateUser(updatedUser) // Preserve the latest saved image.
                                Toast.makeText(
                                    this@MainActivity,
                                    "Profile updated",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            }
        }
    }
    /**
     * Gets the lottery ID when a new notification intent comes in.
     *
     * @param intent The new intent received.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        notificationLotteryId = intent.getStringExtra(LotteryNotificationService.EXTRA_LOTTERY_ID)
    }
}
