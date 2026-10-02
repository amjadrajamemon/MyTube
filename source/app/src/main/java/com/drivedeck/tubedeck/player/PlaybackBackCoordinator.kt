package com.drivedeck.tubedeck.player

internal enum class PlaybackBackAction {
    WAIT_FOR_SECOND_PRESS,
    ENTER_PIP,
    EXPAND_PIP_FOR_EXIT_CHOOSER,
    SHOW_EXIT_CHOOSER,
    IGNORE,
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
        const val CHOOSER_SETTLE_MS: Long = 400L
    }
    private val detector = ConsecutiveBackPressDetector(doublePressWindowMs)
    private var chooserVisible = false
    private var pipEnteredFromBack = false
    private var wasInPip = false
    private var expandedFromPip = false
    private var chooserShownAtMs = 0L

    fun onBack(nowMs: Long, isInPip: Boolean): PlaybackBackAction {
        if (chooserVisible) {
            if (chooserShownAtMs > 0L && nowMs - chooserShownAtMs < CHOOSER_SETTLE_MS) {
                return PlaybackBackAction.IGNORE
            }
            reset()
            return PlaybackBackAction.EXIT_APP
        }

        if (isInPip) {
            detector.clear()
            pipEnteredFromBack = false
            expandedFromPip = false
            return PlaybackBackAction.EXPAND_PIP_FOR_EXIT_CHOOSER
        }

        if (pipEnteredFromBack || expandedFromPip) {
            detector.clear()
            pipEnteredFromBack = false
            expandedFromPip = false
            markChooserVisible(nowMs)
            return PlaybackBackAction.SHOW_EXIT_CHOOSER
        }

        return if (detector.registerPress(nowMs)) {
            detector.clear()
            markChooserVisible(nowMs)
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
            markChooserVisible(0L)
            PlaybackBackAction.SHOW_EXIT_CHOOSER
        }
    }

    fun forceChooserVisible(nowMs: Long) {
        detector.clear()
        pipEnteredFromBack = false
        expandedFromPip = false
        markChooserVisible(nowMs)
    }

    private fun markChooserVisible(nowMs: Long) {
        chooserVisible = true
        chooserShownAtMs = nowMs
    }

    fun onPipModeChanged(isInPip: Boolean) {
        if (wasInPip && !isInPip) {
            expandedFromPip = true
        }
        wasInPip = isInPip
    }

    fun onChooserDismissed() {
        chooserVisible = false
        chooserShownAtMs = 0L
        detector.clear()
    }

    fun onPipEnterFailed() {
        pipEnteredFromBack = false
        expandedFromPip = false
        markChooserVisible(0L)
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
        chooserShownAtMs = 0L
    }
}
