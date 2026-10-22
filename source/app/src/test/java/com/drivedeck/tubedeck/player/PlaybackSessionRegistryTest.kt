package com.drivedeck.tubedeck.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionRegistryTest {
    @Test fun newNativeSessionStopsPreviousOwner() {
        PlaybackSessionRegistry.stopForWeb()
        val first = PlaybackSessionRegistry.open()
        var stops = 0
        assertTrue(PlaybackSessionRegistry.claim(first, Any()) { stops++ })
        val second = PlaybackSessionRegistry.open()
        assertFalse(PlaybackSessionRegistry.isActive(first))
        assertTrue(PlaybackSessionRegistry.claim(second, Any()) { })
        assertEquals(1, stops)
        PlaybackSessionRegistry.close(first)
        assertTrue(PlaybackSessionRegistry.isActive(second))
        PlaybackSessionRegistry.close(second)
    }

    @Test fun onNewIntentReusesSameActivityWithoutSelfClosing() {
        PlaybackSessionRegistry.stopForWeb()
        val activity = Any()
        var stops = 0
        val first = PlaybackSessionRegistry.open()
        assertTrue(PlaybackSessionRegistry.claim(first, activity) { stops++ })
        val second = PlaybackSessionRegistry.open()
        assertTrue(PlaybackSessionRegistry.claim(second, activity) { stops++ })
        assertEquals(0, stops)
        PlaybackSessionRegistry.stopForWeb()
        assertEquals(1, stops)
    }

    @Test fun staleSessionCannotRestartAfterNewerTap() {
        PlaybackSessionRegistry.stopForWeb()
        val old = PlaybackSessionRegistry.open()
        val latest = PlaybackSessionRegistry.open()
        assertFalse(PlaybackSessionRegistry.isActive(old))
        assertFalse(PlaybackSessionRegistry.claim(old, Any()) { })
        assertTrue(PlaybackSessionRegistry.isActive(latest))
        PlaybackSessionRegistry.close(latest)
    }

    @Test fun webTakeoverRevokesNativeOrPip() {
        PlaybackSessionRegistry.stopForWeb()
        val session = PlaybackSessionRegistry.open()
        var stops = 0
        assertTrue(PlaybackSessionRegistry.claim(session, Any()) { stops++ })
        PlaybackSessionRegistry.stopForWeb()
        assertEquals(1, stops)
        assertFalse(PlaybackSessionRegistry.isActive(session))
        assertFalse(PlaybackSessionRegistry.hasActiveSession())
    }
}
