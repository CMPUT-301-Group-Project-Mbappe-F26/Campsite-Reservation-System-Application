package com.example.campsitereservationapp.profile

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.ui.theme.PinedrawColors

private enum class Field { NAME, EMAIL, PHONE }

// Lets the user edit and save their profile details.
/**
 * Shows the profile editing screen where users can update their details.
 *
 * @param user The current user.
 * @param onSave Saves the updated profile.
 * @param onCancel Goes back without saving.
 * @param saving Whether the profile is currently being saved.
 */
@Composable
fun EditProfileScreen(
    user: User,
    onSave: (User) -> Unit,
    onCancel: () -> Unit,
    saving: Boolean = false
) {
    var name by remember(user.id) { mutableStateOf(user.name) }
    var email by remember(user.id) { mutableStateOf(user.email) }
    var phone by remember(user.id) { mutableStateOf(user.phone) }
    var city by remember(user.id) { mutableStateOf(user.city) }
    var error by remember { mutableStateOf("") }
    // Only used to show the error under the right field
    var errorField by remember { mutableStateOf<Field?>(null) }

    Column(
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
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Header
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    .clickable(enabled = !saving) { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back", tint = PinedrawColors.Text,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = "Personal info",
                color = PinedrawColors.Text,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(20.dp))

            // Avatar
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF2E8C86), Color(0xFF1F5C73)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.firstOrNull()?.uppercase() ?: "P",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PinedrawColors.Text
                )
            }

            Spacer(Modifier.height(28.dp))
            ProfileField(
                label = "Full name",
                value = name,
                error = if (errorField == Field.NAME) error else null,
                keyboard = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                )
            ) { name = it }
            ProfileField(
                label = "Email",
                value = email,
                error = if (errorField == Field.EMAIL) error else null,
                keyboard = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            ) { email = it }
            ProfileField(
                label = "Phone",
                value = phone,
                error = if (errorField == Field.PHONE) error else null,
                keyboard = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                )
            ) { phone = it }
            ProfileField(
                label = "Home city (optional)",
                value = city,
                error = null,
                keyboard = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                )
            ) { city = it }
            Text(
                text = "Only you and owners you book with can see your phone number.",
                color = PinedrawColors.TextMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(Modifier.height(24.dp))
        }

        // Save
        Button(
            onClick = {
                val validEmail = email.isBlank() ||
                        Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
                val validPhone = phone.isBlank() ||
                        phone.all { it.isDigit() || it in "+-() " } &&
                        phone.count { it.isDigit() } >= 7
                when {
                    name.isBlank() -> {
                        error = "Please enter your name"
                        errorField = Field.NAME
                    }
                    !validEmail -> {
                        error = "Please enter a valid email"
                        errorField = Field.EMAIL
                    }
                    !validPhone -> {
                        error = "Please enter a valid phone number"
                        errorField = Field.PHONE
                    }
                    else -> {
                        error = ""
                        errorField = null
                        onSave(user.copy(
                            name = name.trim(),
                            email = email.trim(),
                            phone = phone.trim(),
                            city = city.trim()
                        ))
                    }
                }
            },
            enabled = !saving,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = PinedrawColors.Ink,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = PinedrawColors.TextMuted
            ),
            contentPadding = PaddingValues()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (saving) Brush.horizontalGradient(
                            listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.06f))
                        ) else PinedrawColors.PrimaryGradient
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (saving) "Saving..." else "Save changes",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (error.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Fix 1 field to save",
                color = PinedrawColors.Coral,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// Shows a profile input and its validation message.
@Composable
private fun ProfileField(
    label: String,
    value: String,
    error: String?,
    keyboard: KeyboardOptions,
    onChange: (String) -> Unit
) {
    val isError = !error.isNullOrEmpty()
    Text(
        text = label,
        color = PinedrawColors.TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp)
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        isError = isError,
        keyboardOptions = keyboard,
        textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = PinedrawColors.Text,
            unfocusedTextColor = PinedrawColors.Text,
            errorTextColor = PinedrawColors.Text,
            focusedContainerColor = Color.White.copy(alpha = 0.06f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
            errorContainerColor = Color.White.copy(alpha = 0.06f),
            focusedBorderColor = PinedrawColors.Lime,
            unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
            errorBorderColor = PinedrawColors.Coral,
            cursorColor = PinedrawColors.Lime,
            errorCursorColor = PinedrawColors.Coral
        )
    )
    if (isError) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = error,
            color = PinedrawColors.Coral,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
    Spacer(Modifier.height(18.dp))
}
