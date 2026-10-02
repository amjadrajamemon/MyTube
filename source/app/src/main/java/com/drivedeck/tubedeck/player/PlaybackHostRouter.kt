package com.drivedeck.tubedeck.player

import com.drivedeck.tubedeck.data.model.PlayerMode

internal enum class PlaybackHost {
    WEB,
    NATIVE
}

/**
 * Single authority for deciding which player owns a media item.
 *
 * Online YouTube videos honor the saved Web/Native preference. Local/content media always use
 * Media3 because the browser cannot own Android document/file playback.
 */
internal object PlaybackHostRouter {
    fun choose(playerMode: PlayerMode, mediaUri: String): PlaybackHost =
        if (YoutubeUrls.videoId(mediaUri) != null && playerMode == PlayerMode.WEB) {
            PlaybackHost.WEB
        } else {
            PlaybackHost.NATIVE
        }
}
