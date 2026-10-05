package com.drivedeck.tubedeck.player

import android.media.audiofx.Equalizer
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Small lifecycle-safe wrapper around Android's platform Equalizer.
 *
 * The controller keeps the requested curve when ExoPlayer rotates its audio session so playback,
 * seeks, source changes and decoder recovery do not unexpectedly reset the user's EQ.
 */
class PlaybackEqualizerController {

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
        val maxDb: Float
    ) {
        companion object {
            fun unavailable(enabled: Boolean = false, presetId: String = "flat") = Snapshot(
                supported = false,
                enabled = enabled,
                presetId = presetId,
                bands = emptyList(),
                minDb = -15f,
                maxDb = 15f
            )
        }
    }

    private var effect: Equalizer? = null
    private var requestedEnabled = false
    private var requestedPresetId = "flat"
    private var requestedCurve: MutableList<Float> = mutableListOf()
    private var attachedSessionId = 0

    fun attach(audioSessionId: Int): Snapshot {
        if (audioSessionId <= 0) return snapshot()
        if (attachedSessionId == audioSessionId && effect != null) return snapshot()

        releaseEffect()
        attachedSessionId = audioSessionId
        return try {
            val equalizer = Equalizer(0, audioSessionId)
            effect = equalizer
            ensureCurveSize(equalizer.numberOfBands.toInt())

            if (requestedPresetId != "custom") {
                requestedCurve = presetCurve(requestedPresetId, equalizer)
                    .toMutableList()
            }
            applyCurve(equalizer)
            equalizer.enabled = requestedEnabled
            snapshot()
        } catch (_: Throwable) {
            releaseEffect()
            Snapshot.unavailable(requestedEnabled, requestedPresetId)
        }
    }

    fun setEnabled(enabled: Boolean): Snapshot {
        requestedEnabled = enabled
        runCatching { effect?.enabled = enabled }
        return snapshot()
    }

    fun applyPreset(presetId: String): Snapshot {
        requestedPresetId = presetId
        effect?.let { equalizer ->
            requestedCurve = presetCurve(presetId, equalizer).toMutableList()
            applyCurve(equalizer)
        }
        return snapshot()
    }

    fun setBandNormalized(index: Int, normalized: Float): Snapshot {
        val equalizer = effect ?: return snapshot()
        ensureCurveSize(equalizer.numberOfBands.toInt())
        if (index !in requestedCurve.indices) return snapshot()

        requestedPresetId = "custom"
        requestedCurve[index] = normalized.coerceIn(-1f, 1f)
        setEffectBand(equalizer, index, requestedCurve[index])
        return snapshot()
    }

    fun snapshot(): Snapshot {
        val equalizer = effect ?: return Snapshot.unavailable(requestedEnabled, requestedPresetId)
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
                maxDb = maxMb / 100f
            )
        }.getOrElse {
            Snapshot.unavailable(requestedEnabled, requestedPresetId)
        }
    }

    fun release() {
        releaseEffect()
        requestedCurve.clear()
        requestedEnabled = false
        requestedPresetId = "flat"
    }

    private fun ensureCurveSize(count: Int) {
        if (requestedCurve.size == count) return
        val old = requestedCurve.toList()
        requestedCurve = MutableList(count) { index ->
            if (old.isEmpty()) 0f
            else old[((index.toFloat() / (count - 1).coerceAtLeast(1)) * (old.size - 1)).roundToInt()]
                .coerceIn(-1f, 1f)
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
                    hz < 120 -> .72f
                    hz < 300 -> .52f
                    hz < 700 -> .22f
                    hz < 3000 -> .02f
                    else -> .12f
                }
                "voice" -> when {
                    hz < 120 -> -.40f
                    hz < 350 -> -.18f
                    hz < 900 -> .18f
                    hz < 4000 -> .48f
                    hz < 8000 -> .20f
                    else -> -.06f
                }
                "music" -> when {
                    hz < 180 -> .40f
                    hz < 600 -> .20f
                    hz < 2500 -> .06f
                    hz < 7000 -> .22f
                    else -> .38f
                }
                "treble" -> when {
                    hz < 250 -> -.12f
                    hz < 1500 -> .02f
                    hz < 5000 -> .30f
                    else -> .58f
                }
                else -> 0f
            }
        }
    }

    private fun releaseEffect() {
        runCatching {
            effect?.enabled = false
            effect?.release()
        }
        effect = null
        attachedSessionId = 0
    }
}
