package com.drivedeck.tubedeck.player

/** Pure policy so playlist-vs-Mix ordering cannot regress unnoticed. */
internal object PlaylistQueuePolicy {
    fun isDynamicMix(playlistId: String?): Boolean =
        !playlistId.isNullOrBlank() && playlistId.startsWith("RD", ignoreCase = true)

    fun preferContextualWatchNext(playlistId: String?, currentVideoId: String?): Boolean =
        isDynamicMix(playlistId) && !currentVideoId.isNullOrBlank()

    /** Never replay a recently watched video just because it is recommended again. */
    fun nextUnplayed(candidates: Iterable<String>, currentVideoId: String, playedVideoIds: Set<String>): String? =
        candidates.firstOrNull { it.isNotBlank() && it != currentVideoId && it !in playedVideoIds }
}
