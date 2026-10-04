package com.drivedeck.tubedeck.player

import java.net.URLEncoder

/**
 * Centralizes YouTube search routing. Explicit live intent uses YouTube's Live feature filter
 * instead of relying on generic relevance ranking, which can bury an active broadcast under clips.
 */
object YoutubeSearchPolicy {
    private const val LIVE_FILTER = "EgJAAQ%253D%253D"
    private const val PLAYLIST_FILTER = "EgIQAw%253D%253D"

    private val liveIntent = Regex(
        """(?i)(?:\blive\b|\blivestream(?:ing)?\b|\blive\s*stream(?:ing)?\b)"""
    )

    fun isLiveIntent(query: String): Boolean =
        liveIntent.containsMatchIn(query.trim())

    fun buildSearchUrl(query: String, playlistOnly: Boolean = false): String {
        val clean = query.trim()
        val encoded = URLEncoder.encode(clean, "UTF-8")
        val filter = when {
            playlistOnly -> PLAYLIST_FILTER
            isLiveIntent(clean) -> LIVE_FILTER
            else -> null
        }
        return buildString {
            append("https://www.youtube.com/results?search_query=")
            append(encoded)
            if (filter != null) append("&sp=").append(filter)
        }
    }
}
