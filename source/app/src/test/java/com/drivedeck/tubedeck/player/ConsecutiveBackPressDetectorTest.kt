package com.drivedeck.tubedeck.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsecutiveBackPressDetectorTest {
    @Test
    fun secondPressInsideWindowIsDetected() {
        val detector = ConsecutiveBackPressDetector(500L)
        assertFalse(detector.registerPress(1_000L))
        assertTrue(detector.registerPress(1_499L))
    }

    @Test
    fun pressOutsideWindowStartsANewSequence() {
        val detector = ConsecutiveBackPressDetector(500L)
        assertFalse(detector.registerPress(1_000L))
        assertFalse(detector.registerPress(1_501L))
        assertTrue(detector.registerPress(1_900L))
    }

    @Test
    fun clearCancelsPendingSequence() {
        val detector = ConsecutiveBackPressDetector(500L)
        assertFalse(detector.registerPress(1_000L))
        detector.clear()
        assertFalse(detector.registerPress(1_100L))
    }
}
