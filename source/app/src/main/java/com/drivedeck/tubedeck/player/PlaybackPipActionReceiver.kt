package com.drivedeck.tubedeck.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives system PiP actions without bringing an Activity to the foreground.
 *
 * CLOSE is intentionally hard: stop any audio-only service first, then terminate whichever
 * visible playback host owns the current PiP session.
 */
class PlaybackPipActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val nativeSessionId = NativePipSessionBridge.activeSessionId.value

        when (action) {
            PlaybackPipActions.ACTION_KEEP_PIP -> {
                // No Activity launch: the PiP window simply remains where it is.
                if (nativeSessionId.isNullOrBlank()) {
                    PlaybackPipCommandBus.request(PlaybackPipCommandBus.Action.KEEP_PIP)
                }
            }

            PlaybackPipActions.ACTION_AUDIO_ONLY -> {
                if (!nativeSessionId.isNullOrBlank()) {
                    NativePipSessionBridge.request(
                        nativeSessionId,
                        NativePipSessionBridge.Action.AUDIO_ONLY
                    )
                } else {
                    PlaybackPipCommandBus.request(PlaybackPipCommandBus.Action.AUDIO_ONLY)
                }
            }

            PlaybackPipActions.ACTION_CLOSE -> {
                // System PiP X must never leave a detached MediaSession/audio service alive.
                BackgroundAudioController.stop(context.applicationContext)
                if (!nativeSessionId.isNullOrBlank()) {
                    NativePipSessionBridge.request(
                        nativeSessionId,
                        NativePipSessionBridge.Action.CLOSE
                    )
                } else {
                    PlaybackPipCommandBus.request(PlaybackPipCommandBus.Action.CLOSE)
                }
            }
        }
    }
}
