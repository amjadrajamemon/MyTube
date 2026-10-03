package com.drivedeck.tubedeck.player

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AudioHandoffStartupTest {
    @Test fun videoWaitsForItsOwnPlayingAcknowledgement() = runTest {
        val startup = AudioHandoffStartup()
        val request = startup.begin()!!
        assertFalse(request.ready.isCompleted)
        assertNull(startup.begin())
        startup.ready(request.id + 1)
        assertFalse(request.ready.isCompleted)
        startup.ready(request.id)
        assertTrue(request.ready.await().isSuccess)
    }

    @Test fun failureAllowsRetryAndStaleServiceCannotCompleteRetry() = runTest {
        val startup = AudioHandoffStartup()
        val first = startup.begin()!!
        startup.fail(first.id, "Service stopped")
        assertTrue(first.ready.await().isFailure)
        startup.finish(first.id)
        val retry = startup.begin()!!
        startup.ready(first.id)
        startup.finish(first.id)
        assertFalse(retry.ready.isCompleted)
        assertNull(startup.begin())
        startup.ready(retry.id)
        assertTrue(retry.ready.await().isSuccess)
    }
}
