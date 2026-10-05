package com.drivedeck.tubedeck.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

private val DefaultBackground = Color(0xFF080B11)
private val DefaultAccent = Color(0xFF00D2FF)
private val DefaultText = Color(0xFFFFFFFF)

internal object TubeDeckRuntimeStyle {
    // Compose-observable process state keeps MainActivity, native player and PiP/theme preview
    // surfaces synchronized instead of mutating invisible plain globals.
    var background: Color by mutableStateOf(DefaultBackground)
        private set
    var accent: Color by mutableStateOf(DefaultAccent)
        private set
    var textPrimary: Color by mutableStateOf(DefaultText)
        private set

    fun apply(
        backgroundArgb: Long,
        accentArgb: Long,
        textArgb: Long
    ) {
        background = Color(backgroundArgb.toInt())
        accent = Color(accentArgb.toInt())
        textPrimary = Color(textArgb.toInt())
    }

    fun reset() {
        background = DefaultBackground
        accent = DefaultAccent
        textPrimary = DefaultText
    }
}

private fun shifted(color: Color, delta: Float): Color =
    Color(
        red = (color.red + delta).coerceIn(0f, 1f),
        green = (color.green + delta).coerceIn(0f, 1f),
        blue = (color.blue + delta).coerceIn(0f, 1f),
        alpha = color.alpha
    )

private fun isDark(color: Color): Boolean =
    (0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue) < 0.50f

private fun surfaceShift(background: Color, darkAmount: Float, lightAmount: Float): Color =
    if (isDark(background)) shifted(background, lightAmount) else shifted(background, -darkAmount)

private fun mix(a: Color, b: Color, ratio: Float): Color {
    val t = ratio.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t
    )
}

val TubeDeckBackground: Color get() = TubeDeckRuntimeStyle.background
val TubeDeckSurface: Color get() = surfaceShift(TubeDeckBackground, 0.035f, 0.025f)
val TubeDeckSurfaceVariant: Color get() = surfaceShift(TubeDeckBackground, 0.060f, 0.055f)
val TubeDeckSurfaceHighlight: Color get() = surfaceShift(TubeDeckBackground, 0.085f, 0.085f)
val TubeDeckBorder: Color get() = surfaceShift(TubeDeckBackground, 0.14f, 0.13f)

val TubeDeckAccentCyan: Color get() = TubeDeckRuntimeStyle.accent
val TubeDeckAccentCyanGlow: Color get() = shifted(TubeDeckRuntimeStyle.accent, 0.08f)
val TubeDeckAccentMint = Color(0xFF00E676)
val TubeDeckAccentAmber = Color(0xFFFF9100)
val TubeDeckAccentOrange = Color(0xFFFF5722)
val TubeDeckAccentPurple = Color(0xFF9D4EDD)

val TubeDeckTextPrimary: Color get() = TubeDeckRuntimeStyle.textPrimary
val TubeDeckTextSecondary: Color get() = mix(TubeDeckTextPrimary, TubeDeckBackground, 0.43f)
val TubeDeckTextTertiary: Color get() = mix(TubeDeckTextPrimary, TubeDeckBackground, 0.62f)

val TubeDeckError = Color(0xFFFF3366)
val TubeDeckSuccess = Color(0xFF00E676)
