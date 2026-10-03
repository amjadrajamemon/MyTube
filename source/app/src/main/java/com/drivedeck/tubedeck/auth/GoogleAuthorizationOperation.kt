package com.drivedeck.tubedeck.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withTimeout

/** One cancellable request shared by both player hosts. Late results cannot finish a retry. */
internal class GoogleAuthorizationOperation {
    private val lock = Any()
    private var active: Job? = null

    val isRunning: Boolean get() = synchronized(lock) { active != null }

    fun cancel() {
        val previous = synchronized(lock) { active.also { active = null } }
        previous?.cancel()
    }

    suspend fun <T> run(
        timeoutMs: Long,
        timeoutMessage: String,
        onCancelled: () -> Unit = {},
        block: suspend () -> T
    ): T = coroutineScope {
        val current = currentCoroutineContext().job
        val previous = synchronized(lock) { active.also { active = current } }
        previous?.cancel()
        try {
            withGoogleAuthorizationTimeout(timeoutMs, timeoutMessage, block)
        } catch (cancelled: CancellationException) {
            if (synchronized(lock) { active === current }) onCancelled()
            throw cancelled
        } finally {
            synchronized(lock) { if (active === current) active = null }
        }
    }
}

internal suspend fun <T> withGoogleAuthorizationTimeout(
    timeoutMs: Long,
    message: String,
    block: suspend () -> T
): T = try {
    withTimeout(timeoutMs) { block() }
} catch (timeout: TimeoutCancellationException) {
    // Preserve cancellation from an enclosing timeout/operation.
    currentCoroutineContext().job.let { if (!it.isActive) throw timeout }
    throw IllegalStateException(message, timeout)
}
