package com.vocabguard.main

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.ThumbsUpDown
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vocabguard.data.repository.AutoWordsRepository
import com.vocabguard.data.repository.AutoWordsSettings
import com.vocabguard.data.repository.SettingsRepository
import com.vocabguard.domain.CefrLevel
import com.vocabguard.domain.QuizMode
import com.vocabguard.ui.components.PrimaryButton
import com.vocabguard.ui.components.SecondaryButton
import com.vocabguard.ui.components.SettingRow
import com.vocabguard.ui.components.VGSwitch
import com.vocabguard.ui.components.appearOnce
import com.vocabguard.ui.components.ProgressTrack
import com.vocabguard.ui.components.ScreenHeader
import com.vocabguard.ui.components.VGCard
import com.vocabguard.ui.theme.VGColors
import com.vocabguard.util.PermissionUtils
import com.vocabguard.util.startFirstAvailable

private data class QuizModeInfo(val title: String, val description: String, val icon: ImageVector)

private val QuizMode.info: QuizModeInfo
    get() = when (this) {
        QuizMode.CHOICE -> QuizModeInfo("Pick the translation", "See a word, choose its meaning from three.", Icons.Rounded.Translate)
        QuizMode.REVERSE_CHOICE -> QuizModeInfo("Pick the word", "See a meaning, choose the word from three.", Icons.Rounded.SwapHoriz)
        QuizMode.TYPING -> QuizModeInfo("Type the translation", "Write it yourself. Small typos and missing accents are forgiven.", Icons.Rounded.Keyboard)
        QuizMode.TRUE_FALSE -> QuizModeInfo("True or false", "Decide if a word and meaning match. Quickest, but easiest to guess.", Icons.Rounded.ThumbsUpDown)
    }

@Composable
fun SettingsScreen(state: MainUiState, viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = "Settings",
            subtitle = "Display, quizzes, new words and permissions.",
            modifier = Modifier.appearOnce(0),
        )

        DisplaySizeCard(
            current = state.uiScale,
            onSelect = viewModel::setUiScale,
            modifier = Modifier.appearOnce(1),
        )

        VGCard(modifier = Modifier.appearOnce(2)) {
            Text("Quiz types", style = MaterialTheme.typography.titleMedium, color = VGColors.TextPrimary)
            Text(
                "The lock screen mixes the types you turn on.",
                style = MaterialTheme.typography.bodySmall,
                color = VGColors.TextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            QuizMode.entries.forEach { mode ->
                val checked = mode in state.quizModes
                QuizModeRow(
                    info = mode.info,
                    checked = checked,
                    onToggle = { viewModel.setQuizMode(mode, !checked) },
                )
            }
        }

        AutoWordsCard(state = state, viewModel = viewModel, modifier = Modifier.appearOnce(3))

        Column(Modifier.appearOnce(4), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PermissionsSection(state.permissions)
        }
    }
}

@Composable
private fun DisplaySizeCard(current: Float, onSelect: (Float) -> Unit, modifier: Modifier = Modifier) {
    VGCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.TextIncrease, contentDescription = null, tint = VGColors.Primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Display size", style = MaterialTheme.typography.titleMedium, color = VGColors.TextPrimary)
                Text(
                    "Makes text, buttons and spacing bigger everywhere, including the quiz. " +
                        "Adds to your phone’s own font size.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VGColors.TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SettingsRepository.UI_SCALE_OPTIONS) { option ->
                val selected = kotlin.math.abs(option - current) < 0.01f
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(option) },
                    label = {
                        Text(
                            "${(option * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = chipColors(),
                )
            }
        }
    }
}

@Composable
private fun AutoWordsCard(state: MainUiState, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val config = state.autoWords
    LaunchedEffect(config.enabled) {
        if (config.enabled) viewModel.refreshEstimatedLevel()
    }

    VGCard(modifier = modifier) {
        SettingRow(
            title = "Auto-add new words",
            subtitle = "A few new words a day, at the level of the words you already know. Off by default.",
        ) {
            VGSwitch(checked = config.enabled, onCheckedChange = viewModel::setAutoWordsEnabled)
        }

        AnimatedVisibility(
            visible = config.enabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(18.dp))
                Text("Words per day", style = MaterialTheme.typography.titleSmall, color = VGColors.TextPrimary)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AutoWordsSettings.PER_DAY_OPTIONS) { option ->
                        FilterChip(
                            selected = config.perDay == option,
                            onClick = { viewModel.setAutoWordsPerDay(option) },
                            label = { Text("$option") },
                            shape = RoundedCornerShape(12.dp),
                            colors = chipColors(),
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text("Level", style = MaterialTheme.typography.titleSmall, color = VGColors.TextPrimary)
                Text(
                    state.estimatedLevel?.let { "Your words suggest ${it.name}." } ?: "Estimating your level…",
                    style = MaterialTheme.typography.bodySmall,
                    color = VGColors.TextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = config.level == null,
                            onClick = { viewModel.setAutoWordsLevel(null) },
                            label = { Text("Automatic") },
                            shape = RoundedCornerShape(12.dp),
                            colors = chipColors(),
                        )
                    }
                    items(CefrLevel.entries) { level ->
                        FilterChip(
                            selected = config.level == level,
                            onClick = { viewModel.setAutoWordsLevel(level) },
                            label = { Text(level.name) },
                            shape = RoundedCornerShape(12.dp),
                            colors = chipColors(),
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                SecondaryButton(
                    text = if (state.isAddingWords) "Adding…" else "Add ${config.perDay} words now",
                    onClick = viewModel::addWordsNow,
                    icon = Icons.Rounded.AutoAwesome,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "New words go to “${AutoWordsRepository.AUTO_DECK_NAME}”. They come from a built-in, offline " +
                        "German–English list (A1 to B2). Daily adding pauses while you have " +
                        "${AutoWordsRepository.MAX_BACKLOG}+ unlearned words.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VGColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = VGColors.SurfaceHigh,
    labelColor = VGColors.TextSecondary,
    selectedContainerColor = VGColors.Primary.copy(alpha = 0.16f),
    selectedLabelColor = VGColors.Primary,
)

@Composable
private fun QuizModeRow(info: QuizModeInfo, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VGColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(info.icon, contentDescription = null, tint = if (checked) VGColors.Primary else VGColors.TextSecondary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(info.title, style = MaterialTheme.typography.titleSmall, color = VGColors.TextPrimary)
            Text(info.description, style = MaterialTheme.typography.bodySmall, color = VGColors.TextSecondary)
        }
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = VGColors.Primary,
                checkmarkColor = VGColors.OnPrimary,
                uncheckedColor = VGColors.TextSecondary,
            ),
        )
    }
}

@Composable
private fun PermissionsSection(permissions: PermissionState) {
    val context = LocalContext.current
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = permissions.grantedCount / 2f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "setupProgress",
    )
    val progressColor by animateColorAsState(
        if (permissions.allGranted) VGColors.Success else VGColors.Primary,
        label = "setupProgressColor",
    )

    fun open(intents: List<Intent>) {
        if (!context.startFirstAvailable(intents)) {
            Toast.makeText(context, "Open your phone’s Settings app to grant this.", Toast.LENGTH_LONG).show()
        }
    }

    Text("Permissions", style = MaterialTheme.typography.titleMedium, color = VGColors.TextPrimary)
    Column {
        ProgressTrack(fraction = progress, color = progressColor)
        Spacer(Modifier.height(8.dp))
        Text(
            if (permissions.allGranted) "All set" else "${permissions.grantedCount} of 2 done",
            style = MaterialTheme.typography.labelMedium,
            color = VGColors.TextSecondary,
        )
    }

    PermissionCard(
        icon = Icons.Rounded.AccessibilityNew,
        title = "Accessibility service",
        description = "Lets VocabGuard notice when a guarded app comes to the front. It sees which app is open, never what’s on screen.",
        granted = permissions.accessibilityEnabled,
        actionLabel = "Turn on",
        onAction = { showDisclosure = true },
    )
    PermissionCard(
        icon = Icons.Rounded.Layers,
        title = "Display over other apps",
        description = "Lets the quiz appear instantly on top of the app, including on Android versions that limit background launches.",
        granted = permissions.overlayGranted,
        actionLabel = "Allow",
        onAction = { open(PermissionUtils.overlaySettingsIntents(context)) },
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissions.accessibilityEnabled) {
        VGCard {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = VGColors.TextSecondary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Switch greyed out?", style = MaterialTheme.typography.titleSmall, color = VGColors.TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Android restricts this for apps installed outside the Play Store. Open App info, tap the ⋮ menu, " +
                            "choose “Allow restricted settings”, then try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VGColors.TextSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { open(listOf(PermissionUtils.appDetailsIntent(context))) },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, VGColors.Outline),
                    ) {
                        Text("Open App info", color = VGColors.TextPrimary)
                    }
                }
            }
        }
    }

    Text(
        "Everything stays on this phone. VocabGuard has no internet permission.",
        style = MaterialTheme.typography.bodySmall,
        color = VGColors.TextSecondary,
    )

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            containerColor = VGColors.Surface,
            shape = RoundedCornerShape(24.dp),
            icon = { Icon(Icons.Rounded.AccessibilityNew, contentDescription = null, tint = VGColors.Primary) },
            title = { Text("How VocabGuard uses Accessibility") },
            text = {
                Text(
                    "VocabGuard uses the Accessibility API for one thing: noticing when an app you chose opens, " +
                        "so it can show a short vocabulary quiz first.\n\n" +
                        "It doesn’t read what’s on your screen or what you type, and nothing leaves your phone.\n\n" +
                        "On the next screen, find VocabGuard (often under Downloaded or Installed apps) and turn it on.",
                )
            },
            titleContentColor = VGColors.TextPrimary,
            textContentColor = VGColors.TextSecondary,
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    open(PermissionUtils.accessibilitySettingsIntents())
                }) { Text("Open settings", color = VGColors.Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) { Text("Cancel", color = VGColors.TextSecondary) }
            },
        )
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val accent by animateColorAsState(if (granted) VGColors.Success else VGColors.Warning, label = "permissionAccent")
    val borderColor by animateColorAsState(
        if (granted) VGColors.Success.copy(alpha = 0.35f) else VGColors.Outline,
        label = "permissionBorder",
    )
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = VGColors.Surface,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = accent)
                }
                Spacer(Modifier.width(14.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = VGColors.TextPrimary, modifier = Modifier.weight(1f))
                StatusChip(granted = granted, color = accent)
            }
            Spacer(Modifier.height(12.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = VGColors.TextSecondary)
            AnimatedVisibility(
                visible = !granted,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                PrimaryButton(
                    text = actionLabel,
                    onClick = onAction,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StatusChip(granted: Boolean, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (granted) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(if (granted) "On" else "Needed", style = MaterialTheme.typography.labelMedium, color = color)
    }
}
