package com.drivedeck.tubedeck.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSurfaceGesturePolicyTest {
    @Test fun doubleTapEdgesSeekAndCenterTogglesPlayPause() {
        assertEquals(PlaybackSurfaceGesture.SEEK_BACK, PlaybackSurfaceGesturePolicy.doubleTap(100f, 1000f))
        assertEquals(PlaybackSurfaceGesture.TOGGLE_PLAY_PAUSE, PlaybackSurfaceGesturePolicy.doubleTap(500f, 1000f))
        assertEquals(PlaybackSurfaceGesture.SEEK_FORWARD, PlaybackSurfaceGesturePolicy.doubleTap(900f, 1000f))
    }

    @Test fun onlyRightSideLongPressFastForwards() {
        assertEquals(PlaybackSurfaceGesture.NONE, PlaybackSurfaceGesturePolicy.longPress(100f, 1000f))
        assertEquals(PlaybackSurfaceGesture.NONE, PlaybackSurfaceGesturePolicy.longPress(500f, 1000f))
        assertEquals(PlaybackSurfaceGesture.HOLD_FAST_FORWARD, PlaybackSurfaceGesturePolicy.longPress(600f, 1000f))
        assertEquals(PlaybackSurfaceGesture.HOLD_FAST_FORWARD, PlaybackSurfaceGesturePolicy.longPress(900f, 1000f))
        assertEquals(PlaybackSurfaceGesture.NONE, PlaybackSurfaceGesturePolicy.longPress(900f, 0f))
    }

    @Test fun centerVerticalGesturesMoveBetweenFullscreenMiniAndPip() {
        assertEquals(PlaybackSurfaceGesture.ENTER_FULLSCREEN,
            PlaybackSurfaceGesturePolicy.verticalRelease(500f, 1000f, -250f, 1000f, false, true))
        assertEquals(PlaybackSurfaceGesture.EXIT_FULLSCREEN,
            PlaybackSurfaceGesturePolicy.verticalRelease(500f, 1000f, 250f, 1000f, true, true))
        assertEquals(PlaybackSurfaceGesture.ENTER_PIP,
            PlaybackSurfaceGesturePolicy.verticalRelease(500f, 1000f, 250f, 1000f, false, true))
    }

    @Test fun sideVerticalGesturesRemainBrightnessAndVolumeOnly() {
        assertEquals(PlaybackSurfaceGesture.NONE,
            PlaybackSurfaceGesturePolicy.verticalRelease(100f, 1000f, -400f, 1000f, false, true))
        assertEquals(PlaybackSurfaceGesture.NONE,
            PlaybackSurfaceGesturePolicy.verticalRelease(900f, 1000f, 400f, 1000f, false, true))
    }
}
