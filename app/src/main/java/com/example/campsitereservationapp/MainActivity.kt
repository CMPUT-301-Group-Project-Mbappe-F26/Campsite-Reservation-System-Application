package com.example.campsitereservationapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.campsitereservationapp.auth.WelcomeScreen
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.profile.EditProfileScreen
import com.example.campsitereservationapp.profile.ProfileScreen
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
                var editing by remember { mutableStateOf(false) }
                var loading by remember { mutableStateOf(false) }
                var checkingUser by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    if (FirebaseAuth.getInstance().currentUser != null) {
                        try {
                            user = userService.getOrCreateUser() // Load saved profile
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@MainActivity,
                                "Could not load profile",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    checkingUser = false
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
                        )
                    }
                    editing -> {
                        EditProfileScreen(
                            user = user!!,
                            saving = loading,
                            onSave = { updatedUser ->
                                loading = true
                                scope.launch {
                                    try {
                                        userService.updateUser(updatedUser) // Save to Firestore
                                        user = updatedUser
                                        editing = false
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Profile updated",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Could not save profile",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } finally {
                                        loading = false
                                    }
                                }
                            },
                            onCancel = { editing = false }
                        )
                    }
                    else -> {
                        ProfileScreen(
                            user = user!!,
                            onEditClick = { editing = true }
                        )
                    }
                }
            }
        }
    }
}
