package com.drivedeck.tubedeck.player

import org.junit.Assert.*
import org.junit.Test

class AdaptiveQualityPolicyTest {
    private val available = listOf(144, 240, 360, 480, 720, 1080)

    @Test fun degradesOnConsecutiveBufferStarvationEvenIfBandwidthEstimateIsStale() {
        val next = AdaptiveQualityPolicy.decide(
            available, 720, 720, 1_000, true, 2, 0, 0, false, 6_000
        )
        assertEquals(480, next?.targetHeight)
        assertFalse(next!!.upgrade)
    }

    @Test fun constrainedCarUnitStillDownshiftsWhenUpgradeIsProhibited() {
        val decision = AdaptiveQualityPolicy.decide(
            available, 480, 240, 2_000, false, 2, 0, 0, false, 9_000
        )
        assertEquals(240, decision?.targetHeight)
    }

    @Test fun stallsDoNotCauseUpgradeOscillation() {
        assertNull(AdaptiveQualityPolicy.decide(
            available, 720, 1080, 31_000, false, 0, 5, 0, true, 30_000))
        assertEquals(1080, AdaptiveQualityPolicy.decide(
            available, 720, 1080, 32_000, false, 0, 6, 0, true, 30_000
        )?.targetHeight)
        assertNull(AdaptiveQualityPolicy.decide(
            available, 720, 1080, 32_000, false, 0, 6, 1, true, 30_000))
    }

    @Test fun cooldownAndLowestAvailableGuardAgainstRepeatedRepreparation() {
        assertNull(AdaptiveQualityPolicy.decide(
            available, 480, 240, 1000, true, 4, 0, 0, true, 1000))
        assertNull(AdaptiveQualityPolicy.decide(
            available, 144, 144, 500, true, 4, 0, 0, true, 20000))
    }

    @Test fun explicitlySelectedQualityIsNotChangedByNetworkRecovery() {
        assertEquals(1080, AdaptiveQualityPolicy.retryHeight(available, 1080, false))
        assertEquals(720, AdaptiveQualityPolicy.retryHeight(available, 1080, true))
    }
}
