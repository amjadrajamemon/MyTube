package com.drivedeck.tubedeck.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserNavigationPolicyTest {
    @Test fun passiveSpaObservationNeverTriggersReload() {
        assertFalse(
            BrowserNavigationPolicy.shouldLoad(
                currentWebUrl = "https://www.youtube.com/results?search_query=ary",
                targetUrl = "https://www.youtube.com/results?search_query=ary+news",
                navigationGeneration = 4,
                appliedNavigationGeneration = 4
            )
        )
    }

    @Test fun explicitNavigationLoadsDifferentTarget() {
        assertTrue(
            BrowserNavigationPolicy.shouldLoad(
                currentWebUrl = "https://www.youtube.com/results?search_query=ary",
                targetUrl = "https://www.youtube.com/results?search_query=geo+news",
                navigationGeneration = 5,
                appliedNavigationGeneration = 4
            )
        )
    }

    @Test fun sameTargetIsNeverReloadedJustBecauseCommandChanged() {
        assertFalse(
            BrowserNavigationPolicy.shouldLoad(
                currentWebUrl = "https://www.youtube.com/results?search_query=ary",
                targetUrl = "https://www.youtube.com/results?search_query=ary",
                navigationGeneration = 5,
                appliedNavigationGeneration = 4
            )
        )
    }

    @Test fun firstBrowserCreationLoadsInitialTarget() {
        assertTrue(
            BrowserNavigationPolicy.shouldLoad(
                currentWebUrl = null,
                targetUrl = "https://www.youtube.com/results?search_query=ary",
                navigationGeneration = 1,
                appliedNavigationGeneration = -1
            )
        )
    }

    @Test fun unsafeNonHttpsTargetNeverLoads() {
        assertFalse(
            BrowserNavigationPolicy.shouldLoad(
                currentWebUrl = null,
                targetUrl = "http://example.com",
                navigationGeneration = 1,
                appliedNavigationGeneration = -1
            )
        )
    }
}
