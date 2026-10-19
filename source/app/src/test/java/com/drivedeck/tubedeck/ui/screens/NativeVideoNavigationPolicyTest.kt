package com.drivedeck.tubedeck.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeVideoNavigationPolicyTest {
    @Test fun repeatedPassiveCallbacksNeverReopenConsumedVideo() {
        val policy = NativeVideoNavigationPolicy()
        assertTrue(policy.shouldOpen("video1", "video1", false, true))
        repeat(100) { assertFalse(policy.shouldOpen("video1", "video1", false, true)) }
    }
    @Test fun staleWatchCallbacksCannotPopRestoredSearch() {
        assertFalse(NativeVideoNavigationPolicy().shouldOpen("video1", null, false, true))
    }
    @Test fun backgroundBrowserCannotTakeOwnershipFromPlayer() {
        assertFalse(NativeVideoNavigationPolicy().shouldOpen("video1", "video1", true, false))
    }
    @Test fun userCanIntentionallyReopenSameVideo() {
        val policy = NativeVideoNavigationPolicy()
        assertTrue(policy.shouldOpen("video1", "video1", false, true))
        policy.onUserGesture()
        assertTrue(policy.shouldOpen("video1", "video1", false, true))
    }
    @Test fun explicitClickAndAnotherVideoRemainAvailable() {
        val policy = NativeVideoNavigationPolicy()
        assertTrue(policy.shouldOpen("video1", null, true, true))
        assertFalse(policy.shouldOpen("video1", null, true, true))
        assertTrue(policy.shouldOpen("video2", "video2", false, true))
    }
}
