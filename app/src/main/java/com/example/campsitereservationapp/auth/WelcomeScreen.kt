package com.example.campsitereservationapp.auth

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.campsitereservationapp.R
import com.example.campsitereservationapp.ui.components.PinedrawButton
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawRadius
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing

/**
 * Welcome screen with the Pinedraw brand and a single "Continue as guest" action.
 *
 * @param onGuestClick Signs in anonymously and opens the app.
 * @param loading True while the guest account is being created.
 */
@Composable
fun WelcomeScreen(onGuestClick: () -> Unit, loading: Boolean = false) {
    val context = LocalContext.current
    val logo = remember {
        context.assets.open("icons/pinedraw_logo.png").use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fade.animateTo(1f, tween(700)) }

    Box(Modifier.fillMaxSize().background(PinedrawColors.Background)) {
        Image(
            painter = painterResource(R.drawable.scene_aurora_campfire),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to Color.Transparent,
                    0.8f to PinedrawColors.Background.copy(alpha = 0.92f),
                    1f to PinedrawColors.Background
                )
            )
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = PinedrawSpacing.Screen, vertical = PinedrawSpacing.Gutter)
                .graphicsLayer {
                    alpha = fade.value
                    translationY = (1f - fade.value) * 24f
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))
            Image(
                bitmap = logo,
                contentDescription = "Pinedraw logo",
                modifier = Modifier.size(88.dp).clip(PinedrawRadius.Large)
            )
            Spacer(Modifier.height(PinedrawSpacing.Large))
            Text("Pinedraw", style = MaterialTheme.typography.displayLarge.copy(fontSize = 40.sp), color = PinedrawColors.Text)
            Spacer(Modifier.height(PinedrawSpacing.Small))
            Text(
                "FAIR DRAWS · WILD PLACES",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.5.sp),
                color = PinedrawColors.Lime
            )
            Spacer(Modifier.weight(1f))
            Text(
                buildAnnotatedString {
                    append("Camp by chance,\n")
                    withStyle(SpanStyle(color = PinedrawColors.Lime)) { append("not by refresh.") }
                },
                style = MaterialTheme.typography.displayMedium,
                color = PinedrawColors.Text,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(PinedrawSpacing.Medium))
            Text(
                "Fair lotteries for Alberta's busiest campsites.",
                style = MaterialTheme.typography.bodyLarge,
                color = PinedrawColors.TextSecondary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(PinedrawSpacing.Section))
            PinedrawButton(
                text = if (loading) "Setting things up…" else "Continue as guest",
                onClick = onGuestClick,
                loading = loading,
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(PinedrawSpacing.Large))
            Text(
                "By continuing you agree to the Pinedraw Terms and Privacy Policy.",
                style = MaterialTheme.typography.bodySmall,
                color = PinedrawColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
