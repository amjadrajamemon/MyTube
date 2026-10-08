package com.drivedeck.tubedeck.player

import kotlin.math.abs

internal enum class PlaybackSurfaceGesture {
    NONE,
    SEEK_BACK,
    SEEK_FORWARD,
    TOGGLE_PLAY_PAUSE,
    HOLD_FAST_FORWARD,
    ENTER_FULLSCREEN,
    EXIT_FULLSCREEN,
    ENTER_PIP
}

internal object PlaybackSurfaceGesturePolicy {
    private const val CENTER_START = 0.30f
    private const val CENTER_END = 0.70f
    private const val VERTICAL_THRESHOLD = 0.16f

    fun doubleTap(x: Float, width: Float): PlaybackSurfaceGesture {
        if (width <= 0f) return PlaybackSurfaceGesture.NONE
        val fraction = x / width
        return when {
            fraction < CENTER_START -> PlaybackSurfaceGesture.SEEK_BACK
            fraction > CENTER_END -> PlaybackSurfaceGesture.SEEK_FORWARD
            else -> PlaybackSurfaceGesture.TOGGLE_PLAY_PAUSE
        }
    }

    /** Only a right-half hold engages temporary accelerated playback. */
    fun longPress(x: Float, width: Float): PlaybackSurfaceGesture {
        if (width <= 0f) return PlaybackSurfaceGesture.NONE
        return if (x / width > 0.5f) PlaybackSurfaceGesture.HOLD_FAST_FORWARD
        else PlaybackSurfaceGesture.NONE
    }

    fun verticalRelease(
        startX: Float,
        width: Float,
        totalDy: Float,
        height: Float,
        isFullscreen: Boolean,
        pipEnabled: Boolean
    ): PlaybackSurfaceGesture {
        if (width <= 0f || height <= 0f) return PlaybackSurfaceGesture.NONE
        val xFraction = startX / width
        if (xFraction !in CENTER_START..CENTER_END) return PlaybackSurfaceGesture.NONE
        if (abs(totalDy) < height * VERTICAL_THRESHOLD) return PlaybackSurfaceGesture.NONE

        return if (totalDy < 0f) {
            if (isFullscreen) PlaybackSurfaceGesture.NONE else PlaybackSurfaceGesture.ENTER_FULLSCREEN
        } else {
            if (isFullscreen) PlaybackSurfaceGesture.EXIT_FULLSCREEN
            else if (pipEnabled) PlaybackSurfaceGesture.ENTER_PIP
            else PlaybackSurfaceGesture.NONE
        }
    }
}
