package com.drivedeck.tubedeck.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
