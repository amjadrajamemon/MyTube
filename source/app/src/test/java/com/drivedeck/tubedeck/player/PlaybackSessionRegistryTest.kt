package com.drivedeck.tubedeck.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionRegistryTest {
    @Test
    fun newlyOpenedSessionIsActiveUntilExplicitlyClosed() {
        val session = PlaybackSessionRegistry.open()
        assertTrue(PlaybackSessionRegistry.isActive(session))

        PlaybackSessionRegistry.close(session)
        assertFalse(PlaybackSessionRegistry.isActive(session))
    }

    @Test
    fun staleOrMissingSessionIsRejected() {
        assertFalse(PlaybackSessionRegistry.isActive(null))
        assertFalse(PlaybackSessionRegistry.isActive(""))
        assertFalse(PlaybackSessionRegistry.isActive("stale-session-id"))
    }
}
