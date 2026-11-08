package com.drivedeck.tubedeck.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Bounded, local-only watch positions. No cloud sync or persistent timer. */
internal class VideoResumeStore(context: Context) {
    data class Resume(
        val mediaUri: String,
        val positionMs: Long,
        val updatedAt: Long,
        val durationMs: Long
    )
    private val prefs = context.applicationContext.getSharedPreferences("mytube_resume_v2", Context.MODE_PRIVATE)
    private val lock = Any()
    private val keep = 120
    private val expireMs = 45L * 24 * 60 * 60 * 1000

    private fun items(): List<Resume> {
        val array = runCatching { JSONArray(prefs.getString("items", "[]")) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until minOf(keep, array.length())) {
                val o = array.optJSONObject(i) ?: continue
                val url = o.optString("uri")
                val pos = o.optLong("pos", -1L)
                val timestamp = o.optLong("updated", -1L)
                // Legacy entries have no duration, so cannot reliably tell
                // finished videos from unfinished ones: do not prompt for them.
                val duration = o.optLong("duration", 0L)
                if (url.length !in 1..2048 || pos < 0 || timestamp < 0 ||
                    System.currentTimeMillis() - timestamp > expireMs
                ) continue
                add(Resume(url, pos, timestamp, duration))
            }
        }
    }

    fun find(mediaUri: String): Resume? = synchronized(lock) {
        val key = VideoResumePolicy.mediaKey(mediaUri)
        // Read legacy raw-URL entries too; canonicalize on the next save.
        items().firstOrNull {
            VideoResumePolicy.mediaKey(it.mediaUri) == key &&
                VideoResumePolicy.shouldOfferPrompt(
                    it.positionMs, it.durationMs, it.updatedAt,
                    System.currentTimeMillis()
                )
        }
    }

    fun record(mediaUri: String, positionMs: Long, durationMs: Long = 0L) = synchronized(lock) {
        val key = VideoResumePolicy.mediaKey(mediaUri)
        if (key.length !in 1..2048) return@synchronized
        val copy = items().filterNot { VideoResumePolicy.mediaKey(it.mediaUri) == key }.toMutableList()
        if (VideoResumePolicy.shouldSave(positionMs, durationMs)) {
            copy.add(0, Resume(
                key, positionMs.coerceAtMost(86_400_000L),
                System.currentTimeMillis(), durationMs
            ))
        }
        val encoded = JSONArray()
        copy.take(keep).forEach { state ->
            encoded.put(JSONObject().apply {
                put("uri", state.mediaUri)
                put("pos", state.positionMs)
                put("updated", state.updatedAt)
                put("duration", state.durationMs)
            })
        }
        // commit() ensures the final exit snapshot survives a process shutdown.
        prefs.edit().putString("items", encoded.toString()).commit()
    }
}
