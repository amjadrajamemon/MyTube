package com.drivedeck.tubedeck.player

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lifecycle-safe wrapper around Android's platform Equalizer.
 *
 * The controller keeps the active curve, view preferences and saved User preset when ExoPlayer
 * rotates its audio session, so seeks/source changes/decoder recovery do not reset the EQ.
 */
class PlaybackEqualizerController(context: Context) {

    private val appContext = context.applicationContext

    data class Band(
        val index: Int,
        val centerFrequencyHz: Int,
        val normalizedLevel: Float,
        val levelDb: Float
    )

    data class Snapshot(
        val supported: Boolean,
        val enabled: Boolean,
        val presetId: String,
        val bands: List<Band>,
        val minDb: Float,
        val maxDb: Float,
        val layoutId: String,
        val styleId: String,
        val userPresetName: String,
        val hasUserPreset: Boolean,
        val surroundAvailable: Boolean,
        val surroundEnabled: Boolean,
        val surroundStrength: Float,
        val dtsAvailable: Boolean,
        val dtsEnabled: Boolean,
        val dtsEffectName: String?
    ) {
        companion object {
            fun unavailable(
                enabled: Boolean = false,
                presetId: String = "flat",
                layoutId: String = "horizontal",
                styleId: String = "classic",
                userPresetName: String = "User",
                hasUserPreset: Boolean = false,
                surroundAvailable: Boolean = false,
                surroundEnabled: Boolean = false,
                surroundStrength: Float = .65f,
                dtsAvailable: Boolean = false,
                dtsEnabled: Boolean = false,
                dtsEffectName: String? = null
            ) = Snapshot(
                supported = false,
                enabled = enabled,
                presetId = presetId,
                bands = emptyList(),
                minDb = -15f,
                maxDb = 15f,
                layoutId = layoutId,
                styleId = styleId,
                userPresetName = userPresetName,
                hasUserPreset = hasUserPreset,
                surroundAvailable = surroundAvailable,
                surroundEnabled = surroundEnabled,
                surroundStrength = surroundStrength,
                dtsAvailable = dtsAvailable,
                dtsEnabled = dtsEnabled,
                dtsEffectName = dtsEffectName
            )
        }
    }

    private val prefs = appContext
        .getSharedPreferences("mytube_playback_equalizer_v2", Context.MODE_PRIVATE)

    private var effect: Equalizer? = null
    @Suppress("DEPRECATION")
    private var surroundEffect: Virtualizer? = null
    private var dtsDescriptor: AudioEffect.Descriptor? = null
    private var requestedEnabled = prefs.getBoolean(KEY_ENABLED, false)
    private var requestedPresetId = prefs.getString(KEY_PRESET, "flat") ?: "flat"
    private var requestedLayoutId = prefs.getString(KEY_LAYOUT, "horizontal") ?: "horizontal"
    private var requestedStyleId = prefs.getString(KEY_STYLE, "classic") ?: "classic"
    private var requestedUserName = prefs.getString(KEY_USER_NAME, "User")?.ifBlank { "User" } ?: "User"
    private var requestedSurroundEnabled = prefs.getBoolean(KEY_SURROUND_ENABLED, false)
    private var requestedSurroundStrength = prefs.getFloat(KEY_SURROUND_STRENGTH, .65f).coerceIn(0f, 1f)
    private var requestedDtsEnabled = prefs.getBoolean(KEY_DTS_ENABLED, false)
    private var requestedCurve: MutableList<Float> = decodeCurve(prefs.getString(KEY_CURRENT_CURVE, null))
    private var savedUserCurve: MutableList<Float> = decodeCurve(prefs.getString(KEY_USER_CURVE, null))
    private var attachedSessionId = 0

    fun attach(audioSessionId: Int): Snapshot {
        if (audioSessionId <= 0) return snapshot()
        if (attachedSessionId == audioSessionId && effect != null) return snapshot()

        releaseEffect()
        attachedSessionId = audioSessionId
        return try {
            val equalizer = Equalizer(0, audioSessionId)
            effect = equalizer
            val bandCount = equalizer.numberOfBands.toInt()

            requestedCurve = when (requestedPresetId) {
                "custom" -> resampleCurve(requestedCurve, bandCount)
                "user" -> if (savedUserCurve.isNotEmpty()) resampleCurve(savedUserCurve, bandCount)
                    else presetCurve("flat", equalizer).toMutableList()
                else -> presetCurve(requestedPresetId, equalizer).toMutableList()
            }

            applyCurve(equalizer)
            equalizer.enabled = requestedEnabled
            attachSurround(audioSessionId)
            attachDts(audioSessionId)
            persistCurrentState()
            snapshot()
        } catch (_: Throwable) {
            releaseEffect()
            unavailableSnapshot()
        }
    }

    fun setEnabled(enabled: Boolean): Snapshot {
        requestedEnabled = enabled
        runCatching { effect?.enabled = enabled }
        persistCurrentState()
        return snapshot()
    }

    fun setLayout(layoutId: String): Snapshot {
        requestedLayoutId = if (layoutId == "vertical") "vertical" else "horizontal"
        persistCurrentState()
        return snapshot()
    }

    fun setStyle(styleId: String): Snapshot {
        requestedStyleId = if (styleId == "graphic") "graphic" else "classic"
        persistCurrentState()
        return snapshot()
    }

    fun setSurroundEnabled(enabled: Boolean): Snapshot {
        requestedSurroundEnabled = enabled
        runCatching { surroundEffect?.enabled = enabled }
        persistCurrentState()
        return snapshot()
    }

    @Suppress("DEPRECATION")
    fun setSurroundStrength(strength: Float): Snapshot {
        requestedSurroundStrength = strength.coerceIn(0f, 1f)
        surroundEffect?.let { virtualizer ->
            if (virtualizer.strengthSupported) {
                runCatching { virtualizer.setStrength((requestedSurroundStrength * 1000f).roundToInt().toShort()) }
            }
        }
        persistCurrentState()
        return snapshot()
    }

    fun setDtsEnabled(enabled: Boolean): Snapshot {
        requestedDtsEnabled = enabled && dtsDescriptor != null
        if (attachedSessionId > 0 && dtsDescriptor != null) {
            signalDeviceDtsSession(open = requestedDtsEnabled, audioSessionId = attachedSessionId)
        }
        persistCurrentState()
        return snapshot()
    }

    fun applyPreset(presetId: String): Snapshot {
        val equalizer = effect
        if (presetId == "user" && savedUserCurve.isEmpty()) return snapshot()

        requestedPresetId = presetId
        if (equalizer != null) {
            requestedCurve = when (presetId) {
                "custom" -> resampleCurve(requestedCurve, equalizer.numberOfBands.toInt())
                "user" -> resampleCurve(savedUserCurve, equalizer.numberOfBands.toInt())
                else -> presetCurve(presetId, equalizer).toMutableList()
            }
            applyCurve(equalizer)
        }
        persistCurrentState()
        return snapshot()
    }

    fun setBandNormalized(index: Int, normalized: Float): Snapshot {
        val result = setBandNormalizedRealtime(index, normalized)
        persistCurrentState()
        return result
    }

    /**
     * Low-latency graph editing path. Audio is updated immediately while the finger moves,
     * but disk-backed preference persistence is deferred until the gesture finishes.
     */
    fun setBandNormalizedRealtime(index: Int, normalized: Float): Snapshot {
        val equalizer = effect ?: return snapshot()
        ensureCurveSize(equalizer.numberOfBands.toInt())
        if (index !in requestedCurve.indices) return snapshot()

        requestedPresetId = "custom"
        requestedCurve[index] = normalized.coerceIn(-1f, 1f)
        setEffectBand(equalizer, index, requestedCurve[index])
        return snapshot()
    }

    fun commitCustomCurve(): Snapshot {
        persistCurrentState()
        return snapshot()
    }

    fun saveUserPreset(name: String): Snapshot {
        val equalizer = effect
        if (equalizer != null) ensureCurveSize(equalizer.numberOfBands.toInt())
        if (requestedCurve.isEmpty()) return snapshot()

        requestedUserName = name.trim().ifBlank { "User" }.take(24)
        savedUserCurve = requestedCurve.toMutableList()
        requestedPresetId = "user"
        prefs.edit()
            .putString(KEY_USER_NAME, requestedUserName)
            .putString(KEY_USER_CURVE, encodeCurve(savedUserCurve))
            .apply()
        persistCurrentState()
        return snapshot()
    }

    fun recallUserPreset(): Snapshot {
        if (savedUserCurve.isEmpty()) return snapshot()
        return applyPreset("user")
    }

    fun snapshot(): Snapshot {
        val equalizer = effect ?: return unavailableSnapshot()
        return runCatching {
            val range = equalizer.bandLevelRange
            val minMb = range[0].toInt()
            val maxMb = range[1].toInt()
            val count = equalizer.numberOfBands.toInt()
            ensureCurveSize(count)

            Snapshot(
                supported = true,
                enabled = requestedEnabled,
                presetId = requestedPresetId,
                bands = (0 until count).map { index ->
                    val levelMb = equalizer.getBandLevel(index.toShort()).toInt()
                    Band(
                        index = index,
                        centerFrequencyHz = equalizer.getCenterFreq(index.toShort()) / 1000,
                        normalizedLevel = requestedCurve[index],
                        levelDb = levelMb / 100f
                    )
                },
                minDb = minMb / 100f,
                maxDb = maxMb / 100f,
                layoutId = requestedLayoutId,
                styleId = requestedStyleId,
                userPresetName = requestedUserName,
                hasUserPreset = savedUserCurve.isNotEmpty(),
                surroundAvailable = surroundEffect != null,
                surroundEnabled = runCatching { surroundEffect?.enabled == true }.getOrDefault(false),
                surroundStrength = requestedSurroundStrength,
                dtsAvailable = dtsDescriptor != null,
                dtsEnabled = requestedDtsEnabled && dtsDescriptor != null,
                dtsEffectName = dtsDescriptor?.name
            )
        }.getOrElse { unavailableSnapshot() }
    }

    fun release() {
        releaseEffect()
        persistCurrentState()
    }

    private fun unavailableSnapshot() = Snapshot.unavailable(
        enabled = requestedEnabled,
        presetId = requestedPresetId,
        layoutId = requestedLayoutId,
        styleId = requestedStyleId,
        userPresetName = requestedUserName,
        hasUserPreset = savedUserCurve.isNotEmpty(),
        surroundAvailable = surroundEffect != null,
        surroundEnabled = runCatching { surroundEffect?.enabled == true }.getOrDefault(false),
        surroundStrength = requestedSurroundStrength,
        dtsAvailable = dtsDescriptor != null,
        dtsEnabled = requestedDtsEnabled && dtsDescriptor != null,
        dtsEffectName = dtsDescriptor?.name
    )

    private fun ensureCurveSize(count: Int) {
        if (requestedCurve.size == count) return
        requestedCurve = resampleCurve(requestedCurve, count)
    }

    private fun resampleCurve(source: List<Float>, count: Int): MutableList<Float> {
        if (count <= 0) return mutableListOf()
        if (source.isEmpty()) return MutableList(count) { 0f }
        if (source.size == count) return source.map { it.coerceIn(-1f, 1f) }.toMutableList()
        return MutableList(count) { index ->
            val sourceIndex = if (count == 1) 0 else
                ((index.toFloat() / (count - 1)) * (source.size - 1)).roundToInt()
            source[sourceIndex.coerceIn(0, source.lastIndex)].coerceIn(-1f, 1f)
        }
    }

    private fun applyCurve(equalizer: Equalizer) {
        ensureCurveSize(equalizer.numberOfBands.toInt())
        requestedCurve.forEachIndexed { index, value ->
            setEffectBand(equalizer, index, value)
        }
    }

    private fun setEffectBand(equalizer: Equalizer, index: Int, normalized: Float) {
        val range = equalizer.bandLevelRange
        val minMb = range[0].toInt()
        val maxMb = range[1].toInt()
        val target = if (normalized >= 0f) {
            (normalized * maxMb).roundToInt()
        } else {
            (normalized * abs(minMb)).roundToInt()
        }.coerceIn(minMb, maxMb)
        runCatching { equalizer.setBandLevel(index.toShort(), target.toShort()) }
    }

    private fun presetCurve(presetId: String, equalizer: Equalizer): List<Float> {
        return (0 until equalizer.numberOfBands.toInt()).map { index ->
            val hz = equalizer.getCenterFreq(index.toShort()) / 1000
            when (presetId) {
                "bass" -> when {
                    hz < 80 -> .78f
                    hz < 180 -> .68f
                    hz < 400 -> .42f
                    hz < 900 -> .15f
                    hz < 3500 -> .02f
                    else -> .12f
                }
                "voice" -> when {
                    hz < 100 -> -.45f
                    hz < 350 -> -.18f
                    hz < 900 -> .18f
                    hz < 4000 -> .50f
                    hz < 8000 -> .22f
                    else -> -.05f
                }
                "music" -> when {
                    hz < 180 -> .42f
                    hz < 600 -> .20f
                    hz < 2500 -> .06f
                    hz < 7000 -> .24f
                    else -> .40f
                }
                "treble" -> when {
                    hz < 250 -> -.14f
                    hz < 1500 -> .02f
                    hz < 5000 -> .32f
                    else -> .60f
                }
                else -> 0f
            }
        }
    }

    private fun persistCurrentState() {
        prefs.edit()
            .putBoolean(KEY_ENABLED, requestedEnabled)
            .putString(KEY_PRESET, requestedPresetId)
            .putString(KEY_LAYOUT, requestedLayoutId)
            .putString(KEY_STYLE, requestedStyleId)
            .putString(KEY_USER_NAME, requestedUserName)
            .putBoolean(KEY_SURROUND_ENABLED, requestedSurroundEnabled)
            .putFloat(KEY_SURROUND_STRENGTH, requestedSurroundStrength)
            .putBoolean(KEY_DTS_ENABLED, requestedDtsEnabled)
            .putString(KEY_CURRENT_CURVE, encodeCurve(requestedCurve))
            .apply()
    }

    private fun encodeCurve(values: List<Float>): String =
        values.joinToString(",") { String.format(java.util.Locale.ROOT, "%.5f", it.coerceIn(-1f, 1f)) }

    private fun decodeCurve(raw: String?): MutableList<Float> {
        if (raw.isNullOrBlank()) return mutableListOf()
        return raw.split(',')
            .mapNotNull { it.toFloatOrNull()?.coerceIn(-1f, 1f) }
            .toMutableList()
    }

    @Suppress("DEPRECATION")
    private fun attachSurround(audioSessionId: Int) {
        runCatching {
            surroundEffect?.enabled = false
            surroundEffect?.release()
        }
        surroundEffect = null
        if (audioSessionId <= 0) return

        surroundEffect = runCatching {
            Virtualizer(0, audioSessionId).also { virtualizer ->
                if (virtualizer.strengthSupported) {
                    virtualizer.setStrength((requestedSurroundStrength * 1000f).roundToInt().toShort())
                }
                virtualizer.enabled = requestedSurroundEnabled
            }
        }.getOrNull()
    }

    private fun attachDts(audioSessionId: Int) {
        dtsDescriptor = null
        if (audioSessionId <= 0) return

        dtsDescriptor = runCatching {
            AudioEffect.queryEffects()
                ?.firstOrNull { candidate ->
                    val identity = "${candidate.name} ${candidate.implementor}".lowercase()
                    identity.contains("dts") &&
                        candidate.connectMode.equals(AudioEffect.EFFECT_INSERT, ignoreCase = true)
                }
        }.getOrNull()

        if (dtsDescriptor != null && requestedDtsEnabled) {
            signalDeviceDtsSession(open = true, audioSessionId = audioSessionId)
        } else if (dtsDescriptor == null) {
            requestedDtsEnabled = false
        }
    }

    /**
     * Android does not expose a public generic constructor for vendor AudioEffect implementations.
     * Vendor effects such as DTS are instead driven through the documented audio-effect control
     * session broadcasts, allowing the OEM effect service to apply its saved settings to this
     * ExoPlayer audio session.
     */
    private fun signalDeviceDtsSession(open: Boolean, audioSessionId: Int) {
        if (audioSessionId <= 0) return
        val action = if (open) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
        } else {
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
        }
        val intent = Intent(action).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, appContext.packageName)
            if (open) {
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MOVIE)
            }
        }
        runCatching { appContext.sendBroadcast(intent) }
    }

    private fun releaseEffect() {
        runCatching {
            effect?.enabled = false
            effect?.release()
        }
        runCatching {
            surroundEffect?.enabled = false
            surroundEffect?.release()
        }
        if (dtsDescriptor != null && requestedDtsEnabled && attachedSessionId > 0) {
            signalDeviceDtsSession(open = false, audioSessionId = attachedSessionId)
        }
        effect = null
        surroundEffect = null
        dtsDescriptor = null
        attachedSessionId = 0
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_PRESET = "preset"
        const val KEY_LAYOUT = "layout"
        const val KEY_STYLE = "style"
        const val KEY_USER_NAME = "user_name"
        const val KEY_CURRENT_CURVE = "current_curve"
        const val KEY_USER_CURVE = "user_curve"
        const val KEY_SURROUND_ENABLED = "surround_enabled"
        const val KEY_SURROUND_STRENGTH = "surround_strength"
        const val KEY_DTS_ENABLED = "dts_enabled"
    }
}
