package com.drivedeck.tubedeck.player

import java.net.URI
import java.util.Locale

/**
 * Creator-supplied YouTube links from video descriptions. These are NOT
 * YouTube's timestamped, proprietary in-video "i" cards/end-screen metadata.
 * Do not represent generic recommendations as creator-authored links.
 */
internal object CreatorLinksPolicy {
    enum class Kind { VIDEO, PLAYLIST, CHANNEL }
    data class Link(val url: String, val kind: Kind, val videoId: String? = null, val label: String)

    private val urlPattern = Regex(
        """https?://(?:www\.|m\.|music\.)?(?:youtube\.com|youtu\.be)/[^\s<>"'\\]+""",
        RegexOption.IGNORE_CASE
    )
    private val youtubeId = Regex("[A-Za-z0-9_-]{11}")

    fun extract(description: String, max: Int = 8): List<Link> {
        if (description.isBlank()) return emptyList()
        val normalized = description.replace("&amp;", "&")
        return urlPattern.findAll(normalized.take(30_000))
            .mapNotNull { match ->
                val candidate = match.value.trimEnd('.', ',', ')', ';', ']', '>')
                val uri = runCatching { URI(candidate) }.getOrNull() ?: return@mapNotNull null
                if (uri.scheme?.lowercase(Locale.ROOT) != "https") return@mapNotNull null
                val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
                if (host !in setOf("youtube.com", "www.youtube.com", "m.youtube.com",
                        "music.youtube.com", "youtu.be", "www.youtu.be")) return@mapNotNull null
                val path = uri.path.orEmpty()
                val query = uri.rawQuery.orEmpty()
                val id = if (host.endsWith("youtu.be")) path.trim('/').takeIf { youtubeId.matches(it) }
                    else Regex("(?:^|&)v=([A-Za-z0-9_-]{11})(?:&|$)").find(query)?.groupValues?.get(1)
                when {
                    id != null -> Link("https://www.youtube.com/watch?v=$id", Kind.VIDEO,
                        id, "Creator-linked video")
                    path.startsWith("/playlist") && query.contains("list=") ->
                        Link(candidate, Kind.PLAYLIST, label = "Creator-linked playlist")
                    path.startsWith("/channel/") || path.startsWith("/@") ||
                        path.startsWith("/c/") || path.startsWith("/user/") ->
                        Link(candidate, Kind.CHANNEL, label = "Creator channel")
                    else -> null
                }
            }
            .distinctBy { it.url }
            .take(max.coerceIn(1, 12))
            .toList()
    }
}
