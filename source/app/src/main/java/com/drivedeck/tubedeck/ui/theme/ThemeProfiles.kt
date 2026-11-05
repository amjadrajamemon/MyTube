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
/** Real screen structures instead of palette-only variations. */
enum class DeckHomePresentation {
    THREE_COLUMN, IPTV_DASHBOARD, STACKED_SHELVES, DESKTOP_TABLE, HUD_CONSOLE,
    CINEMA_STRIPS, GLASS_CAROUSEL, AMOLED_INDEX, ARCADE_GALLERY, MINIMAL_EDITORIAL, SMARTTUBE_TV, YOUTUBE_STANDARD, YOUTUBE_DESKTOP,
    NEO_GLASS_LOUNGE, CINEMA_DECK_STAGE, COMMAND_DRIVE, STREAMPLUS_LOUNGE,
    MIDNIGHT_EDITORIAL, CYBER_COMMAND
}
enum class DeckPlayerChrome { CLASSIC, GLASS, THEATER, ARCADE, CONSOLE, HUD, TV_CLASSIC, YOUTUBE_MOBILE, YOUTUBE_DESKTOP, PLEX, KODI, MX_GESTURE, VLC_COMPACT, MATERIAL, APPLE_FLOAT, CYBER }
enum class DeckNavVisual { ICONS, COMPACT, PILL, MINIMAL, COMMAND, TV_CLASSIC, YOUTUBE_MOBILE, YOUTUBE_DESKTOP }
enum class DeckMotionStyle { INSTANT, SNAP, GLIDE, PULSE }

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
    val homePresentation: DeckHomePresentation = DeckHomePresentation.THREE_COLUMN,
    val playerChrome: DeckPlayerChrome = DeckPlayerChrome.CLASSIC,
    val navVisual: DeckNavVisual = DeckNavVisual.PILL,
    val motionStyle: DeckMotionStyle = DeckMotionStyle.GLIDE,
    val subtitle: String = ""
)

object DeckThemeCatalog {
    val presets: List<DeckThemeProfile> = listOf(
        DeckThemeProfile("ATV", 0xFF19A9FFL, 0xFF080D19L, fontScale = 1.16f,
            cornerRadiusDp = 18, contentGapDp = 14,
            navigationPlacement = DeckNavigationPlacement.SIDE, navigationHeightDp = 78,
            geometry = DeckCardGeometry.CINEMA,
            homePresentation = DeckHomePresentation.THREE_COLUMN,
            playerChrome = DeckPlayerChrome.THEATER, navVisual = DeckNavVisual.PILL, subtitle = "TV-first rail · cinematic widescreen cards"),
        DeckThemeProfile("Xtreamity IPTV", 0xFFFF9C32L, 0xFF10121CL, fontScale = 1.10f,
            cornerRadiusDp = 5, contentGapDp = 8,
            navigationPlacement = DeckNavigationPlacement.SIDE, navigationHeightDp = 70,
            geometry = DeckCardGeometry.COMPACT,
            homePresentation = DeckHomePresentation.IPTV_DASHBOARD,
            playerChrome = DeckPlayerChrome.HUD, navVisual = DeckNavVisual.COMPACT,
            motionStyle = DeckMotionStyle.SNAP, subtitle = "Dense streaming dashboard · sharp modules"),
        DeckThemeProfile("Android Material 3", 0xFF6750A4L, 0xFFF5F2F9L, textArgb = 0xFF201D26L,
            cornerRadiusDp = 24, contentGapDp = 12,
            geometry = DeckCardGeometry.ROUNDED,
            homePresentation = DeckHomePresentation.STACKED_SHELVES,
            playerChrome = DeckPlayerChrome.MATERIAL, navVisual = DeckNavVisual.PILL, subtitle = "Material-like light surfaces · rounded cards"),
        DeckThemeProfile("Linux Desktop", 0xFF29C49BL, 0xFF151B22L,
            fontFamily = FontFamily.Monospace, cornerRadiusDp = 4, contentGapDp = 6,
            navigationPlacement = DeckNavigationPlacement.TOP, navigationHeightDp = 48,
            geometry = DeckCardGeometry.TERMINAL,
            homePresentation = DeckHomePresentation.DESKTOP_TABLE,
            playerChrome = DeckPlayerChrome.CONSOLE, navVisual = DeckNavVisual.COMMAND,
            motionStyle = DeckMotionStyle.SNAP,
            subtitle = "Compact desktop menu · monospace labels"),
        DeckThemeProfile("Cyberpunk Digital", 0xFF2DFFD1L, 0xFF010F14L,
            fontFamily = FontFamily.Monospace, fontScale = 1.08f,
            cornerRadiusDp = 0, contentGapDp = 14,
            navigationPlacement = DeckNavigationPlacement.TOP, navigationHeightDp = 54,
            geometry = DeckCardGeometry.TERMINAL,
            homePresentation = DeckHomePresentation.HUD_CONSOLE,
            playerChrome = DeckPlayerChrome.CYBER, navVisual = DeckNavVisual.COMMAND,
            motionStyle = DeckMotionStyle.PULSE,
            subtitle = "Instrument-style typography · compact controls"),
        DeckThemeProfile("Plex Cinema", 0xFFF4B860L, 0xFF090909L, fontScale = 1.14f,
            fontFamily = FontFamily.Serif, cornerRadiusDp = 6, contentGapDp = 15,
            navigationHeightDp = 82, geometry = DeckCardGeometry.CINEMA,
            homePresentation = DeckHomePresentation.CINEMA_STRIPS,
            playerChrome = DeckPlayerChrome.PLEX, navVisual = DeckNavVisual.MINIMAL,
            subtitle = "Editorial serif titles · wide cinematic thumbnails"),
        DeckThemeProfile("Kodi Arctic", 0xFF00D2FFL, 0xFF080B11L,
            cornerRadiusDp = 18, contentGapDp = 12, navigationHeightDp = 76,
            geometry = DeckCardGeometry.ROUNDED,
            homePresentation = DeckHomePresentation.GLASS_CAROUSEL,
            playerChrome = DeckPlayerChrome.KODI, navVisual = DeckNavVisual.PILL,
            subtitle = "Translucent-style dark glass · roomy spacing"),
        DeckThemeProfile("VLC Classic", 0xFF00E5FFL, 0xFF000000L,
            cornerRadiusDp = 0, contentGapDp = 5, navigationHeightDp = 60,
            navigationPlacement = DeckNavigationPlacement.SIDE,
            geometry = DeckCardGeometry.SQUARE,
            homePresentation = DeckHomePresentation.AMOLED_INDEX,
            playerChrome = DeckPlayerChrome.VLC_COMPACT, navVisual = DeckNavVisual.ICONS,
            motionStyle = DeckMotionStyle.INSTANT,
            subtitle = "True black · maximum density and contrast"),
        DeckThemeProfile("MX Player Pro", 0xFFFF4DD2L, 0xFF180724L,
            fontFamily = FontFamily.Monospace, fontScale = 1.10f,
            cornerRadiusDp = 2, contentGapDp = 10,
            geometry = DeckCardGeometry.SQUARE,
            homePresentation = DeckHomePresentation.ARCADE_GALLERY,
            playerChrome = DeckPlayerChrome.MX_GESTURE, navVisual = DeckNavVisual.COMMAND,
            motionStyle = DeckMotionStyle.PULSE,
            subtitle = "Arcade lettering · bright neon blocks"),
        DeckThemeProfile("Apple TV Inspired", 0xFF4D78A8L, 0xFFF5F7F8L,
            textArgb = 0xFF202833L, fontScale = 1.04f,
            cornerRadiusDp = 8, contentGapDp = 18, navigationHeightDp = 64,
            geometry = DeckCardGeometry.COMPACT,
            homePresentation = DeckHomePresentation.MINIMAL_EDITORIAL,
            playerChrome = DeckPlayerChrome.APPLE_FLOAT, navVisual = DeckNavVisual.MINIMAL,
            motionStyle = DeckMotionStyle.INSTANT,
            subtitle = "Paper-like spacious interface · quiet controls"),
        DeckThemeProfile("YouTube Standard", 0xFFFF0033L, 0xFF101010L,
            textArgb = 0xFFFFFFFFL, fontScale = 1.04f,
            cornerRadiusDp = 10, contentGapDp = 8,
            navigationPlacement = DeckNavigationPlacement.BOTTOM, navigationHeightDp = 64,
            geometry = DeckCardGeometry.ROUNDED,
            homePresentation = DeckHomePresentation.YOUTUBE_STANDARD,
            playerChrome = DeckPlayerChrome.YOUTUBE_MOBILE,
            navVisual = DeckNavVisual.YOUTUBE_MOBILE, motionStyle = DeckMotionStyle.SNAP),
        DeckThemeProfile("YouTube Desktop", 0xFFFF0033L, 0xFF0F0F0FL,
            textArgb = 0xFFFFFFFFL, fontScale = 1.0f,
            cornerRadiusDp = 6, contentGapDp = 10,
            navigationPlacement = DeckNavigationPlacement.SIDE, navigationHeightDp = 72,
            geometry = DeckCardGeometry.SQUARE,
            homePresentation = DeckHomePresentation.YOUTUBE_DESKTOP,
            playerChrome = DeckPlayerChrome.YOUTUBE_DESKTOP,
            navVisual = DeckNavVisual.YOUTUBE_DESKTOP, motionStyle = DeckMotionStyle.GLIDE),
        DeckThemeProfile("SmartTube Pro", 0xFF18A9B5L, 0xFF0D0D0DL,
            fontScale = 1.09f, cornerRadiusDp = 3, contentGapDp = 10,
            navigationPlacement = DeckNavigationPlacement.SIDE, navigationHeightDp = 72,
            geometry = DeckCardGeometry.SQUARE,
            homePresentation = DeckHomePresentation.SMARTTUBE_TV,
            playerChrome = DeckPlayerChrome.TV_CLASSIC, navVisual = DeckNavVisual.TV_CLASSIC,
            motionStyle = DeckMotionStyle.SNAP,
            subtitle = "TV-first sidebar · full-width video rails · focused playback"),
        // Six premium identities. Each has a unique Home structure, control shape,
        // navigation behavior and motion profile. All use original UI assets.
        DeckThemeProfile("NeoGlass Luxe", 0xFF82DDFBL, 0xFF0B1528L,
            fontScale = 1.06f, cornerRadiusDp = 26, contentGapDp = 16,
            navigationPlacement = DeckNavigationPlacement.TOP, navigationHeightDp = 56,
            geometry = DeckCardGeometry.ROUNDED,
            homePresentation = DeckHomePresentation.NEO_GLASS_LOUNGE,
            playerChrome = DeckPlayerChrome.GLASS, navVisual = DeckNavVisual.PILL,
            motionStyle = DeckMotionStyle.GLIDE,
            subtitle = "Floating frosted lounge · glowing hero · glass discovery trays"),
        DeckThemeProfile("Cinema Deck", 0xFFFFC67BL, 0xFF0C1019L,
            fontScale = 1.13f, fontFamily = FontFamily.Serif,
            cornerRadiusDp = 20, contentGapDp = 18,
            navigationPlacement = DeckNavigationPlacement.BOTTOM, navigationHeightDp = 70,
            geometry = DeckCardGeometry.CINEMA,
            homePresentation = DeckHomePresentation.CINEMA_DECK_STAGE,
            playerChrome = DeckPlayerChrome.THEATER, navVisual = DeckNavVisual.MINIMAL,
            motionStyle = DeckMotionStyle.GLIDE,
            subtitle = "Immersive widescreen stage · episodes · cinema playback"),
        DeckThemeProfile("CommandDrive Auto", 0xFF4DE5D0L, 0xFF071A22L,
            fontScale = 1.12f, cornerRadiusDp = 14, contentGapDp = 12,
            navigationPlacement = DeckNavigationPlacement.BOTTOM, navigationHeightDp = 82,
            geometry = DeckCardGeometry.COMPACT,
            homePresentation = DeckHomePresentation.COMMAND_DRIVE,
            playerChrome = DeckPlayerChrome.HUD, navVisual = DeckNavVisual.ICONS,
            motionStyle = DeckMotionStyle.SNAP,
            subtitle = "Landscape car command panel · large audio-first actions"),
        DeckThemeProfile("StreamPlus Luxe TV", 0xFF5EA9FFL, 0xFF101725L,
            fontScale = 1.16f, cornerRadiusDp = 20, contentGapDp = 16,
            navigationPlacement = DeckNavigationPlacement.SIDE, navigationHeightDp = 84,
            geometry = DeckCardGeometry.CINEMA,
            homePresentation = DeckHomePresentation.STREAMPLUS_LOUNGE,
            playerChrome = DeckPlayerChrome.PLEX, navVisual = DeckNavVisual.TV_CLASSIC,
            motionStyle = DeckMotionStyle.GLIDE,
            subtitle = "Luxury TV spotlight · left rail · continuing side lounge"),
        DeckThemeProfile("Midnight Editorial Pro", 0xFFE7BDA7L, 0xFF13121AL,
            fontScale = 1.06f, fontFamily = FontFamily.Serif,
            cornerRadiusDp = 9, contentGapDp = 21,
            navigationPlacement = DeckNavigationPlacement.TOP, navigationHeightDp = 46,
            geometry = DeckCardGeometry.COMPACT,
            homePresentation = DeckHomePresentation.MIDNIGHT_EDITORIAL,
            playerChrome = DeckPlayerChrome.APPLE_FLOAT, navVisual = DeckNavVisual.MINIMAL,
            motionStyle = DeckMotionStyle.INSTANT,
            subtitle = "Quiet midnight magazine · typography · curated browse cards"),
        DeckThemeProfile("Cyber Console X", 0xFF7EFFC7L, 0xFF040D13L,
            fontScale = 1.03f, fontFamily = FontFamily.Monospace,
            cornerRadiusDp = 3, contentGapDp = 9,
            navigationPlacement = DeckNavigationPlacement.SIDE, navigationHeightDp = 64,
            geometry = DeckCardGeometry.TERMINAL,
            homePresentation = DeckHomePresentation.CYBER_COMMAND,
            playerChrome = DeckPlayerChrome.CYBER, navVisual = DeckNavVisual.COMMAND,
            motionStyle = DeckMotionStyle.PULSE,
            subtitle = "Precision HUD workspace · data panels · technical player")
    )

    // Older app installations retain their chosen preset after structural redesign.
    private val legacyThemeNames = mapOf(
        "Xtreamity" to "Xtreamity IPTV",
        "Android" to "Android Material 3",
        "Digital HUD" to "Cyberpunk Digital",
        "Cinema" to "Plex Cinema",
        "Glass Midnight" to "Kodi Arctic",
        "AMOLED Black" to "VLC Classic",
        "Retro Neon" to "MX Player Pro",
        "Focus Minimal" to "Apple TV Inspired",
        "SmartTube TV" to "SmartTube Pro",
        "Custom" to "Kodi Arctic"
    )

    fun get(name: String): DeckThemeProfile =
        presets.firstOrNull { it.name == name } ?: presets.firstOrNull {
            it.name == (legacyThemeNames[name] ?: "Kodi Arctic")
        } ?: presets.first { it.name == "Kodi Arctic" }
}

/** Immutable, per-render theme override; no global mutable UI state. */
object DeckStudioPolicy {
    fun resolve(
        base: DeckThemeProfile,
        home: String,
        navigation: String,
        player: String,
        motion: String,
        density: String = "Preset",
        corners: String = "Preset"
    ): DeckThemeProfile {
        val homeMode = when (home) {
            "Cinematic" -> DeckHomePresentation.CINEMA_DECK_STAGE
            "Glass Lounge" -> DeckHomePresentation.NEO_GLASS_LOUNGE
            "Dashboard" -> DeckHomePresentation.COMMAND_DRIVE
            "Editorial" -> DeckHomePresentation.MIDNIGHT_EDITORIAL
            "Compact" -> DeckHomePresentation.AMOLED_INDEX
            "TV Lounge" -> DeckHomePresentation.STREAMPLUS_LOUNGE
            "Cyber HUD" -> DeckHomePresentation.CYBER_COMMAND
            else -> base.homePresentation
        }
        val nav = when (navigation) {
            "Top" -> DeckNavigationPlacement.TOP
            "Bottom" -> DeckNavigationPlacement.BOTTOM
            "Sidebar" -> DeckNavigationPlacement.SIDE
            else -> base.navigationPlacement
        }
        val chrome = when (player) {
            "Cinema" -> DeckPlayerChrome.THEATER
            "Glass" -> DeckPlayerChrome.GLASS
            "Dashboard" -> DeckPlayerChrome.HUD
            "Minimal" -> DeckPlayerChrome.APPLE_FLOAT
            "Technical" -> DeckPlayerChrome.CYBER
            else -> base.playerChrome
        }
        val animation = when (motion) {
            "Off" -> DeckMotionStyle.INSTANT
            "Snappy" -> DeckMotionStyle.SNAP
            "Smooth" -> DeckMotionStyle.GLIDE
            "Pulse" -> DeckMotionStyle.PULSE
            else -> base.motionStyle
        }
        return base.copy(
            homePresentation = homeMode,
            navigationPlacement = nav,
            playerChrome = chrome,
            motionStyle = animation,
            contentGapDp = when (density) {
                "Compact" -> (base.contentGapDp - 5).coerceAtLeast(4)
                "Airy" -> (base.contentGapDp + 7).coerceAtMost(32)
                else -> base.contentGapDp
            },
            cornerRadiusDp = when (corners) {
                "Sharp" -> 2
                "Soft" -> 12
                "Rounded" -> 26
                else -> base.cornerRadiusDp
            },
            navVisual = when (navigation) {
                "Top" -> DeckNavVisual.COMMAND
                "Sidebar" -> DeckNavVisual.TV_CLASSIC
                "Bottom" -> DeckNavVisual.PILL
                else -> base.navVisual
            }
        )
    }
}

val LocalDeckThemeProfile = compositionLocalOf { DeckThemeCatalog.get("Kodi Arctic") }
