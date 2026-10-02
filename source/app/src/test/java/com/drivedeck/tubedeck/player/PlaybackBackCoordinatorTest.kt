package com.drivedeck.tubedeck.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackBackCoordinatorTest {
    @Test
    fun singleBackThenTimeoutEntersPip() {
        val coordinator = PlaybackBackCoordinator(500L)
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
    fun rapidDoubleBackShowsChooser() {
        val coordinator = PlaybackBackCoordinator(500L)
        coordinator.onBack(1_000L, isInPip = false)
        assertEquals(
            PlaybackBackAction.SHOW_EXIT_CHOOSER,
            coordinator.onBack(1_400L, isInPip = false)
        )
    }

    @Test
    fun nextBackAfterBackTriggeredPipShowsChooser() {
        val coordinator = PlaybackBackCoordinator(500L)
        coordinator.onBack(1_000L, isInPip = false)
        coordinator.onSinglePressTimeout(canEnterPip = true)

        assertEquals(
            PlaybackBackAction.SHOW_EXIT_CHOOSER,
            coordinator.onBack(2_000L, isInPip = false)
        )
    }

    @Test
    fun expandedManualPipMakesNextBackShowChooser() {
        val coordinator = PlaybackBackCoordinator(500L)
        coordinator.onPipModeChanged(true)
        coordinator.onPipModeChanged(false)

        assertEquals(
            PlaybackBackAction.SHOW_EXIT_CHOOSER,
            coordinator.onBack(2_000L, isInPip = false)
        )
    }

    @Test
    fun backWhileChooserVisibleExits() {
        val coordinator = PlaybackBackCoordinator(500L)
        coordinator.onBack(1_000L, isInPip = false)
        coordinator.onBack(1_200L, isInPip = false)

        assertEquals(
            PlaybackBackAction.EXIT_APP,
            coordinator.onBack(1_300L, isInPip = false)
        )
    }
}
