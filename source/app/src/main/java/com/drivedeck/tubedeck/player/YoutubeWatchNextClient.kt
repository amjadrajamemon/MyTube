package com.drivedeck.tubedeck.player

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight YouTube Watch-Next/InnerTube client.
 *
 * This mirrors the useful part of SmartTube's architecture: obtain YouTube's own playlist and
 * watch-next context for the current video instead of guessing collections from generic search.
 * The API key/client version are read from the current watch page so they are not hard-coded.
 */
object YoutubeWatchNextClient {
    data class ContextVideo(
        val videoId: String,
        val title: String,
        val uploader: String,
        val durationSeconds: Long,
        val thumbnailUrl: String,
        val viewCount: Long = -1L
    )

    data class ContextPlaylist(
        val playlistId: String,
        val title: String,
        val currentIndex: Int?,
        val totalVideos: Int?,
        val owner: String,
        val items: List<ContextVideo>
    )

    data class WatchNextContext(
        val playlists: List<ContextPlaylist>,
        val relatedVideos: List<ContextVideo>
    )

    private data class Config(
        val apiKey: String,
        val clientVersion: String,
        val visitorData: String?
    )

    suspend fun resolve(watchUrl: String, videoId: String): WatchNextContext =
        withContext(Dispatchers.IO) {
            val requestedPlaylistId = queryParameter(watchUrl, "list")
            val config = readConfig(watchUrl)

            val client = JSONObject()
                .put("clientName", "WEB")
                .put("clientVersion", config.clientVersion)
                .put("hl", "en")
                .put("gl", "US")
                .put("utcOffsetMinutes", 0)
            config.visitorData?.takeIf { it.isNotBlank() }?.let {
                client.put("visitorData", it)
            }

            val body = JSONObject()
                .put("context", JSONObject().put("client", client))
                .put("videoId", videoId)
                .put("racyCheckOk", true)
                .put("contentCheckOk", true)
            requestedPlaylistId?.let { body.put("playlistId", it) }

            val endpoint =
                "https://www.youtube.com/youtubei/v1/next?prettyPrint=false&key=" + config.apiKey
            val response = postJson(
                endpoint = endpoint,
                watchUrl = watchUrl,
                clientVersion = config.clientVersion,
                visitorData = config.visitorData,
                body = body.toString()
            )
            val root = JSONObject(response)

            val playlistMap = linkedMapOf<String, ContextPlaylist>()
            collectPlaylists(root, videoId, requestedPlaylistId, playlistMap)

            if (requestedPlaylistId != null && requestedPlaylistId !in playlistMap) {
                playlistMap[requestedPlaylistId] = ContextPlaylist(
                    playlistId = requestedPlaylistId,
                    title = "Current YouTube playlist",
                    currentIndex = null,
                    totalVideos = null,
                    owner = "YouTube",
                    items = emptyList()
                )
            }

            val related = linkedMapOf<String, ContextVideo>()
            collectVideoRenderers(root, related)

            playlistMap.values.flatMap { it.items }.forEach { related.remove(it.videoId) }
            related.remove(videoId)

            WatchNextContext(
                playlists = playlistMap.values.toList(),
                relatedVideos = related.values.take(60)
            )
        }

    private fun readConfig(watchUrl: String): Config {
        val html = get(watchUrl)
        val apiKey = firstGroup(
            html,
            """"INNERTUBE_API_KEY"\s*:\s*"([^"]+)"""",
            """INNERTUBE_API_KEY\\?":\\?"([^"\\]+)"""
        ) ?: error("YouTube watch page did not expose an InnerTube API key")

        val clientVersion = firstGroup(
            html,
            """"INNERTUBE_CLIENT_VERSION"\s*:\s*"([^"]+)"""",
            """INNERTUBE_CLIENT_VERSION\\?":\\?"([^"\\]+)"""
        ) ?: error("YouTube watch page did not expose an InnerTube client version")

        val visitor = firstGroup(
            html,
            """"VISITOR_DATA"\s*:\s*"([^"]+)"""",
            """VISITOR_DATA\\?":\\?"([^"\\]+)"""
        )

        return Config(apiKey, clientVersion, visitor)
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 25_000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
        }

        return try {
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code >= 400) error("YouTube watch page returned HTTP $code")
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun postJson(
        endpoint: String,
        watchUrl: String,
        clientVersion: String,
        visitorData: String?,
        body: String
    ): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            doOutput = true
            useCaches = false
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Origin", "https://www.youtube.com")
            setRequestProperty("Referer", watchUrl)
            setRequestProperty("X-Youtube-Client-Name", "1")
            setRequestProperty("X-Youtube-Client-Version", clientVersion)
            visitorData?.takeIf { it.isNotBlank() }?.let {
                setRequestProperty("X-Goog-Visitor-Id", it)
            }
        }

        return try {
            connection.outputStream.use { out ->
                out.write(body.toByteArray(StandardCharsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val response = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code >= 400) error("YouTube Watch Next returned HTTP $code")
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun collectPlaylists(
        node: Any?,
        currentVideoId: String,
        requestedPlaylistId: String?,
        output: MutableMap<String, ContextPlaylist>
    ) {
        when (node) {
            is JSONObject -> {
                val renderer = node.optJSONObject("playlistPanelRenderer")
                if (renderer != null) {
                    parsePlaylistObject(renderer, currentVideoId, requestedPlaylistId)?.let {
                        output.putIfAbsent(it.playlistId, it)
                    }
                }

                if (node.has("playlistId") && node.has("contents")) {
                    parsePlaylistObject(node, currentVideoId, requestedPlaylistId)?.let {
                        output.putIfAbsent(it.playlistId, it)
                    }
                }

                val keys = node.keys()
                while (keys.hasNext()) {
                    collectPlaylists(node.opt(keys.next()), currentVideoId, requestedPlaylistId, output)
                }
            }
            is JSONArray -> for (index in 0 until node.length()) {
                collectPlaylists(node.opt(index), currentVideoId, requestedPlaylistId, output)
            }
        }
    }

    private fun parsePlaylistObject(
        obj: JSONObject,
        currentVideoId: String,
        requestedPlaylistId: String?
    ): ContextPlaylist? {
        val playlistId = obj.optString("playlistId").ifBlank {
            requestedPlaylistId.orEmpty()
        }
        if (playlistId.isBlank()) return null

        val items = mutableListOf<ContextVideo>()
        collectPlaylistItems(obj.opt("contents"), items)
        val containsCurrent = items.any { it.videoId == currentVideoId }

        if (!containsCurrent && requestedPlaylistId != playlistId) return null

        val title = textOf(obj.opt("title")).ifBlank { "YouTube playlist" }
        val owner = textOf(obj.opt("ownerName")).ifBlank {
            textOf(obj.opt("longBylineText")).ifBlank { "YouTube" }
        }

        return ContextPlaylist(
            playlistId = playlistId,
            title = title,
            currentIndex = obj.optInt("currentIndex").takeIf { obj.has("currentIndex") },
            totalVideos = obj.optInt("totalVideos").takeIf { obj.has("totalVideos") },
            owner = owner,
            items = items.distinctBy { it.videoId }
        )
    }

    private fun collectPlaylistItems(node: Any?, output: MutableList<ContextVideo>) {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("playlistPanelVideoRenderer")?.let {
                    parseVideoRenderer(it)?.let(output::add)
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    collectPlaylistItems(node.opt(keys.next()), output)
                }
            }
            is JSONArray -> for (index in 0 until node.length()) {
                collectPlaylistItems(node.opt(index), output)
            }
        }
    }

    private fun collectVideoRenderers(node: Any?, output: MutableMap<String, ContextVideo>) {
        when (node) {
            is JSONObject -> {
                for (key in VIDEO_RENDERER_KEYS) {
                    node.optJSONObject(key)?.let { renderer ->
                        parseVideoRenderer(renderer)?.let { video ->
                            output.putIfAbsent(video.videoId, video)
                        }
                    }
                }

                val keys = node.keys()
                while (keys.hasNext()) {
                    collectVideoRenderers(node.opt(keys.next()), output)
                }
            }
            is JSONArray -> for (index in 0 until node.length()) {
                collectVideoRenderers(node.opt(index), output)
            }
        }
    }

    private fun parseVideoRenderer(renderer: JSONObject): ContextVideo? {
        val videoId = renderer.optString("videoId").ifBlank {
            renderer.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
                .orEmpty()
        }
        if (videoId.length < 6) return null

        val title = textOf(renderer.opt("title")).ifBlank {
            textOf(renderer.opt("headline"))
        }
        if (title.isBlank()) return null

        val uploader = sequenceOf("longBylineText", "shortBylineText", "ownerText")
            .map { textOf(renderer.opt(it)) }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        val durationText = textOf(renderer.opt("lengthText"))
        val thumbnails = renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbnail = if (thumbnails != null && thumbnails.length() > 0) {
            thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url").orEmpty()
        } else {
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        }

        val viewCountText = textOf(renderer.opt("viewCountText")).ifBlank {
            textOf(renderer.opt("shortViewCountText"))
        }

        return ContextVideo(
            videoId = videoId,
            title = title,
            uploader = uploader,
            durationSeconds = parseDuration(durationText),
            thumbnailUrl = thumbnail,
            viewCount = parseViewCount(viewCountText)
        )
    }

    private fun textOf(value: Any?): String = when (value) {
        is String -> value
        is JSONObject -> {
            value.optString("simpleText").takeIf { it.isNotBlank() }
                ?: value.optJSONArray("runs")?.let { runs ->
                    buildString {
                        for (index in 0 until runs.length()) {
                            append(runs.optJSONObject(index)?.optString("text").orEmpty())
                        }
                    }
                }.orEmpty()
        }
        else -> ""
    }.trim()

    private fun parseDuration(text: String): Long {
        if (text.isBlank()) return 0L
        val parts = text.trim().split(':').mapNotNull { it.toLongOrNull() }
        if (parts.isEmpty()) return 0L
        return parts.fold(0L) { total, part -> total * 60L + part }
    }

    private fun parseViewCount(text: String): Long {
        val clean = text.lowercase().replace(",", "").trim()
        Regex("(\\d+(?:\\.\\d+)?)\\s*([kmb])?").find(clean)?.let { match ->
            val value = match.groupValues[1].toDoubleOrNull() ?: return -1L
            val multiplier = when (match.groupValues[2]) {
                "k" -> 1_000.0
                "m" -> 1_000_000.0
                "b" -> 1_000_000_000.0
                else -> 1.0
            }
            return (value * multiplier).toLong().coerceAtLeast(0L)
        }
        return -1L
    }

    private fun firstGroup(input: String, vararg patterns: String): String? {
        for (pattern in patterns) {
            val match = Regex(pattern).find(input) ?: continue
            return match.groupValues.getOrNull(1)?.replace("\\u0026", "&")
        }
        return null
    }

    private fun queryParameter(url: String, name: String): String? {
        val match = Regex("(?:[?&])" + Regex.escape(name) + "=([^&#]+)").find(url) ?: return null
        return runCatching {
            URLDecoder.decode(match.groupValues[1], StandardCharsets.UTF_8.name())
        }.getOrNull()
    }

    private val VIDEO_RENDERER_KEYS = listOf(
        "compactVideoRenderer",
        "videoRenderer",
        "gridVideoRenderer",
        "playlistPanelVideoRenderer"
    )

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"
}
