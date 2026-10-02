package com.drivedeck.tubedeck.player

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-lifetime ownership guard for native playback.
 *
 * Android can recreate an Activity from an old launch Intent after process/task restoration.
 * Only sessions explicitly opened by the currently running MyTube process are accepted.
 */
internal object PlaybackSessionRegistry {
    private val activeSessions = ConcurrentHashMap.newKeySet<String>()

    fun open(): String = UUID.randomUUID().toString().also(activeSessions::add)

    fun isActive(sessionId: String?): Boolean =
        !sessionId.isNullOrBlank() && activeSessions.contains(sessionId)

    fun close(sessionId: String?) {
        if (!sessionId.isNullOrBlank()) activeSessions.remove(sessionId)
    }
}
