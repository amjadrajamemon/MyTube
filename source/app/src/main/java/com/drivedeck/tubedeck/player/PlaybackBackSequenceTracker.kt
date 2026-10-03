package com.drivedeck.tubedeck.player

/**
 * Process-wide Back gesture tracker so a sequence can span NativePlayerActivity -> MainActivity
 * while system PiP is active.
 *
 * Rapid presses (<= 550 ms apart) belong to one gesture. Slow presses start a fresh sequence.
 */
internal object PlaybackBackSequenceTracker {
    private const val RAPID_GAP_MS = 550L

    private var lastPressMs = 0L
    private var count = 0

    @Synchronized
    fun record(nowMs: Long): Int {
        count = if (lastPressMs > 0L && nowMs - lastPressMs <= RAPID_GAP_MS) {
            count + 1
        } else {
            1
        }
        lastPressMs = nowMs
        return count
    }

    @Synchronized
    fun reset() {
        lastPressMs = 0L
        count = 0
    }

    fun rapidGapMs(): Long = RAPID_GAP_MS
}
