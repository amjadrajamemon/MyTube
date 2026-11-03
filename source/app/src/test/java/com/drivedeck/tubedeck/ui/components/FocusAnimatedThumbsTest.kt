package com.drivedeck.tubedeck.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusAnimatedThumbsTest {
    @Test fun neverStartsPreviewOnConstrainedOrMeteredDevices() {
        val policy = FocusAnimatedThumbs
        assertTrue(policy.eligible(true, false, false, false, 33, false))
        assertFalse(policy.eligible(true, true, false, false, 33, false))
        assertFalse(policy.eligible(true, false, true, false, 33, false))
        assertFalse(policy.eligible(true, false, false, true, 33, false))
        assertFalse(policy.eligible(true, false, false, false, 27, false))
        assertFalse(policy.eligible(true, false, false, false, 33, true))
        assertFalse(policy.eligible(false, false, false, false, 33, false))
    }
    @Test fun onlyCanonicalVideoIdsCanBecomePreviewUrls() {
        assertEquals(
            "https://i.ytimg.com/an_webp/AbC_defG123/mqdefault_6s.webp",
            FocusAnimatedThumbs.url("AbC_defG123")
        )
        assertNull(FocusAnimatedThumbs.url("x/../../secret"))
    }
}
