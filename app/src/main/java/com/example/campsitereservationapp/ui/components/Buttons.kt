package com.example.campsitereservationapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.campsitereservationapp.ui.theme.PinedrawColors
import com.example.campsitereservationapp.ui.theme.PinedrawDimens
import com.example.campsitereservationapp.ui.theme.PinedrawRadius

/** Visual style of a [PinedrawButton]. */
enum class ButtonStyle { Primary, Secondary, Ghost, Destructive, SoftDestructive, Host }

/**
 * The one button used across the app. Every style shares the same height, pill shape and type.
 *
 * @param text Button label.
 * @param onClick Called when the button is pressed.
 * @param modifier Layout modifier; pass fillMaxWidth for full-width actions.
 * @param style Visual style from the design system.
 * @param icon Optional leading Material icon.
 * @param enabled False to disable the button.
 * @param loading True to show a spinner and block presses.
 * @param compact True for the smaller 44dp height used inside cards.
 */
@Composable
fun PinedrawButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false
) {
    val (container, content) = when (style) {
        ButtonStyle.Primary -> PinedrawColors.Lime to PinedrawColors.Ink
        ButtonStyle.Secondary -> PinedrawColors.Text to PinedrawColors.Ink
        ButtonStyle.Ghost -> PinedrawColors.Glass to PinedrawColors.Text
        ButtonStyle.Destructive -> PinedrawColors.Coral to PinedrawColors.Ink
        ButtonStyle.SoftDestructive -> PinedrawColors.Coral.copy(alpha = 0.14f) to PinedrawColors.Coral
        ButtonStyle.Host -> PinedrawColors.Amber to PinedrawColors.Ink
    }
    val border = when (style) {
        ButtonStyle.Ghost -> BorderStroke(PinedrawDimens.BorderWidth, PinedrawColors.Border)
        ButtonStyle.SoftDestructive -> BorderStroke(PinedrawDimens.BorderWidth, PinedrawColors.Coral.copy(alpha = 0.35f))
        else -> null
    }
    val gradient = when (style) {
        ButtonStyle.Primary -> PinedrawColors.PrimaryGradient
        ButtonStyle.Host -> PinedrawColors.AmberGradient
        else -> null
    }
    val active = enabled && !loading
    Button(
        onClick = onClick,
        enabled = active,
        modifier = modifier
            .height(if (compact) PinedrawDimens.CompactButtonHeight else PinedrawDimens.ButtonHeight)
            .clip(PinedrawRadius.Pill)
            .then(if (gradient != null && active) Modifier.background(gradient) else Modifier),
        shape = PinedrawRadius.Pill,
        border = if (active) border else null,
        contentPadding = PaddingValues(horizontal = if (compact) 16.dp else 24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (gradient != null) Color.Transparent else container,
            contentColor = content,
            disabledContainerColor = PinedrawColors.Glass,
            disabledContentColor = PinedrawColors.TextMuted
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(PinedrawDimens.SmallIconSize),
                    strokeWidth = 2.dp,
                    color = PinedrawColors.TextSecondary
                )
                Spacer(Modifier.width(10.dp))
            } else if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(PinedrawDimens.SmallIconSize + 2.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Lime text-only action, e.g. "See all" or "Reset". */
@Composable
fun PinedrawTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = PinedrawColors.Lime,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled, shape = PinedrawRadius.Pill) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(PinedrawDimens.SmallIconSize))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = if (enabled) color else PinedrawColors.TextMuted, style = MaterialTheme.typography.labelLarge)
    }
}

/** Round 48dp glass button holding a single icon (back, share, settings…). */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = PinedrawColors.Text,
    container: Color = PinedrawColors.Glass,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = PinedrawDimens.IconButtonSize
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(size)
            .clip(PinedrawRadius.Pill)
            .background(container),
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint, disabledContentColor = PinedrawColors.TextMuted)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(PinedrawDimens.IconSize))
        }
    }
}
