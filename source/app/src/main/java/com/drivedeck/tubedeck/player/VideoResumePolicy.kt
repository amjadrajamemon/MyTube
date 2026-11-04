package com.drivedeck.tubedeck.player

/**
 * Pure, platform-independent playback-position policy. A stream's URL may
 * vary with playlist, search and short links; video identity must not.
 */
internal object VideoResumePolicy {
    const val MIN_POSITION_MS = 5_000L
    private const val END_GUARD_MS = 5_000L

    fun mediaKey(uri: String): String =
        YoutubeUrls.canonicalVideoUrl(uri) ?: uri.trim()

    fun canResume(positionMs: Long): Boolean = positionMs >= MIN_POSITION_MS

    fun shouldSave(positionMs: Long, durationMs: Long = 0L): Boolean {
        if (!canResume(positionMs)) return false
        return durationMs <= 0L || positionMs < durationMs - END_GUARD_MS
    }

    /** Prevent incomplete preparation from erasing a previously saved entry. */
    fun shouldSnapshot(
        playbackPrepared: Boolean,
        promptVisible: Boolean,
        positionMs: Long,
        completed: Boolean
    ): Boolean = playbackPrepared && !promptVisible &&
        (completed || canResume(positionMs))
}
