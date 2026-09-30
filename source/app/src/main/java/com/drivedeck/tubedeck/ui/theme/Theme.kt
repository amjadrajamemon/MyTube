package com.drivedeck.tubedeck.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = TubeDeckAccentCyan,
    onPrimary = TubeDeckBackground,
    primaryContainer = TubeDeckSurfaceVariant,
    onPrimaryContainer = TubeDeckAccentCyan,
    secondary = TubeDeckAccentMint,
    onSecondary = TubeDeckBackground,
    secondaryContainer = TubeDeckSurfaceVariant,
    onSecondaryContainer = TubeDeckAccentMint,
    tertiary = TubeDeckAccentAmber,
    onTertiary = TubeDeckBackground,
    background = TubeDeckBackground,
    onBackground = TubeDeckTextPrimary,
    surface = TubeDeckSurface,
    onSurface = TubeDeckTextPrimary,
    surfaceVariant = TubeDeckSurfaceVariant,
    onSurfaceVariant = TubeDeckTextSecondary,
    outline = TubeDeckBorder,
    error = TubeDeckError,
    onError = TubeDeckTextPrimary
)

@Composable
fun TubeDeckTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = TubeDeckBackground.toArgb()
                window.navigationBarColor = TubeDeckBackground.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
