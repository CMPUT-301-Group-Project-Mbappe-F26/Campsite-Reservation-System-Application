package com.example.campsitereservationapp.profile

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campsitereservationapp.model.User
import com.example.campsitereservationapp.service.MAX_PROFILE_IMAGE_BYTES
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Draft changes stay local until Save; cancellation preserves the current saved image. */
@Composable
internal fun ProfileImageEditor(
    user: User,
    enabled: Boolean,
    onSaveImage: suspend (Uri?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedUri by rememberSaveable(user.id, user.profileImagePath, user.profileImageUrl) {
        mutableStateOf<String?>(null)
    }
    var removing by rememberSaveable(user.id, user.profileImagePath, user.profileImageUrl) { mutableStateOf(false) }
    var saving by remember(user.id) { mutableStateOf(false) }
    var error by remember(user.id) { mutableStateOf("") }
    val changed = selectedUri != null || removing
    val busy = saving || !enabled
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            // Retain access to a selected draft across activity recreation where supported.
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Some providers grant access only for this app session.
            }
            selectedUri = uri.toString()
            removing = false
            error = ""
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ProfileAvatar(
            name = user.name,
            imageUrl = if (removing) "" else user.profileImageUrl,
            localUri = selectedUri,
            modifier = Modifier.size(104.dp)
                .border(2.5.dp, PinedrawColors.Lime, CircleShape).padding(5.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = !busy,
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            ) {
                Text(if (user.profileImageUrl.isBlank() && selectedUri == null) "Add photo" else "Change photo")
            }
            if (!removing && (user.profileImageUrl.isNotBlank() || user.profileImagePath.isNotBlank() || selectedUri != null)) {
                TextButton(enabled = !busy, onClick = {
                    selectedUri = null
                    removing = user.profileImageUrl.isNotBlank() || user.profileImagePath.isNotBlank()
                    error = ""
                }) { Text("Remove photo", color = PinedrawColors.Coral) }
            }
        }
        if (changed) {
            Text(
                if (removing) "Save to use your default avatar." else "Your photo will change when you save.",
                style = MaterialTheme.typography.bodySmall,
                color = PinedrawColors.TextSecondary,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !busy, onClick = {
                    selectedUri = null
                    removing = false
                    error = ""
                }) { Text("Cancel") }
                Button(enabled = !busy, onClick = {
                    val image = selectedUri?.let(Uri::parse)
                    saving = true
                    error = ""
                    scope.launch {
                        try {
                            onSaveImage(image)
                            selectedUri = null
                            removing = false
                        } catch (exception: Exception) {
                            coroutineContext.ensureActive()
                            error = profileImageErrorMessage(exception)
                        } finally {
                            saving = false
                        }
                    }
                }) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (saving) "Saving photo..." else "Save photo")
                }
            }
        }
        if (error.isNotBlank()) Text(error, color = PinedrawColors.Coral, textAlign = TextAlign.Center)
    }
}

/** Displays the saved photo or a selected draft, with the initials avatar as a fallback. */
@Composable
internal fun ProfileAvatar(
    name: String,
    imageUrl: String,
    modifier: Modifier = Modifier,
    localUri: String? = null
) {
    val context = LocalContext.current
    var loading by remember(imageUrl, localUri) { mutableStateOf(false) }
    var failed by remember(imageUrl, localUri) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, imageUrl, localUri) {
        value = null
        loading = localUri != null || imageUrl.isNotBlank()
        failed = false
        if (loading) {
            try {
                value = withContext(Dispatchers.IO) {
                    val bytes = if (localUri != null) readProfileImage(context, Uri.parse(localUri))
                    else FirebaseStorage.getInstance().getReferenceFromUrl(imageUrl)
                        .getBytes(MAX_PROFILE_IMAGE_BYTES.toLong()).await()
                    decodeProfileImage(bytes)
                }
            } catch (_: Exception) {
                coroutineContext.ensureActive()
                failed = true
            }
        }
        loading = false
    }
    Box(
        modifier = modifier.clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF2E8C86), Color(0xFF1F5C73))))
            .semantics {
                contentDescription = when {
                    loading -> "Loading profile photo"
                    failed -> "Profile photo unavailable; default avatar"
                    bitmap != null -> "Profile photo"
                    else -> "Default profile avatar"
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            loading -> CircularProgressIndicator(Modifier.size(24.dp), color = PinedrawColors.Lime)
            bitmap != null -> Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> Text(
                name.trim().firstOrNull()?.uppercase() ?: "P",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = PinedrawColors.Text
            )
        }
    }
}
