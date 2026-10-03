package com.drivedeck.tubedeck.player

internal enum class ParentPipBackAction { SHOW_CHOICES, KEEP_PIP_AND_EXIT, PAUSE_AND_EXIT }

internal object PlaybackParentPipBackPolicy {
    fun onBack(rapidCount: Int, choicesVisible: Boolean): ParentPipBackAction = when {
        rapidCount >= 3 -> ParentPipBackAction.PAUSE_AND_EXIT
        choicesVisible -> ParentPipBackAction.KEEP_PIP_AND_EXIT
        else -> ParentPipBackAction.SHOW_CHOICES
    }
}
