package com.drivedeck.tubedeck.ui.screens

/** Consume each browser handoff once; only a new user gesture can reopen the same video. */
internal class NativeVideoNavigationPolicy {
    private var consumedVideoId: String? = null
    private var gestureGeneration = 0L
    private var consumedGestureGeneration = -1L

    fun onUserGesture() { gestureGeneration += 1 }

    fun shouldOpen(
        videoId: String,
        currentPageVideoId: String?,
        explicitNavigation: Boolean,
        isForeground: Boolean
    ): Boolean {
        if (!isForeground) return false
        // A delayed onPageFinished/watch callback can arrive after the results page was restored.
        if (!explicitNavigation && currentPageVideoId != videoId) return false
        if (consumedVideoId == videoId &&
            consumedGestureGeneration == gestureGeneration) return false
        consumedVideoId = videoId
        consumedGestureGeneration = gestureGeneration
        return true
    }
}
