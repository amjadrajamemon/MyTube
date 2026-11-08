package com.drivedeck.tubedeck.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTitleTypographyPolicyTest {
    @Test fun carLandscapeTitlesAreReadableAndHaveFourLines() {
        val size = VideoTitleTypographyPolicy.metrics(900, 430, true, 1.0f)
        assertEquals(16, size.titleSp)
        assertEquals(4, size.maxTitleLines)
        assertTrue(size.lineHeightSp >= 20)
        assertTrue(size.metadataSp >= 12)
    }

    @Test fun normalPhoneTitlesRemainLegible() {
        val s = VideoTitleTypographyPolicy.metrics(393, 851, false, 1f)
        assertEquals(15, s.titleSp)
        assertEquals(3, s.maxTitleLines)
    }

    @Test fun studioScaleExpandsCardHeightAlongsideTextToAvoidClipping() {
        val regular = VideoTitleTypographyPolicy.metrics(1024, 600, true, 1f)
        val large = VideoTitleTypographyPolicy.metrics(1024, 600, true, 1.5f)
        assertTrue(large.titleSp > regular.titleSp)
        assertTrue(large.shelfCardHeightDp > regular.shelfCardHeightDp)
        assertTrue(large.listCardHeightDp > regular.listCardHeightDp)
    }

    @Test fun incorrectScaleIsBounded() {
        assertEquals(16, VideoTitleTypographyPolicy.metrics(900, 450, true, -20f).titleSp)
        assertTrue(VideoTitleTypographyPolicy.metrics(900, 450, true, 15f).titleSp <= 24)
    }
}
