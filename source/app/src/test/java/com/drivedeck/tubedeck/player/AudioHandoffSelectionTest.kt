package com.drivedeck.tubedeck.player

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioHandoffSelectionTest {
    private fun media(
        audio: List<YoutubeStreamResolver.AudioOption> = emptyList(),
        live: Boolean = false,
        liveManifest: String? = null,
        progressive: String? = null
    ) = YoutubeStreamResolver.ResolvedMedia(
        title = "Test",
        description = "",
        descriptionIsHtml = false,
        uploaderName = "Test",
        viewCount = 0L,
        uploadDateText = "",
        durationSeconds = 0L,
        progressiveUrl = progressive,
        progressiveMimeType = null,
        progressiveOptions = emptyList(),
        downloadOptions = emptyList(),
        audioOptions = audio,
        options = emptyList(),
        subtitles = emptyList(),
        isLive = live,
        liveManifestUrl = liveManifest
    )

    @Test fun highestBitrateAudioWinsForNormalVideo() {
        val resolved = media(
            audio = listOf(
                YoutubeStreamResolver.AudioOption("https://cdn/low.m4a", "audio/mp4", 64),
                YoutubeStreamResolver.AudioOption("https://cdn/high.m4a", "audio/mp4", 160)
            ),
            progressive = "https://cdn/video.mp4"
        )
        assertEquals(
            "https://cdn/high.m4a",
            YoutubeStreamResolver.preferredAudioPlaybackUrl(resolved)
        )
    }

    @Test fun liveManifestIsUsedWhenNoSeparateAudioExists() {
        val resolved = media(
            live = true,
            liveManifest = "https://cdn/live/master.m3u8"
        )
        assertEquals(
            "https://cdn/live/master.m3u8",
            YoutubeStreamResolver.preferredAudioPlaybackUrl(resolved)
        )
    }

    @Test fun progressiveIsLastFallback() {
        assertEquals(
            "https://cdn/video.mp4",
            YoutubeStreamResolver.preferredAudioPlaybackUrl(
                media(progressive = "https://cdn/video.mp4")
            )
        )
    }
}
