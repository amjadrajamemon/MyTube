package com.drivedeck.tubedeck.player

import java.net.URI
import java.net.URLDecoder

/** Validate the host and the complete eleven-character ID before treating a URL as a video. */
object YoutubeUrls {
    private val idPattern = Regex("[A-Za-z0-9_-]{11}")

    fun videoId(value: String): String? {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() != "https") return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        val segments = uri.path.orEmpty().trim('/').split('/')
        val candidate = when (host) {
            "youtu.be" -> segments.firstOrNull()
            "youtube.com", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com" -> when (segments.firstOrNull()) {
                "watch" -> queryParameter(uri, "v")
                "shorts", "live", "embed" -> segments.getOrNull(1)
                else -> null
            }
            else -> null
        }
        return candidate?.takeIf { idPattern.matches(it) }
    }

    fun playlist(value: String): Boolean = playlistId(value) != null

    fun playlistId(value: String): String? {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() != "https") return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        if (host !in setOf("youtube.com", "m.youtube.com", "music.youtube.com")) return null
        return queryParameter(uri, "list")?.takeIf { it.isNotBlank() }
    }

    fun isYoutubePage(value: String): Boolean {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return false
        return uri.scheme?.lowercase() == "https" &&
            host in setOf("youtube.com", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com")
    }

    private fun queryParameter(uri: URI, name: String): String? =
        uri.rawQuery?.split('&')?.firstOrNull { it.substringBefore('=') == name }
            ?.substringAfter('=', "")
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
}
