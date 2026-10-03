package com.drivedeck.tubedeck.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackBackSequenceTrackerTest {
    @Test
    fun threeRapidPressesBecomeOneTripleGesture() {
        PlaybackBackSequenceTracker.reset()
        try {
            assertEquals(1, PlaybackBackSequenceTracker.record(1_000L))
            assertEquals(2, PlaybackBackSequenceTracker.record(1_350L))
            assertEquals(3, PlaybackBackSequenceTracker.record(1_700L))
        } finally {
            PlaybackBackSequenceTracker.reset()
        }
    }

    @Test
    fun slowPressStartsANewSequence() {
        PlaybackBackSequenceTracker.reset()
        try {
            assertEquals(1, PlaybackBackSequenceTracker.record(1_000L))
            assertEquals(1, PlaybackBackSequenceTracker.record(2_000L))
            assertEquals(1, PlaybackBackSequenceTracker.record(3_000L))
        } finally {
            PlaybackBackSequenceTracker.reset()
        }
    }

    @Test
    fun resetClearsRapidSequence() {
        PlaybackBackSequenceTracker.reset()
        assertEquals(1, PlaybackBackSequenceTracker.record(10_000L))
        assertEquals(2, PlaybackBackSequenceTracker.record(10_250L))
        PlaybackBackSequenceTracker.reset()
        assertEquals(1, PlaybackBackSequenceTracker.record(10_300L))
        PlaybackBackSequenceTracker.reset()
    }
}
