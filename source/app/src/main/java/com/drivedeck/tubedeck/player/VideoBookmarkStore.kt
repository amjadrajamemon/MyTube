package com.drivedeck.tubedeck.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small, bounded local bookmark index; no account, remote server, or background work. */
internal class VideoBookmarkStore(context: Context) {
    data class Bookmark(
        val videoId: String,
        val title: String,
        val positionMs: Long,
        val note: String,
        val createdAtMs: Long
    )

    private val prefs = context.applicationContext.getSharedPreferences("mytube_video_bookmarks", Context.MODE_PRIVATE)

    companion object {
        private const val MAX_BOOKMARKS = 240
        private const val MAX_PER_VIDEO = 30
        private val lock = Any()

        fun shareUrl(videoId: String, positionMs: Long): String? {
            if (!videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) return null
            return "https://youtu.be/$videoId?t=${(positionMs / 1000L).coerceAtLeast(0)}"
        }

        private fun parse(raw: String): List<Bookmark> {
            val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
            return buildList {
                for (i in 0 until minOf(MAX_BOOKMARKS, array.length())) {
                    val o = array.optJSONObject(i) ?: continue
                    val videoId = o.optString("id").trim()
                    val pos = o.optLong("time", -1L)
                    if (videoId.isBlank() || videoId.length > 128 || pos < 0L) continue
                    add(Bookmark(
                        videoId = videoId,
                        title = o.optString("title").take(180),
                        positionMs = pos,
                        note = o.optString("note").take(160),
                        createdAtMs = o.optLong("created").coerceAtLeast(0L)
                    ))
                }
            }
        }

        private fun encode(items: List<Bookmark>): String = JSONArray().apply {
            items.forEach { b ->
                put(JSONObject().apply {
                    put("id", b.videoId)
                    put("title", b.title)
                    put("time", b.positionMs)
                    put("note", b.note)
                    put("created", b.createdAtMs)
                })
            }
        }.toString()
    }

    fun forVideo(videoId: String): List<Bookmark> = synchronized(lock) {
        parse(prefs.getString("items", "[]").orEmpty())
            .filter { it.videoId == videoId }
            .sortedBy { it.positionMs }
    }

    fun add(videoId: String, title: String, positionMs: Long, note: String): Bookmark? = synchronized(lock) {
        if (videoId.isBlank() || videoId.length > 128 || positionMs < 0L) return@synchronized null
        val old = parse(prefs.getString("items", "[]").orEmpty())
        val safePosition = positionMs.coerceAtMost(24L * 60 * 60 * 1000)
        val item = Bookmark(videoId, title.take(180), safePosition, note.trim().take(160), System.currentTimeMillis())
        val same = old.filter { it.videoId == videoId }.sortedByDescending { it.createdAtMs }
        val keptVideo = same.filterNot { kotlin.math.abs(it.positionMs - safePosition) <= 2_000L }
            .take(MAX_PER_VIDEO - 1)
        val remaining = old.filterNot { it.videoId == videoId }
        val updated = (listOf(item) + keptVideo + remaining).sortedByDescending { it.createdAtMs }
            .take(MAX_BOOKMARKS)
        prefs.edit().putString("items", encode(updated)).apply()
        item
    }

    fun delete(videoId: String, createdAtMs: Long) = synchronized(lock) {
        val old = parse(prefs.getString("items", "[]").orEmpty())
        val updated = old.filterNot { it.videoId == videoId && it.createdAtMs == createdAtMs }
        prefs.edit().putString("items", encode(updated)).apply()
        Unit
    }

    fun exportJson(): String = synchronized(lock) {
        encode(parse(prefs.getString("items", "[]").orEmpty()))
    }
}
