package com.vocabguard.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Dark-first palette. One accent (cyan); green and red are reserved for answer feedback. */
object VGColors {
    val Background = Color(0xFF121212)
    val Surface = Color(0xFF1E1E1E)
    val SurfaceHigh = Color(0xFF262626)
    val Outline = Color(0xFF303030)
    val Track = Color(0xFF2A2A2A)
    val Primary = Color(0xFF3DDCFF)
    val OnPrimary = Color(0xFF00161D)
    val TextPrimary = Color(0xFFF1F1F1)
    val TextSecondary = Color(0xFF9A9A9A)
    val Success = Color(0xFF4ADE80)
    val Error = Color(0xFFFF6B6B)
    val Warning = Color(0xFFFFB547)
}

private val VGColorScheme = darkColorScheme(
    primary = VGColors.Primary,
    onPrimary = VGColors.OnPrimary,
    secondary = VGColors.Warning,
    onSecondary = Color.Black,
    background = VGColors.Background,
    onBackground = VGColors.TextPrimary,
    surface = VGColors.Surface,
    onSurface = VGColors.TextPrimary,
    surfaceVariant = VGColors.SurfaceHigh,
    onSurfaceVariant = VGColors.TextSecondary,
    surfaceContainerLowest = VGColors.Background,
    surfaceContainerLow = VGColors.Surface,
    surfaceContainer = VGColors.Surface,
    surfaceContainerHigh = VGColors.SurfaceHigh,
    surfaceContainerHighest = VGColors.SurfaceHigh,
    outline = VGColors.Outline,
    outlineVariant = VGColors.Outline,
    error = VGColors.Error,
    onError = Color.Black,
)

private val VGTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Medium),
    )
}

private val VGShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * [uiScale] enlarges everything (spacing, touch targets, icons and text) on top of the system
 * font size, for users who need a bigger UI. Changes animate smoothly; the first value doesn't.
 */
@Composable
fun VocabGuardTheme(uiScale: Float = 1f, content: @Composable () -> Unit) {
    val scale = remember { Animatable(uiScale) }
    LaunchedEffect(uiScale) {
        scale.animateTo(uiScale, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
    }
    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(base.density * scale.value, base.fontScale)) {
        MaterialTheme(
            colorScheme = VGColorScheme,
            typography = VGTypography,
            shapes = VGShapes,
            content = content,
        )
    }
}
