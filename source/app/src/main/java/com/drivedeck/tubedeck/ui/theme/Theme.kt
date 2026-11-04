package com.drivedeck.tubedeck.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    appearancePreset: String = "Glass Midnight",
    lowResourceMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val profile = DeckThemeCatalog.get(appearancePreset)
    val experience = DeckExperienceCatalog.tokens(
        profile, lowResourceMode, DeckExperienceCatalog.restrainedMotion(LocalContext.current)
    )
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

    val scheme = if (darkBackground) {
        darkColorScheme(
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
    } else {
        lightColorScheme(
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
    }

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
    val family = profile.fontFamily
    val typography = Typography(
        displayLarge = TextStyle(fontFamily = family, fontSize = 42.sp, fontWeight = FontWeight.Bold),
        headlineMedium = TextStyle(fontFamily = family, fontSize = 25.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontFamily = family, fontSize = 21.sp, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontFamily = family, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontFamily = family, fontSize = 16.sp),
        bodyMedium = TextStyle(fontFamily = family, fontSize = 14.sp),
        bodySmall = TextStyle(fontFamily = family, fontSize = 12.sp),
        labelMedium = TextStyle(fontFamily = family, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    )
    val radius = profile.cornerRadiusDp.dp
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(radius / 3),
        small = RoundedCornerShape(radius / 2),
        medium = RoundedCornerShape(radius),
        large = RoundedCornerShape(radius * 1.4f),
        extraLarge = RoundedCornerShape(radius * 1.8f)
    )
    CompositionLocalProvider(
        LocalDeckThemeProfile provides profile,
        LocalDeckExperience provides experience,
        LocalDensity provides Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * boundedFontScale
        )
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes) {
            ProvideTextStyle(TextStyle(fontFamily = family)) { content() }
        }
    }
}
