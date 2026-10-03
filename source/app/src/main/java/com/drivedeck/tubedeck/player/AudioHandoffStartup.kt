package com.drivedeck.tubedeck.player

import kotlinx.coroutines.CompletableDeferred

/** A single handoff must be acknowledged by its own service playback before the video closes. */
internal class AudioHandoffStartup {
    data class Request(val id: Long, val ready: CompletableDeferred<Result<Unit>>)
    private var sequence = 0L
    private var pending: Request? = null

    @Synchronized fun begin(): Request? {
        if (pending != null) return null
        return Request(++sequence, CompletableDeferred()).also { pending = it }
    }

    @Synchronized fun ready(id: Long) {
        pending?.takeIf { it.id == id }?.ready?.complete(Result.success(Unit))
    }

    @Synchronized fun fail(id: Long, message: String) {
        pending?.takeIf { it.id == id }?.ready?.complete(Result.failure(IllegalStateException(message)))
    }

    @Synchronized fun finish(id: Long) {
        if (pending?.id == id) pending = null
    }
}
