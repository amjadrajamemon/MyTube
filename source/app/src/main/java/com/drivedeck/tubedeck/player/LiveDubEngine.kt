package com.drivedeck.tubedeck.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.abs

/**
 * Zero-subscription Live Dub engine.
 *
 * Caption cues are de-duplicated, translated strictly in presentation order, and then added
 * to Android TTS with QUEUE_ADD. This deliberately avoids QUEUE_FLUSH because flushing every
 * incoming cue clips the sentence that is already being spoken and makes dubbing intermittent.
 *
 * Translation is ML Kit on-device and speech output is restricted to locally installed Android
 * TTS voices, so there is no paid API or per-minute service dependency.
 */
data class DubVoiceProfile(
    val id: String,
    val label: String,
    val rate: Float,
    val pitch: Float
)

object LiveDubVoiceProfiles {
    val supported = listOf(
        DubVoiceProfile("natural", "Natural", 1.00f, 1.00f),
        DubVoiceProfile("clear", "Clear", 0.94f, 1.02f),
        DubVoiceProfile("warm", "Warm", 0.96f, 0.92f),
        DubVoiceProfile("bright", "Bright", 1.03f, 1.10f),
        DubVoiceProfile("deep", "Deep", 0.92f, 0.82f),
        DubVoiceProfile("fast", "Fast", 1.14f, 1.00f),
        DubVoiceProfile("calm", "Calm", 0.88f, 0.96f),
        DubVoiceProfile("narrator", "Narrator", 0.91f, 0.88f),
        DubVoiceProfile("news", "News", 1.06f, 0.98f),
        DubVoiceProfile("compact", "Compact", 1.18f, 1.04f)
    )
    fun byId(id: String?) = supported.firstOrNull { it.id == id } ?: supported.first()
}

data class DubLanguage(
    val code: String,
    val label: String,
    val localeTag: String = code
)

object LiveDubLanguages {
    val supported = listOf(
        DubLanguage("en", "English"),
        DubLanguage("ur", "Urdu"),
        DubLanguage("hi", "Hindi"),
        DubLanguage("ar", "Arabic"),
        DubLanguage("es", "Spanish"),
        DubLanguage("fr", "French"),
        DubLanguage("de", "German"),
        DubLanguage("pt", "Portuguese"),
        DubLanguage("ru", "Russian"),
        DubLanguage("zh", "Chinese", "zh-CN")
    )

    fun byCode(code: String?): DubLanguage? {
        val base = code.orEmpty().substringBefore('-').lowercase(Locale.ROOT)
        return supported.firstOrNull { it.code == base }
    }

    fun label(code: String?): String = byCode(code)?.label ?: code.orEmpty().ifBlank { "Unknown" }
}

enum class LiveDubInputMode { AUDIO, CAPTIONS }

class LiveDubEngine(context: Context) : TextToSpeech.OnInitListener {
    private data class CuePacket(val text: String, val presentationTimeUs: Long)

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val queueLock = Any()
    private val cueQueue = ArrayDeque<CuePacket>()

    // Keep playback startup light: TTS/ML Kit/Whisper are not created merely because a video
    // player is visible. They are initialized only when Live Dub is explicitly enabled.
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var voiceReady = false
    private var translator: Translator? = null
    private var modelReady = false
    private var enabled = false
    private var sourceCode: String = ""
    private var targetCode: String = ""
    private var voiceProfileId: String = "natural"
    @Volatile private var inputMode: LiveDubInputMode = LiveDubInputMode.AUDIO
    private var translating = false
    private var generation = 0L
    private var queuedUtterances = 0
    private var lastCue = ""
    private var lastPresentationTimeUs = Long.MIN_VALUE
    private var statusListener: (String) -> Unit = {}
    private var closed = false
    private var audioAsr: WhisperAudioAsr? = null

    private fun ensureTts() {
        if (closed || tts != null) return
        ttsReady = false
        voiceReady = false
        tts = TextToSpeech(appContext, this)
    }

    private fun ensureAudioAsr() {
        if (closed || audioAsr != null) return
        audioAsr = WhisperAudioAsr(
            appContext,
            onText = { text, detectedLanguage ->
                mainHandler.post { acceptAsrSentence(text, detectedLanguage) }
            },
            onStatus = { message -> postStatus(message) }
        )
    }

    override fun onInit(status: Int) {
        if (closed) return
        ttsReady = status == TextToSpeech.SUCCESS
        if (!ttsReady) {
            postStatus("Android offline speech engine is unavailable")
            return
        }

        applyVoiceProfile()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                synchronized(queueLock) {
                    queuedUtterances = (queuedUtterances - 1).coerceAtLeast(0)
                }
            }

            @Suppress("DEPRECATION")
            override fun onError(utteranceId: String?) {
                synchronized(queueLock) {
                    queuedUtterances = (queuedUtterances - 1).coerceAtLeast(0)
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                synchronized(queueLock) {
                    queuedUtterances = (queuedUtterances - 1).coerceAtLeast(0)
                }
            }
        })

        if (targetCode.isNotBlank()) configureVoice()
        processQueue()
    }

    fun configure(
        sourceLanguageTag: String,
        targetLanguageCode: String,
        voiceProfile: String = "natural",
        inputMode: LiveDubInputMode = LiveDubInputMode.AUDIO,
        onStatus: (String) -> Unit
    ) {
        if (closed) return
        ensureTts()
        if (inputMode == LiveDubInputMode.AUDIO) ensureAudioAsr()
        statusListener = onStatus
        voiceProfileId = voiceProfile
        this.inputMode = inputMode
        generation++
        clearQueuedSpeech(resetCueHistory = true)
        enabled = true

        val source = if (sourceLanguageTag == "auto") null else
            (TranslateLanguage.fromLanguageTag(sourceLanguageTag)
                ?: TranslateLanguage.fromLanguageTag(sourceLanguageTag.substringBefore('-')))
        val target = TranslateLanguage.fromLanguageTag(targetLanguageCode)
            ?: TranslateLanguage.fromLanguageTag(targetLanguageCode.substringBefore('-'))

        audioAsr?.setEnabled(inputMode == LiveDubInputMode.AUDIO, sourceLanguageTag)
        if (source == null && sourceLanguageTag != "auto") {
            modelReady = false
            postStatus("Source language is not supported for offline translation")
            return
        }
        if (target == null) {
            modelReady = false
            postStatus("Target language is not supported for offline translation")
            return
        }

        sourceCode = source ?: ""
        targetCode = target
        translator?.close()
        translator = null
        modelReady = false
        configureVoice()

        if (source == null) {
            modelReady = false
            postStatus(if (inputMode == LiveDubInputMode.AUDIO) "Listening to audio • Whisper auto-detect" else "Caption dubbing waiting for source language")
            return
        }

        if (source == target) {
            modelReady = true
            if (voiceReady) postStatus("Live Dub ready • ${LiveDubLanguages.label(target)} voice")
            processQueue()
            return
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()
        val newTranslator = Translation.getClient(options)
        translator = newTranslator
        val configureGeneration = generation

        postStatus("Preparing free offline ${LiveDubLanguages.label(source)} → ${LiveDubLanguages.label(target)} model…")
        // Translation models are downloaded once and then stay on-device. Do not deadlock Live
        // Dub behind a Wi-Fi-only requirement when the user is on a usable metered connection.
        newTranslator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                if (closed || translator !== newTranslator || !enabled || generation != configureGeneration) return@addOnSuccessListener
                modelReady = true
                if (voiceReady) {
                    postStatus("Live Dub ready • ${LiveDubLanguages.label(source)} → ${LiveDubLanguages.label(target)}")
                }
                processQueue()
            }
            .addOnFailureListener { error ->
                if (closed || translator !== newTranslator || !enabled || generation != configureGeneration) return@addOnFailureListener
                modelReady = false
                postStatus("Offline language model unavailable: ${error.message ?: "download failed"}")
            }
    }

    /** PCM mirror from ExoPlayer. ASR work is asynchronous and never blocks playback. */
    fun onPlaybackPcm(bytes: ByteArray, sampleRate: Int, channels: Int) {
        if (enabled && !closed && inputMode == LiveDubInputMode.AUDIO) {
            audioAsr?.accept(bytes, sampleRate, channels)
        }
    }

    fun isAudioCaptureEnabled(): Boolean =
        enabled && !closed && inputMode == LiveDubInputMode.AUDIO && audioAsr?.isCapturing() == true

    private fun acceptAsrSentence(text: String, detectedLanguage: String?) {
        if (!enabled || closed || inputMode != LiveDubInputMode.AUDIO || text.isBlank()) return
        val detected = detectedLanguage?.let { TranslateLanguage.fromLanguageTag(it) }
        if (sourceCode.isBlank() && detected != null) {
            // Auto language is resolved by Whisper; rebuild the on-device translator once.
            configure(detected, targetCode, voiceProfileId, inputMode, statusListener)
        }
        speak(text, -1L)
    }

    /**
     * Adds an active caption cue to the ordered dub pipeline.
     * presentationTimeUs is used to distinguish rolling-caption updates from a seek/replay.
     */
    fun speak(cueText: String, presentationTimeUs: Long = -1L) {
        if (!enabled || closed) return
        val cleaned = cleanCue(cueText)
        if (cleaned.isBlank()) return

        synchronized(queueLock) {
            // A backward time jump means the user sought/replayed. Old queued speech must not leak
            // into the new playback position.
            if (presentationTimeUs >= 0L && lastPresentationTimeUs >= 0L &&
                presentationTimeUs + 750_000L < lastPresentationTimeUs
            ) {
                clearQueueLocked(resetCueHistory = true)
                mainHandler.post { tts?.stop() }
            }

            val closeInTime = presentationTimeUs < 0L || lastPresentationTimeUs < 0L ||
                abs(presentationTimeUs - lastPresentationTimeUs) <= 3_500_000L
            val novel = if (closeInTime) novelCaptionText(lastCue, cleaned) else cleaned

            lastCue = cleaned
            if (presentationTimeUs >= 0L) lastPresentationTimeUs = presentationTimeUs
            if (novel.isBlank()) return

            // Combine tiny adjacent caption fragments before translation. This reduces choppy TTS
            // without dropping words and keeps the translated phrase more natural.
            val tail = cueQueue.peekLast()
            if (tail != null && novel.length < 90 &&
                (presentationTimeUs < 0L || tail.presentationTimeUs < 0L ||
                    presentationTimeUs - tail.presentationTimeUs in 0L..1_400_000L) &&
                tail.text.length + novel.length < 220
            ) {
                cueQueue.removeLast()
                cueQueue.addLast(CuePacket("${tail.text} $novel".trim(), presentationTimeUs))
            } else {
                cueQueue.addLast(CuePacket(novel, presentationTimeUs))
            }
        }
        processQueue()
    }

    /** Clears speech that belongs to the old playhead position after a seek/skip. */
    fun resetForSeek() {
        if (closed) return
        generation++
        synchronized(queueLock) {
            clearQueueLocked(resetCueHistory = true)
        }
        mainHandler.post { tts?.stop() }
    }

    /** Prevent dubbed speech from continuing after the video itself has been paused. */
    fun pauseForPlayback() {
        if (!enabled || closed) return
        generation++
        synchronized(queueLock) {
            clearQueueLocked(resetCueHistory = true)
        }
        mainHandler.post { tts?.stop() }
    }

    fun stop() {
        enabled = false
        audioAsr?.setEnabled(false, null)
        generation++
        modelReady = false
        synchronized(queueLock) {
            clearQueueLocked(resetCueHistory = true)
        }
        translator?.close()
        translator = null
        mainHandler.post { tts?.stop() }
    }

    fun close() {
        if (closed) return
        stop()
        closed = true
        audioAsr?.close()
        audioAsr = null
        mainHandler.post {
            tts?.shutdown()
            tts = null
        }
    }

    private fun configureVoice() {
        voiceReady = false
        if (!ttsReady || targetCode.isBlank()) return
        val language = LiveDubLanguages.byCode(targetCode)
        val locale = Locale.forLanguageTag(language?.localeTag ?: targetCode)
        val engine = tts ?: return

        val offlineVoice = runCatching {
            engine.voices
                ?.asSequence()
                ?.filter { !it.isNetworkConnectionRequired }
                ?.filter { it.locale.language.equals(locale.language, ignoreCase = true) }
                ?.sortedByDescending { it.quality }
                ?.firstOrNull()
        }.getOrNull()

        if (offlineVoice != null) {
            voiceReady = engine.setVoice(offlineVoice) == TextToSpeech.SUCCESS
        }

        if (!voiceReady) {
            postStatus("Offline ${LiveDubLanguages.label(targetCode)} TTS voice is not installed on this phone")
        } else {
            applyVoiceProfile()
        }
    }

    fun setVoiceProfile(profileId: String) {
        voiceProfileId = LiveDubVoiceProfiles.byId(profileId).id
        applyVoiceProfile()
    }

    private fun applyVoiceProfile() {
        if (!ttsReady) return
        val profile = LiveDubVoiceProfiles.byId(voiceProfileId)
        tts?.setSpeechRate(profile.rate)
        tts?.setPitch(profile.pitch)
    }

    private fun processQueue() {
        if (closed || !enabled || !modelReady || !ttsReady || !voiceReady) return

        val job: CuePacket
        val activeTranslator: Translator?
        val activeGeneration: Long
        synchronized(queueLock) {
            if (translating || cueQueue.isEmpty()) return
            translating = true
            job = cueQueue.removeFirst()
            activeTranslator = translator
            activeGeneration = generation
        }

        if (sourceCode == targetCode) {
            queueSpeech(job.text, activeGeneration)
            finishTranslation(activeGeneration)
            return
        }

        if (activeTranslator == null) {
            finishTranslation(activeGeneration)
            return
        }

        activeTranslator.translate(job.text)
            .addOnSuccessListener { translated ->
                if (!closed && enabled && generation == activeGeneration && translator === activeTranslator && translated.isNotBlank()) {
                    queueSpeech(translated, activeGeneration)
                }
                finishTranslation(activeGeneration)
            }
            .addOnFailureListener { error ->
                if (!closed && enabled && generation == activeGeneration && translator === activeTranslator) {
                    postStatus("Dub translation skipped: ${error.message ?: "translation error"}")
                }
                finishTranslation(activeGeneration)
            }
    }

    private fun finishTranslation(activeGeneration: Long) {
        synchronized(queueLock) {
            if (generation == activeGeneration) translating = false
        }
        mainHandler.post { processQueue() }
    }

    private fun queueSpeech(text: String, activeGeneration: Long) {
        mainHandler.post {
            if (closed || !enabled || generation != activeGeneration || !ttsReady || !voiceReady) return@post
            val engine = tts ?: return@post
            val backlog = synchronized(queueLock) { queuedUtterances }
            val profile = LiveDubVoiceProfiles.byId(voiceProfileId)
            val catchUp = when {
                backlog >= 5 -> 1.16f
                backlog >= 2 -> 1.08f
                else -> 1.00f
            }
            engine.setSpeechRate((profile.rate * catchUp).coerceIn(0.75f, 1.35f))
            engine.setPitch(profile.pitch)
            val utteranceId = "mytube-live-dub-${SystemClock.elapsedRealtime()}-${activeGeneration}"
            synchronized(queueLock) { queuedUtterances++ }
            val result = engine.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
            if (result == TextToSpeech.ERROR) {
                synchronized(queueLock) { queuedUtterances = (queuedUtterances - 1).coerceAtLeast(0) }
            }
        }
    }

    private fun clearQueuedSpeech(resetCueHistory: Boolean) {
        synchronized(queueLock) {
            clearQueueLocked(resetCueHistory)
        }
        mainHandler.post { tts?.stop() }
    }

    private fun clearQueueLocked(resetCueHistory: Boolean) {
        cueQueue.clear()
        translating = false
        queuedUtterances = 0
        if (resetCueHistory) {
            lastCue = ""
            lastPresentationTimeUs = Long.MIN_VALUE
        }
    }

    private fun postStatus(message: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) statusListener(message)
        else mainHandler.post { if (!closed) statusListener(message) }
    }

    private fun cleanCue(text: String): String = text
        .replace(Regex("<[^>]+>"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * Auto-generated captions commonly arrive as rolling text (A -> A B -> A B C). Speak only
     * the newly-added tail. For line transitions, remove a multi-word overlap between the end of
     * the prior cue and the start of the new cue. A one-word overlap is deliberately not removed
     * because speakers may legitimately repeat a single word.
     */
    private fun novelCaptionText(previous: String, current: String): String {
        if (previous.isBlank()) return current
        if (current.equals(previous, ignoreCase = true)) return ""
        if (current.startsWith(previous, ignoreCase = true)) {
            return current.substring(previous.length).trim().trimStart('-', '–', '—', ':', ',', '.', ' ')
        }

        val previousWords = previous.split(Regex("\\s+")).filter { it.isNotBlank() }
        val currentWords = current.split(Regex("\\s+")).filter { it.isNotBlank() }
        val maxOverlap = minOf(previousWords.size, currentWords.size)
        for (count in maxOverlap downTo 2) {
            val left = previousWords.takeLast(count).joinToString(" ")
            val right = currentWords.take(count).joinToString(" ")
            if (left.equals(right, ignoreCase = true)) {
                return currentWords.drop(count).joinToString(" ").trim()
            }
        }

        // If the new cue is wholly contained in the previous rolling caption, it has already been spoken.
        if (previous.contains(current, ignoreCase = true) && current.length >= 8) return ""
        return current
    }
}
