package com.drivedeck.tubedeck.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Small, bounded cache; the UI debounces calls and cancels stale queries. */
internal object YoutubeSearchSuggestions {
    private data class Cached(val at: Long, val terms: List<String>)
    private val cache = LinkedHashMap<String, Cached>(16, 0.75f, true)

    suspend fun fetch(query: String): List<String> = withContext(Dispatchers.IO) {
        val clean = SearchAssistPolicy.clean(query)
        if (clean.isBlank() || SearchAssistPolicy.isUrl(clean)) return@withContext emptyList()
        val language = Locale.getDefault().language.ifBlank { "en" }
        val key = "$language:${clean.lowercase(Locale.ROOT)}"
        synchronized(cache) {
            cache[key]?.takeIf { System.currentTimeMillis() - it.at < 300_000L }?.terms
        }?.let { return@withContext it }
        val endpoint = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&ie=utf-8&oe=utf-8" +
            "&hl=${URLEncoder.encode(language, "UTF-8")}&q=${URLEncoder.encode(clean, "UTF-8")}"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 3_000
            connection.readTimeout = 3_000
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext emptyList()
            val payload = connection.inputStream.use { input ->
                val bytes = input.readBytesLimited(32_768)
                String(bytes, Charsets.UTF_8)
            }
            val terms = parse(payload)
            synchronized(cache) {
                cache[key] = Cached(System.currentTimeMillis(), terms)
                while (cache.size > 16) cache.remove(cache.keys.first())
            }
            terms
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(payload: String): List<String> {
        val rows = JSONArray(payload).optJSONArray(1) ?: return emptyList()
        return (0 until minOf(rows.length(), 12)).map { index ->
            val value = rows.opt(index)
            if (value is JSONArray) value.optString(0) else value as? String ?: ""
        }.map(SearchAssistPolicy::clean).filter { it.isNotBlank() }.distinct().take(8)
    }

    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(2_048)
        while (output.size() < limit) {
            val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
