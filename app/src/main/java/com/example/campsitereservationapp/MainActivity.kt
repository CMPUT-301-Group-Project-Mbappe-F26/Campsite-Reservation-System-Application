package com.example.campsitereservationapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.example.campsitereservationapp.auth.WelcomeScreen
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.service.UserService
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PinedrawTheme {
                val userService = remember { UserService() }
                val scope = rememberCoroutineScope()
                var user by remember { mutableStateOf<User?>(null) }
                var loading by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    if (FirebaseAuth.getInstance().currentUser?.isAnonymous == true) {
                        try {
                            user = userService.getOrCreateUser() // Load returning guest
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@MainActivity,
                                "Could not load profile",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                if (user != null) {
                    GuestScreen(user!!)
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        WelcomeScreen(
                            onGoogleClick = {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Google sign-in not implemented yet",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onRegisterClick = {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Registration not implemented yet",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onLoginClick = {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Login not implemented yet",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onGuestClick = {
                                if (!loading) {
                                    loading = true
                                    scope.launch {
                                        try {
                                            user = userService.getOrCreateUser() // Create or load guest
                                        } catch (e: Exception) {
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
                            }
                        )
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                                color = PinedrawColors.Lime
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GuestScreen(user: User) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to Pinedraw!",
            color = PinedrawColors.Lime,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Guest profile loaded successfully",
            color = PinedrawColors.Text
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "User ID: ${user.id}",
            color = PinedrawColors.TextSecondary,
            fontSize = 12.sp
        )
    }
}
