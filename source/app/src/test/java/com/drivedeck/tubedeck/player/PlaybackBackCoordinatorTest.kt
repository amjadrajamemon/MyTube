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
    fun veryFastSecondPhysicalBackBeforePipEntryStillCompletesPipEntry() {
        val coordinator = PlaybackBackCoordinator(900L)
        coordinator.onBack(1_000L, isInPip = false)
        assertEquals(
            PlaybackBackAction.ENTER_PIP,
            coordinator.onBack(1_100L, isInPip = false)
        )
    }

    @Test
    fun secondPlaybackBackWhileInPipExpandsWithChooserAlreadyArmed() {
        val coordinator = PlaybackBackCoordinator(900L)
        coordinator.onBack(1_000L, isInPip = false)
        coordinator.onSinglePressTimeout(canEnterPip = true)
        coordinator.onPipModeChanged(true)

        assertEquals(
            PlaybackBackAction.EXPAND_PIP,
            coordinator.onBack(2_000L, isInPip = true)
        )

        coordinator.onPipModeChanged(false)

        // The second Back already owns the decision stage. A third Back must never be required
        // and must never silently select Close or Audio.
        assertEquals(
            PlaybackBackAction.IGNORE,
            coordinator.onBack(5_000L, isInPip = false)
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
    fun explicitPipChoiceDismissesChooserAndStartsFreshBackCycle() {
        val coordinator = PlaybackBackCoordinator(900L)
        coordinator.forceChooserVisible()
        coordinator.onChooserDismissed()

        assertEquals(
            PlaybackBackAction.WAIT_FOR_SECOND_PRESS,
            coordinator.onBack(5_000L, isInPip = false)
        )
    }

    @Test
    fun pipUnavailableFallsBackDirectlyToChooser() {
        val coordinator = PlaybackBackCoordinator(900L)
        coordinator.onBack(1_000L, isInPip = false)
        assertEquals(
            PlaybackBackAction.SHOW_EXIT_CHOOSER,
            coordinator.onSinglePressTimeout(canEnterPip = false)
        )
        assertEquals(
            PlaybackBackAction.IGNORE,
            coordinator.onBack(3_000L, isInPip = false)
        )
    }
}
