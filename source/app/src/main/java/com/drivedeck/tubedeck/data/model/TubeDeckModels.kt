package com.drivedeck.tubedeck.data.model

enum class DownloadMode(val label: String) {
    VIDEO("Video + Audio"),
    AUDIO("Audio only (M4A/WebM)")
}

enum class PlayerMode(val label: String) {
    WEB("YouTube Web Player"),
    NATIVE("Native ExoPlayer")
}

/** Optional viewing treatments; distinct from the full audio-only blackout. */
enum class ViewingMode(val label: String) {
    NORMAL("Normal"),
    LIGHTS_OUT("Lights Out"),
    CINEMA("Cinema"),
    NIGHT("Night")
}

/** Pure viewing policy: PiP must never inherit video dimming. */
object ViewingModePolicy {
    /** Final TextureView opacity. Underlying player container is opaque black. */
    fun videoAlpha(mode: ViewingMode, level: Int, inPip: Boolean = false): Float =
        (1f - videoDim(mode, level, inPip)).coerceIn(.25f, 1f)

    fun videoDim(mode: ViewingMode, level: Int, inPip: Boolean = false): Float {
        if (inPip) return 0f
        val amount = level.coerceIn(10, 85) / 100f
        return when (mode) {
            ViewingMode.NORMAL, ViewingMode.CINEMA -> 0f
            ViewingMode.LIGHTS_OUT -> amount * 0.78f
            ViewingMode.NIGHT -> amount * 0.62f
        }
    }
}

enum class PlaybackContinuationMode(val label: String) {
    LOOP_CURRENT("Loop"),
    AUTO_NEXT("Auto play"),
    EPISODES("Episodes")
}

enum class TaskStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
    RETRIED
}

data class DownloadTask(
    val id: String,
    val url: String,
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val channelTitle: String = "YouTube Media",
    val durationText: String = "",
    val mode: DownloadMode = DownloadMode.VIDEO,
    val quality: String = "Best",
    val outputFormat: String = "",
    val progress: Float = 0f,
    val speed: String = "0 KB/s",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val status: TaskStatus = TaskStatus.QUEUED,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val sessionId: Long = createdAt,
    val eventAt: Long = createdAt
)

data class OfflineMedia(
    val id: String,
    val title: String,
    val videoId: String,
    val thumbnailUrl: String,
    val mediaUri: String,
    val isAudioOnly: Boolean = false,
    val qualityLabel: String = "Auto",
    val format: String = "",
    val fileSizeBytes: Long = 0L,
    val durationSeconds: Long = 0L,
    val downloadDate: Long = System.currentTimeMillis()
)

data class SponsorSegment(
    val category: String,
    val startTime: Float,
    val endTime: Float
)

data class TubeDeckSettings(
    val downloadDir: String = "MyTube",
    val downloadTreeUri: String? = null,
    
    // Separate settings for different download types
    val instantMode: DownloadMode = DownloadMode.VIDEO,
    val instantQuality: String = "1080p",
    val customMode: DownloadMode = DownloadMode.VIDEO,
    val customQuality: String = "Best",
    
    // Playlist settings
    val playlistMode: DownloadMode = DownloadMode.VIDEO,
    val playlistQuality: String = "1080p",
    
    // Player settings
    val playerMode: PlayerMode = PlayerMode.NATIVE,
    val xyAutoCompatibilityMode: Boolean = false,
    val forceTvNavigation: Boolean = false,
    // 0 = adaptive Auto. Positive values remember a fixed preferred height.
    val preferredPlaybackQualityHeight: Int = 0,
    val preferredPlaybackSpeed: Float = 1.0f,
    val seekIntervalSeconds: Int = 10,
    val viewingMode: ViewingMode = ViewingMode.NORMAL,
    val viewingDimPercent: Int = 55,
    val nightAudioEnabled: Boolean = false,
    val autoRotatePlayback: Boolean = true,
    val playbackContinuationMode: PlaybackContinuationMode = PlaybackContinuationMode.AUTO_NEXT,
    val homeSuggestionsEnabled: Boolean = true,
    val homeHistoryEnabled: Boolean = true,
    val homeTrendingEnabled: Boolean = true,
    
    // Movable browser buttons, stored in screen pixels.
    val fabInstantX: Float = 40f,
    val fabInstantY: Float = 120f,
    val fabCustomX: Float = 40f,
    val fabCustomY: Float = 190f,
    
    val adBlockEnabled: Boolean = true,
    val blockVideoAds: Boolean = true,
    val blockBannerAds: Boolean = true,
    val blockTrackers: Boolean = false,
    val sponsorBlockEnabled: Boolean = true,
    val blockShortsEnabled: Boolean = true,
    val pipEnabled: Boolean = true,
    // Performance-first: expensive companion shelves are opt-in by default.
    val autoLoadEpisodes: Boolean = false,
    val autoLoadUpNext: Boolean = false,
    val sideBySideEpisodeFeeds: Boolean = true,
    val desktopYoutubeUiEnabled: Boolean = false,

    // Native-player controller and upward-swipe continuation shelf.
    val playerControllerScale: Float = 1.20f,
    val equalizerTextScale: Float = 1.25f,
    val showRelevantShelf: Boolean = true,
    val showEpisodesShelf: Boolean = true,
    // Next-generation TV improvements are opt-in where extra work is involved.
    val floatingCardsMode: String = "Manual",
    val animatedCardPreviews: Boolean = false, // Off, Manual, Last 15 seconds
    val hideWatchedRecommendations: Boolean = true,
    val lowResourceMode: Boolean = false,
    val resumeAfterInterruptions: Boolean = true,
    // Saved-video playback decision: default Start Over after 3 seconds.
    val resumeDecisionSeconds: Int = 3,
    val subtitleTextScale: Float = 1.0f,
    val subtitleBottomInsetDp: Int = 0,
    val remoteNextKeyCode: Int = 0,
    val remotePreviousKeyCode: Int = 0,

    // Experience Studio: opt-in device-aware layouts, a parked-use car dashboard,
    // independent home/feed appearance, and structural theme controls.
    val adaptiveExperienceEnabled: Boolean = false,
    val carDashboardEnabled: Boolean = false,
    val homeFeedOrder: String = "History,Relevant,Trending",
    val homeDisplayMode: String = "Theme",
    // Independent, persisted browsing preference; never reset on theme/navigation change.
    val homeBrowseMode: String = "List",
    val homeInitialVideoCount: Int = 20,
    val studioHomeLayout: String = "Preset",
    val studioNavigation: String = "Preset",
    val studioPlayer: String = "Preset",
    val studioMotion: String = "Preset",
    val studioDensity: String = "Preset",
    val studioCorners: String = "Preset",
    val studioGlow: String = "Preset",

    // Appearance customization.
    val appearancePreset: String = "Glass Midnight",
    val fontScale: Float = 1.0f,
    // Separate video-title size. Does not magnify toolbars/controls.
    val videoTitleScale: Float = 1.0f,
    val themeAccentArgb: Long = 0xFF00D2FFL,
    val themeBackgroundArgb: Long = 0xFF080B11L,
    val themeTextArgb: Long = 0xFFFFFFFFL
)


data class HomeVideo(
    val videoId: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String,
    val channelTitle: String = "YouTube",
    val durationText: String = "",
    val viewCount: Long = -1L,
    val watchedAt: Long = System.currentTimeMillis()
)

data class PlaylistCandidate(
    val title: String,
    val url: String,
    val channel: String = "YouTube",
    val itemCount: Long = 0L,
    val thumbnailUrl: String = "",
    val kind: String = "Playlist",
    val items: List<PlaylistItem> = emptyList()
)

data class PlaylistItem(
    val videoId: String,
    val title: String,
    val channel: String = "YouTube",
    val duration: String = "",
    val thumbnailUrl: String = "",
    val viewCount: Long = -1L,
    var isSelected: Boolean = true
)

data class DownloadChoices(val videoQualities: List<String>, val audioAvailable: Boolean)

