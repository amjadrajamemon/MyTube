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

    @Test fun shortAndNearEndPositionsAreNotResumed() {
        assertFalse(VideoResumePolicy.shouldSave(4_999L, 100_000L))
        assertTrue(VideoResumePolicy.shouldSave(7_000L, 100_000L))
        assertFalse(VideoResumePolicy.shouldSave(98_000L, 100_000L))
        assertTrue(VideoResumePolicy.shouldSave(53_000L, 200_000L))
    }

    @Test fun unresolvedStreamDoesNotErasePriorResume() {
        assertFalse(VideoResumePolicy.shouldSnapshot(false, false, 0L, false))
        assertFalse(VideoResumePolicy.shouldSnapshot(true, true, 10_000L, false))
        assertTrue(VideoResumePolicy.shouldSnapshot(true, false, 25_000L, false))
        assertTrue(VideoResumePolicy.shouldSnapshot(true, false, 0L, true))
    }
}
