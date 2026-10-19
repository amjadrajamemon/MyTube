package com.drivedeck.tubedeck.player

/** A missing low-resolution representation must fall back to the smallest available stream. */
internal object PlaybackQualityPolicy {
    fun chooseHeight(heights: List<Int>, requestedHeight: Int, compatibilityMode: Boolean): Int? {
        val available = heights.filter { it > 0 }.distinct()
        val target = if (compatibilityMode) requestedHeight.coerceAtMost(480) else requestedHeight
        return available.filter { it <= target }.maxOrNull() ?: available.minOrNull()
    }
}
