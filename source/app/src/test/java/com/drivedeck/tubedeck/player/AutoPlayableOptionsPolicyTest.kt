package com.drivedeck.tubedeck.player

import org.junit.Assert.*
import org.junit.Test

class AutoPlayableOptionsPolicyTest {
    private fun option(h: Int, mime: String, progressive: Boolean = false) =
        YoutubeStreamResolver.VideoOption(
            label = "${h}p",
            height = h,
            videoUrl = "https://example.test/video/$h",
            audioUrl = if (progressive) null else "https://example.test/audio",
            videoMimeType = mime,
            audioMimeType = if (progressive) null else "audio/mp4"
        )

    @Test fun carUnitUsesActualPlayableLowerMp4Alternatives() {
        val progressive = listOf(option(480, "video/mp4", true))
        val fromResolver = listOf(
            option(1080, "video/mp4"), option(480, "video/mp4"),
            option(360, "video/webm"), option(240, "video/mp4")
        )
        val eligible = AutoPlayableOptionsPolicy.choices(fromResolver, progressive, true)
        assertEquals(listOf(480, 240), eligible.map { it.height })
        assertEquals(240, AdaptiveQualityPolicy.nextLower(eligible.map { it.height }, 480))
        assertNull(AdaptiveQualityPolicy.nextLower(eligible.map { it.height }, 240))
    }

    @Test fun lowerMp4FoundInDownloadFormatsEvenWhenDisplayOptionsChooseWebm() {
        val progressive = listOf(option(480, "video/mp4", true))
        val displayOptions = listOf(option(480, "video/mp4"), option(240, "video/webm"))
        val downloads = listOf(option(240, "video/mp4"))
        val eligible = AutoPlayableOptionsPolicy.choices(
            displayOptions, progressive, compatibilityMode = true,
            downloadOptions = downloads
        )
        assertEquals(listOf(480, 240), eligible.map { it.height })
    }

    @Test fun nonCarKeepsAllRealResolverQualityAlternatives() {
        val videos = listOf(option(1080, "video/mp4"), option(720, "video/webm"))
        assertEquals(listOf(1080, 720),
            AutoPlayableOptionsPolicy.choices(videos, emptyList(), false).map { it.height })
    }
}
