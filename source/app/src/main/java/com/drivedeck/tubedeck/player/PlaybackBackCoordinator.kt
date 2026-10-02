package com.drivedeck.tubedeck.player

internal enum class PlaybackBackAction {
    WAIT_FOR_SECOND_PRESS,
    ENTER_PIP,
    EXPAND_PIP,
    SHOW_EXIT_CHOOSER,
    IGNORE
}

/**
 * Deterministic playback Back behavior shared by both player hosts.
 *
 * Back never silently chooses a terminal playback mode. The user explicitly selects Close,
 * Audio-only or PiP from the chooser.
 */
internal class PlaybackBackCoordinator(
    private val consecutiveWindowMs: Long = DEFAULT_WINDOW_MS
) {
    companion object {
        const val DEFAULT_WINDOW_MS: Long = ConsecutiveBackPressDetector.DEFAULT_WINDOW_MS
        const val PIP_ENTRY_DELAY_MS: Long = 180L
    }

    private val detector = ConsecutiveBackPressDetector(consecutiveWindowMs)
    private var chooserVisible = false
    private var pipEnteredFromBack = false
    private var wasInPip = false
    private var expandedFromPip = false

    fun onBack(nowMs: Long, isInPip: Boolean): PlaybackBackAction {
        if (chooserVisible) return PlaybackBackAction.IGNORE

        if (isInPip) {
            detector.clear()
            return PlaybackBackAction.EXPAND_PIP
        }

        if (expandedFromPip || pipEnteredFromBack) {
            detector.clear()
            pipEnteredFromBack = false
            expandedFromPip = false
            chooserVisible = true
            return PlaybackBackAction.SHOW_EXIT_CHOOSER
        }

        return if (detector.registerPress(nowMs)) {
            detector.clear()
            pipEnteredFromBack = true
            PlaybackBackAction.ENTER_PIP
        } else {
            PlaybackBackAction.WAIT_FOR_SECOND_PRESS
        }
    }

    fun onSinglePressTimeout(canEnterPip: Boolean): PlaybackBackAction {
        detector.clear()
        return if (canEnterPip) {
            pipEnteredFromBack = true
            PlaybackBackAction.ENTER_PIP
        } else {
            chooserVisible = true
            PlaybackBackAction.SHOW_EXIT_CHOOSER
        }
    }

    fun forceChooserVisible() {
        detector.clear()
        pipEnteredFromBack = false
        expandedFromPip = false
        chooserVisible = true
    }

    fun onPipModeChanged(isInPip: Boolean) {
        if (wasInPip && !isInPip) expandedFromPip = true
        wasInPip = isInPip
    }

    fun onChooserDismissed() {
        chooserVisible = false
        pipEnteredFromBack = false
        expandedFromPip = false
        detector.clear()
    }

    fun onPipEnterFailed() {
        pipEnteredFromBack = false
        expandedFromPip = false
        chooserVisible = true
        detector.clear()
    }

    fun onNewMedia() = reset()

    fun reset() {
        detector.clear()
        chooserVisible = false
        pipEnteredFromBack = false
        wasInPip = false
        expandedFromPip = false
    }
}
