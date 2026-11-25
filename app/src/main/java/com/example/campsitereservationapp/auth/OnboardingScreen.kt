package com.example.campsitereservationapp.auth

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.R
import com.example.campsitereservationapp.ui.components.CircleIconButton
import com.example.campsitereservationapp.ui.components.InitialsAvatar
import com.example.campsitereservationapp.ui.components.PinedrawBackground
import com.example.campsitereservationapp.ui.components.PinedrawButton
import com.example.campsitereservationapp.ui.components.PinedrawTextButton
import com.example.campsitereservationapp.ui.components.StatusChip
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawDimens
import com.example.campsitereservationapp.ui.theme.PinedrawRadius
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private const val PREFS = "pinedraw_prefs"
private const val KEY_ONBOARDED = "onboarding_complete"

/** True once the user has finished or skipped onboarding on this device. */
fun hasCompletedOnboarding(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ONBOARDED, false)

/** Remembers that onboarding was shown so it only appears on first launch. */
fun markOnboardingComplete(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ONBOARDED, true).apply()
}

private data class OnboardingPage(val title: String, val highlight: String, val body: String)

private val pages = listOf(
    OnboardingPage(
        "Join a draw,", "not a race",
        "Popular campsites open as lotteries. Join the waiting list any time before registration closes."
    ),
    OnboardingPage(
        "Fair for", "everyone",
        "When registration closes, winners are drawn at random. Busy schedule or slow Wi-Fi, everyone gets the same chance."
    ),
    OnboardingPage(
        "Win, reserve, and", "go camping",
        "Winners get a notification, pick their dates in draw order, and receive a confirmation they can download."
    )
)

/**
 * Three-slide introduction shown the first time the app opens.
 *
 * @param onFinish Called when the user skips or finishes the last slide.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == pages.lastIndex

    PinedrawBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = PinedrawSpacing.Medium)) {
                if (!lastPage) {
                    PinedrawTextButton("Skip", onFinish, Modifier.align(Alignment.CenterEnd), color = PinedrawColors.TextSecondary)
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
                val page = pages[index]
                Column(Modifier.fillMaxSize().padding(horizontal = PinedrawSpacing.Screen)) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        when (index) {
                            0 -> DrawIllustration()
                            1 -> FairIllustration()
                            else -> WinIllustration()
                        }
                    }
                    Spacer(Modifier.height(PinedrawSpacing.Section))
                    Text(
                        buildAnnotatedString {
                            append(page.title)
                            append("\n")
                            withStyle(SpanStyle(color = PinedrawColors.Lime)) { append(page.highlight) }
                        },
                        style = MaterialTheme.typography.displayLarge,
                        color = PinedrawColors.Text
                    )
                    Spacer(Modifier.height(PinedrawSpacing.Medium))
                    Text(page.body, style = MaterialTheme.typography.bodyLarge, color = PinedrawColors.TextSecondary)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(PinedrawSpacing.Screen),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(pages.size) { dot ->
                        val selected = dot == pagerState.currentPage
                        val width by animateDpAsState(if (selected) 28.dp else 8.dp, label = "dot")
                        val color by animateColorAsState(if (selected) PinedrawColors.Lime else PinedrawColors.TextMuted, label = "dotColor")
                        Box(Modifier.height(8.dp).width(width).clip(PinedrawRadius.Pill).background(color))
                    }
                }
                if (lastPage) {
                    PinedrawButton("Get started", onFinish, icon = Icons.AutoMirrored.Rounded.ArrowForward)
                } else {
                    CircleIconButton(
                        Icons.AutoMirrored.Rounded.ArrowForward, "Next",
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        container = PinedrawColors.Lime, tint = PinedrawColors.Ink, size = 64.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun SceneCard(drawable: Int, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(0.9f).clip(PinedrawRadius.XLarge)
            .border(PinedrawDimens.BorderWidth, PinedrawColors.Border, PinedrawRadius.XLarge)
    ) {
        Image(painterResource(drawable), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        content()
    }
}

@Composable
private fun DrawIllustration() {
    SceneCard(R.drawable.scene_banff_purple_peak) {
        Row(Modifier.fillMaxWidth().padding(PinedrawSpacing.Large), horizontalArrangement = Arrangement.SpaceBetween) {
            OverlayPill { StatusChip("Lottery open · 2d left", color = PinedrawColors.Lime) }
            OverlayPill { StatusChip("128 joined", color = PinedrawColors.Text, icon = Icons.Rounded.Groups) }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(PinedrawSpacing.Large).fillMaxWidth()
                .clip(PinedrawRadius.Large).background(PinedrawColors.Background.copy(alpha = 0.88f))
                .border(PinedrawDimens.BorderWidth, PinedrawColors.Border, PinedrawRadius.Large)
                .padding(PinedrawSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(44.dp).clip(PinedrawRadius.Small).background(PinedrawColors.Lime), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ConfirmationNumber, null, tint = PinedrawColors.Ink)
            }
            Spacer(Modifier.width(PinedrawSpacing.Medium))
            Column(Modifier.weight(1f)) {
                Text("You're in the draw", style = MaterialTheme.typography.titleMedium, color = PinedrawColors.Text)
                Text("Draw Monday at 8 PM", style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary)
            }
            Icon(Icons.Rounded.CheckCircle, null, tint = PinedrawColors.Lime)
        }
    }
}

@Composable
private fun OverlayPill(content: @Composable () -> Unit) {
    Box(Modifier.clip(PinedrawRadius.Pill).background(PinedrawColors.Background.copy(alpha = 0.7f))) { content() }
}

@Composable
private fun FairIllustration() {
    val names = listOf("Amir M", "Olivia C", "Priya N", "Sam O", "Lee W", "Jen P", "Maya T", "Dan R")
    val winners = setOf(1, 4, 6)
    Box(
        Modifier.fillMaxWidth().aspectRatio(0.9f).clip(PinedrawRadius.XLarge)
            .background(PinedrawColors.Glass)
            .border(PinedrawDimens.BorderWidth, PinedrawColors.Border, PinedrawRadius.XLarge),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(150.dp).clip(CircleShape).background(PinedrawColors.Lime.copy(alpha = 0.08f)))
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(PinedrawColors.PrimaryGradient),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Casino, null, tint = PinedrawColors.Ink, modifier = Modifier.size(44.dp)) }
        names.forEachIndexed { index, name ->
            val angle = Math.toRadians(index * 360.0 / names.size - 90)
            val radius = 118f
            Box(Modifier.offset((cos(angle) * radius).dp, (sin(angle) * radius).dp)) {
                val winner = index in winners
                InitialsAvatar(
                    name,
                    size = 48.dp,
                    modifier = if (winner) Modifier.border(3.dp, PinedrawColors.Lime, CircleShape) else Modifier
                )
                if (winner) {
                    Icon(
                        Icons.Rounded.CheckCircle, null, tint = PinedrawColors.Lime,
                        modifier = Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(20.dp)
                            .clip(CircleShape).background(PinedrawColors.Ink)
                    )
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(PinedrawSpacing.Large)) {
            OverlayPill { StatusChip("Winners drawn at random", color = PinedrawColors.Lime) }
        }
    }
}

@Composable
private fun WinIllustration() {
    SceneCard(R.drawable.scene_red_peak_dock) {
        Row(
            Modifier.padding(PinedrawSpacing.Large).fillMaxWidth()
                .clip(PinedrawRadius.Large).background(Color(0xCC1B2A33))
                .border(PinedrawDimens.BorderWidth, PinedrawColors.Border, PinedrawRadius.Large)
                .padding(PinedrawSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(44.dp).clip(PinedrawRadius.Small).background(PinedrawColors.PrimaryGradient), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Park, null, tint = PinedrawColors.Ink)
            }
            Spacer(Modifier.width(PinedrawSpacing.Medium))
            Column(Modifier.weight(1f)) {
                Text("PINEDRAW", style = MaterialTheme.typography.labelSmall, color = PinedrawColors.TextSecondary)
                Text("You won a spot!", style = MaterialTheme.typography.titleMedium, color = PinedrawColors.Text)
                Text("Reserve by 11:59 PM", style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary)
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(PinedrawSpacing.Large).fillMaxWidth()
                .clip(PinedrawRadius.Large).background(PinedrawColors.Text)
                .padding(PinedrawSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.CheckCircle, null, tint = PinedrawColors.LimeDark, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(PinedrawSpacing.Medium))
            Column {
                Text("Reservation confirmed", style = MaterialTheme.typography.titleMedium, color = PinedrawColors.Ink)
                Text("Jun 11 – 13 · 4 guests", style = MaterialTheme.typography.bodySmall, color = PinedrawColors.Ink.copy(alpha = 0.7f))
            }
        }
    }
}
