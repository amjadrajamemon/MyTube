package com.drivedeck.tubedeck.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistQueuePolicyTest {
    @Test
    fun rdMixPrefersWatchContextWhenCurrentVideoExists() {
        assertTrue(PlaylistQueuePolicy.preferContextualWatchNext("RDabc123", "dQw4w9WgXcQ"))
    }

    @Test
    fun ordinaryPlaylistMustNotBeTruncatedToWatchNextPanel() {
        assertFalse(PlaylistQueuePolicy.preferContextualWatchNext("PLabc123", "dQw4w9WgXcQ"))
        assertFalse(PlaylistQueuePolicy.preferContextualWatchNext("UUabc123", "dQw4w9WgXcQ"))
    }

    @Test
    fun mixWithoutCurrentVideoFallsBackToPlaylistResolvers() {
        assertFalse(PlaylistQueuePolicy.preferContextualWatchNext("RDabc123", null))
    }

    @Test
    fun nextDoesNotBounceBetweenTwoAlreadyPlayedRecommendations() {
        assertEquals(
            "C",
            PlaylistQueuePolicy.nextUnplayed(listOf("B", "A", "C"), "B", setOf("A", "B"))
        )
        assertNull(PlaylistQueuePolicy.nextUnplayed(listOf("A", "B"), "B", setOf("A", "B")))
    }

    @Test
    fun nextSkipsEmptyAndPreviouslyPlayedCandidates() {
        assertEquals(
            "D",
            PlaylistQueuePolicy.nextUnplayed(listOf("", "A", "C", "D"), "A", setOf("C"))
        )
    }
}
