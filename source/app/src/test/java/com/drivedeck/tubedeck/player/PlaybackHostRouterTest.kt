package com.drivedeck.tubedeck.player

import com.drivedeck.tubedeck.data.model.PlayerMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackHostRouterTest {
    @Test
    fun onlineYoutubeVideoUsesSavedWebMode() {
        assertEquals(
            PlaybackHost.WEB,
            PlaybackHostRouter.choose(
                PlayerMode.WEB,
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
            )
        )
    }

    @Test
    fun onlineYoutubeVideoUsesSavedNativeMode() {
        assertEquals(
            PlaybackHost.NATIVE,
            PlaybackHostRouter.choose(
                PlayerMode.NATIVE,
                "https://youtu.be/dQw4w9WgXcQ"
            )
        )
    }

    @Test
    fun localContentAlwaysUsesNativePlayer() {
        assertEquals(
            PlaybackHost.NATIVE,
            PlaybackHostRouter.choose(
                PlayerMode.WEB,
                "content://com.android.providers.downloads.documents/document/42"
            )
        )
    }
}
