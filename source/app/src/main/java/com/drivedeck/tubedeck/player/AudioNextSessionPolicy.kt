package com.drivedeck.tubedeck.player

/**
 * Audio-only continuation must use the same no-repeat rule as the native player.
 *
 * This is session-local: a new explicit Play starts a fresh recommendation chain,
 * while automatic/manual Next retains up to [maxHistory] recently played IDs.
 * No URL, thumbnail or entire resolved-media object is retained in memory.
 */
internal class AudioNextSessionPolicy(private val maxHistory: Int = 80) {
    init {
        require(maxHistory >= 1)
    }

    private val played = LinkedHashSet<String>()

    fun startExplicitPlayback(videoId: String) {
        played.clear()
        recordPlayed(videoId)
    }

    fun recordPlayed(videoId: String) {
        if (videoId.isBlank()) return
        played.remove(videoId)
        played.add(videoId)
        while (played.size > maxHistory) {
            val iterator = played.iterator()
            iterator.next()
            iterator.remove()
        }
    }

    fun firstUnplayed(currentVideoId: String, candidates: Iterable<String>): String? =
        PlaylistQueuePolicy.nextUnplayed(candidates, currentVideoId, played)

    fun recentCount(): Int = played.size
}
