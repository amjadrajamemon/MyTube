package com.drivedeck.tubedeck.ui.theme

import com.drivedeck.tubedeck.data.model.ViewingMode
import com.drivedeck.tubedeck.data.model.ViewingModePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Player experience is shared by every Home/profile and must not be gated by theme. */
class ViewingModesAcrossThemesTest {
    @Test fun nineteenThemesProvideConsistentViewingModePolicies() {
        val presets = DeckThemeCatalog.presets
        assertEquals(19, presets.size)
        assertTrue(presets.map { it.playerChrome }.toSet().size >= 13)
        assertEquals(4, ViewingMode.values().size)
        for (profile in presets) {
            assertTrue(profile.name.isNotBlank())
            assertEquals(0f, ViewingModePolicy.videoDim(ViewingMode.NORMAL, 55), .0001f)
            assertEquals(0f, ViewingModePolicy.videoDim(ViewingMode.CINEMA, 55), .0001f)
            assertTrue(ViewingModePolicy.videoDim(ViewingMode.LIGHTS_OUT, 55) > 0f)
            assertTrue(ViewingModePolicy.videoDim(ViewingMode.NIGHT, 55) > 0f)
            for (mode in ViewingMode.values()) {
                assertEquals(0f, ViewingModePolicy.videoDim(mode, 55, inPip = true), .0001f)
            }
        }
    }
}
