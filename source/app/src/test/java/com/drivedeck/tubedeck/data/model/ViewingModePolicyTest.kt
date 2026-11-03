package com.drivedeck.tubedeck.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewingModePolicyTest {
    @Test fun modesAreDistinctAndNoDimmingLeaksIntoPip() {
        assertEquals(4, ViewingMode.values().size)
        for (mode in ViewingMode.values()) {
            assertEquals(0f, ViewingModePolicy.videoDim(mode, 55, inPip = true), .001f)
        }
    }

    @Test fun nightAndLightsOutClampTheirIntensityAndLeaveCinemaClear() {
        assertEquals(0f, ViewingModePolicy.videoDim(ViewingMode.NORMAL, 65), .001f)
        assertEquals(0f, ViewingModePolicy.videoDim(ViewingMode.CINEMA, 65), .001f)
        val lightsOut = ViewingModePolicy.videoDim(ViewingMode.LIGHTS_OUT, 65)
        val night = ViewingModePolicy.videoDim(ViewingMode.NIGHT, 65)
        assertTrue(lightsOut > night)
        assertTrue(night > 0f)
        assertEquals(ViewingModePolicy.videoDim(ViewingMode.NIGHT, 85),
            ViewingModePolicy.videoDim(ViewingMode.NIGHT, 200), .001f)
        assertEquals(ViewingModePolicy.videoDim(ViewingMode.LIGHTS_OUT, 10),
            ViewingModePolicy.videoDim(ViewingMode.LIGHTS_OUT, -2), .001f)
    }
}
