package com.drivedeck.tubedeck.player

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Opt-in session-scoped night compressor, never an output-mix (session 0) effect.
 * No platform audio effect is instantiated at player startup unless explicitly enabled.
 * Some TV/car vendor audio stacks reject effects; failure always leaves playback untouched.
 */
class NightAudioController {
    private var processor: DynamicsProcessing? = null
    private var sessionId = 0

    fun apply(audioSessionId: Int, enabled: Boolean): Boolean {
        if (!enabled || audioSessionId <= 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            release()
            return false
        }
        if (sessionId == audioSessionId && processor != null) return true
        release()
        return runCatching {
            val next = buildProcessor(audioSessionId)
            next.enabled = true
            processor = next
            sessionId = audioSessionId
            true
        }.getOrElse {
            release()
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun buildProcessor(audioSessionId: Int): DynamicsProcessing {
        // One broad-band compressor preserves the input's tonal balance. Soft-knee
        // 3:1 above -27 dBFS reduces loud jumps; +4 dB makeup lifts soft speech.
        // A linked -2 dBFS limiter contains the result without raising system volume.
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION,
            2,          // stereo; Android replicates configuration as needed
            false, 0,   // do not override the user's existing equalizer
            true, 1,    // one multiband-compressor band spanning the spectrum
            false, 0,   // no additional post-EQ
            true        // safety limiter
        ).build()
        config.setMbcBandAllChannelsTo(
            0,
            DynamicsProcessing.MbcBand(
                true, 20000f, 12f, 250f, 3f, -27f, 7f,
                -90f, 1f, 0f, 4f
            )
        )
        config.setLimiterAllChannelsTo(
            DynamicsProcessing.Limiter(true, true, 0, 3f, 180f, 10f, -2f, 0f)
        )
        return DynamicsProcessing(0, audioSessionId, config)
    }

    fun release() {
        val old = processor
        processor = null
        sessionId = 0
        runCatching { old?.enabled = false }
        runCatching { old?.release() }
    }
}
