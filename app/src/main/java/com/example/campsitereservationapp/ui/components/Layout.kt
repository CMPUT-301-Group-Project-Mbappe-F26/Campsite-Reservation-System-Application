package com.example.campsitereservationapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawDimens
import com.example.campsitereservationapp.ui.theme.PinedrawRadius
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing

/** Full-screen forest gradient that sits behind every screen. */
@Composable
fun PinedrawBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PinedrawColors.Background)
            .background(PinedrawColors.ScreenGradient),
        content = content
    )
}

/**
 * Standard screen: gradient background, top bar with an optional back button, and a
 * scrolling column of content with the shared screen gutter.
 *
 * @param title Title shown in the top bar.
 * @param onBack Shows a back button when not null.
 * @param actions Optional icons on the right of the top bar.
 * @param scrollable False when the content manages its own scrolling (e.g. a LazyColumn).
 * @param bottomBar Optional content pinned to the bottom, such as a primary action.
 */
@Composable
fun PinedrawScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    backLabel: String = "Back",
    actions: @Composable RowScope.() -> Unit = {},
    scrollable: Boolean = true,
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    PinedrawBackground(modifier) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            PinedrawTopBar(title = title, onBack = onBack, backLabel = backLabel, actions = actions)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = PinedrawSpacing.Gutter),
                verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Large)
            ) {
                if (scrollable) Spacer(Modifier.height(PinedrawSpacing.XSmall))
                content()
                if (scrollable) Spacer(Modifier.height(PinedrawSpacing.Screen))
            }
            if (bottomBar != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(PinedrawColors.Background.copy(alpha = 0.92f))
                        .navigationBarsPadding()
                        .padding(horizontal = PinedrawSpacing.Gutter, vertical = PinedrawSpacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Small),
                    content = bottomBar
                )
            } else {
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

/** Top bar with a round back button, a centred title and optional actions. */
@Composable
fun PinedrawTopBar(
    title: String,
    onBack: (() -> Unit)?,
    backLabel: String = "Back",
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = PinedrawSpacing.Large),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, backLabel, onBack)
            Spacer(Modifier.width(PinedrawSpacing.Medium))
        } else {
            Spacer(Modifier.width(PinedrawSpacing.XSmall))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = PinedrawColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(PinedrawSpacing.Small), content = actions)
    }
}

/**
 * Large page header used by the main tabs ("My trips", "Profile").
 *
 * @param subtitle Optional one-line summary under the title.
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.displayLarge, color = PinedrawColors.Text)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(PinedrawSpacing.XSmall))
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = PinedrawColors.TextSecondary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PinedrawSpacing.Small), content = actions)
    }
}

/** Section title with an optional lime action on the right ("See all"). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = PinedrawColors.Text, modifier = Modifier.weight(1f))
        if (actionText != null && onAction != null) PinedrawTextButton(actionText, onAction)
    }
}

/** Small all-caps label above a group, e.g. "ACCOUNT". */
@Composable
fun OverlineLabel(text: String, modifier: Modifier = Modifier, color: Color = PinedrawColors.TextMuted) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier.padding(start = PinedrawSpacing.XSmall)
    )
}

/**
 * Glass card used for every grouped block of content.
 *
 * @param onClick Makes the whole card tappable when not null.
 * @param borderColor Highlight colour for the 1dp border.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color = PinedrawColors.Border,
    container: Color = PinedrawColors.Glass,
    shape: Shape = PinedrawRadius.Large,
    contentPadding: Dp = PinedrawDimens.CardPadding,
    spacing: Dp = PinedrawSpacing.Medium,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .border(BorderStroke(PinedrawDimens.BorderWidth, borderColor), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}

/** Rounded-square tinted badge that holds an icon, as used in list rows and cards. */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = PinedrawColors.Lime,
    filled: Boolean = false,
    size: Dp = PinedrawDimens.IconBadgeSize
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(PinedrawRadius.Small)
            .background(if (filled) tint else tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (filled) PinedrawColors.Ink else tint,
            modifier = Modifier.size(PinedrawDimens.IconSize)
        )
    }
}

/**
 * Tappable settings-style row: icon badge, title, optional value and a chevron.
 */
@Composable
fun ListRow(
    title: String,
    icon: ImageVector,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    tint: Color = PinedrawColors.TextSecondary,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = PinedrawSpacing.Large, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, tint = tint, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = PinedrawColors.Text)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PinedrawColors.TextSecondary)
            }
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = PinedrawColors.TextSecondary)
            Spacer(Modifier.width(PinedrawSpacing.XSmall))
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = PinedrawColors.TextMuted,
                modifier = Modifier.size(PinedrawDimens.IconSize)
            )
        }
    }
}

/** Card that groups several [ListRow]s with dividers between them. */
@Composable
fun ListGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PinedrawRadius.Large)
            .background(PinedrawColors.Glass)
            .border(PinedrawDimens.BorderWidth, PinedrawColors.Border, PinedrawRadius.Large),
        content = content
    )
}

/** Thin divider used between rows inside a [ListGroup]. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = 70.dp, end = PinedrawSpacing.Large),
        thickness = PinedrawDimens.BorderWidth,
        color = PinedrawColors.Border
    )
}

/** Label on the left, value on the right; used for detail summaries. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = PinedrawColors.Text) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PinedrawSpacing.Medium)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = PinedrawColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            color = valueColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1.3f)
        )
    }
}
