package com.drivedeck.tubedeck.player

import org.junit.Assert.*
import org.junit.Test

class AdaptiveQualityPolicyTest {
    private val available = listOf(144, 240, 360, 480, 720, 1080)

    @Test fun repeatedBufferingDropsEvenWhenBandwidthMeterStillReportsHigh() {
        val first = AdaptiveQualityPolicy.decide(
            available, 1080, 1080, 0, true, 2, 0, 0, false, 4_000
        )
        assertEquals(720, first?.targetHeight)
        assertFalse(first!!.upgrade)
        val second = AdaptiveQualityPolicy.decide(
            available, 720, 720, 100, true, 2, 0, 0, false, 4_000,
            downshiftedBefore = true
        )
        assertEquals(480, second?.targetHeight)
    }

    @Test fun measuredWeakBandwidthSkipsSeveralLevels() {
        val d = AdaptiveQualityPolicy.decide(
            available, 1080, 240, 2_000, false, 2, 0, 0, false, 4_000
        )
        assertEquals(240, d?.targetHeight)
    }

    @Test fun carRespectsActualAvailableLowerStreams() {
        val realOptions = listOf(240, 480)
        assertEquals(240, AdaptiveQualityPolicy.decide(
            realOptions, 480, 480, 1000, true, 2, 0, 0, false, 5_000
        )?.targetHeight)
        assertNull(AdaptiveQualityPolicy.decide(
            realOptions, 240, 240, 1000, true, 2, 0, 0, false, 5_000
        ))
    }

    @Test fun doNotReboundHighAfterAStallDrivenDownshift() {
        assertNull(AdaptiveQualityPolicy.decide(
            available, 480, 1080, 55_000, false, 0, 10, 0, true, 70_000,
            downshiftedBefore = true
        ))
        assertEquals(1080, AdaptiveQualityPolicy.decide(
            available, 720, 1080, 40_000, false, 0, 8, 0, true, 70_000,
            downshiftedBefore = false
        )?.targetHeight)
    }

    @Test fun cooldownAndLowestQualityGuardAgainstReprepareLoops() {
        assertNull(AdaptiveQualityPolicy.decide(
            available, 480, 240, 0, true, 3, 0, 0, true, 1_000
        ))
        assertNull(AdaptiveQualityPolicy.decide(
            available, 144, 144, 0, true, 5, 0, 0, true, 10_000
        ))
    }

    @Test fun manualQualityIsNeverReducedAutomaticallyByErrorFallback() {
        assertEquals(1080, AdaptiveQualityPolicy.retryHeight(available, 1080, false))
        assertEquals(720, AdaptiveQualityPolicy.retryHeight(available, 1080, true))
    }
}
