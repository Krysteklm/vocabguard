package com.vocabguard.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vocabguard.ui.theme.VGColors
import kotlinx.coroutines.delay

@Composable
fun ScreenHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = VGColors.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = VGColors.TextSecondary)
    }
}

@Composable
fun TopBackBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = VGColors.TextPrimary)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = VGColors.TextPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun VGCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val border = BorderStroke(1.dp, VGColors.Outline)
    val inner: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
            content = content,
        )
    }
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        val pressScale by rememberPressScale(interaction, pressedScale = 0.98f)
        Surface(
            onClick = onClick,
            shape = shape,
            color = VGColors.Surface,
            border = border,
            interactionSource = interaction,
            modifier = modifier
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .fillMaxWidth(),
        ) { inner() }
    } else {
        Surface(shape = shape, color = VGColors.Surface, border = border, modifier = modifier.fillMaxWidth()) { inner() }
    }
}

@Composable
fun NoticeCard(icon: ImageVector, tint: Color, title: String, body: String, modifier: Modifier = Modifier) {
    VGCard(modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = VGColors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = VGColors.TextSecondary)
            }
        }
    }
}

@Composable
fun VGTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 10,
    maxLength: Int = 60,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= maxLength) onValueChange(it) },
        label = { Text(label) },
        placeholder = if (placeholder == null) null else {
            { Text(placeholder, color = VGColors.TextSecondary.copy(alpha = 0.6f)) }
        },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            imeAction = if (singleLine) imeAction else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VGColors.Primary,
            unfocusedBorderColor = VGColors.Outline,
            focusedLabelColor = VGColors.Primary,
            unfocusedLabelColor = VGColors.TextSecondary,
            cursorColor = VGColors.Primary,
            focusedTextColor = VGColors.TextPrimary,
            unfocusedTextColor = VGColors.TextPrimary,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressScale by rememberPressScale(interaction, pressedScale = 0.96f)
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = VGColors.Primary,
            contentColor = VGColors.OnPrimary,
            disabledContainerColor = VGColors.SurfaceHigh,
            disabledContentColor = VGColors.TextSecondary,
        ),
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .heightIn(min = 52.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val interaction = remember { MutableInteractionSource() }
    val pressScale by rememberPressScale(interaction, pressedScale = 0.96f)
    OutlinedButton(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, VGColors.Outline),
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .heightIn(min = 52.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = VGColors.TextPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = VGColors.TextPrimary)
    }
}

@Composable
fun VGSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = VGColors.OnPrimary,
            checkedTrackColor = VGColors.Primary,
            uncheckedThumbColor = VGColors.TextSecondary,
            uncheckedTrackColor = VGColors.SurfaceHigh,
            uncheckedBorderColor = VGColors.Outline,
        ),
    )
}

/** A labelled row with a control on the right (switch, stepper, checkbox). */
@Composable
fun SettingRow(title: String, subtitle: String?, modifier: Modifier = Modifier, control: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = VGColors.TextPrimary)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VGColors.TextSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        control()
    }
}

@Composable
fun Stepper(
    valueLabel: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    canDecrease: Boolean,
    canIncrease: Boolean,
    decreaseDescription: String,
    increaseDescription: String,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(VGColors.SurfaceHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDecrease, enabled = canDecrease) {
            Icon(
                Icons.Rounded.Remove,
                contentDescription = decreaseDescription,
                tint = if (canDecrease) VGColors.TextPrimary else VGColors.TextSecondary.copy(alpha = 0.4f),
            )
        }
        Text(
            valueLabel,
            style = MaterialTheme.typography.titleSmall,
            color = VGColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 56.dp),
        )
        IconButton(onClick = onIncrease, enabled = canIncrease) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = increaseDescription,
                tint = if (canIncrease) VGColors.TextPrimary else VGColors.TextSecondary.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
fun ProgressTrack(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(50))
            .background(VGColors.Track),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(50))
                .background(color),
        )
    }
}

@Composable
fun StatusDot(active: Boolean, color: Color) {
    val transition = rememberInfiniteTransition(label = "statusPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "statusPulseScale",
    )
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        if (active) {
            Box(
                Modifier
                    .size(10.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                        alpha = ((2.2f - pulse) / 1.2f) * 0.5f
                    }
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

/** Current time that ticks every second while [ticking] is true. */
@Composable
fun rememberNow(ticking: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ticking) {
        now = System.currentTimeMillis()
        while (ticking) {
            delay(1_000L - (now % 1_000L))
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** Single-field dialog for naming or renaming a deck. */
@Composable
fun NameDialog(
    title: String,
    confirmLabel: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VGColors.Surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, color = VGColors.TextPrimary) },
        text = {
            VGTextField(
                value = name,
                onValueChange = { name = it },
                label = "Deck name",
                placeholder = "Spanish verbs",
                maxLength = 40,
                onImeAction = { if (name.isNotBlank()) onConfirm(name) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(confirmLabel, color = if (name.isNotBlank()) VGColors.Primary else VGColors.TextSecondary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = VGColors.TextSecondary) }
        },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VGColors.Surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, color = VGColors.TextPrimary) },
        text = { Text(body, color = VGColors.TextSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) VGColors.Error else VGColors.Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = VGColors.TextSecondary) }
        },
    )
}
