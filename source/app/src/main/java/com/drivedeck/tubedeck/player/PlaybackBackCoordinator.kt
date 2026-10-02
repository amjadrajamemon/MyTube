package com.drivedeck.tubedeck.player

internal enum class PlaybackBackAction {
    WAIT_FOR_SECOND_PRESS,
    ENTER_PIP,
    SHOW_EXIT_CHOOSER,
    EXIT_APP
}

/**
 * Single state machine for playback Back behavior used by both MainActivity and NativePlayerActivity.
 *
 * Android does not guarantee Back delivery while the system PiP window itself owns focus. Therefore
 * the state machine remembers a Back-triggered PiP transition and, once that PiP is expanded again,
 * the next Back opens Close / Audio only instead of entering PiP again.
 */
internal class PlaybackBackCoordinator(
    private val doublePressWindowMs: Long = DEFAULT_WINDOW_MS
) {
    companion object {
        const val DEFAULT_WINDOW_MS: Long = ConsecutiveBackPressDetector.DEFAULT_WINDOW_MS
    }
    private val detector = ConsecutiveBackPressDetector(doublePressWindowMs)
    private var chooserVisible = false
    private var pipEnteredFromBack = false
    private var wasInPip = false
    private var expandedFromPip = false

    fun onBack(nowMs: Long, isInPip: Boolean): PlaybackBackAction {
        if (chooserVisible) {
            reset()
            return PlaybackBackAction.EXIT_APP
        }

        if (isInPip || pipEnteredFromBack || expandedFromPip) {
            detector.clear()
            pipEnteredFromBack = false
            expandedFromPip = false
            chooserVisible = true
            return PlaybackBackAction.SHOW_EXIT_CHOOSER
        }

        return if (detector.registerPress(nowMs)) {
            detector.clear()
            chooserVisible = true
            PlaybackBackAction.SHOW_EXIT_CHOOSER
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

    fun onPipModeChanged(isInPip: Boolean) {
        if (wasInPip && !isInPip) {
            expandedFromPip = true
        }
        wasInPip = isInPip
    }

    fun onChooserDismissed() {
        chooserVisible = false
        detector.clear()
    }

    fun onPipEnterFailed() {
        pipEnteredFromBack = false
        expandedFromPip = false
        chooserVisible = true
        detector.clear()
    }

    fun onNewMedia() {
        reset()
    }

    fun reset() {
        detector.clear()
        chooserVisible = false
        pipEnteredFromBack = false
        wasInPip = false
        expandedFromPip = false
    }
}
