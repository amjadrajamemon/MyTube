package com.drivedeck.tubedeck

import org.junit.Assert.*
import org.junit.Test

class NavigationCoordinatorTest {
    private val results = NavigationEntry(ActiveScreen.BROWSER, "https://www.youtube.com/results?search_query=test")

    @Test fun settingsAndDownloadToolsNeverBecomeBackOrForwardStops() {
        val nav = NavigationCoordinator(NavigationEntry(ActiveScreen.HOME))
        nav.navigate(results)
        nav.navigate(NavigationEntry(ActiveScreen.SETTINGS))
        nav.navigate(NavigationEntry(ActiveScreen.DOWNLOADS))
        assertEquals(results, nav.back())
        assertFalse(nav.canGoForward())
        assertEquals(ActiveScreen.HOME, nav.back()?.screen)
        assertNull(nav.back())
        assertEquals(results, nav.forward())
    }

    @Test fun switchingTabsClearsOldScreensAndBackFromHomeExits() {
        val nav = NavigationCoordinator(NavigationEntry(ActiveScreen.HOME))
        nav.navigate(results)
        nav.navigate(NavigationEntry(ActiveScreen.SETTINGS))
        nav.navigate(NavigationEntry(ActiveScreen.MIXES))
        assertEquals(ActiveScreen.HOME, nav.back()?.screen)
        assertNull(nav.back())
        assertFalse(nav.canGoForward())
    }

    @Test fun leavingSettingsForAnotherVideoDoesNotRecordSettings() {
        val nav = NavigationCoordinator(NavigationEntry(ActiveScreen.HOME))
        nav.navigate(results)
        nav.navigate(NavigationEntry(ActiveScreen.SETTINGS))
        nav.navigate(NavigationEntry(ActiveScreen.PLAYER))
        assertEquals(results, nav.back())
        assertEquals(ActiveScreen.HOME, nav.back()?.screen)
        assertNull(nav.back())
    }

    @Test fun passiveBrowserUpdatesDoNotCreateExtraBackStops() {
        val nav = NavigationCoordinator(NavigationEntry(ActiveScreen.HOME))
        nav.navigate(results)
        nav.replace(NavigationEntry(ActiveScreen.BROWSER, "https://www.youtube.com/results?search_query=b"))
        assertEquals(ActiveScreen.HOME, nav.back()?.screen)
        assertNull(nav.back())
    }
}
