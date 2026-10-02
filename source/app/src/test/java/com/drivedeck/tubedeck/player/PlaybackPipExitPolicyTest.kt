package com.drivedeck.tubedeck.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPipExitPolicyTest {
    @Test
    fun realPipExitWithPendingPlaybackChoiceRestoresChooser() {
        assertTrue(
            PlaybackPipExitPolicy.shouldRestoreChoices(
                wasInPip = true,
                isInPip = false,
                choicePending = true,
                playerActive = true,
                closing = false
            )
        )
    }

    @Test
    fun unrelatedLifecycleChangeNeverRestoresChooser() {
        assertFalse(
            PlaybackPipExitPolicy.shouldRestoreChoices(
                wasInPip = false,
                isInPip = false,
                choicePending = true,
                playerActive = true,
                closing = false
            )
        )
    }

    @Test
    fun closingOrInactivePlaybackNeverRestoresChooser() {
        assertFalse(
            PlaybackPipExitPolicy.shouldRestoreChoices(
                wasInPip = true,
                isInPip = false,
                choicePending = true,
                playerActive = false,
                closing = false
            )
        )
        assertFalse(
            PlaybackPipExitPolicy.shouldRestoreChoices(
                wasInPip = true,
                isInPip = false,
                choicePending = true,
                playerActive = true,
                closing = true
            )
        )
    }
}
