package com.drivedeck.tubedeck.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeekPreviewPolicyTest {
    @Test fun positionsSelectCorrectFramesAcrossPages() {
        assertEquals(SeekPreviewPolicy.Frame(0, 0, 0), SeekPreviewPolicy.frame(0L, 1000, 60, 5, 5, 3))
        assertEquals(SeekPreviewPolicy.Frame(0, 4, 4), SeekPreviewPolicy.frame(24_000L, 1000, 60, 5, 5, 3))
        assertEquals(SeekPreviewPolicy.Frame(1, 0, 0), SeekPreviewPolicy.frame(25_000L, 1000, 60, 5, 5, 3))
        assertEquals(SeekPreviewPolicy.Frame(2, 1, 4), SeekPreviewPolicy.frame(999_000L, 1000, 60, 5, 5, 3))
    }

    @Test fun invalidInputsAndEdgesDoNotCrashThePlayer() {
        assertNull(SeekPreviewPolicy.frame(4L, 0, 10, 2, 2, 2))
        assertNull(SeekPreviewPolicy.frame(4L, 1000, 0, 2, 2, 2))
        assertEquals(0f, SeekPreviewPolicy.fraction(12f, 0f))
        assertEquals(0f, SeekPreviewPolicy.fraction(-10f, 20f))
        assertEquals(1f, SeekPreviewPolicy.fraction(100f, 20f))
    }
}
