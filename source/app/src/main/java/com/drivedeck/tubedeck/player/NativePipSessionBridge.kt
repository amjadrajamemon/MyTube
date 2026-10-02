package com.drivedeck.tubedeck.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-lifetime bridge between NativePlayerActivity (the ExoPlayer owner) and MainActivity
 * (the parent UI shown underneath system PiP).
 *
 * SmartTube-style navigation keeps the playback engine owned by the player while the parent UI
 * becomes active. MainActivity uses this state to own the second Back press and show the
 * Keep PiP / Audio only / Close chooser without tearing down or recreating ExoPlayer.
 */
internal object NativePipSessionBridge {
    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    fun activate(sessionId: String) {
        if (sessionId.isNotBlank()) _activeSessionId.value = sessionId
    }

    fun clear(sessionId: String? = null) {
        if (sessionId == null || _activeSessionId.value == sessionId) {
            _activeSessionId.value = null
        }
    }

    fun isActive(): Boolean = !_activeSessionId.value.isNullOrBlank()
}
