package com.drivedeck.tubedeck.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoResumePolicyTest {
    @Test fun youtubeUrlsShareSavedPositionAcrossSurfaces() {
        val expected = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertEquals(expected, VideoResumePolicy.mediaKey("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(expected, VideoResumePolicy.mediaKey("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=1m"))
        assertEquals(expected, VideoResumePolicy.mediaKey("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=playlist1"))
        assertEquals(expected, VideoResumePolicy.mediaKey("https://www.youtube.com/embed/dQw4w9WgXcQ"))
    }

    @Test fun barelyStartedVideosNeverOfferResume() {
        assertFalse(VideoResumePolicy.shouldSave(4_999L, 100_000L))
        assertFalse(VideoResumePolicy.shouldSave(20_000L, 200_000L))
        assertTrue(VideoResumePolicy.shouldSave(40_000L, 200_000L))
    }

    @Test fun finishedAndNearlyFinishedVideosDoNotResume() {
        assertFalse(VideoResumePolicy.shouldSave(98_000L, 100_000L))
        assertFalse(VideoResumePolicy.shouldSave(91_000L, 100_000L))
        assertTrue(VideoResumePolicy.shouldSave(60_000L, 100_000L))
    }

    @Test fun unknownDurationAndStaleEntriesDoNotOffer() {
        val now = 1_800_000_000_000L
        assertFalse(VideoResumePolicy.shouldSave(55_000L, 0L))
        assertFalse(VideoResumePolicy.shouldOfferPrompt(50_000L, 0L, now - 1_000L, now))
        assertFalse(VideoResumePolicy.shouldOfferPrompt(50_000L, 200_000L, now - 31L * 24 * 60 * 60 * 1000, now))
        assertTrue(VideoResumePolicy.shouldOfferPrompt(50_000L, 200_000L, now - 1_000L, now))
    }

    @Test fun defaultIsThreeSecondsAndSettingIsBounded() {
        assertEquals(3, VideoResumePolicy.DEFAULT_PROMPT_SECONDS)
        assertEquals(1, VideoResumePolicy.promptSeconds(-10))
        assertEquals(15, VideoResumePolicy.promptSeconds(60))
        assertEquals(7, VideoResumePolicy.promptSeconds(7))
    }

    @Test fun unresolvedStreamDoesNotErasePriorResume() {
        assertFalse(VideoResumePolicy.shouldSnapshot(false, false, 0L, false))
        assertFalse(VideoResumePolicy.shouldSnapshot(true, true, 40_000L, false))
        assertTrue(VideoResumePolicy.shouldSnapshot(true, false, 45_000L, false))
        assertTrue(VideoResumePolicy.shouldSnapshot(true, false, 0L, true))
    }
}
