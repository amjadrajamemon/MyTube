package com.drivedeck.tubedeck.player

/** Physical alternatives that the Android player can actually select. */
internal object AutoPlayableOptionsPolicy {
    fun choices(
        options: List<YoutubeStreamResolver.VideoOption>,
        progressiveOptions: List<YoutubeStreamResolver.VideoOption>,
        compatibilityMode: Boolean,
        downloadOptions: List<YoutubeStreamResolver.VideoOption> = emptyList()
    ): List<YoutubeStreamResolver.VideoOption> {
        if (!compatibilityMode) return options.filter { it.height > 0 }
            .distinctBy { it.height }
        // Download options preserve MP4 alternatives sometimes dropped by the
        // resolver's display-quality grouping (which may prefer WebM).
        val combined = progressiveOptions + downloadOptions + options
        // Car firmware is more reliable with MP4 and <=480p. Unlike the
        // previous "progressive if any" preference, we must keep lower MP4
        // adaptive streams as candidates for network degradation.
        val compatible = combined.filter {
            it.height in 1..480 && it.videoMimeType == "video/mp4"
        }
        val alternatives = compatible.ifEmpty {
            combined.filter { it.height > 0 && it.videoMimeType == "video/mp4" }
        }.ifEmpty {
            options.filter { it.height > 0 }
        }
        return alternatives.distinctBy { it.height }
    }
}
