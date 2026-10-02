package com.drivedeck.tubedeck.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackBackCoordinatorTest {
    @Test
    fun pipEntryDelayIsShorterThanSecondBackWindow() {
        assertEquals(180L, PlaybackBackCoordinator.PIP_ENTRY_DELAY_MS)
        assertEquals(900L, PlaybackBackCoordinator.DEFAULT_WINDOW_MS)
        assertEquals(800L, PlaybackBackCoordinator.CHOOSER_SETTLE_MS)
    }

    @Test
    fun singleBackThenTimeoutEntersPip() {
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
    fun naturalSecondBackWithinNineHundredMsShowsChooser() {
        val coordinator = PlaybackBackCoordinator(900L)
        coordinator.onBack(1_000L, isInPip = false)
        assertEquals(
            PlaybackBackAction.SHOW_EXIT_CHOOSER,
            coordinator.onBack(1_700L, isInPip = false)
        )
    }

    @Test
    fun nextBackAfterBackTriggeredPipShowsChooser() {
        val coordinator = PlaybackBackCoordinator(900L)
        coordinator.onBack(1_000L, isInPip = false)
        coordinator.onSinglePressTimeout(canEnterPip = true)

        assertEquals(
            PlaybackBackAction.SHOW_EXIT_CHOOSER,
            coordinator.onBack(2_000L, isInPip = false)
        )
    }

    @Test
    fun backWhileActuallyInPipRequestsForegroundExpansion() {
        val coordinator = PlaybackBackCoordinator(900L)

        assertEquals(
            PlaybackBackAction.EXPAND_PIP_FOR_EXIT_CHOOSER,
            coordinator.onBack(2_000L, isInPip = true)
        )
    }

    @Test
    fun expandedManualPipMakesNextBackShowChooser() {
        val coordinator = PlaybackBackCoordinator(900L)
        coordinator.onPipModeChanged(true)
        coordinator.onPipModeChanged(false)

        assertEquals(
            PlaybackBackAction.SHOW_EXIT_CHOOSER,
            coordinator.onBack(2_000L, isInPip = false)
        )
    }

    @Test
    fun chooserIgnoresImmediateReplayThenLaterBackExits() {
        val coordinator = PlaybackBackCoordinator(900L)
        coordinator.onBack(1_000L, isInPip = false)
        coordinator.onBack(1_200L, isInPip = false)

        assertEquals(
            PlaybackBackAction.IGNORE,
            coordinator.onBack(1_300L, isInPip = false)
        )
        assertEquals(
            PlaybackBackAction.EXIT_APP,
            coordinator.onBack(2_100L, isInPip = false)
        )
    }

    @Test
    fun forcedChooserFromPipHasTheSameSettleGuard() {
        val coordinator = PlaybackBackCoordinator(900L)
        coordinator.forceChooserVisible(2_000L)

        assertEquals(
            PlaybackBackAction.IGNORE,
            coordinator.onBack(2_100L, isInPip = false)
        )
        assertEquals(
            PlaybackBackAction.EXIT_APP,
            coordinator.onBack(2_900L, isInPip = false)
        )
    }
}
