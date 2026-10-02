package com.drivedeck.tubedeck.player

import android.os.SystemClock
import android.util.Log

/**
 * Lightweight in-process startup telemetry for the native YouTube player.
 *
 * A trace begins at the user's tap and ends at Media3's first rendered frame. Nothing is
 * uploaded: results are written only to Logcat under the MyTubeStartup tag so performance
 * tuning can be based on real device timings without collecting user analytics.
 */
internal object PlaybackStartupTelemetry {
    private const val TAG = "MyTubeStartup"
    private const val TRACE_TTL_MS = 30_000L
    private val lock = Any()
    private val traces = LinkedHashMap<String, Trace>()

    private data class Trace(
        val tapMs: Long,
        var resolveStartMs: Long = 0L,
        var resolveDoneMs: Long = 0L,
        var prepareMs: Long = 0L,
        var cacheHit: Boolean = false
    )

    fun markTap(url: String) {
        val key = key(url) ?: return
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            prune(now)
            traces[key] = Trace(tapMs = now)
        }
    }

    fun markResolveStart(url: String) {
        val key = key(url) ?: return
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            val trace = traces[key] ?: return
            if (trace.resolveStartMs == 0L) trace.resolveStartMs = now
        }
    }

    fun markResolveDone(url: String, cacheHit: Boolean) {
        val key = key(url) ?: return
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            val trace = traces[key] ?: return
            if (trace.resolveDoneMs == 0L) {
                trace.resolveDoneMs = now
                trace.cacheHit = cacheHit
            }
        }
    }

    fun markResolveFailed(url: String) {
        val key = key(url) ?: return
        val now = SystemClock.elapsedRealtime()
        val trace = synchronized(lock) { traces.remove(key) } ?: return
        Log.i(TAG, "startup_failed video=$key after=${now - trace.tapMs}ms")
    }

    fun markPrepare(url: String) {
        val key = key(url) ?: return
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            val trace = traces[key] ?: return
            if (trace.prepareMs == 0L) trace.prepareMs = now
        }
    }

    fun markFirstFrame(url: String) {
        val key = key(url) ?: return
        val now = SystemClock.elapsedRealtime()
        val trace = synchronized(lock) { traces.remove(key) } ?: return
        val resolveMs = if (trace.resolveStartMs > 0L && trace.resolveDoneMs >= trace.resolveStartMs) {
            trace.resolveDoneMs - trace.resolveStartMs
        } else -1L
        val resolveToPrepareMs = if (trace.resolveDoneMs > 0L && trace.prepareMs >= trace.resolveDoneMs) {
            trace.prepareMs - trace.resolveDoneMs
        } else -1L
        val prepareToFrameMs = if (trace.prepareMs > 0L && now >= trace.prepareMs) {
            now - trace.prepareMs
        } else -1L
        Log.i(
            TAG,
            "ttff video=$key total=${now - trace.tapMs}ms resolve=${resolveMs}ms " +
                "resolve_to_prepare=${resolveToPrepareMs}ms prepare_to_frame=${prepareToFrameMs}ms " +
                "cache_hit=${trace.cacheHit}"
        )
    }

    private fun key(url: String): String? = YoutubeUrls.videoId(url)

    private fun prune(now: Long) {
        val iterator = traces.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value.tapMs > TRACE_TTL_MS) iterator.remove()
        }
    }
}
