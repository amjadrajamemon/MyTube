package com.drivedeck.tubedeck.player

internal object PlaybackPipExitPolicy {
    fun shouldRestoreChoices(
        wasInPip: Boolean,
        isInPip: Boolean,
        choicePending: Boolean,
        playerActive: Boolean,
        closing: Boolean
    ): Boolean =
        wasInPip &&
            !isInPip &&
            choicePending &&
            playerActive &&
            !closing
}
