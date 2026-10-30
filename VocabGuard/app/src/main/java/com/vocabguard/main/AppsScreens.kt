package com.vocabguard.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocabguard.data.local.DefaultApps
import com.vocabguard.data.local.GuardedAppEntity
import com.vocabguard.data.repository.InstalledApp
import com.vocabguard.ui.components.AppIcon
import com.vocabguard.ui.components.NoticeCard
import com.vocabguard.ui.components.PrimaryButton
import com.vocabguard.ui.components.ScreenHeader
import com.vocabguard.ui.components.SettingRow
import com.vocabguard.ui.components.Stepper
import com.vocabguard.ui.components.TopBackBar
import com.vocabguard.ui.components.VGCard
import com.vocabguard.ui.components.VGSwitch
import com.vocabguard.ui.components.VGTextField
import com.vocabguard.ui.components.appearOnce
import com.vocabguard.ui.theme.VGColors
import com.vocabguard.util.epochDay

// ---------------------------------------------------------------------------------------------
// Guarded apps list
// ---------------------------------------------------------------------------------------------

@Composable
fun AppsHomeScreen(viewModel: AppsViewModel, onAddApps: () -> Unit) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    var editingPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val today = epochDay()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            ScreenHeader(
                title = "Apps",
                subtitle = "A quiz appears when any of these open. Tap an app to set its limits.",
                modifier = Modifier.appearOnce(0),
            )
        }
        item(key = "add") {
            PrimaryButton(
                text = "Add apps",
                onClick = onAddApps,
                icon = Icons.Rounded.Add,
                modifier = Modifier
                    .appearOnce(1)
                    .fillMaxWidth(),
            )
        }
        if (apps.isEmpty()) {
            item(key = "empty") {
                NoticeCard(
                    icon = Icons.Rounded.Apps,
                    tint = VGColors.Primary,
                    title = "No apps guarded yet",
                    body = "Add the apps you open without thinking, like TikTok, Instagram or YouTube.",
                )
            }
        }
        items(apps, key = { it.packageName }) { app ->
            GuardedAppRow(
                app = app,
                today = today,
                onClick = { editingPackage = app.packageName },
                onEnabledChange = { viewModel.setEnabled(app, it) },
                modifier = Modifier.animateItem(),
            )
        }
    }

    apps.firstOrNull { it.packageName == editingPackage }?.let { app ->
        AppRulesSheet(
            app = app,
            today = today,
            onDismiss = { editingPackage = null },
            onSave = { rules ->
                editingPackage = null
                viewModel.saveRules(app, rules)
            },
            onRemove = {
                editingPackage = null
                viewModel.remove(app)
            },
        )
    }
}

@Composable
private fun GuardedAppRow(
    app: GuardedAppEntity,
    today: Long,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    VGCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (app.isEnabled) VGColors.TextPrimary else VGColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(rulesSummary(app), style = MaterialTheme.typography.bodySmall, color = VGColors.TextSecondary)
                if (app.isEnabled && app.hasDailyLimit()) {
                    Text(
                        "${app.unlocksUsed(today)} of ${app.dailyUnlockLimit} unlocks used today",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (app.unlocksLeft(today) == 0) VGColors.Warning else VGColors.TextSecondary,
                    )
                }
            }
            VGSwitch(checked = app.isEnabled, onCheckedChange = onEnabledChange)
        }
    }
}

private fun rulesSummary(app: GuardedAppEntity): String =
    if (!app.isEnabled) {
        "Not guarded right now"
    } else {
        "${app.requiredStreak} in a row for a ${app.sessionMinutes}-minute pass"
    }

// ---------------------------------------------------------------------------------------------
// Rules editor
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRulesSheet(
    app: GuardedAppEntity,
    today: Long,
    onDismiss: () -> Unit,
    onSave: (AppRules) -> Unit,
    onRemove: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var enabled by rememberSaveable(app.packageName) { mutableStateOf(app.isEnabled) }
    var streak by rememberSaveable(app.packageName) { mutableIntStateOf(app.requiredStreak) }
    var minutes by rememberSaveable(app.packageName) { mutableIntStateOf(app.sessionMinutes) }
    var dailyLimit by rememberSaveable(app.packageName) { mutableIntStateOf(app.dailyUnlockLimit) }
    val streakRange = GuardedAppEntity.STREAK_RANGE
    val limitRange = GuardedAppEntity.DAILY_LIMIT_RANGE
    val minuteOptions = (GuardedAppEntity.SESSION_MINUTE_OPTIONS + minutes).distinct().sorted()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VGColors.Surface,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.packageName, size = 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleLarge, color = VGColors.TextPrimary)
                    Text(
                        if (enabled) "Quiz before opening" else "Opens freely",
                        style = MaterialTheme.typography.bodySmall,
                        color = VGColors.TextSecondary,
                    )
                }
                VGSwitch(checked = enabled, onCheckedChange = { enabled = it })
            }

            Spacer(Modifier.height(24.dp))
            SettingRow(title = "Correct answers needed", subtitle = "In a row. More answers, more friction.") {
                Stepper(
                    valueLabel = "$streak",
                    onDecrease = { streak = (streak - 1).coerceIn(streakRange) },
                    onIncrease = { streak = (streak + 1).coerceIn(streakRange) },
                    canDecrease = streak > streakRange.first,
                    canIncrease = streak < streakRange.last,
                    decreaseDescription = "Fewer answers",
                    increaseDescription = "More answers",
                )
            }

            Spacer(Modifier.height(24.dp))
            Text("Pass length", style = MaterialTheme.typography.titleSmall, color = VGColors.TextPrimary)
            Text(
                "How long ${app.label} stays open after a solved quiz.",
                style = MaterialTheme.typography.bodySmall,
                color = VGColors.TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(minuteOptions) { option ->
                    FilterChip(
                        selected = minutes == option,
                        onClick = { minutes = option },
                        label = { Text("$option min") },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = VGColors.SurfaceHigh,
                            labelColor = VGColors.TextSecondary,
                            selectedContainerColor = VGColors.Primary.copy(alpha = 0.16f),
                            selectedLabelColor = VGColors.Primary,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SettingRow(
                title = "Unlocks per day",
                subtitle = "After the last one, only “Not now” is left until tomorrow. " +
                    "Used today: ${app.unlocksUsed(today)}.",
            ) {
                Stepper(
                    valueLabel = if (dailyLimit == 0) "No limit" else "$dailyLimit",
                    onDecrease = { dailyLimit = (dailyLimit - 1).coerceIn(limitRange) },
                    onIncrease = { dailyLimit = (dailyLimit + 1).coerceIn(limitRange) },
                    canDecrease = dailyLimit > limitRange.first,
                    canIncrease = dailyLimit < limitRange.last,
                    decreaseDescription = "Fewer unlocks",
                    increaseDescription = "More unlocks",
                )
            }

            Spacer(Modifier.height(28.dp))
            PrimaryButton(
                text = "Save",
                onClick = { onSave(AppRules(enabled, streak, minutes, dailyLimit)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                Text("Stop guarding ${app.label}", color = VGColors.Error)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// App picker
// ---------------------------------------------------------------------------------------------

@Composable
fun AddAppsScreen(viewModel: AppsViewModel, onBack: () -> Unit) {
    val picker by viewModel.picker.collectAsStateWithLifecycle()
    val guarded by viewModel.apps.collectAsStateWithLifecycle()
    val guardedPackages = guarded.map { it.packageName }.toSet()

    val query = picker.query.trim()
    val filtered = if (query.isEmpty()) {
        picker.installed
    } else {
        picker.installed.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }
    val suggested = if (query.isEmpty()) filtered.filter { it.packageName in DefaultApps.SUGGESTED } else emptyList()
    val rest = filtered.filterNot { it in suggested }
    val selectedCount = picker.selected.size

    Column(Modifier.fillMaxSize()) {
        TopBackBar(title = "Add apps", onBack = onBack)
        VGTextField(
            value = picker.query,
            onValueChange = viewModel::onQueryChange,
            label = "Search apps",
            placeholder = "Instagram",
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))

        // fillMaxWidth matters: without it the Box shrinks to its content and
        // Alignment.Center has no width to center in (the spinner sat on the left).
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
                picker.isLoading -> CircularProgressIndicator(color = VGColors.Primary, modifier = Modifier.align(Alignment.Center))
                picker.loadFailed -> Text(
                    "Couldn’t load your apps. Go back and try again.",
                    color = VGColors.TextSecondary,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center,
                )
                filtered.isEmpty() -> Text(
                    "No apps match “$query”.",
                    color = VGColors.TextSecondary,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    if (suggested.isNotEmpty()) {
                        item(key = "suggestedHeader") { PickerSectionLabel("Common time sinks") }
                        items(suggested, key = { "s:" + it.packageName }) { app ->
                            PickerRow(app, app.packageName in guardedPackages, app.packageName in picker.selected) {
                                viewModel.toggleSelection(app.packageName)
                            }
                        }
                        item(key = "allHeader") { PickerSectionLabel("All apps") }
                    }
                    items(rest, key = { it.packageName }) { app ->
                        PickerRow(app, app.packageName in guardedPackages, app.packageName in picker.selected) {
                            viewModel.toggleSelection(app.packageName)
                        }
                    }
                }
            }
        }

        PrimaryButton(
            text = when (selectedCount) {
                0 -> "Select apps to guard"
                1 -> "Guard 1 app"
                else -> "Guard $selectedCount apps"
            },
            onClick = viewModel::addSelected,
            enabled = selectedCount > 0,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun PickerSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = VGColors.TextSecondary,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun PickerRow(app: InstalledApp, alreadyGuarded: Boolean, selected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !alreadyGuarded, onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (alreadyGuarded) VGColors.TextSecondary else VGColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (alreadyGuarded) {
                Text("Already guarded", style = MaterialTheme.typography.bodySmall, color = VGColors.TextSecondary)
            }
        }
        Checkbox(
            checked = alreadyGuarded || selected,
            onCheckedChange = if (alreadyGuarded) null else { _ -> onToggle() },
            enabled = !alreadyGuarded,
            colors = CheckboxDefaults.colors(
                checkedColor = VGColors.Primary,
                checkmarkColor = VGColors.OnPrimary,
                uncheckedColor = VGColors.TextSecondary,
                disabledCheckedColor = VGColors.TextSecondary,
            ),
        )
    }
}
