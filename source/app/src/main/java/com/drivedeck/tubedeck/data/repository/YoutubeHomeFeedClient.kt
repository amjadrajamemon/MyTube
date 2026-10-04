package com.drivedeck.tubedeck.data.repository

import com.drivedeck.tubedeck.data.model.HomeVideo
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object YoutubeHomeFeedClient {
    fun trending(token: String, country: String): List<HomeVideo> {
        require(country.matches(Regex("[A-Z]{2}")))
        val connection = (URL("https://www.googleapis.com/youtube/v3/videos?part=snippet,contentDetails&chart=mostPopular&regionCode=$country&maxResults=12")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            check(connection.responseCode == 200) { "Trending unavailable" }
            val items = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONArray("items")
            return List(items.length()) { index ->
                val item = items.getJSONObject(index)
                val snippet = item.getJSONObject("snippet")
                val thumbnails = snippet.optJSONObject("thumbnails")
                HomeVideo(
                    videoId = item.getString("id"),
                    title = snippet.getString("title"),
                    url = "https://www.youtube.com/watch?v=${item.getString("id")}",
                    thumbnailUrl = thumbnails?.optJSONObject("medium")?.optString("url")
                        ?: thumbnails?.optJSONObject("default")?.optString("url").orEmpty(),
                    channelTitle = snippet.optString("channelTitle"),
                    watchedAt = 0L
                )
            }
        } finally {
            connection.disconnect()
        }
    }
}
