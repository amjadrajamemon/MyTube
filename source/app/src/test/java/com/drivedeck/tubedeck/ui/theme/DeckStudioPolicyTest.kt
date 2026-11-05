package com.drivedeck.tubedeck.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckStudioPolicyTest {
    @Test fun premiumPackHasSixUniqueHomePresentationArchitectures() {
        val names = listOf(
            "NeoGlass Luxe", "Cinema Deck", "CommandDrive Auto",
            "StreamPlus Luxe TV", "Midnight Editorial Pro", "Cyber Console X"
        )
        val profiles = names.map(DeckThemeCatalog::get)
        assertEquals(names, profiles.map { it.name })
        assertEquals(names.size, profiles.map { it.homePresentation }.toSet().size)
        assertTrue(profiles.all { it.subtitle.isNotBlank() })
    }

    @Test fun allStudioOverridesChangeStructureAndKeepOriginalPresetImmutable() {
        val base = DeckThemeCatalog.get("NeoGlass Luxe")
        val changed = DeckStudioPolicy.resolve(
            base, "Cyber HUD", "Sidebar", "Technical", "Snappy", "Airy", "Sharp"
        )
        assertEquals(DeckHomePresentation.CYBER_COMMAND, changed.homePresentation)
        assertEquals(DeckNavigationPlacement.SIDE, changed.navigationPlacement)
        assertEquals(DeckPlayerChrome.CYBER, changed.playerChrome)
        assertEquals(DeckMotionStyle.SNAP, changed.motionStyle)
        assertEquals(2, changed.cornerRadiusDp)
        assertTrue(changed.contentGapDp > base.contentGapDp)
        assertNotEquals(changed.homePresentation, base.homePresentation)
        assertEquals(DeckHomePresentation.NEO_GLASS_LOUNGE, base.homePresentation)
    }

    @Test fun resetToPresetsRetainsEveryStructuralValue() {
        for (base in DeckThemeCatalog.presets) {
            assertEquals(base, DeckStudioPolicy.resolve(
                base, "Preset", "Preset", "Preset", "Preset", "Preset", "Preset"
            ))
        }
    }
}
