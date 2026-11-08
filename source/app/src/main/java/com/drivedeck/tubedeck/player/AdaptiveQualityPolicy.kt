package com.drivedeck.tubedeck.player

/**
 * Conservative quality fallback for individually extracted YouTube URLs.
 * Unlike HLS/DASH this switch re-prepares a MediaSource at the same position;
 * it cannot promise zero buffering. Prioritize fewer stalls over sharpness.
 */
internal object AdaptiveQualityPolicy {
    data class Decision(val targetHeight: Int, val upgrade: Boolean)

    fun nextLower(heights: List<Int>, current: Int): Int? =
        heights.filter { it > 0 && it < current }.maxOrNull()

    /**
     * Inputs MUST be compatible, physically selectable variants. If the player
     * can only decode 480p, it cannot "downshift" to a non-existent 360p.
     */
    fun decide(
        heights: List<Int>,
        current: Int,
        recommended: Int,
        bufferedAheadMs: Long,
        buffering: Boolean,
        lowBufferSamples: Int,
        stableSamples: Int,
        upgradesSoFar: Int,
        allowUpgrade: Boolean,
        millisSinceLastSwitch: Long,
        downshiftedBefore: Boolean = false
    ): Decision? {
        if (current <= 0) return null
        val available = heights.filter { it > 0 }.distinct().sorted()
        if (current !in available) return null

        // NetworkCapabilities reports link speed, not usable internet speed.
        // A repeatedly exhausted playback buffer is stronger evidence.
        val starving = lowBufferSamples >= 2 &&
            (buffering || bufferedAheadMs < 7_000L)
        val clearlyUnderBandwidth = recommended < current &&
            (buffering || bufferedAheadMs < 12_000L)
        if (millisSinceLastSwitch >= 3_000L && (starving || clearlyUnderBandwidth)) {
            val next = nextLower(available, current) ?: return null
            val target = if (clearlyUnderBandwidth) {
                available.filter { it <= recommended && it < current }.maxOrNull() ?: next
            } else next
            return Decision(targetHeight = target, upgrade = false)
        }
        // Do not jump back up after a stall-driven downshift on unstable car
        // mobile networks. Changing Auto manually can create a fresh session.
        if (!downshiftedBefore && allowUpgrade && upgradesSoFar < 1 &&
            recommended > current && stableSamples >= 8 &&
            bufferedAheadMs >= 35_000L && millisSinceLastSwitch >= 30_000L) {
            val target = available.filter { it <= recommended }.maxOrNull()
            if (target != null && target > current) return Decision(target, upgrade = true)
        }
        return null
    }

    /** On a recoverable network error, Auto retries a cheaper stream first. */
    fun retryHeight(heights: List<Int>, current: Int, autoSelected: Boolean): Int {
        if (!autoSelected) return current
        return nextLower(heights, current) ?: current
    }
}
