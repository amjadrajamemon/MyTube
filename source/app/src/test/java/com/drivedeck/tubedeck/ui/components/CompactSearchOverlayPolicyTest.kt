package com.drivedeck.tubedeck.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CompactSearchOverlayPolicyTest {
    @Test fun landscapeImeHidesHistoryAndSuggestions() {
        assertEquals(0, CompactSearchOverlayPolicy.suggestionCount(true, true))
        assertEquals(3, CompactSearchOverlayPolicy.suggestionCount(true, false))
    }

    @Test fun portraitKeepsShortScrollableHistory() {
        assertEquals(4, CompactSearchOverlayPolicy.suggestionCount(false, false))
        assertEquals(2, CompactSearchOverlayPolicy.suggestionCount(false, true))
    }

    @Test fun dialogsAreWidthBoundedOnTabletsAndPhones() {
        assertEquals(376, CompactSearchOverlayPolicy.panelMaxWidthDp(400))
        assertEquals(700, CompactSearchOverlayPolicy.panelMaxWidthDp(1600))
    }
}
