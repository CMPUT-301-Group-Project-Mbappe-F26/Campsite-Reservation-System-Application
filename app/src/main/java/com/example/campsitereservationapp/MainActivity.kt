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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.campsitereservationapp.auth.WelcomeScreen
import com.example.campsitereservationapp.home.MainScreen
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.notification.LotteryNotificationService
import com.example.campsitereservationapp.service.UserService
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawTheme
import com.google.firebase.auth.FirebaseAuth
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.launch

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

                when {
                    checkingUser || loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = PinedrawColors.Lime)
                        }
                    }
                    user == null -> {
                        WelcomeScreen(
                            onGoogleClick = {
                                Toast.makeText(this, "Google sign-in not available yet", Toast.LENGTH_SHORT).show()
                            },
                            onRegisterClick = {
                                Toast.makeText(this, "Registration not available yet", Toast.LENGTH_SHORT).show()
                            },
                            onLoginClick = {
                                Toast.makeText(this, "Login not available yet", Toast.LENGTH_SHORT).show()
                            },
                            onGuestClick = {
                                loading = true
                                scope.launch {
                                    try {
                                        user = userService.getOrCreateUser()
                                    } catch (_: Exception) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Could not continue as guest",
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
                            onDeleteProfile = {
                                userService.deleteProfile()
                                notificationLotteryId = null
                                intent.removeExtra(LotteryNotificationService.EXTRA_LOTTERY_ID)
                                NotificationManagerCompat.from(this@MainActivity).cancelAll()
                                user = null
                                Toast.makeText(this@MainActivity, "Profile deleted", Toast.LENGTH_SHORT).show()
                            },
                            onSaveProfile = { updatedUser ->
                                userService.updateUser(updatedUser) // Save to Firestore
                                user = updatedUser
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
