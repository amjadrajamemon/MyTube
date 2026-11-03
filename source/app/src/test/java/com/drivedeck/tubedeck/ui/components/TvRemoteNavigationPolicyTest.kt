package com.drivedeck.tubedeck.ui.components

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class TvRemoteNavigationPolicyTest {
    private fun decide(
        key: Int,
        surface: Boolean = true,
        visible: Boolean = false,
        modal: Boolean = false,
        repeat: Int = 0
    ) = TvRemoteNavigationPolicy.decide(key, repeat, surface, visible, modal)

    @Test fun centerOpensPlayerControlsWithoutPausingPlayback() {
        assertEquals(TvPlayerKeyAction.REVEAL_CENTER_CONTROLS,
            decide(KeyEvent.KEYCODE_DPAD_CENTER).action)
        assertEquals(TvPlayerKeyAction.IGNORE,
            decide(KeyEvent.KEYCODE_DPAD_CENTER, surface = false, visible = true).action)
    }

    @Test fun dpadNavigatesVisibleControlsInsteadOfSeekingBehindThem() {
        assertEquals(TvPlayerKeyAction.REVEAL_CENTER_CONTROLS,
            decide(KeyEvent.KEYCODE_DPAD_RIGHT, visible = true).action)
        assertEquals(TvPlayerKeyAction.IGNORE,
            decide(KeyEvent.KEYCODE_DPAD_RIGHT, surface = false, visible = true).action)
        assertEquals(TvPlayerKeyAction.REVEAL_TOP_CONTROLS,
            decide(KeyEvent.KEYCODE_DPAD_UP).action)
    }

    @Test fun hiddenControlsSupportSeekingWithAcceleratedRepeats() {
        assertEquals(TvPlayerKeyAction.SEEK_BACKWARD,
            decide(KeyEvent.KEYCODE_DPAD_LEFT).action)
        val held = decide(KeyEvent.KEYCODE_DPAD_RIGHT, repeat = 9)
        assertEquals(TvPlayerKeyAction.SEEK_FORWARD, held.action)
        assertEquals(3, held.multiplier)
    }

    @Test fun keyboardMediaPageAndChannelKeysMatchTvPlaybackActions() {
        assertEquals(TvPlayerKeyAction.NEXT, decide(KeyEvent.KEYCODE_CHANNEL_UP).action)
        assertEquals(TvPlayerKeyAction.PREVIOUS, decide(KeyEvent.KEYCODE_PAGE_DOWN).action)
        assertEquals(TvPlayerKeyAction.PLAY_PAUSE, decide(KeyEvent.KEYCODE_SPACE).action)
        assertEquals(TvPlayerKeyAction.PLAY_PAUSE, decide(KeyEvent.KEYCODE_K).action)
        assertEquals(TvPlayerKeyAction.SEEK_BACKWARD, decide(KeyEvent.KEYCODE_J).action)
        assertEquals(TvPlayerKeyAction.SEEK_FORWARD, decide(KeyEvent.KEYCODE_L).action)
        assertEquals(TvPlayerKeyAction.STOP, decide(KeyEvent.KEYCODE_MEDIA_STOP).action)
    }

    @Test fun escapeClosesOverlayFirstAndThenPlayer() {
        assertEquals(TvPlayerKeyAction.HIDE_CONTROLS,
            decide(KeyEvent.KEYCODE_ESCAPE, visible = true).action)
        assertEquals(TvPlayerKeyAction.STOP,
            decide(KeyEvent.KEYCODE_ESCAPE).action)
    }

    @Test fun digitsSeekByVideoPercentageOnlyOnUnobscuredSurface() {
        assertEquals(60, decide(KeyEvent.KEYCODE_6).percent)
        assertEquals(TvPlayerKeyAction.IGNORE,
            decide(KeyEvent.KEYCODE_6, surface = false).action)
        assertEquals(TvPlayerKeyAction.IGNORE,
            decide(KeyEvent.KEYCODE_6, visible = true).action)
    }

    @Test fun dialogsNeverLeakRemoteEventsIntoPlayback() {
        for (key in listOf(
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_K,
            KeyEvent.KEYCODE_ESCAPE
        )) {
            assertEquals(TvPlayerKeyAction.IGNORE, decide(key, modal = true).action)
        }
    }
}
