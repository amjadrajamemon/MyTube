package com.drivedeck.tubedeck.player

internal enum class PlaybackBackAction {
    WAIT_FOR_SECOND_PRESS,
    ENTER_PIP,
    EXPAND_PIP,
    SHOW_EXIT_CHOOSER,
    IGNORE
}

/**
 * Playback Back state machine shared by both player hosts.
 *
 * There is deliberately no double-press timer here. Android PiP entry/exit is asynchronous, so
 * interpreting Back by elapsed milliseconds races the Activity lifecycle. Instead:
 *  - first Back from normal playback enters PiP immediately;
 *  - Back while PiP is active expands the existing Activity;
 *  - once expanded from PiP, the next handled Back shows the explicit Close / Audio / PiP chooser.
 */
internal class PlaybackBackCoordinator {
    private var chooserVisible = false
    private var pipEnteredFromBack = false
    private var wasInPip = false
    private var expandedFromPip = false

    fun onBack(nowMs: Long, isInPip: Boolean): PlaybackBackAction {
        @Suppress("UNUSED_VARIABLE")
        val ignoredTimestamp = nowMs
        if (chooserVisible) return PlaybackBackAction.IGNORE

        if (isInPip) {
            pipEnteredFromBack = false
            expandedFromPip = false
            return PlaybackBackAction.EXPAND_PIP
        }

        if (expandedFromPip || pipEnteredFromBack) {
            pipEnteredFromBack = false
            expandedFromPip = false
            chooserVisible = true
            return PlaybackBackAction.SHOW_EXIT_CHOOSER
        }

        pipEnteredFromBack = true
        return PlaybackBackAction.ENTER_PIP
    }

    /**
     * Kept for source compatibility with older hosts. New hosts should never need a timing
     * timeout; if invoked, it resolves directly from capability rather than press timing.
     */
    fun onSinglePressTimeout(canEnterPip: Boolean): PlaybackBackAction =
        if (canEnterPip) {
            pipEnteredFromBack = true
            PlaybackBackAction.ENTER_PIP
        } else {
            chooserVisible = true
            PlaybackBackAction.SHOW_EXIT_CHOOSER
        }

    fun forceChooserVisible() {
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
    }

    fun onPipEnterFailed() {
        pipEnteredFromBack = false
        expandedFromPip = false
        chooserVisible = true
    }

    fun onNewMedia() = reset()

    fun reset() {
        chooserVisible = false
        pipEnteredFromBack = false
        wasInPip = false
        expandedFromPip = false
    }
}
