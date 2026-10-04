package com.drivedeck.tubedeck.player

internal enum class ParentPipBackAction { SHOW_CHOICES, KEEP_PIP_AND_EXIT, PAUSE_AND_EXIT }

internal object PlaybackParentPipBackPolicy {
    /**
     * A single isolated Back while PiP is active must never expand/maximize the player.
     * A deliberately quick second Back opens the three playback choices; a rapid triple
     * Back pauses. Once choices are already visible, Back leaves them without reopening.
     */
    fun onBack(rapidCount: Int, choicesVisible: Boolean): ParentPipBackAction = when {
        rapidCount >= 3 -> ParentPipBackAction.PAUSE_AND_EXIT
        choicesVisible -> ParentPipBackAction.KEEP_PIP_AND_EXIT
        rapidCount == 2 -> ParentPipBackAction.SHOW_CHOICES
        else -> ParentPipBackAction.KEEP_PIP_AND_EXIT
    }
}
