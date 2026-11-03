package com.drivedeck.tubedeck.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.FontFamily

/**
 * Real theme systems, not ten aliases for a background color.
 * Includes navigation placement, spacing, typography and control geometry.
 * No proprietary app assets or fonts are copied.
 */
enum class DeckNavigationPlacement { BOTTOM, TOP, SIDE }
enum class DeckCardGeometry { SQUARE, COMPACT, ROUNDED, CINEMA, TERMINAL }

data class DeckThemeProfile(
    val name: String,
    val accentArgb: Long,
    val backgroundArgb: Long,
    val textArgb: Long = 0xFFFFFFFFL,
    val fontScale: Float = 1f,
    val fontFamily: FontFamily = FontFamily.SansSerif,
    val cornerRadiusDp: Int = 12,
    val contentGapDp: Int = 8,
    val navigationPlacement: DeckNavigationPlacement = DeckNavigationPlacement.BOTTOM,
    val navigationHeightDp: Int = 68,
    val geometry: DeckCardGeometry = DeckCardGeometry.ROUNDED,
    val subtitle: String = ""
)

object DeckThemeCatalog {
    val presets: List<DeckThemeProfile> = listOf(
        DeckThemeProfile("ATV", 0xFF19A9FFL, 0xFF080D19L, fontScale = 1.16f,
            cornerRadiusDp = 18, contentGapDp = 14,
            navigationPlacement = DeckNavigationPlacement.SIDE, navigationHeightDp = 78,
            geometry = DeckCardGeometry.CINEMA, subtitle = "TV-first rail · cinematic widescreen cards"),
        DeckThemeProfile("Xtreamity", 0xFFFF9C32L, 0xFF10121CL, fontScale = 1.10f,
            cornerRadiusDp = 5, contentGapDp = 8,
            navigationPlacement = DeckNavigationPlacement.SIDE, navigationHeightDp = 70,
            geometry = DeckCardGeometry.COMPACT, subtitle = "Dense streaming dashboard · sharp modules"),
        DeckThemeProfile("Android", 0xFF6750A4L, 0xFFF5F2F9L, textArgb = 0xFF201D26L,
            cornerRadiusDp = 24, contentGapDp = 12,
            geometry = DeckCardGeometry.ROUNDED, subtitle = "Material-like light surfaces · rounded cards"),
        DeckThemeProfile("Linux Desktop", 0xFF29C49BL, 0xFF151B22L,
            fontFamily = FontFamily.Monospace, cornerRadiusDp = 4, contentGapDp = 6,
            navigationPlacement = DeckNavigationPlacement.TOP, navigationHeightDp = 48,
            geometry = DeckCardGeometry.TERMINAL, subtitle = "Compact desktop menu · monospace labels"),
        DeckThemeProfile("Digital HUD", 0xFF2DFFD1L, 0xFF010F14L,
            fontFamily = FontFamily.Monospace, fontScale = 1.08f,
            cornerRadiusDp = 0, contentGapDp = 14,
            navigationPlacement = DeckNavigationPlacement.TOP, navigationHeightDp = 54,
            geometry = DeckCardGeometry.TERMINAL, subtitle = "Instrument-style typography · compact controls"),
        DeckThemeProfile("Cinema", 0xFFF4B860L, 0xFF090909L, fontScale = 1.14f,
            fontFamily = FontFamily.Serif, cornerRadiusDp = 6, contentGapDp = 15,
            navigationHeightDp = 82, geometry = DeckCardGeometry.CINEMA,
            subtitle = "Editorial serif titles · wide cinematic thumbnails"),
        DeckThemeProfile("Glass Midnight", 0xFF00D2FFL, 0xFF080B11L,
            cornerRadiusDp = 18, contentGapDp = 12, navigationHeightDp = 76,
            geometry = DeckCardGeometry.ROUNDED, subtitle = "Translucent-style dark glass · roomy spacing"),
        DeckThemeProfile("AMOLED Black", 0xFF00E5FFL, 0xFF000000L,
            cornerRadiusDp = 0, contentGapDp = 5, navigationHeightDp = 60,
            geometry = DeckCardGeometry.SQUARE, subtitle = "True black · maximum density and contrast"),
        DeckThemeProfile("Retro Neon", 0xFFFF4DD2L, 0xFF180724L,
            fontFamily = FontFamily.Monospace, fontScale = 1.10f,
            cornerRadiusDp = 2, contentGapDp = 10,
            geometry = DeckCardGeometry.SQUARE, subtitle = "Arcade lettering · bright neon blocks"),
        DeckThemeProfile("Focus Minimal", 0xFF4D78A8L, 0xFFF5F7F8L,
            textArgb = 0xFF202833L, fontScale = 1.04f,
            cornerRadiusDp = 8, contentGapDp = 18, navigationHeightDp = 64,
            geometry = DeckCardGeometry.COMPACT, subtitle = "Paper-like spacious interface · quiet controls")
    )

    fun get(name: String): DeckThemeProfile =
        presets.firstOrNull { it.name == name } ?: presets.firstOrNull {
            name == "Custom" && it.name == "Glass Midnight"
        } ?: presets.first { it.name == "Glass Midnight" }
}

val LocalDeckThemeProfile = compositionLocalOf { DeckThemeCatalog.get("Glass Midnight") }
