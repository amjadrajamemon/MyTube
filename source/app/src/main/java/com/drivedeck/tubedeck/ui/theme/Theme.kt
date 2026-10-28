package com.drivedeck.tubedeck.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import com.drivedeck.tubedeck.ui.components.isTvLikeNavigationDevice
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

@Composable
fun TubeDeckTheme(
    fontScale: Float = 1.0f,
    accentArgb: Long = 0xFF00D2FFL,
    backgroundArgb: Long = 0xFF080B11L,
    textArgb: Long = 0xFFFFFFFFL,
    content: @Composable () -> Unit
) {
    // Living-room displays need readable text at a distance; leave phones/cars alone.
    val tvInputMode = isTvLikeNavigationDevice(LocalContext.current)
    val boundedFontScale = (fontScale.coerceIn(0.80f, 1.35f) * if (tvInputMode) 1.18f else 1f)
        .coerceAtMost(1.60f)
    TubeDeckRuntimeStyle.apply(
        backgroundArgb = backgroundArgb,
        accentArgb = accentArgb,
        textArgb = textArgb
    )

    val background = TubeDeckBackground
    val accent = TubeDeckAccentCyan
    val primaryText = TubeDeckTextPrimary
    val darkBackground = background.luminance() < 0.50f
    val onAccent = if (accent.luminance() > 0.55f) Color.Black else Color.White

    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = TubeDeckSurfaceVariant,
        onPrimaryContainer = accent,
        secondary = TubeDeckAccentMint,
        onSecondary = TubeDeckBackground,
        secondaryContainer = TubeDeckSurfaceVariant,
        onSecondaryContainer = TubeDeckAccentMint,
        tertiary = TubeDeckAccentAmber,
        onTertiary = TubeDeckBackground,
        background = background,
        onBackground = primaryText,
        surface = TubeDeckSurface,
        onSurface = primaryText,
        surfaceVariant = TubeDeckSurfaceVariant,
        onSurfaceVariant = TubeDeckTextSecondary,
        outline = TubeDeckBorder,
        error = TubeDeckError,
        onError = primaryText
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = background.toArgb()
                window.navigationBarColor = background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkBackground
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkBackground
            }
        }
    }

    val baseDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * boundedFontScale
        )
    ) {
        MaterialTheme(
            colorScheme = scheme,
            content = content
        )
    }
}
