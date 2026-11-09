package com.drivedeck.tubedeck.player

/**
 * A native-player watch qualifies only when time coverage is continuous from
 * near zero until the real end event. READY/buffering do not constitute a pause,
 * but a manual pause or seek disqualifies the current watch session.
 *
 * This class does not retain media bytes. The host must independently verify
 * download permission, connectivity, storage and an existing offline copy.
 */
internal class SmartOfflineReplayWatch {
    private var started = false
    private var invalidated = false
    private var completed = false
    private var lastPositionMs = 0L
    private var observedPlayMs = 0L

    fun sample(positionMs: Long, durationMs: Long) {
        if (completed || invalidated || durationMs < 5_000L || positionMs < 0L ||
            positionMs > durationMs + 3_000L) return
        if (!started) {
            if (positionMs > START_TOLERANCE_MS) {
                invalidated = true
                return
            }
            started = true
            lastPositionMs = positionMs
            return
        }
        val advance = positionMs - lastPositionMs
        if (advance < -250L || advance > MAX_SAMPLE_JUMP_MS) {
            invalidated = true
            return
        }
        if (advance > 0L) observedPlayMs += advance
        lastPositionMs = positionMs
    }

    fun onManualPause() {
        if (started) invalidated = true
    }

    fun onSeekOrSkip() {
        invalidated = true
    }

    /** Call only on Media3 STATE_ENDED; duration and last point must be real. */
    fun finish(positionMs: Long, durationMs: Long): Boolean {
        if (completed || invalidated) return false
        sample(positionMs, durationMs)
        val nearEnd = positionMs >= durationMs - END_TOLERANCE_MS
        val coverage = observedPlayMs >= (durationMs * REQUIRED_COVERAGE).toLong()
        if (started && nearEnd && coverage && durationMs >= 5_000L) {
            completed = true
            return true
        }
        return false
    }

    companion object {
        private const val START_TOLERANCE_MS = 2_000L
        private const val MAX_SAMPLE_JUMP_MS = 3_000L
        private const val END_TOLERANCE_MS = 2_000L
        private const val REQUIRED_COVERAGE = 0.94
    }
}

/** No quota is imposed: user-selected storage capacity is the only ceiling. */
internal object SmartOfflineReplayPolicy {
    fun eligibleForAutomaticSave(
        enabled: Boolean, completed: Boolean, videoId: String?,
        alreadySaved: Boolean, alreadyQueued: Boolean, networkPermitted: Boolean,
        enoughFreeStorage: Boolean
    ): Boolean = enabled && completed && !videoId.isNullOrBlank() &&
        !alreadySaved && !alreadyQueued && networkPermitted && enoughFreeStorage
}
