package com.drivedeck.tubedeck.player

/**
 * Only prompt when there is substantial, recent, unfinished playback with
 * a reliable duration. Legacy entries without duration do not create prompts.
 */
internal object VideoResumePolicy {
    const val MIN_POSITION_MS = 30_000L
    const val DEFAULT_PROMPT_SECONDS = 3
    const val MIN_PROMPT_SECONDS = 1
    const val MAX_PROMPT_SECONDS = 15
    private const val MAX_ENTRY_AGE_MS = 30L * 24 * 60 * 60 * 1000

    fun mediaKey(uri: String): String =
        YoutubeUrls.canonicalVideoUrl(uri) ?: uri.trim()

    fun canResume(positionMs: Long): Boolean = positionMs >= MIN_POSITION_MS

    fun promptSeconds(seconds: Int): Int =
        seconds.coerceIn(MIN_PROMPT_SECONDS, MAX_PROMPT_SECONDS)

    fun shouldSave(positionMs: Long, durationMs: Long = 0L): Boolean {
        if (!canResume(positionMs) || durationMs <= 0L) return false
        val minimumMeaningfulProgress = maxOf(
            MIN_POSITION_MS, (durationMs * 0.03).toLong().coerceAtMost(120_000L)
        )
        val finishGuard = maxOf(
            15_000L, (durationMs * 0.05).toLong().coerceAtMost(120_000L)
        )
        return positionMs >= minimumMeaningfulProgress &&
            positionMs < durationMs - finishGuard
    }

    fun shouldOfferPrompt(
        positionMs: Long,
        durationMs: Long,
        updatedAtMs: Long,
        nowMs: Long
    ): Boolean {
        if (updatedAtMs <= 0L || nowMs < updatedAtMs ||
            nowMs - updatedAtMs > MAX_ENTRY_AGE_MS) return false
        return shouldSave(positionMs, durationMs)
    }

    /** Avoid snapshotting a zero/IDLE position before a real playback starts. */
    fun shouldSnapshot(
        playbackPrepared: Boolean,
        promptVisible: Boolean,
        positionMs: Long,
        completed: Boolean
    ): Boolean = playbackPrepared && !promptVisible &&
        (completed || canResume(positionMs))
}
