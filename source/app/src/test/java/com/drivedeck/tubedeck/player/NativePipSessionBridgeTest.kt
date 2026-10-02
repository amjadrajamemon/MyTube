package com.drivedeck.tubedeck.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class NativePipSessionBridgeTest {
    @Test
    fun activeSessionIsOwnedUntilMatchingSessionClearsIt() {
        NativePipSessionBridge.clear()
        try {
            NativePipSessionBridge.activate("session-a")
            assertEquals("session-a", NativePipSessionBridge.activeSessionId.value)

            NativePipSessionBridge.clear("session-b")
            assertEquals("session-a", NativePipSessionBridge.activeSessionId.value)

            NativePipSessionBridge.clear("session-a")
            assertNull(NativePipSessionBridge.activeSessionId.value)
        } finally {
            NativePipSessionBridge.clear()
        }
    }

    @Test
    fun commandsOnlyTargetTheActiveNativeSession() = runBlocking {
        NativePipSessionBridge.clear()
        try {
            NativePipSessionBridge.activate("session-a")
            val next = async(start = CoroutineStart.UNDISPATCHED) { NativePipSessionBridge.commands.first() }

            NativePipSessionBridge.request("session-b", NativePipSessionBridge.Action.CLOSE)
            NativePipSessionBridge.request("session-a", NativePipSessionBridge.Action.AUDIO_ONLY)

            val command = next.await()
            assertEquals("session-a", command.sessionId)
            assertEquals(NativePipSessionBridge.Action.AUDIO_ONLY, command.action)
        } finally {
            NativePipSessionBridge.clear()
        }
    }
}
