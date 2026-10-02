package com.drivedeck.tubedeck.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackBackCoordinatorTest {
    @Test
    fun firstBackThenTimeoutEntersPip() {
        val coordinator = PlaybackBackCoordinator(900L)
        assertEquals(
            PlaybackBackAction.WAIT_FOR_SECOND_PRESS,
            coordinator.onBack(1_000L, isInPip = false)
        )
        assertEquals(
            PlaybackBackAction.ENTER_PIP,
            coordinator.onSinglePressTimeout(canEnterPip = true)
        )
    }

    @Test
    fun veryFastSecondBackStillMeansPipNotAPlaybackDecision() {
        val coordinator = PlaybackBackCoordinator(900L)
        coordinator.onBack(1_000L, isInPip = false)
        assertEquals(
            PlaybackBackAction.ENTER_PIP,
            coordinator.onBack(1_100L, isInPip = false)
        )
    }

    @Test
    fun backWhileSystemPipOwnsFocusOnlyRequestsExpansion() {
        val coordinator = PlaybackBackCoordinator(900L)
        coordinator.onPipModeChanged(true)
        assertEquals(
            PlaybackBackAction.EXPAND_PIP,
            coordinator.onBack(2_000L, isInPip = true)
        )
    }

    @Test
    fun backAfterPipWasExpandedShowsExplicitChooser() {
        val coordinator = PlaybackBackCoordinator(900L)
        coordinator.onPipModeChanged(true)
        coordinator.onPipModeChanged(false)

        assertEquals(
            PlaybackBackAction.SHOW_EXIT_CHOOSER,
            coordinator.onBack(2_000L, isInPip = false)
        )
    }

    @Test
    fun chooserNeverTurnsLaterBackIntoCloseOrAudio() {
        val coordinator = PlaybackBackCoordinator(900L)
        coordinator.forceChooserVisible()

        assertEquals(
            PlaybackBackAction.IGNORE,
            coordinator.onBack(2_100L, isInPip = false)
        )
        assertEquals(
            PlaybackBackAction.IGNORE,
            coordinator.onBack(60_000L, isInPip = false)
        )
    }

    @Test
    fun explicitPipChoiceCanDismissChooserAndStartFreshCycle() {
        val coordinator = PlaybackBackCoordinator(900L)
        coordinator.forceChooserVisible()
        coordinator.onChooserDismissed()

        assertEquals(
            PlaybackBackAction.WAIT_FOR_SECOND_PRESS,
            coordinator.onBack(5_000L, isInPip = false)
        )
    }
    @Test
    fun fullSlowBackPipReturnCycleAlwaysEndsAtChooser() {
        val coordinator = PlaybackBackCoordinator(900L)

        assertEquals(
            PlaybackBackAction.WAIT_FOR_SECOND_PRESS,
            coordinator.onBack(1_000L, isInPip = false)
        )
        assertEquals(
            PlaybackBackAction.ENTER_PIP,
            coordinator.onSinglePressTimeout(canEnterPip = true)
        )

        coordinator.onPipModeChanged(true)
        coordinator.onPipModeChanged(false)

        assertEquals(
            PlaybackBackAction.SHOW_EXIT_CHOOSER,
            coordinator.onBack(5_000L, isInPip = false)
        )
        assertEquals(
            PlaybackBackAction.IGNORE,
            coordinator.onBack(8_000L, isInPip = false)
        )
    }

}
