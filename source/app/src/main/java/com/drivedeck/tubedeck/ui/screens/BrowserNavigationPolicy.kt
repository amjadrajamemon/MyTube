package com.drivedeck.tubedeck.ui.screens

/**
 * Keeps passive WebView/YouTube SPA observations separate from explicit MyTube navigation.
 * A changed observed URL alone must never cause loadUrl(), otherwise SPA navigation becomes
 * a full page reload and search results lose their in-memory state.
 */
internal object BrowserNavigationPolicy {
    fun shouldLoad(
        currentWebUrl: String?,
        targetUrl: String,
        navigationGeneration: Int,
        appliedNavigationGeneration: Int
    ): Boolean {
        if (!targetUrl.startsWith("https://")) return false
        val firstLoad = currentWebUrl.isNullOrBlank()
        val explicitCommand = navigationGeneration != appliedNavigationGeneration
        return (firstLoad || explicitCommand) && currentWebUrl != targetUrl
    }
}
