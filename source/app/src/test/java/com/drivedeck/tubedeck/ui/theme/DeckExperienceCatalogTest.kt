package com.drivedeck.tubedeck.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage-D regression tests: all thirteen actual themes share one rendering
 * policy without collapsing into identical design signatures.
 */
class DeckExperienceCatalogTest {
    @Test
    fun everyExistingThemeHasAnIndependentVisualLanguage() {
        val profiles = DeckThemeCatalog.presets
        assertEquals(13, profiles.size)
        val styles = profiles.map { DeckExperienceCatalog.tokens(it) }
        assertEquals(13, styles.map { it.backdrop }.toSet().size)
        assertTrue(profiles.all { it.homePresentation in DeckHomePresentation.entries })
        assertTrue(styles.all { it.focusBorderDp >= 1.5f })
        assertTrue(styles.all { it.focusScale in 1f..1.04f })
        assertTrue(styles.all { it.hoverScale in 1f..1.02f })
    }

    @Test
    fun reducedMotionAlwaysWinsOverAnimatedThemes() {
        for (profile in DeckThemeCatalog.presets) {
            val plain = DeckExperienceCatalog.tokens(profile, reducedMotion = true)
            assertEquals(0, plain.motionDurationMs)
            assertFalse(plain.animated)
            assertEquals(1f, plain.focusScale, 0.0001f)
            assertEquals(1f, plain.hoverScale, 0.0001f)
        }
    }

    @Test
    fun lowResourceDevicesDoNotDecodeAtmosphereImagesOrAnimateFocus() {
        for (profile in DeckThemeCatalog.presets) {
            val lite = DeckExperienceCatalog.tokens(profile, lowResource = true)
            assertFalse(lite.artworkAtmosphere)
            assertFalse(lite.animated)
            assertEquals(0, lite.motionDurationMs)
            assertEquals(1f, lite.focusScale, 0.0001f)
        }
    }

    @Test
    fun highResourceCinematicThemesCanUseArtworkAtmospheres() {
        for (name in listOf("ATV", "Plex Cinema", "Kodi Arctic", "Apple TV Inspired", "SmartTube Pro")) {
            assertTrue(DeckExperienceCatalog.tokens(DeckThemeCatalog.get(name)).artworkAtmosphere)
        }
        assertFalse(DeckExperienceCatalog.tokens(DeckThemeCatalog.get("VLC Classic")).artworkAtmosphere)
        assertFalse(DeckExperienceCatalog.tokens(DeckThemeCatalog.get("Linux Desktop")).artworkAtmosphere)
    }

    @Test
    fun selectionDurationsAreBoundedAndStaticStylesStayStatic() {
        val duration = DeckThemeCatalog.presets.map { DeckExperienceCatalog.tokens(it).motionDurationMs }
        assertTrue(duration.all { it in 0..300 })
        assertEquals(0, DeckExperienceCatalog.tokens(DeckThemeCatalog.get("VLC Classic")).motionDurationMs)
    }
}
