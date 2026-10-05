package com.drivedeck.tubedeck.player

/** Pure policy so playlist-vs-Mix ordering cannot regress unnoticed. */
internal object PlaylistQueuePolicy {
    fun isDynamicMix(playlistId: String?): Boolean =
        !playlistId.isNullOrBlank() && playlistId.startsWith("RD", ignoreCase = true)

    fun preferContextualWatchNext(playlistId: String?, currentVideoId: String?): Boolean =
        isDynamicMix(playlistId) && !currentVideoId.isNullOrBlank()
}
