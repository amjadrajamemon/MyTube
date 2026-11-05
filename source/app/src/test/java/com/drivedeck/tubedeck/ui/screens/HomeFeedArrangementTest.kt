package com.drivedeck.tubedeck.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFeedArrangementTest {
    @Test fun preservesCanonicalDefaults() {
        assertEquals(listOf("History", "Relevant", "Trending"),
            HomeFeedArrangement.normalize("History,Relevant,Trending"))
    }

    @Test fun recoversFromInvalidAndDuplicatePreferences() {
        assertEquals(listOf("Trending", "History", "Relevant"),
            HomeFeedArrangement.normalize("Trending,Trending,Unrecognized"))
    }

    @Test fun movesOnlyOnePositionAndPreservesAllFeeds() {
        assertEquals("Relevant,History,Trending",
            HomeFeedArrangement.move("History,Relevant,Trending", "Relevant", -1))
        assertEquals("History,Trending,Relevant",
            HomeFeedArrangement.move("History,Relevant,Trending", "Relevant", 1))
        assertEquals("History,Relevant,Trending",
            HomeFeedArrangement.move("History,Relevant,Trending", "History", -1))
    }
}
