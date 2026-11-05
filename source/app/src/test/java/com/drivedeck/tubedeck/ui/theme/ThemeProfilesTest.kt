package com.drivedeck.tubedeck.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeProfilesTest {
    @Test
    fun allPresetsHaveDifferentLayoutsNavigationAndPlayers() {
        val themes = DeckThemeCatalog.presets
        assertEquals(19, themes.size)
        val signatures = themes.map { theme ->
            listOf(
                theme.homePresentation.name,
                theme.navigationPlacement.name,
                theme.navVisual.name,
                theme.playerChrome.name,
                theme.geometry.name,
                theme.motionStyle.name
            ).joinToString("/")
        }
        assertEquals("Each theme must have its own structure, controls and player signature", themes.size, signatures.toSet().size)
        assertTrue("Premium layouts reuse tested player engines but keep distinct Home signatures",
            themes.map { it.playerChrome }.toSet().size >= 13)
        assertTrue(themes.all { it.subtitle.length < 140 })

    }

    @Test
    fun knownReferenceExperiencesHaveDistinctPlayerChrome() {
        val required = listOf(
            "SmartTube Pro", "YouTube Standard", "YouTube Desktop",
            "Plex Cinema", "Kodi Arctic", "MX Player Pro", "VLC Classic",
            "Android Material 3", "Apple TV Inspired", "Xtreamity IPTV",
            "Linux Desktop", "Cyberpunk Digital", "ATV"
        )
        val themes = required.map { DeckThemeCatalog.get(it) }
        assertEquals(required, themes.map { it.name })
        assertEquals(required.size, themes.map { it.playerChrome }.toSet().size)
        assertEquals(required.size, themes.map { it.homePresentation }.toSet().size)
    }

    @Test
    fun oldPresetNamesResolveToTheirReplacementLayouts() {
        val examples = mapOf(
            "Glass Midnight" to "Kodi Arctic",
            "Cinema" to "Plex Cinema",
            "Retro Neon" to "MX Player Pro",
            "Android" to "Android Material 3",
            "Digital HUD" to "Cyberpunk Digital",
            "SmartTube TV" to "SmartTube Pro"
        )
        examples.forEach { (oldName, newName) ->
            assertEquals(newName, DeckThemeCatalog.get(oldName).name)
        }
    }

    @Test
    fun coverMultipleNavigationHomeAndPlayerArchitectures() {
        val themes = DeckThemeCatalog.presets
        assertEquals(DeckNavigationPlacement.entries.toSet(),
            themes.map { it.navigationPlacement }.toSet())
        assertEquals(DeckHomePresentation.entries.toSet(),
            themes.map { it.homePresentation }.toSet())
        assertEquals(DeckNavVisual.entries.toSet(),
            themes.map { it.navVisual }.toSet())
        assertTrue(themes.map { it.playerChrome }.toSet().size >= 5)
        assertEquals(DeckMotionStyle.entries.toSet(),
            themes.map { it.motionStyle }.toSet())
        assertTrue(themes.all { it.cornerRadiusDp >= 0 && it.contentGapDp > 0 })
    }
}
