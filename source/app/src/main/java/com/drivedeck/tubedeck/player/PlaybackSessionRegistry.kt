package com.drivedeck.tubedeck.player

import java.util.UUID

/** Single authoritative video session across Native, Web takeover and PiP. */
internal object PlaybackSessionRegistry {
    private val lock = Any()
    private var latestSessionId: String? = null
    private var ownerSessionId: String? = null
    private var owner: Any? = null
    private var stopOwner: (() -> Unit)? = null

    fun open(): String = synchronized(lock) {
        UUID.randomUUID().toString().also { latestSessionId = it }
    }

    fun hasActiveSession(): Boolean = synchronized(lock) { latestSessionId != null }

    fun isActive(sessionId: String?): Boolean = synchronized(lock) {
        !sessionId.isNullOrBlank() && sessionId == latestSessionId
    }

    /** Same Activity can accept onNewIntent without closing its own engine. */
    fun claim(sessionId: String?, newOwner: Any, stopWhenReplaced: () -> Unit): Boolean {
        val previous: (() -> Unit)?
        synchronized(lock) {
            if (sessionId.isNullOrBlank() || sessionId != latestSessionId) return false
            previous = if (owner !== newOwner) stopOwner else null
            owner = newOwner
            ownerSessionId = sessionId
            stopOwner = stopWhenReplaced
        }
        previous?.invoke()
        return true
    }

    /** New Web playback preempts any native/embedded PiP playback. */
    fun stopForWeb() {
        val previous: (() -> Unit)?
        synchronized(lock) {
            latestSessionId = null
            previous = stopOwner
            stopOwner = null
            ownerSessionId = null
            owner = null
        }
        previous?.invoke()
    }

    fun close(sessionId: String?) {
        if (sessionId.isNullOrBlank()) return
        synchronized(lock) {
            if (latestSessionId == sessionId) latestSessionId = null
            if (ownerSessionId == sessionId) {
                ownerSessionId = null
                owner = null
                stopOwner = null
            }
        }
    }
}
