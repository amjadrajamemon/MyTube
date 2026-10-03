package com.drivedeck.tubedeck.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoogleAuthorizationOperationTest {
    @Test fun stalledGoogleTaskTimesOutAndAllowsRetry() = runTest {
        val operation = GoogleAuthorizationOperation()
        val result = async {
            runCatching { operation.run(20_000, "Google timed out") { awaitCancellation() } }
        }
        runCurrent()
        assertTrue(operation.isRunning)
        advanceTimeBy(20_001)
        runCurrent()
        assertEquals("Google timed out", result.await().exceptionOrNull()?.message)
        assertFalse(operation.isRunning)
        assertEquals("connected", operation.run(20_000, "timeout") { "connected" })
    }

    @Test fun manualCancelStopsPendingResultAndAllowsRetry() = runTest {
        val operation = GoogleAuthorizationOperation()
        val google = CompletableDeferred<String>()
        var published = false
        val first = launch { operation.run(20_000, "timeout") { google.await(); published = true } }
        runCurrent()
        operation.cancel()
        runCurrent()
        google.complete("late token")
        runCurrent()
        assertTrue(first.isCancelled)
        assertFalse(published)
        assertFalse(operation.isRunning)
        assertEquals(1, operation.run(20_000, "timeout") { 1 })
    }

    @Test fun replacingRequestCannotLetOldCancellationResetNewState() = runTest {
        val operation = GoogleAuthorizationOperation()
        var oldCancellationReset = false
        val first = launch {
            operation.run(20_000, "timeout", { oldCancellationReset = true }) { awaitCancellation() }
        }
        runCurrent()
        val second = async { operation.run(20_000, "timeout") { delay(100); "new account" } }
        runCurrent()
        assertTrue(first.isCancelled)
        assertFalse(oldCancellationReset)
        assertTrue(operation.isRunning)
        advanceTimeBy(101)
        assertEquals("new account", second.await())
        assertFalse(operation.isRunning)
    }

    @Test fun hostCancellationRunsCleanupWithoutBeingReportedAsTimeout() = runTest {
        val operation = GoogleAuthorizationOperation()
        var reset = false
        val host = launch {
            operation.run(20_000, "timeout", { reset = true }) { awaitCancellation() }
        }
        runCurrent()
        host.cancel()
        runCurrent()
        assertTrue(reset)
        assertTrue(host.isCancelled)
        assertFalse(operation.isRunning)
    }

    @Test fun apiFailureIsPreservedAndDoesNotLeaveBusyState() = runTest {
        val operation = GoogleAuthorizationOperation()
        val failure = runCatching {
            operation.run(20_000, "timeout") { error("accessNotConfigured") }
        }
        assertEquals("accessNotConfigured", failure.exceptionOrNull()?.message)
        assertFalse(operation.isRunning)
    }
}
