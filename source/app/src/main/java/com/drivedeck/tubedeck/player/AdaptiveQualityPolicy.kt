package com.drivedeck.tubedeck.player

/** Playback quality rules for extracted single-bitrate YouTube media URLs.
 * This is NOT manifest-level seamless ABR: each progressive quality selection
 * prepares another MediaSource and attempts to preserve the current position.
 */
internal object AdaptiveQualityPolicy {
    data class Decision(val targetHeight: Int, val upgrade: Boolean)

    fun nextLower(heights: List<Int>, current: Int): Int? =
        heights.filter { it > 0 && it < current }.maxOrNull()

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
        millisSinceLastSwitch: Long
    ): Decision? {
        if (current <= 0) return null
        val available = heights.filter { it > 0 }.distinct().sorted()
        if (current !in available) return null
        // Repeated stalls are stronger evidence than Android's sometimes stale
        // connectivity bandwidth estimate (particularly on aftermarket head units).
        val prolongedBufferStarvation = lowBufferSamples >= 2 &&
            (buffering || bufferedAheadMs < 4_000L)
        val measuredDownshift = recommended < current &&
            (buffering || bufferedAheadMs < 8_000L)
        if (millisSinceLastSwitch >= 5_000L &&
            (measuredDownshift || prolongedBufferStarvation)) {
            val next = nextLower(available, current) ?: return null
            val target = if (measuredDownshift) {
                available.filter { it <= recommended }.maxOrNull() ?: next
            } else next
            return Decision(target, upgrade = false)
        }
        // Upgrades are deliberately conservative: avoid oscillation on weak LTE.
        if (allowUpgrade && upgradesSoFar < 1 && recommended > current &&
            stableSamples >= 6 && bufferedAheadMs >= 30_000L &&
            millisSinceLastSwitch >= 20_000L) {
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
