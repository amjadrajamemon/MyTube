package com.drivedeck.tubedeck.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackResourcePolicyTest {
    @Test fun smallHeapUses480pAndSmallBuffers() {
        val policy = PlaybackResourcePolicy.choose(lowRam = true, memoryClassMb = 128, cpuCores = 2, carCompatibility = false)
        assertTrue(policy.constrained)
        assertEquals(480, policy.maxAutoVideoHeight)
        assertEquals(8 * 1024 * 1024, policy.targetBufferBytes)
        assertEquals(0, policy.relatedPrefetchCount)
        assertFalse(policy.allowAdaptiveUpgrade)
    }

    @Test fun weakCpuUses720pWhileKeepingManualQualityAvailable() {
        val policy = PlaybackResourcePolicy.choose(lowRam = false, memoryClassMb = 256, cpuCores = 4, carCompatibility = false)
        assertTrue(policy.constrained)
        assertEquals(720, policy.maxAutoVideoHeight)
        assertEquals(1, policy.relatedPrefetchCount)
    }

    @Test fun carModeKeepsConservativeDefaults() {
        val policy = PlaybackResourcePolicy.choose(lowRam = false, memoryClassMb = 512, cpuCores = 8, carCompatibility = true)
        assertEquals(480, policy.maxAutoVideoHeight)
        assertTrue(policy.constrained)
        assertFalse(policy.allowAdaptiveUpgrade)
    }

    @Test fun strongerDeviceRetainsQualityAndBufferOptions() {
        val policy = PlaybackResourcePolicy.choose(lowRam = false, memoryClassMb = 512, cpuCores = 8, carCompatibility = false)
        assertFalse(policy.constrained)
        assertEquals(Int.MAX_VALUE, policy.maxAutoVideoHeight)
        assertTrue(policy.allowAdaptiveUpgrade)
        assertEquals(-1, policy.targetBufferBytes)
    }
}
