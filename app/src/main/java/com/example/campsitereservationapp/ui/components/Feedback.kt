package com.example.campsitereservationapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawRadius
import com.example.campsitereservationapp.ui.theme.PinedrawSpacing

/**
 * Small pill badge, e.g. "Lottery open" or "Rejected".
 *
 * @param color Accent colour; the background is a tinted version of it.
 * @param icon Optional icon; a dot is shown when null and [showDot] is true.
 */
@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PinedrawColors.Lime,
    icon: ImageVector? = null,
    showDot: Boolean = true
) {
    Row(
        modifier = modifier
            .clip(PinedrawRadius.Pill)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            icon != null -> {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
            }
            showDot -> {
                Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
            }
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

/** Centred spinner shown while a screen loads. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = PinedrawColors.Lime, strokeWidth = 3.dp)
    }
}

/**
 * Friendly empty state with an icon, a title and an optional message and action.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = PinedrawSpacing.Screen),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(PinedrawColors.Glass),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = PinedrawColors.Lime, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(PinedrawSpacing.Large))
        Text(title, style = MaterialTheme.typography.titleLarge, color = PinedrawColors.Text, textAlign = TextAlign.Center)
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = PinedrawColors.TextSecondary, textAlign = TextAlign.Center)
        }
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(PinedrawSpacing.Large))
            PinedrawButton(actionText, onAction, style = ButtonStyle.Ghost, compact = true)
        }
    }
}

/** Error message with a "Try again" action. */
@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier, retryText: String = "Try again") {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = PinedrawSpacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = PinedrawColors.Coral, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(PinedrawSpacing.Medium))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = PinedrawColors.Text, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(PinedrawSpacing.Medium))
            PinedrawButton(retryText, onRetry, style = ButtonStyle.Ghost, icon = Icons.Rounded.Refresh, compact = true)
        }
    }
}

/** Inline coloured message, used for form and action errors. */
@Composable
fun InlineMessage(text: String, modifier: Modifier = Modifier, color: Color = PinedrawColors.Coral) {
    if (text.isBlank()) return
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = modifier
            .fillMaxWidth()
            .clip(PinedrawRadius.Small)
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

/**
 * Confirmation dialog in the Pinedraw style: tinted icon, centred title and message,
 * and two equal-width pill buttons.
 *
 * @param confirmStyle Use [ButtonStyle.Destructive] for irreversible actions.
 */
@Composable
fun PinedrawDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = PinedrawColors.Lime,
    message: String? = null,
    dismissText: String = "Cancel",
    confirmStyle: ButtonStyle = ButtonStyle.Primary,
    confirmEnabled: Boolean = true,
    loading: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        modifier = modifier,
        containerColor = PinedrawColors.Surface,
        titleContentColor = PinedrawColors.Text,
        textContentColor = PinedrawColors.TextSecondary,
        shape = PinedrawRadius.XLarge,
        icon = icon?.let {
            {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) { Icon(it, contentDescription = null, tint = iconTint, modifier = Modifier.size(30.dp)) }
            }
        },
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = if (message == null && content == null) null else {
            {
                Column(verticalArrangement = Arrangement.spacedBy(PinedrawSpacing.Medium)) {
                    if (message != null) {
                        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    content?.invoke(this)
                }
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PinedrawSpacing.Small)) {
                PinedrawButton(dismissText, onDismiss, Modifier.weight(1f), style = ButtonStyle.Ghost, enabled = !loading, compact = true)
                PinedrawButton(
                    confirmText, onConfirm, Modifier.weight(1f), style = confirmStyle,
                    enabled = confirmEnabled, loading = loading, compact = true
                )
            }
        }
    )
}

/** Padding used by lists that sit above the floating bottom dock. */
val DockContentPadding = PaddingValues(bottom = 120.dp)
