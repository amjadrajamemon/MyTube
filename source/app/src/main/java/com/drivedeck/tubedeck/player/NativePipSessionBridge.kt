package com.drivedeck.tubedeck.player

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-lifetime bridge between NativePlayerActivity (the ExoPlayer owner) and MainActivity
 * (the parent UI shown underneath system PiP).
 *
 * SmartTube-style navigation keeps the playback engine owned by the player while the parent UI
 * becomes active. MainActivity owns the second Back press and sends commands back to the same
 * native playback session without relaunching or recreating ExoPlayer.
 */
internal object NativePipSessionBridge {
    enum class Action {
        SHOW_CHOOSER,
        PAUSE,
        AUDIO_ONLY,
        CLOSE
    }

    data class Command(
        val sessionId: String,
        val action: Action
    )

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 8)
    val commands: SharedFlow<Command> = _commands.asSharedFlow()

    fun activate(sessionId: String) {
        if (sessionId.isNotBlank()) _activeSessionId.value = sessionId
    }

    fun request(sessionId: String, action: Action) {
        if (sessionId.isNotBlank() && _activeSessionId.value == sessionId) {
            _commands.tryEmit(Command(sessionId, action))
        }
    }

    fun clear(sessionId: String? = null) {
        if (sessionId == null || _activeSessionId.value == sessionId) {
            _activeSessionId.value = null
        }
    }

    fun isActive(): Boolean = !_activeSessionId.value.isNullOrBlank()
}
