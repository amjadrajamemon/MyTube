package com.drivedeck.tubedeck.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * One design language per existing theme, shared across Home, search, downloads,
 * Settings, navigation and player. This does not add new features or replace
 * the 13 existing screen structures.
 */
enum class DeckBackdropStyle {
    TV_SPOTLIGHT, BROADCAST_GRID, MATERIAL_MIST, DESKTOP,
    DIGITAL_HUD, CINEMA, FROSTED_GLASS, INK_BLACK,
    ARCADE, EDITORIAL, MOBILE, DESKTOP_VIDEO, TV_RAILS
}

data class DeckExperienceTokens(
    val backdrop: DeckBackdropStyle,
    val focusScale: Float,
    val hoverScale: Float,
    val focusBorderDp: Float,
    val animated: Boolean,
    val artworkAtmosphere: Boolean,
    val motionDurationMs: Int,
    val surfaceGlow: Float
)

/** A pure policy. A low-RAM box or Android's reduced-animation setting wins. */
object DeckExperienceCatalog {
    fun tokens(
        profile: DeckThemeProfile,
        lowResource: Boolean = false,
        reducedMotion: Boolean = false
    ): DeckExperienceTokens {
        val backdrop = when (profile.name) {
            "ATV" -> DeckBackdropStyle.TV_SPOTLIGHT
            "Xtreamity IPTV" -> DeckBackdropStyle.BROADCAST_GRID
            "Android Material 3" -> DeckBackdropStyle.MATERIAL_MIST
            "Linux Desktop" -> DeckBackdropStyle.DESKTOP
            "Cyberpunk Digital" -> DeckBackdropStyle.DIGITAL_HUD
            "Plex Cinema" -> DeckBackdropStyle.CINEMA
            "Kodi Arctic" -> DeckBackdropStyle.FROSTED_GLASS
            "VLC Classic" -> DeckBackdropStyle.INK_BLACK
            "MX Player Pro" -> DeckBackdropStyle.ARCADE
            "Apple TV Inspired" -> DeckBackdropStyle.EDITORIAL
            "YouTube Standard" -> DeckBackdropStyle.MOBILE
            "YouTube Desktop" -> DeckBackdropStyle.DESKTOP_VIDEO
            "NeoGlass Luxe" -> DeckBackdropStyle.FROSTED_GLASS
            "Cinema Deck" -> DeckBackdropStyle.CINEMA
            "CommandDrive Auto" -> DeckBackdropStyle.BROADCAST_GRID
            "StreamPlus Luxe TV" -> DeckBackdropStyle.TV_SPOTLIGHT
            "Midnight Editorial Pro" -> DeckBackdropStyle.EDITORIAL
            "Cyber Console X" -> DeckBackdropStyle.DIGITAL_HUD
            "SmartTube Pro" -> DeckBackdropStyle.TV_RAILS
            else -> DeckBackdropStyle.FROSTED_GLASS
        }
        val minimal = lowResource || reducedMotion ||
            profile.motionStyle == DeckMotionStyle.INSTANT
        val duration = if (minimal) 0 else when (profile.motionStyle) {
            DeckMotionStyle.INSTANT -> 0
            DeckMotionStyle.SNAP -> 100
            DeckMotionStyle.GLIDE -> 230
            DeckMotionStyle.PULSE -> 160
        }
        val focusScale = if (minimal) 1f else when (backdrop) {
            DeckBackdropStyle.TV_SPOTLIGHT, DeckBackdropStyle.TV_RAILS -> 1.035f
            DeckBackdropStyle.FROSTED_GLASS, DeckBackdropStyle.EDITORIAL -> 1.025f
            DeckBackdropStyle.ARCADE, DeckBackdropStyle.DIGITAL_HUD -> 1.018f
            else -> 1.012f
        }
        return DeckExperienceTokens(
            backdrop = backdrop,
            focusScale = focusScale,
            hoverScale = if (minimal) 1f else 1.008f,
            focusBorderDp = when (backdrop) {
                DeckBackdropStyle.TV_RAILS, DeckBackdropStyle.TV_SPOTLIGHT -> 3f
                DeckBackdropStyle.DESKTOP, DeckBackdropStyle.DESKTOP_VIDEO -> 1.5f
                else -> 2f
            },
            animated = !minimal,
            artworkAtmosphere = !lowResource && backdrop in setOf(
                DeckBackdropStyle.TV_SPOTLIGHT,
                DeckBackdropStyle.CINEMA,
                DeckBackdropStyle.FROSTED_GLASS,
                DeckBackdropStyle.EDITORIAL,
                DeckBackdropStyle.TV_RAILS
            ),
            motionDurationMs = duration,
            surfaceGlow = if (minimal) 0f else when (backdrop) {
                DeckBackdropStyle.DIGITAL_HUD, DeckBackdropStyle.ARCADE -> .18f
                DeckBackdropStyle.FROSTED_GLASS, DeckBackdropStyle.TV_SPOTLIGHT -> .11f
                else -> .055f
            }
        )
    }

    fun restrainedMotion(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val lowRam = manager?.isLowRamDevice == true
        val reduced = runCatching {
            Settings.Global.getFloat(context.contentResolver, "animator_duration_scale", 1f) == 0f
        }.getOrDefault(false)
        return lowRam || reduced
    }
}

val LocalDeckExperience = compositionLocalOf {
    DeckExperienceCatalog.tokens(DeckThemeCatalog.get("Kodi Arctic"))
}

/**
 * Non-interactive, constant-cost theme backdrop. Brushes draw on the GPU;
 * no timer, bitmap decode, or per-frame polling is required.
 */
@Composable
fun DeckAmbientBackdrop(
    modifier: Modifier = Modifier,
    atmosphere: Color? = null
) {
    val profile = LocalDeckThemeProfile.current
    val tokens = LocalDeckExperience.current
    val base = Color(profile.backgroundArgb.toInt())
    val accent = Color(profile.accentArgb.toInt())
    val target = atmosphere ?: accent
    val highlight by animateColorAsState(
        targetValue = target,
        animationSpec = tween(tokens.motionDurationMs),
        label = "Deck ambient color"
    )
    val fraction = when (tokens.backdrop) {
        DeckBackdropStyle.INK_BLACK, DeckBackdropStyle.DESKTOP -> 0f
        DeckBackdropStyle.MOBILE, DeckBackdropStyle.DESKTOP_VIDEO -> .035f
        DeckBackdropStyle.TV_RAILS, DeckBackdropStyle.BROADCAST_GRID -> .07f
        DeckBackdropStyle.DIGITAL_HUD, DeckBackdropStyle.ARCADE -> .13f
        DeckBackdropStyle.CINEMA, DeckBackdropStyle.EDITORIAL -> .09f
        else -> .12f
    }
    Box(
        modifier.fillMaxSize().background(
            Brush.verticalGradient(
                colors = listOf(
                    base.copy(alpha = 1f).let { androidx.compose.ui.graphics.lerp(it, highlight, fraction) },
                    base,
                    base
                )
            )
        )
    )
}
