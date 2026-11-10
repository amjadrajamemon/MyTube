package com.drivedeck.tubedeck.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchCompactPolicyTest {
    @Test fun carTabletLandscapeUsesSingleRowEvenWhenFirmwareReportsTablet() {
        assertTrue(SearchCompactPolicy.useSingleRowHeader(900, 440, true, false))
        assertTrue(SearchCompactPolicy.useSingleRowHeader(1024, 600, true, true))
    }

    @Test fun portraitAndNormalWidthLayoutsAreUnchanged() {
        assertFalse(SearchCompactPolicy.useSingleRowHeader(412, 915, false, true))
        assertFalse(SearchCompactPolicy.useSingleRowHeader(450, 380, true, false))
        assertFalse(SearchCompactPolicy.useSingleRowHeader(1000, 900, false, false))
    }

    @Test fun suggestionsAreBoundedOnShortScreens() {
        assertEquals(82, SearchCompactPolicy.overlayMaxHeightDp(240))
        assertEquals(120, SearchCompactPolicy.overlayMaxHeightDp(360))
        assertEquals(172, SearchCompactPolicy.overlayMaxHeightDp(900))
        assertEquals(4, SearchCompactPolicy.suggestionLimit(true))
    }
}
