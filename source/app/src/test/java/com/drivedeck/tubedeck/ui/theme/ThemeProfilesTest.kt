package com.drivedeck.tubedeck.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeProfilesTest {
    @Test
    fun allPresetsHaveDifferentLayoutsNavigationAndPlayers() {
        val themes = DeckThemeCatalog.presets
        assertEquals(13, themes.size)
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
