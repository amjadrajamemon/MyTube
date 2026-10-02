package com.drivedeck.tubedeck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationCoordinatorTest {
    @Test
    fun backAndForwardRestoreLogicalRoutes() {
        val nav = NavigationCoordinator(NavigationEntry(ActiveScreen.HOME))
        nav.navigate(NavigationEntry(ActiveScreen.BROWSER, "https://www.youtube.com/results?search_query=test"))
        nav.navigate(NavigationEntry(ActiveScreen.DOWNLOADS))

        assertTrue(nav.canGoBack())
        assertEquals(ActiveScreen.BROWSER, nav.back()?.screen)
        assertEquals(ActiveScreen.DOWNLOADS, nav.forward()?.screen)
    }

    @Test
    fun newNavigationClearsForwardHistory() {
        val nav = NavigationCoordinator(NavigationEntry(ActiveScreen.HOME))
        nav.navigate(NavigationEntry(ActiveScreen.BROWSER, "https://www.youtube.com/results?search_query=a"))
        nav.navigate(NavigationEntry(ActiveScreen.SETTINGS))
        nav.back()
        assertTrue(nav.canGoForward())

        nav.navigate(NavigationEntry(ActiveScreen.DOWNLOADS))
        assertFalse(nav.canGoForward())
    }

    @Test
    fun replaceDoesNotCreateExtraBackStop() {
        val nav = NavigationCoordinator(NavigationEntry(ActiveScreen.HOME))
        nav.navigate(NavigationEntry(ActiveScreen.BROWSER, "https://www.youtube.com/results?search_query=a"))
        nav.replace(NavigationEntry(ActiveScreen.BROWSER, "https://www.youtube.com/results?search_query=b"))

        assertEquals(ActiveScreen.HOME, nav.back()?.screen)
        assertNull(nav.back())
    }
}
