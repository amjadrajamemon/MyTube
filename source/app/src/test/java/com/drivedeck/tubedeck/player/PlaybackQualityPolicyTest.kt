package com.drivedeck.tubedeck.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackQualityPolicyTest {
    @Test fun missingLowQualityNeverPromotesToHighestStream() {
        assertEquals(720, PlaybackQualityPolicy.chooseHeight(listOf(2160, 1080, 720), 480, true))
    }
    @Test fun compatibilityCapsEvenSavedHighQualityPreferences() {
        assertEquals(360, PlaybackQualityPolicy.chooseHeight(listOf(360, 720, 1080), 1080, true))
    }
    @Test fun regularPlaybackKeepsRequestedQuality() {
        assertEquals(1080, PlaybackQualityPolicy.chooseHeight(listOf(360, 720, 1080), 1080, false))
    }
    @Test fun unsupportedQualitySelectsNearestLowerAvailable() {
        assertEquals(480, PlaybackQualityPolicy.chooseHeight(listOf(360, 480, 1080), 720, false))
    }
    @Test fun emptyOrInvalidRepresentationsHaveNoSelection() {
        assertNull(PlaybackQualityPolicy.chooseHeight(listOf(0, -1), 480, true))
    }
}
