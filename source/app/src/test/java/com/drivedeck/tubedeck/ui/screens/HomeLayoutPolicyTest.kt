package com.drivedeck.tubedeck.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutPolicyTest {
    @Test
    fun phoneWidthsUseFullSizeSwipeablePanelsNotThreeCompressedColumns() {
        listOf(320, 360, 393, 569, 600, 899).forEach { width ->
            assertEquals("Unexpected layout at ${width}dp",
                HomeLayoutPolicy.Layout.SWIPE_PANELS,
                HomeLayoutPolicy.decide(width, threeColumnTheme = true))
        }
    }

    @Test
    fun sufficientlyWideThreeColumnThemeUsesReadableTvColumns() {
        listOf(900, 960, 1280, 1920).forEach { width ->
            assertEquals(HomeLayoutPolicy.Layout.WIDE_COLUMNS,
                HomeLayoutPolicy.decide(width, threeColumnTheme = true))
        }
    }

    @Test
    fun shelfAndCinemaThemesRetainHorizontalVideoSwipeRails() {
        listOf(320, 600, 1080, 1920).forEach { width ->
            assertEquals(HomeLayoutPolicy.Layout.HORIZONTAL_SHELVES,
                HomeLayoutPolicy.decide(width, threeColumnTheme = false))
        }
    }
}
