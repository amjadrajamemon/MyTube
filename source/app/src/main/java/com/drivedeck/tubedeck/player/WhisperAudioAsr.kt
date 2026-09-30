package com.drivedeck.tubedeck.player

import android.content.Context
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperModel
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Zero-cloud audio ASR bridge.
 *
 * Stability is deliberately prioritized over maximum throughput:
 * - PCM delivery is bounded through one small channel instead of launching a coroutine per buffer.
 * - Whisper inference is single-flight and guarded by a native-context mutex.
 * - Model release can never race a running whisper_full() call.
 * - Speech clips are capped and resampled to Whisper's native 16 kHz mono format before JNI.
 * - The multilingual Tiny Q5_1 model reduces RAM/CPU pressure substantially versus full Tiny.
 */
class WhisperAudioAsr(
    context: Context,
    private val onText: (String, String?) -> Unit,
    private val onStatus: (String) -> Unit
) {
    companion object {
        const val MODEL_NAME = "ggml-tiny-q5_1.bin"
        const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin"
        const val MODEL_SHA1 = "2827a03e495b1ed3048ef28a6a4620537db4ee51"
        private const val WHISPER_SAMPLE_RATE = 16_000
        private const val MIN_CLIP_SECONDS = 0.75
        private const val MAX_CLIP_SECONDS = 4.5
        private const val END_SILENCE_MS = 360
    }

    private data class PcmPacket(val bytes: ByteArray, val sampleRate: Int, val channels: Int)

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val inferenceMutex = Mutex()
    private val modelDir = File(appContext.filesDir, "whisper-models").apply { mkdirs() }
    private val modelFile = File(modelDir, MODEL_NAME)
    private val pcmChannel = Channel<PcmPacket>(
        capacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val transcribing = AtomicBoolean(false)
    private val loadingModel = AtomicBoolean(false)
    private val lock = Any()

    @Volatile private var closing = false
    @Volatile private var enabled = false
    private var model: WhisperModel? = null
    private var pcm = ArrayList<Short>(WHISPER_SAMPLE_RATE * 5)
    private var speechStarted = false
    private var silentSamples = 0
    private var sourceLanguage: String? = null
    private var activeSampleRate = 0

    init {
        scope.launch {
            for (packet in pcmChannel) {
                if (!enabled || closing) continue
                processPcm(packet.bytes, packet.sampleRate, packet.channels)
            }
        }
    }

    fun setEnabled(value: Boolean, languageHint: String?) {
        enabled = value && !closing
        sourceLanguage = languageHint?.substringBefore('-')?.takeUnless { it == "auto" }
        if (!enabled) {
            synchronized(lock) { resetLocked() }
        } else {
            ensureModel()
        }
    }

    fun isCapturing(): Boolean = enabled && !closing && model != null

    fun modelInstalled(): Boolean = modelFile.isFile && modelFile.length() > 25_000_000L

    fun ensureModel() {
        if (!enabled || closing || model != null) return
        if (!loadingModel.compareAndSet(false, true)) return
        scope.launch {
            try {
                inferenceMutex.withLock {
                    if (!enabled || closing || model != null) return@withLock
                    if (!modelInstalled()) {
                        if (!installBundledModel()) {
                            onStatus("Installing multilingual Whisper Tiny Q5_1 model (~31 MB)…")
                            downloadModel()
                        }
                    }
                    if (!verifySha1(modelFile)) throw IllegalStateException("Whisper model checksum mismatch")
                    onStatus("Loading offline Whisper model…")
                    val loaded = Whisper.loadModel(appContext, modelFile.absolutePath)
                    if (closing) {
                        Whisper.releaseModel(loaded)
                    } else {
                        model = loaded
                        onStatus("Audio Live Dub ready • Whisper Tiny Q5_1 offline")
                    }
                }
            } catch (t: Throwable) {
                if (!closing) onStatus("Whisper unavailable: ${t.message ?: t.javaClass.simpleName}")
            } finally {
                loadingModel.set(false)
            }
        }
    }

    /** Receives decoded PCM16 from Media3. Playback is never blocked by ASR processing. */
    fun accept(bytes: ByteArray, sampleRate: Int, channels: Int) {
        if (!enabled || closing || model == null || sampleRate <= 0 || channels <= 0 || bytes.isEmpty()) return
        pcmChannel.trySend(PcmPacket(bytes, sampleRate, channels))
    }

    private fun processPcm(bytes: ByteArray, sampleRate: Int, channels: Int) {
        if (!enabled || closing) return
        val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val mono = ShortArray(shorts.remaining() / channels)
        var count = 0
        while (shorts.remaining() >= channels) {
            var sum = 0L
            repeat(channels) { sum += shorts.get().toLong() }
            mono[count++] = (sum / channels).coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toShort()
        }
        if (count == 0) return
        val used = if (count == mono.size) mono else mono.copyOf(count)
        val rms = sqrt(used.fold(0.0) { acc, value -> acc + value.toDouble() * value } / used.size) / 32768.0
        val speech = rms > 0.011

        var clip: ShortArray? = null
        var clipRate = sampleRate
        synchronized(lock) {
            if (activeSampleRate != 0 && activeSampleRate != sampleRate) resetLocked()
            activeSampleRate = sampleRate

            if (speech) {
                speechStarted = true
                silentSamples = 0
            } else if (speechStarted) {
                silentSamples += used.size
            }

            if (speechStarted) used.forEach { pcm.add(it) }
            val seconds = if (sampleRate > 0) pcm.size.toDouble() / sampleRate else 0.0
            val silenceMs = if (sampleRate > 0) silentSamples * 1000L / sampleRate else 0L

            if ((silenceMs >= END_SILENCE_MS && seconds >= MIN_CLIP_SECONDS) || seconds >= MAX_CLIP_SECONDS) {
                clip = pcm.toShortArray()
                clipRate = sampleRate
                resetLocked()
            }
        }
        clip?.let { submitClip(it, clipRate) }
    }

    private fun submitClip(samples: ShortArray, sampleRate: Int) {
        if (closing || !enabled || samples.isEmpty()) return
        if (!transcribing.compareAndSet(false, true)) return

        scope.launch {
            val wav = File(appContext.cacheDir, "livedub-${System.nanoTime()}.wav")
            try {
                inferenceMutex.withLock {
                    if (closing || !enabled) return@withLock
                    val whisperModel = model ?: return@withLock
                    val resampled = resampleTo16k(samples, sampleRate)
                    if (resampled.size < (WHISPER_SAMPLE_RATE * MIN_CLIP_SECONDS).toInt()) return@withLock
                    writeWav(wav, resampled, WHISPER_SAMPLE_RATE)

                    // One native worker is intentionally conservative on mobile. It avoids the
                    // native ggml graph race/pressure seen on some Android 15 ARM64 devices.
                    val config = sourceLanguage?.let {
                        WhisperConfig(language = it, threads = 1, printTimestamps = false)
                    } ?: WhisperConfig(threads = 1, printTimestamps = false)
                    val result = Whisper.transcribe(whisperModel, wav.absolutePath, config)
                    if (closing || !enabled) return@withLock
                    val text = result.text.trim()
                    if (text.isNotBlank()) onText(text, sourceLanguage)
                }
            } catch (t: Throwable) {
                if (!closing) onStatus("Whisper transcription error: ${t.message ?: t.javaClass.simpleName}")
            } finally {
                wav.delete()
                transcribing.set(false)
            }
        }
    }

    private fun resampleTo16k(input: ShortArray, inputRate: Int): ShortArray {
        if (inputRate <= 0 || input.isEmpty()) return ShortArray(0)
        if (inputRate == WHISPER_SAMPLE_RATE) return input
        val outputSize = floor(input.size.toDouble() * WHISPER_SAMPLE_RATE / inputRate).toInt().coerceAtLeast(1)
        val output = ShortArray(outputSize)
        val ratio = inputRate.toDouble() / WHISPER_SAMPLE_RATE
        for (i in output.indices) {
            val src = i * ratio
            val left = floor(src).toInt().coerceIn(0, input.lastIndex)
            val right = (left + 1).coerceAtMost(input.lastIndex)
            val fraction = src - left
            val value = input[left] * (1.0 - fraction) + input[right] * fraction
            output[i] = value.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return output
    }

    private suspend fun installBundledModel(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            appContext.assets.open("models/$MODEL_NAME").use { input ->
                FileOutputStream(modelFile).use { output -> input.copyTo(output, 1024 * 256) }
            }
            if (!verifySha1(modelFile)) {
                modelFile.delete()
                false
            } else true
        }.getOrDefault(false)
    }

    private suspend fun downloadModel() = withContext(Dispatchers.IO) {
        val tmp = File(modelDir, "$MODEL_NAME.part")
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) error("model download HTTP ${connection.responseCode}")
            connection.inputStream.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output, 1024 * 256) }
            }
            if (!verifySha1(tmp)) error("downloaded model failed checksum")
            if (modelFile.exists()) modelFile.delete()
            if (!tmp.renameTo(modelFile)) error("could not install model")
        } finally {
            connection.disconnect()
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun verifySha1(file: File): Boolean {
        if (!file.isFile) return false
        val md = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 256)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                md.update(buffer, 0, count)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }.equals(MODEL_SHA1, true)
    }

    private fun writeWav(file: File, samples: ShortArray, sampleRate: Int) {
        val dataSize = samples.size * 2
        FileOutputStream(file).use { out ->
            fun ascii(value: String) = out.write(value.toByteArray(Charsets.US_ASCII))
            fun le16(value: Int) = out.write(byteArrayOf((value and 255).toByte(), ((value ushr 8) and 255).toByte()))
            fun le32(value: Int) = out.write(
                byteArrayOf(
                    (value and 255).toByte(),
                    ((value ushr 8) and 255).toByte(),
                    ((value ushr 16) and 255).toByte(),
                    ((value ushr 24) and 255).toByte()
                )
            )
            ascii("RIFF")
            le32(36 + dataSize)
            ascii("WAVEfmt ")
            le32(16)
            le16(1)
            le16(1)
            le32(sampleRate)
            le32(sampleRate * 2)
            le16(2)
            le16(16)
            ascii("data")
            le32(dataSize)
            val buffer = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            samples.forEach { buffer.putShort(it) }
            out.write(buffer.array())
        }
    }

    private fun resetLocked() {
        pcm.clear()
        speechStarted = false
        silentSamples = 0
        activeSampleRate = 0
    }

    /**
     * Never free the native context while whisper_full() is executing. JNI crashes cannot be
     * caught by Kotlin, so release is serialized behind inference and performed asynchronously.
     */
    fun close() {
        if (closing) return
        closing = true
        enabled = false
        pcmChannel.close()
        synchronized(lock) { resetLocked() }
        scope.launch {
            try {
                inferenceMutex.withLock {
                    model?.let { Whisper.releaseModel(it) }
                    model = null
                }
            } finally {
                scope.cancel()
            }
        }
    }
}
