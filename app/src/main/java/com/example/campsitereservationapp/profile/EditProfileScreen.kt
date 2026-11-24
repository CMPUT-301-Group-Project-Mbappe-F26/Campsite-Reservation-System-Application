package com.example.campsitereservationapp.profile

import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.ui.components.ButtonStyle
import com.example.campsitereservationapp.ui.components.GlassCard
import com.example.campsitereservationapp.ui.components.PinedrawButton
import com.example.campsitereservationapp.ui.components.PinedrawDialog
import com.example.campsitereservationapp.ui.components.PinedrawScreen
import com.example.campsitereservationapp.ui.components.PinedrawTextField
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.ui.theme.PinedrawColors

private enum class Field { NAME, EMAIL, PHONE }

/**
 * Shows the profile editing screen where users can update their details.
 *
 * @param user The current user.
 * @param onSave Saves the updated profile.
 * @param onCancel Goes back without saving.
 * @param saving Whether the profile is currently being saved.
 * @param deleting Whether profile deletion is in progress.
 * @param onDelete Runs after the user confirms permanent deletion.
 */
@Composable
fun EditProfileScreen(
    user: User,
    onSave: (User) -> Unit,
    onCancel: () -> Unit,
    saving: Boolean = false,
    deleting: Boolean = false,
    onDelete: () -> Unit
) {
    val busy = saving || deleting
    var confirmDelete by rememberSaveable(user.id) { mutableStateOf(false) }
    BackHandler { if (!busy) onCancel() }
    var name by remember(user.id) { mutableStateOf(user.name) }
    var email by remember(user.id) { mutableStateOf(user.email) }
    var phone by remember(user.id) { mutableStateOf(user.phone) }
    var city by remember(user.id) { mutableStateOf(user.city) }
    var error by remember { mutableStateOf("") }
    // Only used to show the error under the right field
    var errorField by remember { mutableStateOf<Field?>(null) }

    fun save() {
        val validEmail = email.isBlank() || Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
        val validPhone = phone.isBlank() ||
            phone.all { it.isDigit() || it in "+-() " } && phone.count { it.isDigit() } >= 7
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
                onSave(user.copy(name = name.trim(), email = email.trim(), phone = phone.trim(), city = city.trim()))
            }
        }
    }

    PinedrawScreen(
        title = "Personal info",
        onBack = { if (!busy) onCancel() },
        modifier = Modifier.imePadding(),
        bottomBar = {
            PinedrawButton(
                if (saving) "Saving..." else "Save changes",
                onClick = ::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                loading = saving
            )
        }
    ) {
        ProfileAvatar(
            name = name,
            imageUrl = user.profileImageUrl,
            modifier = Modifier.align(Alignment.CenterHorizontally).size(96.dp)
        )
        ProfileField("Full name", name, if (errorField == Field.NAME) error else null, Icons.Rounded.Person,
            KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)) { name = it }
        ProfileField("Email", email, if (errorField == Field.EMAIL) error else null, Icons.Rounded.Email,
            KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)) { email = it }
        ProfileField("Phone", phone, if (errorField == Field.PHONE) error else null, Icons.Rounded.Phone,
            KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)) { phone = it }
        ProfileField("Home city (optional)", city, null, Icons.Rounded.LocationCity,
            KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done)) { city = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = PinedrawColors.TextMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Only you and owners you book with can see your phone number.",
                style = MaterialTheme.typography.bodySmall,
                color = PinedrawColors.TextMuted
            )
        }
        Spacer(Modifier.height(PinedrawSpacing.Small))
        GlassCard(borderColor = PinedrawColors.Coral.copy(alpha = 0.3f), container = PinedrawColors.Coral.copy(alpha = 0.06f)) {
            Text("Danger zone", style = MaterialTheme.typography.titleMedium, color = PinedrawColors.Coral)
            Text(
                "Deleting your profile removes you from every waiting list and cancels your active reservations.",
                style = MaterialTheme.typography.bodyMedium,
                color = PinedrawColors.TextSecondary
            )
            PinedrawButton(
                if (deleting) "Deleting profile..." else "Delete Profile",
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
                style = ButtonStyle.SoftDestructive,
                icon = Icons.Rounded.DeleteForever,
                enabled = !busy,
                loading = deleting,
                compact = true
            )
        }
    }

    if (confirmDelete) PinedrawDialog(
        title = "Delete your profile?",
        message = "This is permanent. You will be removed from all waiting lists, and your active reservations will be cancelled.",
        icon = Icons.Rounded.DeleteForever,
        iconTint = PinedrawColors.Coral,
        confirmText = "Delete Profile",
        confirmStyle = ButtonStyle.Destructive,
        confirmEnabled = !busy,
        onDismiss = { confirmDelete = false },
        onConfirm = {
            confirmDelete = false
            onDelete()
        }
    )
}

// Shows a profile input and its validation message.
@Composable
private fun ProfileField(
    label: String,
    value: String,
    error: String?,
    icon: ImageVector,
    keyboard: KeyboardOptions,
    onChange: (String) -> Unit
) {
    PinedrawTextField(
        value = value,
        onValueChange = onChange,
        label = label,
        leadingIcon = icon,
        isError = !error.isNullOrEmpty(),
        supportingText = error?.takeIf { it.isNotEmpty() },
        keyboardOptions = keyboard
    )
}
