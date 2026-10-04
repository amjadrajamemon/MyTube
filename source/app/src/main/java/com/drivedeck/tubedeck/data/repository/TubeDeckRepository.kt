package com.drivedeck.tubedeck.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.telephony.TelephonyManager
import java.util.Locale
import com.drivedeck.tubedeck.player.YoutubeWatchNextClient
import com.drivedeck.tubedeck.data.model.*
import com.drivedeck.tubedeck.player.YoutubeStreamResolver
import com.drivedeck.tubedeck.player.YoutubeUrls
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/** Application-scoped queue. Interrupted work restarts on the next app launch. */
class TubeDeckRepository private constructor(private val context: Context) {
    companion object {
        private const val SYSTEM_TIMEOUT_MESSAGE = "Waiting for MyTube after Android background transfer limit"
        @Volatile private var instance: TubeDeckRepository? = null
        fun get(context: Context): TubeDeckRepository =
            instance ?: synchronized(this) {
                instance ?: TubeDeckRepository(context.applicationContext).also { instance = it }
            }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("tubedeck_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lock = Any()
    private val activeJobs = mutableMapOf<String, Job>()
    private var sponsorJob: Job? = null
    private var activeSponsorId: String? = null
    private var homeSuggestionsJob: Job? = null
    private var homeTrendingJob: Job? = null
    @Volatile private var schedulingSuspendedBySystem = false

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<TubeDeckSettings> = _settings.asStateFlow()
    private val _blockedAdsCount = MutableStateFlow(prefs.getInt("blocked_ads_count", 0))
    val blockedAdsCount: StateFlow<Int> = _blockedAdsCount.asStateFlow()
    private val _downloadQueue = MutableStateFlow(loadQueue())
    val downloadQueue: StateFlow<List<DownloadTask>> = _downloadQueue.asStateFlow()
    private val _offlineMediaList = MutableStateFlow(loadOfflineMedia())
    val offlineMediaList: StateFlow<List<OfflineMedia>> = _offlineMediaList.asStateFlow()
    private val _fileClipboard = MutableStateFlow<FileClipboard?>(null)
    val fileClipboard: StateFlow<FileClipboard?> = _fileClipboard.asStateFlow()
    private val _downloadHistory = MutableStateFlow(loadHistory())
    val downloadHistory: StateFlow<List<DownloadTask>> = _downloadHistory.asStateFlow()
    private val _watchHistory = MutableStateFlow(loadWatchHistory())
    val watchHistory: StateFlow<List<HomeVideo>> = _watchHistory.asStateFlow()
    private val _homeSuggestions = MutableStateFlow(loadHomeSuggestions())
    val homeSuggestions: StateFlow<List<HomeVideo>> = _homeSuggestions.asStateFlow()
    private val _homeTrending = MutableStateFlow<List<HomeVideo>>(emptyList())
    val homeTrending: StateFlow<List<HomeVideo>> = _homeTrending.asStateFlow()
    private val _homeCountry = MutableStateFlow(detectHomeCountry())
    val homeCountry: StateFlow<String> = _homeCountry.asStateFlow()
    private val _homeTrendingStatus = MutableStateFlow("")
    val homeTrendingStatus: StateFlow<String> = _homeTrendingStatus.asStateFlow()

    private val _currentSponsorSegments = MutableStateFlow<List<SponsorSegment>>(emptyList())
    val currentSponsorSegments: StateFlow<List<SponsorSegment>> = _currentSponsorSegments.asStateFlow()

    init {
        DownloadQueueNotifier.update(context, _downloadQueue.value, force = true)
        processQueue()
    }

    fun updateSettings(value: TubeDeckSettings) {
        val previous = _settings.value
        val next = value
        _settings.value = next
        prefs.edit().apply {
            putString("downloadDir", next.downloadDir); putString("downloadTreeUri", next.downloadTreeUri)
            remove("maxConcurrent")
            putString("instantMode", next.instantMode.name); putString("instantQuality", next.instantQuality)
            putString("customMode", next.customMode.name); putString("customQuality", next.customQuality)
            putString("playlistMode", next.playlistMode.name); putString("playlistQuality", next.playlistQuality)
            putString("playerMode", next.playerMode.name)
            putInt("preferredPlaybackQualityHeight", next.preferredPlaybackQualityHeight.coerceAtLeast(0))
            putFloat("preferredPlaybackSpeed", next.preferredPlaybackSpeed.coerceIn(0.25f, 2.0f))
            putInt("seekIntervalSeconds", next.seekIntervalSeconds.coerceIn(5, 60))
            putBoolean("autoRotatePlayback", next.autoRotatePlayback)
            putBoolean("homeSuggestionsEnabled", next.homeSuggestionsEnabled)
            putBoolean("homeTrendingEnabled", next.homeTrendingEnabled)
            putFloat("fabInstantX", next.fabInstantX); putFloat("fabInstantY", next.fabInstantY)
            putFloat("fabCustomX", next.fabCustomX); putFloat("fabCustomY", next.fabCustomY)
            putBoolean("adBlockEnabled", next.adBlockEnabled); putBoolean("blockVideoAds", next.blockVideoAds)
            putBoolean("blockBannerAds", next.blockBannerAds)
            putBoolean("blockTrackers", next.blockTrackers); putBoolean("sponsorBlockEnabled", next.sponsorBlockEnabled)
            putBoolean("blockShortsEnabled", next.blockShortsEnabled); putBoolean("pipEnabled", next.pipEnabled)
            putBoolean("autoLoadEpisodes", next.autoLoadEpisodes)
            putBoolean("autoLoadUpNext", next.autoLoadUpNext)
            putString("appearancePreset", next.appearancePreset)
            putFloat("fontScale", next.fontScale.coerceIn(0.80f, 1.35f))
            putLong("themeAccentArgb", next.themeAccentArgb)
            putLong("themeBackgroundArgb", next.themeBackgroundArgb)
            putLong("themeTextArgb", next.themeTextArgb)
            apply()
        }
        if (!next.homeSuggestionsEnabled) {
            homeSuggestionsJob?.cancel()
            _homeSuggestions.value = emptyList()
        } else if (!previous.homeSuggestionsEnabled) {
            _homeSuggestions.value = loadHomeSuggestions()
        }
        if (!next.homeTrendingEnabled) {
            homeTrendingJob?.cancel()
            _homeTrendingStatus.value = ""
            _homeTrending.value = emptyList()
        }
        processQueue()
    }

    private fun loadSettings() = TubeDeckSettings(
        downloadDir = prefs.getString("downloadDir", "MyTube")?.takeIf { it.matches(Regex("[a-zA-Z0-9 _-]{1,40}")) } ?: "MyTube",
        downloadTreeUri = prefs.getString("downloadTreeUri", null),
        instantMode = enumValue(prefs.getString("instantMode", null), DownloadMode.VIDEO),
        instantQuality = prefs.getString("instantQuality", "Best") ?: "Best",
        customMode = enumValue(prefs.getString("customMode", null), DownloadMode.VIDEO),
        customQuality = prefs.getString("customQuality", "Best") ?: "Best",
        playlistMode = enumValue(prefs.getString("playlistMode", null), DownloadMode.VIDEO),
        playlistQuality = prefs.getString("playlistQuality", "Best") ?: "Best",
        playerMode = enumValue(prefs.getString("playerMode", null), PlayerMode.NATIVE),
        preferredPlaybackQualityHeight = prefs.getInt("preferredPlaybackQualityHeight", 0).coerceAtLeast(0),
        preferredPlaybackSpeed = prefs.getFloat("preferredPlaybackSpeed", 1.0f).coerceIn(0.25f, 2.0f),
        seekIntervalSeconds = prefs.getInt("seekIntervalSeconds", 10).coerceIn(5, 60),
        autoRotatePlayback = prefs.getBoolean("autoRotatePlayback", true),
        homeSuggestionsEnabled = prefs.getBoolean("homeSuggestionsEnabled", true),
        homeTrendingEnabled = prefs.getBoolean("homeTrendingEnabled", true),
        fabInstantX = prefs.getFloat("fabInstantX", 40f), fabInstantY = prefs.getFloat("fabInstantY", 120f),
        fabCustomX = prefs.getFloat("fabCustomX", 40f), fabCustomY = prefs.getFloat("fabCustomY", 190f),
        adBlockEnabled = prefs.getBoolean("adBlockEnabled", true),
        blockVideoAds = prefs.getBoolean("blockVideoAds", true),
        blockBannerAds = prefs.getBoolean("blockBannerAds", true),
        blockTrackers = prefs.getBoolean("blockTrackers", false),
        sponsorBlockEnabled = prefs.getBoolean("sponsorBlockEnabled", true),
        blockShortsEnabled = prefs.getBoolean("blockShortsEnabled", true),
        pipEnabled = prefs.getBoolean("pipEnabled", true),
        autoLoadEpisodes = prefs.getBoolean("autoLoadEpisodes", false),
        autoLoadUpNext = prefs.getBoolean("autoLoadUpNext", false),
        appearancePreset = prefs.getString("appearancePreset", "Glass Midnight") ?: "Glass Midnight",
        fontScale = prefs.getFloat("fontScale", 1.0f).coerceIn(0.80f, 1.35f),
        themeAccentArgb = prefs.getLong("themeAccentArgb", 0xFF00D2FFL),
        themeBackgroundArgb = prefs.getLong("themeBackgroundArgb", 0xFF080B11L),
        themeTextArgb = prefs.getLong("themeTextArgb", 0xFFFFFFFFL)
    )

    private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
        runCatching { enumValueOf<T>(value ?: fallback.name) }.getOrDefault(fallback)

    fun extractVideoId(url: String): String? = YoutubeUrls.videoId(url)
    fun getVideoThumbnail(videoId: String): String = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    fun recordWatch(media: OfflineMedia) {
        val videoId = media.videoId.ifBlank { YoutubeUrls.videoId(media.mediaUri).orEmpty() }
        if (videoId.isBlank() || YoutubeUrls.videoId(media.mediaUri) == null) return
        val item = HomeVideo(
            videoId = videoId,
            title = media.title.ifBlank { "YouTube video" },
            url = media.mediaUri,
            thumbnailUrl = media.thumbnailUrl.ifBlank { getVideoThumbnail(videoId) },
            watchedAt = System.currentTimeMillis()
        )
        val next = (listOf(item) + _watchHistory.value.filterNot { it.videoId == videoId }).take(40)
        _watchHistory.value = next
        saveHomeVideos("watch_history_json", next)
    }

    fun clearWatchHistory() {
        homeSuggestionsJob?.cancel()
        _watchHistory.value = emptyList()
        _homeSuggestions.value = emptyList()
        prefs.edit().remove("watch_history_json").remove("home_suggestions_json")
            .remove("home_suggestions_seed").remove("home_suggestions_updated").apply()
    }

    private fun detectHomeCountry(): String {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return HomeFeedPolicy.country(
            runCatching { telephony?.networkCountryIso }.getOrNull(),
            runCatching { telephony?.simCountryIso }.getOrNull(),
            Locale.getDefault().country
        )
    }

    fun refreshHomeFeed(accessToken: String?, force: Boolean = false) {
        val current = _settings.value
        val now = System.currentTimeMillis()
        val region = detectHomeCountry()
        _homeCountry.value = region
        val seed = _watchHistory.value.firstOrNull()?.url
        if (current.homeSuggestionsEnabled && seed != null && homeSuggestionsJob?.isActive != true &&
            (seed != prefs.getString("home_suggestions_seed", null) || HomeFeedPolicy.shouldRefresh(
                true, now, prefs.getLong("home_suggestions_updated", 0L), force))) {
            homeSuggestionsJob = scope.launch {
                try {
                    val items = YoutubeWatchNextClient.resolve(seed, YoutubeUrls.videoId(seed)!!).relatedVideos
                    ensureActive()
                    if (!_settings.value.homeSuggestionsEnabled) return@launch
                    _homeSuggestions.value = items.distinctBy { it.videoId }.take(12).map { item ->
                        HomeVideo(item.videoId, item.title, "https://www.youtube.com/watch?v=${item.videoId}",
                            item.thumbnailUrl, item.uploader, formatDurationLabel(item.durationSeconds), 0L)
                    }
                    saveHomeVideos("home_suggestions_json", _homeSuggestions.value)
                    prefs.edit().putString("home_suggestions_seed", seed)
                        .putLong("home_suggestions_updated", now).apply()
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    // Back off failed requests too, retaining a useful cached shelf.
                    prefs.edit().putLong("home_suggestions_updated", now).putString("home_suggestions_seed", seed).apply()
                }
            }
        }
        if (!current.homeTrendingEnabled) return
        val cacheKey = "home_trending_$region"
        if (_homeTrending.value.isEmpty() || region != prefs.getString("home_trending_region", null)) {
            _homeTrending.value = loadHomeVideos(cacheKey)
        }
        if (accessToken.isNullOrBlank()) {
            _homeTrendingStatus.value = "Connect YouTube"
            return
        }
        if (homeTrendingJob?.isActive == true || !HomeFeedPolicy.shouldRefresh(
                true, now, prefs.getLong("${cacheKey}_updated", 0L), force)) return
        homeTrendingJob = scope.launch {
            _homeTrendingStatus.value = "Loading"
            try {
                val items = YoutubeHomeFeedClient.trending(accessToken, region)
                ensureActive()
                if (!_settings.value.homeTrendingEnabled) return@launch
                _homeTrending.value = items
                saveHomeVideos(cacheKey, items)
                prefs.edit().putLong("${cacheKey}_updated", now).putString("home_trending_region", region).apply()
                _homeTrendingStatus.value = ""
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                prefs.edit().putLong("${cacheKey}_updated", now).apply()
                _homeTrendingStatus.value = "Retry"
            }
        }
    }

    suspend fun fetchDownloadChoices(url: String): DownloadChoices {
        val media = YoutubeStreamResolver.resolve(context, url)
        return DownloadChoices(
            media.downloadOptions.map { it.label }.distinct(),
            media.audioOptions.isNotEmpty()
        )
    }

    suspend fun discoverPlaylistCandidates(url: String, titleHint: String): List<PlaylistCandidate> =
        YoutubeStreamResolver.discoverPlaylists(url, titleHint).map { candidate ->
            PlaylistCandidate(
                title = candidate.title,
                url = candidate.url,
                channel = candidate.uploader,
                itemCount = candidate.itemCount,
                thumbnailUrl = candidate.thumbnailUrl,
                kind = candidate.kind,
                items = candidate.episodes.map { episode ->
                    PlaylistItem(
                        videoId = episode.videoId,
                        title = episode.title,
                        channel = episode.uploader,
                        duration = if (episode.durationSeconds > 0) {
                            "${episode.durationSeconds / 60}:${(episode.durationSeconds % 60).toString().padStart(2, '0')}"
                        } else "",
                        thumbnailUrl = episode.thumbnailUrl
                    )
                }
            )
        }

    suspend fun fetchPlaylistItems(url: String): List<PlaylistItem> =
        YoutubeStreamResolver.playlistItems(url).mapNotNull { item ->
            val id = YoutubeUrls.videoId(item.url) ?: return@mapNotNull null
            PlaylistItem(
                videoId = id, title = item.name, channel = item.uploaderName ?: "YouTube",
                duration = if (item.duration > 0) "${item.duration / 60}:${(item.duration % 60).toString().padStart(2, '0')}" else "",
                thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: getVideoThumbnail(id)
            )
        }.distinctBy { it.videoId }

    fun incrementBlockedAds(count: Int = 1) {
        _blockedAdsCount.value += count
        prefs.edit().putInt("blocked_ads_count", _blockedAdsCount.value).apply()
    }
    fun resetBlockedAdsCount() {
        _blockedAdsCount.value = 0
        prefs.edit().putInt("blocked_ads_count", 0).apply()
    }

    fun fetchSponsorSegments(videoId: String) {
        sponsorJob?.cancel()
        activeSponsorId = videoId.takeIf { it.isNotBlank() && _settings.value.sponsorBlockEnabled }
        _currentSponsorSegments.value = emptyList()
        if (activeSponsorId == null) return
        sponsorJob = scope.launch {
            val categories = "[\"sponsor\",\"selfpromo\",\"interaction\",\"intro\",\"outro\",\"preview\"]"
            val url = URL("https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=${URLEncoder.encode(categories, "UTF-8")}")
            var connection: HttpURLConnection? = null
            try {
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000; readTimeout = 5000
                }
                val segments = if (connection.responseCode == 200) {
                    val array = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
                    List(array.length()) { index ->
                        val item = array.getJSONObject(index)
                        val span = item.getJSONArray("segment")
                        SponsorSegment(item.getString("category"), span.getDouble(0).toFloat(), span.getDouble(1).toFloat())
                    }
                } else emptyList()
                if (activeSponsorId == videoId && isActive) _currentSponsorSegments.value = segments
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                if (activeSponsorId == videoId) _currentSponsorSegments.value = emptyList()
            } finally { connection?.disconnect() }
        }
    }

    fun enqueueDownload(
        url: String, title: String? = null,
        mode: DownloadMode = _settings.value.instantMode,
        quality: String = _settings.value.instantQuality
    ): String {
        require(!_settings.value.downloadTreeUri.isNullOrBlank()) { "Choose a download folder in Settings first" }
        val videoId = YoutubeUrls.videoId(url) ?: throw IllegalArgumentException("Open a valid YouTube video first")
        val task = DownloadTask(
            id = UUID.randomUUID().toString(), url = url, videoId = videoId,
            title = title ?: "Video $videoId", thumbnailUrl = getVideoThumbnail(videoId),
            mode = mode, quality = if (mode == DownloadMode.AUDIO) "Best" else quality,
            outputFormat = if (mode == DownloadMode.AUDIO) "M4A/WebM" else "MP4/WebM"
        )
        synchronized(lock) { setQueue(_downloadQueue.value + task) }
        processQueue()
        return task.id
    }

    fun enqueuePlaylist(items: List<PlaylistItem>, mode: DownloadMode, quality: String) {
        require(!_settings.value.downloadTreeUri.isNullOrBlank()) { "Choose a download folder in Settings first" }
        val tasks = items.filter { it.isSelected }.mapNotNull { item ->
            val url = "https://www.youtube.com/watch?v=${item.videoId}"
            val videoId = YoutubeUrls.videoId(url) ?: return@mapNotNull null
            DownloadTask(
                id = UUID.randomUUID().toString(), url = url, videoId = videoId,
                title = item.title, thumbnailUrl = item.thumbnailUrl.ifBlank { getVideoThumbnail(videoId) },
                mode = mode, quality = if (mode == DownloadMode.AUDIO) "Best" else quality,
                outputFormat = if (mode == DownloadMode.AUDIO) "M4A/WebM" else "MP4/WebM"
            )
        }
        if (tasks.isEmpty()) return
        synchronized(lock) { setQueue(_downloadQueue.value + tasks) }
        processQueue()
    }

    private fun processQueue() {
        if (schedulingSuspendedBySystem) return
        if (_downloadQueue.value.any { it.status == TaskStatus.QUEUED || it.status == TaskStatus.DOWNLOADING }) {
            DownloadForegroundService.ensureRunning(context)
        }

        // The user-facing queue remains unrestricted: every item may be queued and auto-retried.
        // Heavy transfers are admitted through an invisible device-aware governor so a large
        // playlist cannot exhaust RAM/file descriptors, trigger thermal throttling, or make all
        // downloads slower by starting hundreds of extractors and muxers at once.
        val toStart = mutableListOf<Job>()
        synchronized(lock) {
            val targetConcurrency = DownloadResourceGovernor.targetConcurrency(context)
            while (activeJobs.size < targetConcurrency) {
                val next = _downloadQueue.value.firstOrNull {
                    it.status == TaskStatus.QUEUED && it.id !in activeJobs
                } ?: break
                updateTask(next.id) { it.copy(status = TaskStatus.DOWNLOADING, speed = "Resolving…") }
                val job = scope.launch(start = CoroutineStart.LAZY) { runTask(next.id) }
                activeJobs[next.id] = job
                job.invokeOnCompletion {
                    synchronized(lock) {
                        if (activeJobs[next.id] === job) activeJobs.remove(next.id)
                    }
                    processQueue()
                }
                toStart.add(job)
            }
        }
        toStart.forEach { it.start() }
    }

    private suspend fun runTask(id: String) {
        var retryAttempt = synchronized(lock) {
            _downloadQueue.value.firstOrNull { it.id == id }?.retryCount ?: 0
        }

        while (currentCoroutineContext().isActive) {
            val task = synchronized(lock) {
                _downloadQueue.value.firstOrNull { it.id == id && it.status == TaskStatus.DOWNLOADING }
            } ?: return

            try {
                val media = MediaDownloader.download(context, task, _settings.value) { progress ->
                    synchronized(lock) {
                        updateTask(id, persist = false) {
                            it.copy(
                                title = progress.title,
                                quality = progress.quality,
                                outputFormat = progress.format,
                                downloadedBytes = progress.bytes,
                                totalBytes = progress.total,
                                progress = if (progress.total > 0) {
                                    (progress.bytes.toFloat() / progress.total).coerceIn(0f, 1f)
                                } else {
                                    it.progress
                                },
                                speed = progress.speed,
                                errorMessage = null
                            )
                        }
                    }
                }

                val accepted = synchronized(lock) {
                    val current = _downloadQueue.value.firstOrNull {
                        it.id == id && it.status == TaskStatus.DOWNLOADING
                    }
                    if (current != null) {
                        val complete = current.copy(
                            status = TaskStatus.COMPLETED,
                            progress = 1f,
                            speed = "Done",
                            totalBytes = media.fileSizeBytes,
                            downloadedBytes = media.fileSizeBytes,
                            errorMessage = null
                        )
                        updateTask(id) { complete }
                        _offlineMediaList.value =
                            listOf(media) + _offlineMediaList.value.filterNot { it.id == media.id }
                        saveOfflineMedia(_offlineMediaList.value)
                        _downloadHistory.value =
                            (listOf(complete) + _downloadHistory.value).take(100)
                        saveHistory(_downloadHistory.value)
                        true
                    } else {
                        false
                    }
                }

                if (accepted) {
                    // Remove any stale .part fragments left in an older app-storage folder if the
                    // user changed download destination while this task was retrying.
                    MediaDownloader.deletePartials(context, id)
                } else {
                    MediaDownloader.deleteOutput(context, media)
                }
                return
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                val recoverable = DownloadFailureClassifier.isRecoverable(error)

                if (!recoverable) {
                    synchronized(lock) {
                        updateTask(id) {
                            if (it.status == TaskStatus.DOWNLOADING) {
                                it.copy(
                                    status = TaskStatus.FAILED,
                                    speed = "Needs attention",
                                    errorMessage = error.message ?: "Download cannot continue automatically"
                                )
                            } else {
                                it
                            }
                        }
                    }
                    return
                }

                retryAttempt += 1
                val waitSeconds = retryDelaySeconds(retryAttempt)
                synchronized(lock) {
                    updateTask(id) {
                        if (it.status == TaskStatus.DOWNLOADING) {
                            it.copy(
                                speed = "Auto retry in ${waitSeconds}s",
                                retryCount = retryAttempt,
                                errorMessage = buildString {
                                    append("Temporary interruption")
                                    error.message?.takeIf { msg -> msg.isNotBlank() }?.let {
                                        append(": ").append(it)
                                    }
                                    append(" • attempt ").append(retryAttempt)
                                }
                            )
                        } else {
                            it
                        }
                    }
                }

                delay(waitSeconds * 1_000L)

                val stillActive = synchronized(lock) {
                    _downloadQueue.value.any {
                        it.id == id && it.status == TaskStatus.DOWNLOADING
                    }
                }
                if (!stillActive) return

                synchronized(lock) {
                    updateTask(id) {
                        if (it.status == TaskStatus.DOWNLOADING) {
                            it.copy(
                                speed = "Reconnecting…",
                                errorMessage = null
                            )
                        } else {
                            it
                        }
                    }
                }
                // Loop: resolve fresh YouTube stream URLs, then MediaDownloader.transfer() resumes
                // the retained .part file using HTTP Range when supported.
            }
        }
    }

    private fun retryDelaySeconds(attempt: Int): Long = when {
        attempt <= 1 -> 3L
        attempt == 2 -> 5L
        attempt == 3 -> 10L
        attempt == 4 -> 20L
        attempt == 5 -> 30L
        else -> 60L
    }

    /**
     * Android can time-limit dataSync foreground services after very long background transfers.
     * Pause live jobs without losing partial files, then resume automatically when MyTube becomes
     * user-visible again (or on the next process launch).
     */
    fun suspendDownloadsForSystemTimeout() {
        val jobs = synchronized(lock) {
            schedulingSuspendedBySystem = true
            val running = activeJobs.values.toList()
            val updated = _downloadQueue.value.map { task ->
                if (task.status == TaskStatus.DOWNLOADING || task.status == TaskStatus.QUEUED) {
                    task.copy(
                        status = TaskStatus.PAUSED,
                        speed = "Waiting to resume",
                        errorMessage = SYSTEM_TIMEOUT_MESSAGE
                    )
                } else task
            }
            setQueue(updated)
            running
        }
        jobs.forEach { it.cancel() }
    }

    fun resumeDownloadsAfterSystemTimeout() {
        val shouldResume = synchronized(lock) {
            val hasSystemPaused = _downloadQueue.value.any {
                it.status == TaskStatus.PAUSED && it.errorMessage == SYSTEM_TIMEOUT_MESSAGE
            }
            if (!hasSystemPaused && !schedulingSuspendedBySystem) return@synchronized false
            schedulingSuspendedBySystem = false
            setQueue(
                _downloadQueue.value.map { task ->
                    if (task.status == TaskStatus.PAUSED && task.errorMessage == SYSTEM_TIMEOUT_MESSAGE) {
                        task.copy(status = TaskStatus.QUEUED, speed = "Queued", errorMessage = null)
                    } else task
                }
            )
            true
        }
        if (shouldResume) processQueue()
    }

    fun pauseTask(id: String) {
        synchronized(lock) {
            updateTask(id) { if (it.status == TaskStatus.DOWNLOADING || it.status == TaskStatus.QUEUED) it.copy(status = TaskStatus.PAUSED, speed = "Paused") else it }
            activeJobs[id]?.cancel()
        }
        processQueue()
    }
    fun resumeTask(id: String) {
        synchronized(lock) {
            updateTask(id) { if (it.status == TaskStatus.PAUSED || it.status == TaskStatus.FAILED) it.copy(status = TaskStatus.QUEUED, errorMessage = null) else it }
        }
        processQueue()
    }
    fun cancelTask(id: String) {
        val job = synchronized(lock) {
            val running = activeJobs[id]
            running?.cancel()
            setQueue(_downloadQueue.value.filterNot { it.id == id })
            running
        }
        scope.launch {
            job?.join()
            MediaDownloader.deletePartials(context, id)
        }
        processQueue()
    }
    fun retryTask(id: String) = resumeTask(id)

    fun pauseTasks(ids: Set<String>) {
        if (ids.isEmpty()) return
        synchronized(lock) {
            val targets = ids.toSet()
            val updated = _downloadQueue.value.map { task ->
                if (task.id in targets && (task.status == TaskStatus.DOWNLOADING || task.status == TaskStatus.QUEUED)) {
                    activeJobs[task.id]?.cancel()
                    task.copy(status = TaskStatus.PAUSED, speed = "Paused")
                } else {
                    task
                }
            }
            setQueue(updated)
        }
        processQueue()
    }

    fun resumeOrRetryTasks(ids: Set<String>) {
        if (ids.isEmpty()) return
        synchronized(lock) {
            val targets = ids.toSet()
            val updated = _downloadQueue.value.map { task ->
                if (task.id in targets && (task.status == TaskStatus.PAUSED || task.status == TaskStatus.FAILED)) {
                    task.copy(status = TaskStatus.QUEUED, speed = "Queued", errorMessage = null)
                } else {
                    task
                }
            }
            setQueue(updated)
        }
        processQueue()
    }

    fun pauseAllTasks() {
        pauseTasks(
            _downloadQueue.value
                .filter { it.status == TaskStatus.DOWNLOADING || it.status == TaskStatus.QUEUED }
                .mapTo(mutableSetOf()) { it.id }
        )
    }

    fun resumeOrRetryAllTasks() {
        resumeOrRetryTasks(
            _downloadQueue.value
                .filter { it.status == TaskStatus.PAUSED || it.status == TaskStatus.FAILED }
                .mapTo(mutableSetOf()) { it.id }
        )
    }

    fun deleteTasks(ids: Set<String>) {
        if (ids.isEmpty()) return
        val targets = ids.toSet()
        val jobs = synchronized(lock) {
            val running = targets.mapNotNull { id ->
                activeJobs[id]?.also { it.cancel() }?.let { id to it }
            }
            setQueue(_downloadQueue.value.filterNot { it.id in targets })
            running
        }
        scope.launch {
            jobs.forEach { (_, job) -> runCatching { job.join() } }
            targets.forEach { id -> runCatching { MediaDownloader.deletePartials(context, id) } }
        }
        processQueue()
    }

    fun deleteAllQueueTasks() {
        deleteTasks(_downloadQueue.value.mapTo(mutableSetOf()) { it.id })
    }

    fun clearFinished() = synchronized(lock) {
        setQueue(_downloadQueue.value.filterNot { it.status == TaskStatus.COMPLETED })
    }

    fun refreshDownloadNotification() {
        DownloadQueueNotifier.update(context, _downloadQueue.value, force = true)
    }

    private fun updateTask(id: String, persist: Boolean = true, change: (DownloadTask) -> DownloadTask) {
        val updated = _downloadQueue.value.map { if (it.id == id) change(it) else it }
        if (persist) {
            setQueue(updated)
        } else {
            _downloadQueue.value = updated
            DownloadQueueNotifier.update(context, updated)
        }
    }
    private fun setQueue(items: List<DownloadTask>) {
        _downloadQueue.value = items
        val json = JSONArray()
        items.forEach { task ->
            json.put(JSONObject().apply {
                put("id", task.id); put("url", task.url); put("videoId", task.videoId)
                put("title", task.title); put("thumbnailUrl", task.thumbnailUrl)
                put("mode", task.mode.name); put("quality", task.quality); put("outputFormat", task.outputFormat)
                put("progress", task.progress.toDouble()); put("downloadedBytes", task.downloadedBytes)
                put("totalBytes", task.totalBytes); put("status", task.status.name)
                put("retryCount", task.retryCount)
                put("createdAt", task.createdAt); put("errorMessage", task.errorMessage)
            })
        }
        prefs.edit().putString("queue_json", json.toString()).apply()
        DownloadQueueNotifier.update(context, items, force = true)
    }
    private fun loadQueue(): List<DownloadTask> = runCatching {
        val data = JSONArray(prefs.getString("queue_json", "[]"))
        List(data.length()) { index ->
            val item = data.getJSONObject(index)
            val status = enumValue(item.optString("status"), TaskStatus.QUEUED)
            val persistedError = item.optString("errorMessage")
                .takeIf { it.isNotBlank() && it != "null" }
            val systemTimedOut =
                status == TaskStatus.PAUSED && persistedError == SYSTEM_TIMEOUT_MESSAGE
            DownloadTask(
                id = item.getString("id"), url = item.getString("url"), videoId = item.getString("videoId"),
                title = item.getString("title"), thumbnailUrl = item.optString("thumbnailUrl"),
                mode = enumValue(item.optString("mode"), DownloadMode.VIDEO),
                quality = item.optString("quality", "Best"), outputFormat = item.optString("outputFormat"),
                progress = item.optDouble("progress", 0.0).toFloat(),
                downloadedBytes = item.optLong("downloadedBytes"), totalBytes = item.optLong("totalBytes"),
                status = when {
                    systemTimedOut -> TaskStatus.QUEUED
                    status == TaskStatus.DOWNLOADING -> TaskStatus.QUEUED
                    status == TaskStatus.FAILED -> {
                        if (
                            persistedError == null ||
                            DownloadFailureClassifier.isRecoverable(IllegalStateException(persistedError))
                        ) TaskStatus.QUEUED else TaskStatus.FAILED
                    }
                    else -> status
                },
                errorMessage = if (systemTimedOut) null else persistedError,
                retryCount = item.optInt("retryCount", 0),
                createdAt = item.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }.getOrDefault(emptyList())

    fun copyFilesToClipboard(ids: Set<String>, move: Boolean = false) {
        _fileClipboard.value = FileClipboard(ids.intersect(_offlineMediaList.value.mapTo(mutableSetOf()) { it.id }), move)
    }

    suspend fun transferOfflineFiles(ids: Set<String>, destination: Uri, move: Boolean): FileOperationResult = withContext(Dispatchers.IO) {
        val sources = synchronized(lock) { _offlineMediaList.value.filter { it.id in ids } }
        var succeeded = 0
        var failed = ids.size - sources.size
        var detail = ""
        for (source in sources) {
            ensureActive()
            try {
                val transferred = DownloadedFileManager.transfer(context, source, destination, move)
                synchronized(lock) {
                    val retained = if (move) _offlineMediaList.value.filterNot { it.id == source.id } else _offlineMediaList.value
                    _offlineMediaList.value = retained + transferred
                    saveOfflineMedia(_offlineMediaList.value)
                }
                succeeded++
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                failed++
                detail = error.message ?: "File unavailable"
            }
        }
        if (move && failed == 0 && _fileClipboard.value?.ids == ids) _fileClipboard.value = null
        FileOperationResult(succeeded, failed, detail)
    }

    suspend fun deleteOfflineFiles(ids: Set<String>): FileOperationResult = withContext(Dispatchers.IO) {
        var succeeded = 0
        ids.forEach { id ->
            val source = synchronized(lock) { _offlineMediaList.value.firstOrNull { it.id == id } }
            if (source != null && runCatching { MediaDownloader.deleteOutput(context, source) }.getOrDefault(false)) {
                synchronized(lock) {
                    _offlineMediaList.value = _offlineMediaList.value.filterNot { it.id == id }
                    saveOfflineMedia(_offlineMediaList.value)
                }
                succeeded++
            }
        }
        FileOperationResult(succeeded, ids.size - succeeded, "")
    }

    fun deleteOfflineMedia(id: String) {
        synchronized(lock) {
            val media = _offlineMediaList.value.firstOrNull { it.id == id } ?: return
            if (!MediaDownloader.deleteOutput(context, media)) return
            _offlineMediaList.value = _offlineMediaList.value.filterNot { it.id == id }
            saveOfflineMedia(_offlineMediaList.value)
        }
    }
    private fun saveOfflineMedia(items: List<OfflineMedia>) {
        val array = JSONArray()
        items.forEach { media ->
            array.put(JSONObject().apply {
                put("id", media.id); put("title", media.title); put("videoId", media.videoId)
                put("thumbnailUrl", media.thumbnailUrl); put("mediaUri", media.mediaUri)
                put("isAudioOnly", media.isAudioOnly); put("qualityLabel", media.qualityLabel)
                put("format", media.format); put("fileSizeBytes", media.fileSizeBytes)
                put("durationSeconds", media.durationSeconds); put("downloadDate", media.downloadDate)
            })
        }
        prefs.edit().putString("offline_media_json", array.toString()).apply()
    }
    private fun loadOfflineMedia(): List<OfflineMedia> = runCatching {
        val array = JSONArray(prefs.getString("offline_media_json", "[]"))
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            OfflineMedia(
                item.getString("id"), item.getString("title"), item.getString("videoId"),
                item.optString("thumbnailUrl"), item.getString("mediaUri"),
                item.optBoolean("isAudioOnly"), item.optString("qualityLabel"),
                item.optString("format"), item.optLong("fileSizeBytes"),
                item.optLong("durationSeconds"), item.optLong("downloadDate")
            )
        }.filter { media ->
            val uri = Uri.parse(media.mediaUri)
            when (uri.scheme) {
                "file" -> uri.path?.let { File(it).isFile } == true
                "content" -> runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { true } == true }.getOrDefault(false)
                else -> false
            }
        }
    }.getOrDefault(emptyList())

    fun clearHistory() {
        _downloadHistory.value = emptyList()
        prefs.edit().remove("history_json").apply()
    }
    private fun saveHistory(items: List<DownloadTask>) {
        val array = JSONArray()
        items.forEach { task ->
            array.put(JSONObject().apply {
                put("id", task.id); put("url", task.url); put("videoId", task.videoId)
                put("title", task.title); put("thumbnailUrl", task.thumbnailUrl)
                put("mode", task.mode.name); put("quality", task.quality)
                put("outputFormat", task.outputFormat); put("totalBytes", task.totalBytes)
                put("status", task.status.name); put("createdAt", task.createdAt)
            })
        }
        prefs.edit().putString("history_json", array.toString()).apply()
    }
    private fun loadHistory(): List<DownloadTask> = runCatching {
        val array = JSONArray(prefs.getString("history_json", "[]"))
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            DownloadTask(
                id = item.getString("id"), url = item.getString("url"),
                videoId = item.getString("videoId"), title = item.getString("title"),
                thumbnailUrl = item.optString("thumbnailUrl"),
                mode = enumValue(item.optString("mode"), DownloadMode.VIDEO),
                quality = item.optString("quality"), outputFormat = item.optString("outputFormat"),
                totalBytes = item.optLong("totalBytes"), status = TaskStatus.COMPLETED,
                createdAt = item.optLong("createdAt")
            )
        }
    }.getOrDefault(emptyList())

    private fun saveHomeVideos(key: String, items: List<HomeVideo>) {
        val array = JSONArray()
        items.forEach { video ->
            array.put(JSONObject().apply {
                put("videoId", video.videoId)
                put("title", video.title)
                put("url", video.url)
                put("thumbnailUrl", video.thumbnailUrl)
                put("channelTitle", video.channelTitle)
                put("durationText", video.durationText)
                put("watchedAt", video.watchedAt)
            })
        }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private fun loadWatchHistory(): List<HomeVideo> = loadHomeVideos("watch_history_json")

    private fun loadHomeSuggestions(): List<HomeVideo> = loadHomeVideos("home_suggestions_json")

    private fun loadHomeVideos(key: String): List<HomeVideo> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            HomeVideo(
                videoId = item.optString("videoId"),
                title = item.optString("title", "YouTube video"),
                url = item.optString("url"),
                thumbnailUrl = item.optString("thumbnailUrl"),
                channelTitle = item.optString("channelTitle", "YouTube"),
                durationText = item.optString("durationText"),
                watchedAt = item.optLong("watchedAt")
            )
        }.filter { it.videoId.isNotBlank() && YoutubeUrls.videoId(it.url) != null }
    }.getOrDefault(emptyList())

    private fun formatDurationLabel(seconds: Long): String {
        if (seconds <= 0) return ""
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
        else "$minutes:${secs.toString().padStart(2, '0')}"
    }
}


data class FileClipboard(val ids: Set<String>, val move: Boolean)
data class FileOperationResult(val succeeded: Int, val failed: Int, val detail: String)
