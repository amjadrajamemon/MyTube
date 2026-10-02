package com.drivedeck.tubedeck.data.model

enum class DownloadMode(val label: String) {
    VIDEO("Video + Audio"),
    AUDIO("Audio only (M4A/WebM)")
}

enum class PlayerMode(val label: String) {
    WEB("YouTube Web Player"),
    NATIVE("Native ExoPlayer")
}

enum class TaskStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
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
    val createdAt: Long = System.currentTimeMillis()
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
    val autoLoadUpNext: Boolean = false
)


data class HomeVideo(
    val videoId: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String,
    val channelTitle: String = "YouTube",
    val durationText: String = "",
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
    var isSelected: Boolean = true
)

data class DownloadChoices(val videoQualities: List<String>, val audioAvailable: Boolean)
