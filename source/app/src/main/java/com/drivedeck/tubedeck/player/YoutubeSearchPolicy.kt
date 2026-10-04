package com.drivedeck.tubedeck.player

import java.net.URLEncoder

/**
 * Centralizes YouTube web-search routing. Active live discovery is handled natively by
 * YoutubeStreamResolver because YouTube's web Live filter token is not stable enough to be the
 * sole discovery path.
 */
object YoutubeSearchPolicy {
    private const val PLAYLIST_FILTER = "EgIQAw%253D%253D"

    private val liveIntent = Regex(
        """(?i)(?:\blive\b|\blivestream(?:ing)?\b|\blive\s*stream(?:ing)?\b)"""
    )

    fun isLiveIntent(query: String): Boolean =
        liveIntent.containsMatchIn(query.trim())

    fun buildSearchUrl(query: String, playlistOnly: Boolean = false): String {
        val clean = query.trim()
        val encoded = URLEncoder.encode(clean, "UTF-8")
        val filter = if (playlistOnly) PLAYLIST_FILTER else null
        return buildString {
            append("https://www.youtube.com/results?search_query=")
            append(encoded)
            if (filter != null) append("&sp=").append(filter)
        }
    }
}
