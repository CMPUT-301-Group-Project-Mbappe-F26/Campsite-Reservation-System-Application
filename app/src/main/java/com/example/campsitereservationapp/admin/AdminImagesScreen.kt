package com.example.campsitereservationapp.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cabin
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.campsite.CampsitePhoto
import com.example.campsitereservationapp.service.CampsiteService
import com.example.campsitereservationapp.service.UserService
import com.example.campsitereservationapp.ui.components.ButtonStyle
import com.example.campsitereservationapp.ui.components.CircleIconButton
import com.example.campsitereservationapp.ui.components.EmptyState
import com.example.campsitereservationapp.ui.components.ErrorState
import com.example.campsitereservationapp.ui.components.LoadingState
import com.example.campsitereservationapp.ui.components.PinedrawButton
import com.example.campsitereservationapp.ui.components.PinedrawScreen
import com.example.campsitereservationapp.ui.components.SegmentedTabs
import com.example.campsitereservationapp.ui.components.StatusChip
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawRadius
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Lets an admin browse every uploaded image (US 03.06.01): campsite promo photos and
 * profile photos, each labelled with the campsite or profile it belongs to. Tapping an
 * image opens a larger preview.
 *
 * [AdminImagesScreen] loads campsites and profiles from Firebase. [AdminImageGrid] only
 * draws the images, so intent tests can check it without Firebase.
 *
 * Outstanding issues: removing an image (US 03.03.01) is not implemented yet.
 */

/**
 * Loads every campsite and profile, then shows all of their uploaded images.
 *
 * @param onBack Returns to the Admin Dashboard.
 */
@Composable
fun AdminImagesScreen(onBack: () -> Unit) {
    val campsiteService = remember { CampsiteService() }
    val userService = remember { UserService() }
    var images by remember { mutableStateOf<List<AdminImage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh) {
        loading = true
        error = ""
        try {
            val result = withTimeoutOrNull(20000) {
                coroutineScope {
                    val campsites = async { campsiteService.getAllCampsites() }
                    val users = async { userService.getAllUsers() }
                    adminImages(campsites.await(), users.await())
                }
            }
            if (result == null) error = "Could not load images. Please try again."
            else images = result
        } catch (_: Exception) {
            coroutineContext.ensureActive() // Stop normally when leaving the screen.
            error = "Could not load images. Please try again."
        } finally {
            loading = false
        }
    }

    PinedrawScreen(
        title = "Images",
        onBack = onBack,
        backLabel = "Admin Dashboard",
        scrollable = false,
        actions = { CircleIconButton(Icons.Rounded.Refresh, "Refresh", { refresh++ }, enabled = !loading) }
    ) {
        when {
            loading -> LoadingState()
            error.isNotEmpty() -> ErrorState(error, onRetry = { refresh++ })
            else -> AdminImageGrid(images)
        }
    }
}

/**
 * Shows uploaded images in a grid with All, Campsite promos and Profile photos tabs.
 *
 * Each tile is labelled with what the image belongs to. Tapping a tile opens a larger
 * preview. Shows "No images found" when there are none.
 *
 * @param images Images to show, from [adminImages].
 */
@Composable
fun AdminImageGrid(images: List<AdminImage>) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    val campsiteCount = images.count { it.source == AdminImageSource.CAMPSITE }
    val profileCount = images.size - campsiteCount
    val shown = when (tab) {
        1 -> images.filter { it.source == AdminImageSource.CAMPSITE }
        2 -> images.filter { it.source == AdminImageSource.PROFILE }
        else -> images
    }

    Column(verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Medium)) {
        Text(
            "${images.size} uploaded · $campsiteCount campsite · $profileCount profile",
            style = MaterialTheme.typography.bodyMedium,
            color = PinedrawColors.TextSecondary
        )
        SegmentedTabs(
            options = listOf("All", "Campsite promos", "Profile photos"),
            selectedIndex = tab,
            onSelect = { tab = it }
        )
        if (shown.isEmpty()) {
            EmptyState(Icons.Rounded.Image, "No images found")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.testTag("adminImageGrid"),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = PinedrawSpacing.Screen)
            ) {
                items(shown, key = { it.id }) { image ->
                    AdminImageTile(image, onClick = { previewId = image.id })
                }
            }
        }
    }

    images.find { it.id == previewId }?.let { image ->
        AdminImagePreview(image, onClose = { previewId = null })
    }
}

// One square tile: the image with the owner's name along the bottom.
@Composable
private fun AdminImageTile(image: AdminImage, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(PinedrawRadius.Medium)
            .background(PinedrawColors.Surface)
            .border(1.dp, PinedrawColors.Border, PinedrawRadius.Medium)
            .clickable(onClick = onClick)
            .testTag("admin-image-${image.id}")
    ) {
        AdminImageView(image, Modifier.matchParentSize())
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0.5f to Color.Transparent, 1f to PinedrawColors.Background.copy(alpha = 0.9f))
            )
        )
        Text(
            image.ownerName,
            style = MaterialTheme.typography.labelMedium,
            color = PinedrawColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
        )
    }
}

// Campsite photos fill the frame; profile photos are shown as a round avatar, like elsewhere in the app.
@Composable
private fun AdminImageView(image: AdminImage, modifier: Modifier = Modifier) {
    when (image.source) {
        AdminImageSource.CAMPSITE -> CampsitePhoto(image.url, modifier, seed = image.ownerId, shape = null)
        AdminImageSource.PROFILE -> Box(modifier, contentAlignment = Alignment.Center) {
            CampsitePhoto(
                image.url,
                Modifier.fillMaxSize(0.78f).aspectRatio(1f),
                seed = image.ownerId,
                shape = CircleShape
            )
        }
    }
}

// Larger view of one image with what it belongs to.
@Composable
private fun AdminImagePreview(image: AdminImage, onClose: () -> Unit) {
    val campsite = image.source == AdminImageSource.CAMPSITE
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = PinedrawColors.Surface,
        shape = PinedrawRadius.XLarge,
        title = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                StatusChip(
                    if (campsite && image.position == 0) "Campsite cover photo" else image.source.label,
                    color = if (campsite) PinedrawColors.Lime else PinedrawColors.Sky,
                    icon = if (campsite) Icons.Rounded.Cabin else Icons.Rounded.Person
                )
                Text(
                    image.ownerName,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = PinedrawSpacing.Small)
                )
            }
        },
        text = {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(PinedrawRadius.Large).background(PinedrawColors.Background)
            ) {
                AdminImageView(image, Modifier.matchParentSize())
            }
        },
        confirmButton = {
            PinedrawButton("Close", onClose, Modifier.fillMaxWidth(), style = ButtonStyle.Ghost, compact = true)
        }
    )
}
