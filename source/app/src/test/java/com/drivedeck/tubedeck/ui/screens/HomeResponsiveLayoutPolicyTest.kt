package com.drivedeck.tubedeck.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeResponsiveLayoutPolicyTest {
    @Test fun narrowPortraitUsesSingleCardColumn() {
        assertEquals(1, HomeResponsiveLayoutPolicy.gridColumns(350, false))
        assertEquals(2, HomeResponsiveLayoutPolicy.gridColumns(440, false))
    }

    @Test fun tvAndTabletWidthsReflowWithoutTinyCards() {
        assertEquals(1, HomeResponsiveLayoutPolicy.gridColumns(420, true))
        assertEquals(3, HomeResponsiveLayoutPolicy.gridColumns(800, true))
        assertEquals(5, HomeResponsiveLayoutPolicy.gridColumns(1600, true))
        assertEquals(4, HomeResponsiveLayoutPolicy.gridColumns(1250, false))
    }

    @Test fun initialBatchIsConfigurableAndClamped() {
        assertEquals(20, HomeResponsiveLayoutPolicy.initialVideos(20))
        assertEquals(10, HomeResponsiveLayoutPolicy.initialVideos(0))
        assertEquals(50, HomeResponsiveLayoutPolicy.initialVideos(500))
    }
}
