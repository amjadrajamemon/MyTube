package com.drivedeck.tubedeck.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackBackCoordinatorTest {
    @Test
    fun firstBackEntersPipImmediately() {
        val coordinator = PlaybackBackCoordinator()
        assertEquals(
            PlaybackBackAction.ENTER_PIP,
            coordinator.onBack(1_000L, isInPip = false)
        )
    }

    @Test
    fun backWhileInPipExpandsExistingPlayer() {
        val coordinator = PlaybackBackCoordinator()
        coordinator.onBack(1_000L, isInPip = false)
        coordinator.onPipModeChanged(true)

        assertEquals(
            PlaybackBackAction.EXPAND_PIP,
            coordinator.onBack(2_000L, isInPip = true)
        )
    }

    @Test
    fun expandedPipTransitionsToExplicitChooser() {
        val coordinator = PlaybackBackCoordinator()
        coordinator.onBack(1_000L, isInPip = false)
        coordinator.onPipModeChanged(true)
        coordinator.onBack(2_000L, isInPip = true)
        coordinator.onPipModeChanged(false)

        assertEquals(
            PlaybackBackAction.SHOW_EXIT_CHOOSER,
            coordinator.onBack(2_100L, isInPip = false)
        )
        assertEquals(
            PlaybackBackAction.CLOSE_PLAYBACK,
            coordinator.onBack(60_000L, isInPip = false)
        )
    }

    @Test
    fun backWhileChooserVisibleClosesPlaybackDeterministically() {
        val coordinator = PlaybackBackCoordinator()
        coordinator.forceChooserVisible()

        assertEquals(
            PlaybackBackAction.CLOSE_PLAYBACK,
            coordinator.onBack(2_100L, isInPip = false)
        )
    }

    @Test
    fun explicitPipChoiceDismissesChooserAndStartsFreshCycle() {
        val coordinator = PlaybackBackCoordinator()
        coordinator.forceChooserVisible()
        coordinator.onChooserDismissed()

        assertEquals(
            PlaybackBackAction.ENTER_PIP,
            coordinator.onBack(5_000L, isInPip = false)
        )
    }

    @Test
    fun pipUnavailableFallsBackDirectlyToChooser() {
        val coordinator = PlaybackBackCoordinator()
        assertEquals(
            PlaybackBackAction.SHOW_EXIT_CHOOSER,
            coordinator.onSinglePressTimeout(canEnterPip = false)
        )
        assertEquals(
            PlaybackBackAction.CLOSE_PLAYBACK,
            coordinator.onBack(3_000L, isInPip = false)
        )
    }
}
