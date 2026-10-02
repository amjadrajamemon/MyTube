package com.drivedeck.tubedeck.player

/**
 * Tracks whether a Back press is the second press inside a short consecutive-press window.
 *
 * The caller owns any delayed single-press action. This class only owns the timing state so the
 * behavior is identical in MainActivity and NativePlayerActivity and can be unit-tested.
 */
internal class ConsecutiveBackPressDetector(
    private val windowMs: Long = DEFAULT_WINDOW_MS
) {
    companion object {
        const val DEFAULT_WINDOW_MS = 900L
    }

    private var firstPressAtMs: Long = 0L

    fun registerPress(nowMs: Long): Boolean {
        val isSecond =
            firstPressAtMs > 0L &&
                nowMs >= firstPressAtMs &&
                nowMs - firstPressAtMs <= windowMs

        firstPressAtMs = if (isSecond) 0L else nowMs
        return isSecond
    }

    fun clear() {
        firstPressAtMs = 0L
    }
}
