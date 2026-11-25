package com.example.campsitereservationapp.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campsitereservationapp.R
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import kotlin.math.absoluteValue

private val scenes = listOf(
    R.drawable.scene_banff_purple_peak,
    R.drawable.scene_red_peak_dock,
    R.drawable.scene_teal_lake,
    R.drawable.scene_foggy_forest,
    R.drawable.scene_glacier_lake,
    R.drawable.scene_sunset_lake,
    R.drawable.scene_turquoise_river,
    R.drawable.scene_cliff_lake,
    R.drawable.scene_misty_lake,
    R.drawable.scene_autumn_meadow,
    R.drawable.scene_grey_river
)

/** Picks a stable landscape illustration for a campsite, so each one always shows the same scene. */
@DrawableRes
fun sceneFor(seed: String): Int = scenes[seed.hashCode().absoluteValue % scenes.size]

/** Landscape illustration used when a campsite has no uploaded photo. */
@Composable
fun SceneImage(seed: String, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(
        painter = painterResource(sceneFor(seed)),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

private val avatarColors = listOf(
    Color(0xFF2E8C86), Color(0xFF3E7BD6), Color(0xFFC0574A), Color(0xFF8C6BD1), Color(0xFFC48A2E), Color(0xFF3F9A5B)
)

/** Coloured circle with a person's initials. */
@Composable
fun InitialsAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val initials = name.trim().split(" ").filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifBlank { "P" }
    val base = avatarColors[name.hashCode().absoluteValue % avatarColors.size]
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.75f)))),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, color = PinedrawColors.Text, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
    }
}
