package com.drivedeck.tubedeck.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioNextSessionPolicyTest {
    @Test
    fun recommendationsCannotPingPongBetweenTwoPlayedVideos() {
        val session = AudioNextSessionPolicy()
        session.startExplicitPlayback("A")
        assertEquals("B", session.firstUnplayed("A", listOf("A", "B", "C")))
        session.recordPlayed("B")
        assertEquals("C", session.firstUnplayed("B", listOf("A", "B", "C")))
        session.recordPlayed("C")
        assertNull(session.firstUnplayed("C", listOf("A", "B", "C")))
    }

    @Test
    fun newExplicitPlayResetsThePreviousRecommendations() {
        val session = AudioNextSessionPolicy()
        session.startExplicitPlayback("A")
        session.recordPlayed("B")
        session.startExplicitPlayback("C")
        assertEquals("A", session.firstUnplayed("C", listOf("C", "A")))
        assertEquals(1, session.recentCount())
    }

    @Test
    fun historyIsBoundedAndTheOldestVideoCanBeSelectedAgain() {
        val session = AudioNextSessionPolicy(maxHistory = 2)
        session.startExplicitPlayback("A")
        session.recordPlayed("B")
        session.recordPlayed("C")
        assertEquals(2, session.recentCount())
        assertEquals("A", session.firstUnplayed("C", listOf("B", "A")))
    }

    @Test
    fun invalidCandidatesAreIgnoredAndFirstFreshCandidateIsSelected() {
        val session = AudioNextSessionPolicy()
        session.startExplicitPlayback("A")
        assertEquals("D", session.firstUnplayed("A", listOf("", "A", "D", "E")))
        session.recordPlayed("")
        assertEquals(1, session.recentCount())
    }
}
