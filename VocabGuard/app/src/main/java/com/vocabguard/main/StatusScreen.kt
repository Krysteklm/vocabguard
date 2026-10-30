package com.vocabguard.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.vocabguard.data.local.GuardedAppEntity
import com.vocabguard.data.repository.DailyStats
import com.vocabguard.ui.components.AppIcon
import com.vocabguard.ui.components.NoticeCard
import com.vocabguard.ui.components.ScreenHeader
import com.vocabguard.ui.components.StatusDot
import com.vocabguard.ui.components.VGCard
import com.vocabguard.ui.components.VGSwitch
import com.vocabguard.ui.components.appearOnce
import com.vocabguard.ui.components.rememberNow
import com.vocabguard.ui.theme.VGColors
import com.vocabguard.util.formatCountdown

@Composable
fun StatusScreen(
    state: MainUiState,
    onToggleBlocker: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenWords: () -> Unit,
) {
    val anyPassActive = state.guardedApps.any { it.sessionUntil > System.currentTimeMillis() }
    val now = rememberNow(ticking = anyPassActive)
    val openNow = state.guardedApps.filter { it.isEnabled && it.sessionUntil > now }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = "VocabGuard",
            subtitle = "A short quiz stands between you and the scroll.",
            modifier = Modifier.appearOnce(0),
        )

        if (state.loadFailed) {
            NoticeCard(
                icon = Icons.Rounded.WarningAmber,
                tint = VGColors.Error,
                title = "Settings didn’t load",
                body = "Restart the app. The blocker keeps running with its last saved settings.",
            )
        }

        AnimatedVisibility(
            visible = !state.isLoading && !state.permissions.allGranted,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            AttentionBanner(
                title = "Finish setup",
                body = "VocabGuard can’t see your apps open until both permissions are on.",
                onClick = onOpenSettings,
            )
        }

        AnimatedVisibility(
            visible = !state.isLoading && state.activeWordCount == 0,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            AttentionBanner(
                title = "No active words",
                body = "Quizzes use the built-in German pack until you turn on a deck.",
                onClick = onOpenWords,
                tint = VGColors.Primary,
            )
        }

        BlockerCard(
            enabled = state.blockerEnabled,
            guardedCount = state.guardedApps.count { it.isEnabled },
            onToggle = onToggleBlocker,
            onManageApps = onOpenApps,
            modifier = Modifier.appearOnce(1),
        )

        AnimatedVisibility(
            visible = state.blockerEnabled && openNow.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            OpenNowCard(apps = openNow, now = now)
        }

        Column(Modifier.appearOnce(2), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Today", style = MaterialTheme.typography.titleMedium, color = VGColors.TextPrimary)
            StatsRow(stats = state.todayStats)
        }
    }
}

@Composable
private fun AttentionBanner(title: String, body: String, onClick: () -> Unit, tint: Color = VGColors.Warning) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = tint.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (tint == VGColors.Warning) Icons.Rounded.WarningAmber else Icons.Rounded.Info, contentDescription = null, tint = tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = tint)
                Text(body, style = MaterialTheme.typography.bodySmall, color = VGColors.TextSecondary)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = VGColors.TextSecondary)
        }
    }
}

@Composable
private fun BlockerCard(
    enabled: Boolean,
    guardedCount: Int,
    onToggle: (Boolean) -> Unit,
    onManageApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val accent by animateColorAsState(if (enabled) VGColors.Primary else VGColors.TextSecondary, label = "blockerAccent")
    val title = when {
        !enabled -> "Paused"
        guardedCount == 0 -> "No apps guarded"
        guardedCount == 1 -> "Guarding 1 app"
        else -> "Guarding $guardedCount apps"
    }

    VGCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(active = enabled && guardedCount > 0, color = accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Blocker", style = MaterialTheme.typography.labelLarge, color = VGColors.TextSecondary)
                AnimatedContent(
                    targetState = title,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                    label = "blockerTitle",
                ) { text ->
                    Text(text, style = MaterialTheme.typography.titleLarge, color = VGColors.TextPrimary)
                }
            }
            VGSwitch(
                checked = enabled,
                onCheckedChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle(it)
                },
            )
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onManageApps, modifier = Modifier.padding(start = 28.dp)) {
            Text("Choose apps and limits", color = VGColors.Primary)
        }
    }
}

@Composable
private fun OpenNowCard(apps: List<GuardedAppEntity>, now: Long) {
    VGCard {
        Text("Open right now", style = MaterialTheme.typography.titleSmall, color = VGColors.TextPrimary)
        apps.forEach { app ->
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.packageName, size = 32.dp)
                Spacer(Modifier.width(12.dp))
                Text(app.label, style = MaterialTheme.typography.bodyMedium, color = VGColors.TextPrimary, modifier = Modifier.weight(1f))
                Text(
                    "${formatCountdown(app.sessionUntil - now)} left",
                    style = MaterialTheme.typography.labelLarge,
                    color = VGColors.Warning,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(VGColors.Warning.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StatsRow(stats: DailyStats) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile("Quizzes solved", stats.quizzesSolved, Modifier.weight(1f))
        StatTile("Times you said not now", stats.mindfulExits, Modifier.weight(1f))
        StatTile("Minutes saved (est.)", stats.estimatedMinutesSaved, Modifier.weight(1f), VGColors.Success)
    }
    Text(
        "Each “Not now” counts as ${DailyStats.MINUTES_SAVED_PER_EXIT} minutes not spent scrolling.",
        style = MaterialTheme.typography.bodySmall,
        color = VGColors.TextSecondary,
    )
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier, accent: Color = VGColors.Primary) {
    val animated by animateIntAsState(value, tween(700, easing = FastOutSlowInEasing), label = "statValue")
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = VGColors.Surface,
        border = BorderStroke(1.dp, VGColors.Outline),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("$animated", style = MaterialTheme.typography.headlineMedium, color = accent)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = VGColors.TextSecondary, minLines = 2)
        }
    }
}

