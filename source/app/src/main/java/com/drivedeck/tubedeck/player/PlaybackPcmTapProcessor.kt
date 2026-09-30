package com.drivedeck.tubedeck.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * Pass-through Media3 processor that mirrors decoded PCM16 to LiveDub without touching playback.
 *
 * Stability rule: this processor must never reject a format that Media3 can otherwise play. When
 * the negotiated output is not PCM16, or when audio ASR is not active, it simply passes bytes
 * through untouched and performs no allocation/copy for Whisper.
 */
class PlaybackPcmTapProcessor(
    private val shouldCapture: () -> Boolean,
    private val consumer: (ByteArray, Int, Int) -> Unit
) : BaseAudioProcessor() {
    private var sampleRate = 0
    private var channels = 0
    private var encoding = C.ENCODING_INVALID

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size <= 0) return

        // Mirror only the format WhisperAudioAsr currently expects. All other encodings remain
        // valid playback formats and are passed through without throwing.
        if (encoding == C.ENCODING_PCM_16BIT && sampleRate > 0 && channels > 0 && shouldCapture()) {
            val duplicate = inputBuffer.duplicate()
            val copy = ByteArray(size)
            duplicate.get(copy)
            runCatching { consumer(copy, sampleRate, channels) }
        }

        val output = replaceOutputBuffer(size)
        output.put(inputBuffer)
        output.flip()
    }
}
