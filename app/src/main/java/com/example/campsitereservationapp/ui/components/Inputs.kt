package com.example.campsitereservationapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.campsitereservationapp.ui.theme.PinedrawDimens
import com.example.campsitereservationapp.ui.theme.PinedrawRadius

/** Shared colours for every text field. */
@Composable
fun pinedrawFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = PinedrawColors.Lime.copy(alpha = 0.06f),
    unfocusedContainerColor = PinedrawColors.Glass,
    disabledContainerColor = PinedrawColors.Glass,
    errorContainerColor = PinedrawColors.Coral.copy(alpha = 0.06f),
    focusedBorderColor = PinedrawColors.Lime,
    unfocusedBorderColor = PinedrawColors.Border,
    disabledBorderColor = PinedrawColors.Border,
    errorBorderColor = PinedrawColors.Coral,
    focusedLabelColor = PinedrawColors.Lime,
    unfocusedLabelColor = PinedrawColors.TextSecondary,
    focusedLeadingIconColor = PinedrawColors.Lime,
    unfocusedLeadingIconColor = PinedrawColors.TextSecondary,
    cursorColor = PinedrawColors.Lime,
    focusedTextColor = PinedrawColors.Text,
    unfocusedTextColor = PinedrawColors.Text,
    focusedPlaceholderColor = PinedrawColors.TextMuted,
    unfocusedPlaceholderColor = PinedrawColors.TextMuted
)

/**
 * The shared text field: glass background, 16dp corners and lime focus.
 */
@Composable
fun PinedrawTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(PinedrawDimens.IconSize)) } },
        trailingIcon = trailingIcon,
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = PinedrawRadius.Medium,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = pinedrawFieldColors()
    )
}

/**
 * Pill segmented control, e.g. "Upcoming · Past".
 */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PinedrawRadius.Pill)
            .background(PinedrawColors.Glass)
            .border(PinedrawDimens.BorderWidth, PinedrawColors.Border, PinedrawRadius.Pill)
            .padding(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(PinedrawRadius.Pill)
                    .background(if (selected) PinedrawColors.Text else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) PinedrawColors.Ink else PinedrawColors.TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

/** Selectable pill used for filters and amenities. */
@Composable
fun SelectChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .clip(PinedrawRadius.Pill)
            .background(if (selected) PinedrawColors.Lime else PinedrawColors.Glass)
            .border(PinedrawDimens.BorderWidth, if (selected) Color.Transparent else PinedrawColors.Border, PinedrawRadius.Pill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) PinedrawColors.Ink else PinedrawColors.Text, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) PinedrawColors.Ink else PinedrawColors.Text
        )
    }
}

/**
 * Minus / value / plus stepper used for party size and guests.
 */
@Composable
fun Stepper(
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    canDecrement: Boolean = true,
    canIncrement: Boolean = true,
    decrementLabel: String = "Decrease",
    incrementLabel: String = "Increase"
) {
    Row(
        modifier = modifier
            .clip(PinedrawRadius.Pill)
            .background(PinedrawColors.Glass)
            .border(PinedrawDimens.BorderWidth, PinedrawColors.Border, PinedrawRadius.Pill)
            .padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CircleIconButton(
            Icons.Rounded.Remove, decrementLabel, onDecrement,
            enabled = canDecrement, size = 36.dp, container = Color.White.copy(alpha = 0.08f)
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = PinedrawColors.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 36.dp)
        )
        CircleIconButton(
            Icons.Rounded.Add, incrementLabel, onIncrement,
            enabled = canIncrement, size = 36.dp,
            container = if (canIncrement) PinedrawColors.Lime else Color.White.copy(alpha = 0.08f),
            tint = PinedrawColors.Ink
        )
    }
}
