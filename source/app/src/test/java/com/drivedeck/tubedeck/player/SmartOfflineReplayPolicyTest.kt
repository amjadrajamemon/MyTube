package com.drivedeck.tubedeck.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartOfflineReplayPolicyTest {
    private fun complete(watch: SmartOfflineReplayWatch, duration: Long = 20_000L): Boolean {
        for (position in 0L..duration step 250L) watch.sample(position, duration)
        return watch.finish(duration, duration)
    }

    @Test fun completeWatchOnceOnly() {
        val watch = SmartOfflineReplayWatch()
        assertTrue(complete(watch))
        assertFalse(watch.finish(20_000L, 20_000L))
    }

    @Test fun resumeAndSeekingCannotTriggerAutomaticSaving() {
        val resumed = SmartOfflineReplayWatch()
        for (point in 10_000L..20_000L step 250L) resumed.sample(point, 20_000L)
        assertFalse(resumed.finish(20_000L, 20_000L))
        val skipped = SmartOfflineReplayWatch()
        for (point in 0L..4_000L step 250L) skipped.sample(point, 20_000L)
        skipped.onSeekOrSkip()
        for (point in 4_000L..20_000L step 250L) skipped.sample(point, 20_000L)
        assertFalse(skipped.finish(20_000L, 20_000L))
    }

    @Test fun manuallyPausedWatchNotEligible() {
        val watch = SmartOfflineReplayWatch()
        for (point in 0L..5_000L step 250L) watch.sample(point, 20_000L)
        watch.onManualPause()
        for (point in 5_000L..20_000L step 250L) watch.sample(point, 20_000L)
        assertFalse(watch.finish(20_000L, 20_000L))
    }

    @Test fun suddenTimelineGapFailsEvenWithoutSeekCallback() {
        val watch = SmartOfflineReplayWatch()
        watch.sample(0L, 20_000L)
        watch.sample(200L, 20_000L)
        watch.sample(19_000L, 20_000L)
        assertFalse(watch.finish(20_000L, 20_000L))
    }

    @Test fun bufferingWhileStillRequestedDoesNotInvalidate() {
        val watch = SmartOfflineReplayWatch()
        for (point in 0L..5_000L step 250L) watch.sample(point, 20_000L)
        // Buffering produces no new position and is not a user interruption.
        repeat(40) { watch.sample(5_000L, 20_000L) }
        for (point in 5_250L..20_000L step 250L) watch.sample(point, 20_000L)
        assertTrue(watch.finish(20_000L, 20_000L))
    }

    @Test fun queueGuardPreventsDuplicatesAndRespectsNetwork() {
        assertTrue(SmartOfflineReplayPolicy.eligibleForAutomaticSave(
            true, true, "video", false, false, true, true
        ))
        assertFalse(SmartOfflineReplayPolicy.eligibleForAutomaticSave(
            true, true, "video", true, false, true, true
        ))
        assertFalse(SmartOfflineReplayPolicy.eligibleForAutomaticSave(
            true, true, "video", false, true, true, true
        ))
        assertFalse(SmartOfflineReplayPolicy.eligibleForAutomaticSave(
            true, true, "video", false, false, false, true
        ))
        assertFalse(SmartOfflineReplayPolicy.eligibleForAutomaticSave(
            false, true, "video", false, false, true, true
        ))
    }
}
