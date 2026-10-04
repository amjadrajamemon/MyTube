package com.drivedeck.tubedeck.player

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-local delivery for system PiP RemoteActions when the Web/MainActivity host owns video.
 * NativePlayerActivity continues to use NativePipSessionBridge so its session identity is retained.
 */
internal object PlaybackPipCommandBus {
    enum class Action { KEEP_PIP, AUDIO_ONLY, CLOSE }

    private val _commands = MutableSharedFlow<Action>(extraBufferCapacity = 8)
    val commands: SharedFlow<Action> = _commands.asSharedFlow()

    fun request(action: Action) {
        _commands.tryEmit(action)
    }
}
