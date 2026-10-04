package com.drivedeck.tubedeck.player

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Resolves a YouTube webpage URL into real media stream URLs for Media3.
 *
 * The extractor is deliberately isolated from the Compose player. If YouTube changes its
 * extraction protocol, this class is the single integration point that needs updating.
 */
object YoutubeStreamResolver {
    private val initialized = AtomicBoolean(false)
    private val resolutionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val resolutionLock = Any()
    private val resolutionCache = LinkedHashMap<String, CachedResolution>(16, 0.75f, true)
    private val resolutionInFlight = mutableMapOf<String, Deferred<ResolvedMedia>>()

    private data class CachedResolution(
        val media: ResolvedMedia,
        val cachedAtMs: Long
    )

    private const val RESOLUTION_CACHE_TTL_MS = 5 * 60 * 1000L
    private const val MAX_RESOLUTION_CACHE_ENTRIES = 12

    fun prewarm() {
        resolutionScope.launch {
            ensureInitialized()
        }
    }

    @Synchronized
    private fun ensureInitialized() {
        if (!initialized.get()) {
            NewPipe.init(object : Downloader() {
                override fun execute(request: Request): Response {
                    val connection = (URL(request.url()).openConnection() as HttpURLConnection).apply {
                        requestMethod = request.httpMethod()
                        instanceFollowRedirects = true
                        connectTimeout = 20_000
                        readTimeout = 30_000
                        useCaches = false
                        setRequestProperty("User-Agent", USER_AGENT)
                        setRequestProperty("Accept", "*/*")
                        setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                        request.headers().forEach { (name, values) ->
                            values.forEach { setRequestProperty(name, it) }
                        }
                    }

                    request.dataToSend()?.let { body ->
                        connection.doOutput = true
                        connection.outputStream.use { it.write(body) }
                    }

                    val code = connection.responseCode
                    if (code == 429) {
                        connection.disconnect()
                        throw ReCaptchaException("HTTP 429", request.url())
                    }

                    val stream = if (code >= 400) connection.errorStream else connection.inputStream
                    val body = if (stream != null && request.httpMethod() != "HEAD") {
                        BufferedInputStream(stream).use { input ->
                            ByteArrayOutputStream().use { output ->
                                input.copyTo(output)
                                output.toString(StandardCharsets.UTF_8.name())
                            }
                        }
                    } else {
                        ""
                    }

                    return try {
                        Response(
                            code,
                            connection.responseMessage ?: "",
                            connection.headerFields.filterKeys { it != null },
                            body,
                            connection.url?.toString() ?: request.url()
                        )
                    } finally {
                        connection.disconnect()
                    }
                }
            })
            initialized.set(true)
        }
    }

    data class Subtitle(
        val url: String,
        val languageTag: String,
        val displayName: String,
        val mimeType: String,
        val autoGenerated: Boolean
    )

    data class VideoOption(
        val label: String,
        val height: Int,
        val videoUrl: String,
        val audioUrl: String?,
        val videoMimeType: String?,
        val audioMimeType: String?
    )

    data class AudioOption(
        val url: String,
        val mimeType: String,
        val bitrate: Int
    )

    data class RelatedVideo(
        val videoId: String,
        val title: String,
        val url: String,
        val thumbnailUrl: String,
        val channelTitle: String,
        val durationSeconds: Long
    )

    data class DiscoveredEpisode(
        val videoId: String,
        val title: String,
        val uploader: String,
        val durationSeconds: Long,
        val thumbnailUrl: String,
        val episodeNumber: Int?
    )

    data class DiscoveredPlaylist(
        val title: String,
        val url: String,
        val uploader: String,
        val itemCount: Long,
        val thumbnailUrl: String,
        val kind: String,
        val episodes: List<DiscoveredEpisode> = emptyList()
    )

    data class Chapter(
        val title: String,
        val startTimeSeconds: Int,
        val previewUrl: String?
    )

    data class PreviewStoryboard(
        val urls: List<String>,
        val frameWidth: Int,
        val frameHeight: Int,
        val totalCount: Int,
        val durationPerFrameMs: Int,
        val framesPerPageX: Int,
        val framesPerPageY: Int
    )

    data class NativeComment(
        val id: String,
        val author: String,
        val text: String,
        val publishedText: String,
        val likeText: String,
        val replyCount: Int,
        val pinned: Boolean,
        val channelOwner: Boolean
    )

    data class ResolvedMedia(
        val title: String,
        val description: String,
        val descriptionIsHtml: Boolean,
        val uploaderName: String,
        val viewCount: Long,
        val uploadDateText: String,
        val durationSeconds: Long,
        val progressiveUrl: String?,
        val progressiveMimeType: String?,
        val progressiveOptions: List<VideoOption>,
        val downloadOptions: List<VideoOption>,
        val audioOptions: List<AudioOption>,
        val options: List<VideoOption>,
        val subtitles: List<Subtitle>,
        val chapters: List<Chapter> = emptyList(),
        val previewStoryboard: PreviewStoryboard? = null,
        val relatedVideos: List<RelatedVideo> = emptyList(),
        val isLive: Boolean = false,
        val liveManifestUrl: String? = null
    )

    suspend fun resolve(
        context: Context,
        url: String,
        forceRefresh: Boolean = false
    ): ResolvedMedia {
        require(YoutubeUrls.videoId(url) != null) { "Enter a valid YouTube video URL" }
        val key = resolutionKey(url)
        val now = android.os.SystemClock.elapsedRealtime()
        PlaybackStartupTelemetry.markResolveStart(url)

        if (!forceRefresh) {
            synchronized(resolutionLock) {
                val cached = resolutionCache[key]
                if (cached != null && now - cached.cachedAtMs <= RESOLUTION_CACHE_TTL_MS) {
                    PlaybackStartupTelemetry.markResolveDone(url, cacheHit = true)
                    return cached.media
                }
                if (cached != null) resolutionCache.remove(key)
            }
        }

        val deferred = synchronized(resolutionLock) {
            if (!forceRefresh) {
                resolutionInFlight[key]?.let { return@synchronized it }
            }
            resolutionScope.async {
                resolvePlaybackWithFallback(context.applicationContext, url)
            }.also { resolutionInFlight[key] = it }
        }

        return try {
            val media = deferred.await()
            synchronized(resolutionLock) {
                resolutionCache[key] = CachedResolution(
                    media = media,
                    cachedAtMs = android.os.SystemClock.elapsedRealtime()
                )
                while (resolutionCache.size > MAX_RESOLUTION_CACHE_ENTRIES) {
                    val eldest = resolutionCache.entries.iterator()
                    if (eldest.hasNext()) {
                        eldest.next()
                        eldest.remove()
                    }
                }
            }
            PlaybackStartupTelemetry.markResolveDone(url, cacheHit = false)
            media
        } catch (error: Throwable) {
            PlaybackStartupTelemetry.markResolveFailed(url)
            throw error
        } finally {
            synchronized(resolutionLock) {
                if (resolutionInFlight[key] === deferred) {
                    resolutionInFlight.remove(key)
                }
            }
        }
    }

    fun prefetch(context: Context, url: String) {
        if (YoutubeUrls.videoId(url) == null) return
        resolutionScope.launch {
            runCatching { resolve(context.applicationContext, url) }
        }
    }

    /**
     * Audio/video mode changes should reuse the stream metadata that is already driving the
     * foreground player. A mode switch is not a reason to hit YouTube again.
     *
     * Handoffs are allowed to reuse a somewhat older cache entry because the currently playing
     * direct URL is strong evidence that it is still valid. If it has expired, Media3's normal
     * error path refreshes the extractor once in the background.
     */
    fun peekForHandoff(url: String, maxAgeMs: Long = 30 * 60 * 1000L): ResolvedMedia? {
        if (YoutubeUrls.videoId(url) == null) return null
        val key = resolutionKey(url)
        val now = android.os.SystemClock.elapsedRealtime()
        return synchronized(resolutionLock) {
            resolutionCache[key]
                ?.takeIf { now - it.cachedAtMs <= maxAgeMs.coerceAtLeast(RESOLUTION_CACHE_TTL_MS) }
                ?.media
        }
    }

    fun preferredAudioPlaybackUrl(media: ResolvedMedia): String? =
        media.audioOptions.maxByOrNull { it.bitrate }?.url
            ?: media.liveManifestUrl?.takeIf { media.isLive && it.startsWith("https://") }
            ?: media.progressiveUrl
            ?: media.options.firstOrNull()?.videoUrl

    /**
     * Returning from audio-only is a continuation, not a fresh open. Promote a recent handoff
     * entry back into the normal five-minute cache so the video can recreate Media3 immediately.
     * Any genuinely expired CDN URL is still covered by the player's forced-refresh recovery.
     */
    fun promoteForHandoffResume(url: String, maxAgeMs: Long = 30 * 60 * 1000L): Boolean {
        if (YoutubeUrls.videoId(url) == null) return false
        val key = resolutionKey(url)
        val now = android.os.SystemClock.elapsedRealtime()
        return synchronized(resolutionLock) {
            val cached = resolutionCache[key] ?: return@synchronized false
            if (now - cached.cachedAtMs > maxAgeMs) {
                false
            } else {
                resolutionCache[key] = cached.copy(cachedAtMs = now)
                true
            }
        }
    }

    fun invalidate(url: String) {
        val key = resolutionKey(url)
        synchronized(resolutionLock) {
            resolutionCache.remove(key)
        }
    }

    private fun resolutionKey(url: String): String =
        YoutubeUrls.videoId(url) ?: url

    /**
     * Offline downloads deliberately prefer the bundled Download Engine. Playback keeps the
     * faster NewPipe path, while downloads get yt-dlp's frequently updated YouTube extraction.
     * If the installed engine has become stale, update it once and retry before falling back.
     */
    suspend fun resolveForDownload(context: Context, url: String): ResolvedMedia =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            require(YoutubeUrls.videoId(url) != null) { "Enter a valid YouTube video URL" }
            val app = context.applicationContext

            val firstAttempt = runCatching { resolveWithDownloadEngine(app, url) }
            firstAttempt.getOrNull()?.let { return@withContext it }

            // A YouTube extractor change commonly presents as a sudden download failure.
            // Refresh the stable yt-dlp payload once, then retry with the new engine.
            YtDlpUpdateManager.updateNow(app)

            val secondAttempt = runCatching { resolveWithDownloadEngine(app, url) }
            secondAttempt.getOrNull()?.let { return@withContext it }

            // Keep NewPipe as a genuine independent fallback rather than a single point of failure.
            runCatching { resolve(app, url, forceRefresh = true) }.getOrElse { extractorError ->
                val engineError = secondAttempt.exceptionOrNull() ?: firstAttempt.exceptionOrNull()
                throw IOException(
                    listOfNotNull(
                        "Could not resolve downloadable media",
                        engineError?.message?.takeIf { it.isNotBlank() }?.let { "Download Engine: " + it },
                        extractorError.message?.takeIf { it.isNotBlank() }?.let { "Extractor: " + it }
                    ).joinToString(" • "),
                    engineError ?: extractorError
                )
            }
        }

    private suspend fun resolveWithDownloadEngine(context: Context, url: String): ResolvedMedia {
        YtDlpUpdateManager.ensureInitialized(context)

        val request = YoutubeDLRequest(url)
            .addOption("--dump-single-json")
            .addOption("--no-playlist")
            .addOption("--no-warnings")

        val response = YoutubeDL.execute(request)
        val output = response.out.trim()
        if (output.isBlank()) throw IOException("Download Engine returned no media data")

        val root = JSONObject(output)
        val formats = root.optJSONArray("formats")
            ?: throw IOException("Download Engine returned no formats")

        val progressive = mutableListOf<VideoOption>()
        val videoOnly = mutableListOf<VideoOption>()
        val audioOptions = mutableListOf<AudioOption>()
        val hlsLiveCandidates = mutableListOf<Pair<Int, String>>()

        for (index in 0 until formats.length()) {
            currentCoroutineContext().ensureActive()
            val item = formats.optJSONObject(index) ?: continue
            val mediaUrl = item.optString("url").trim()
            if (!mediaUrl.startsWith("https://")) continue

            val vcodec = item.optString("vcodec", "none")
            val acodec = item.optString("acodec", "none")
            val hasVideo = vcodec.isNotBlank() && !vcodec.equals("none", ignoreCase = true)
            val hasAudio = acodec.isNotBlank() && !acodec.equals("none", ignoreCase = true)
            val ext = item.optString("ext").lowercase(Locale.US)
            val protocol = item.optString("protocol").lowercase(Locale.US)

            if (hasVideo && hasAudio && protocol.contains("m3u8")) {
                hlsLiveCandidates += item.optInt("height", 0).coerceAtLeast(0) to mediaUrl
            }

            if (hasVideo) {
                val height = item.optInt("height", 0)
                if (height <= 0) continue
                val videoMime = when (ext) {
                    "mp4", "m4v" -> "video/mp4"
                    "webm" -> "video/webm"
                    else -> null
                } ?: continue
                val option = VideoOption(
                    label = "${height}p",
                    height = height,
                    videoUrl = mediaUrl,
                    audioUrl = null,
                    videoMimeType = videoMime,
                    audioMimeType = null
                )
                if (hasAudio) progressive += option else videoOnly += option
            } else if (hasAudio) {
                val audioMime = when (ext) {
                    "m4a", "mp4" -> "audio/mp4"
                    "webm" -> "audio/webm"
                    else -> null
                } ?: continue
                val bitrate = item.optDouble(
                    "abr",
                    item.optDouble("tbr", 0.0)
                ).toInt().coerceAtLeast(0)
                audioOptions += AudioOption(
                    url = mediaUrl,
                    mimeType = audioMime,
                    bitrate = bitrate
                )
            }
        }

        val distinctAudio = audioOptions
            .distinctBy { it.url }
            .sortedByDescending { it.bitrate }

        val adaptive = videoOnly.map { video ->
            val preferredAudioMime = when (video.videoMimeType) {
                "video/mp4" -> "audio/mp4"
                "video/webm" -> "audio/webm"
                else -> null
            }
            val audio = distinctAudio.firstOrNull { it.mimeType == preferredAudioMime }
                ?: distinctAudio.firstOrNull()
            video.copy(
                audioUrl = audio?.url,
                audioMimeType = audio?.mimeType
            )
        }

        val allVideoOptions = (progressive + adaptive)
            .groupBy { it.height }
            .mapNotNull { (_, candidates) ->
                candidates.firstOrNull { it.audioUrl == null }
                    ?: candidates.firstOrNull { it.audioUrl != null }
            }
            .sortedByDescending { it.height }

        val downloadOptions = (progressive + adaptive.filter { it.audioUrl != null })
            .groupBy { it.height }
            .mapNotNull { (_, candidates) ->
                candidates.minWithOrNull(
                    compareBy<VideoOption> { if (it.audioUrl == null) 0 else 1 }
                        .thenBy { if (it.videoMimeType == "video/mp4") 0 else 1 }
                )
            }
            .sortedByDescending { it.height }

        if (downloadOptions.isEmpty() && distinctAudio.isEmpty()) {
            throw IOException("Download Engine found no downloadable streams")
        }

        val chapters = buildList {
            val values = root.optJSONArray("chapters") ?: return@buildList
            for (index in 0 until values.length()) {
                val item = values.optJSONObject(index) ?: continue
                val title = item.optString("title").trim()
                val start = item.optDouble("start_time", -1.0).toInt()
                if (title.isNotBlank() && start >= 0) {
                    add(Chapter(title, start, null))
                }
            }
        }

        val bestProgressive = progressive.maxByOrNull { it.height }
        val engineIsLive =
            root.optBoolean("is_live", false) ||
                root.optString("live_status").equals("is_live", ignoreCase = true)
        val engineLiveManifest = if (engineIsLive) {
            hlsLiveCandidates.maxByOrNull { it.first }?.second
                ?: root.optString("url").takeIf { url ->
                    url.startsWith("https://") &&
                        root.optString("protocol").contains("m3u8", ignoreCase = true)
                }
        } else {
            null
        }
        return ResolvedMedia(
            title = root.optString("title").ifBlank { "YouTube video" },
            description = root.optString("description"),
            descriptionIsHtml = false,
            uploaderName = root.optString("uploader")
                .ifBlank { root.optString("channel") }
                .ifBlank { "YouTube" },
            viewCount = root.optLong("view_count", 0L),
            uploadDateText = root.optString("upload_date"),
            durationSeconds = root.optDouble("duration", 0.0).toLong().coerceAtLeast(0L),
            progressiveUrl = bestProgressive?.videoUrl,
            progressiveMimeType = bestProgressive?.videoMimeType,
            progressiveOptions = progressive
                .filter { it.videoMimeType == "video/mp4" }
                .distinctBy { it.height }
                .sortedByDescending { it.height },
            downloadOptions = downloadOptions,
            audioOptions = distinctAudio,
            options = allVideoOptions,
            subtitles = emptyList(),
            chapters = chapters,
            previewStoryboard = null,
            relatedVideos = emptyList(),
            isLive = engineIsLive,
            liveManifestUrl = engineLiveManifest
        )
    }

    private suspend fun resolvePlaybackWithFallback(context: Context, url: String): ResolvedMedia {
        val canonicalUrl = YoutubeUrls.canonicalVideoUrl(url) ?: url
        val extractorAttempt = runCatching { resolveUncached(canonicalUrl) }
        extractorAttempt.getOrNull()?.let { return it }

        val engineAttempt = runCatching { resolveWithDownloadEngine(context, canonicalUrl) }
        engineAttempt.getOrNull()?.let { return it }

        runCatching { YtDlpUpdateManager.updateNow(context) }
        val refreshedEngineAttempt = runCatching {
            resolveWithDownloadEngine(context, canonicalUrl)
        }
        refreshedEngineAttempt.getOrNull()?.let { return it }

        val extractorError = extractorAttempt.exceptionOrNull()
        val engineError = refreshedEngineAttempt.exceptionOrNull() ?: engineAttempt.exceptionOrNull()
        throw IOException(
            listOfNotNull(
                "Could not resolve video stream",
                extractorError?.message?.takeIf { it.isNotBlank() }?.let { "Extractor: $it" },
                engineError?.message?.takeIf { it.isNotBlank() }?.let { "Download Engine: $it" }
            ).joinToString(" • "),
            engineError ?: extractorError
        )
    }

    private suspend fun resolveUncached(url: String): ResolvedMedia =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            ensureInitialized()
            require(YoutubeUrls.videoId(url) != null) { "Enter a valid YouTube video URL" }
            val canonicalUrl = YoutubeUrls.canonicalVideoUrl(url) ?: url
            val info = StreamInfo.getInfo(canonicalUrl)
            val isLive =
                info.streamType == StreamType.LIVE_STREAM ||
                    info.streamType == StreamType.AUDIO_LIVE_STREAM
            val liveManifestUrl = if (isLive) {
                info.hlsUrl.takeIf { it.startsWith("https://") }
            } else {
                null
            }

            val progressive = info.videoStreams
                .filter { it.isUrl() && it.getContent().startsWith("https://") }
                .mapNotNull { stream ->
                    val h = stream.getResolution().removeSuffix("p").toIntOrNull() ?: 0
                    if (h <= 0) null else VideoOption(
                        label = "${h}p",
                        height = h,
                        videoUrl = stream.getContent(),
                        audioUrl = null,
                        videoMimeType = stream.getFormat()?.mimeType,
                        audioMimeType = null
                    )
                }

            val audioOptions = info.audioStreams
                .filter { it.isUrl() && it.getContent().startsWith("https://") }
                .mapNotNull { stream ->
                    val mime = stream.getFormat()?.mimeType ?: return@mapNotNull null
                    if (mime != "audio/mp4" && mime != "audio/webm") return@mapNotNull null
                    AudioOption(stream.getContent(), mime, stream.getAverageBitrate())
                }.sortedByDescending { it.bitrate }

            val adaptive = info.videoOnlyStreams
                .filter { it.isUrl() && it.getContent().startsWith("https://") }
                .mapNotNull { stream ->
                    val h = stream.getResolution().removeSuffix("p").toIntOrNull() ?: 0
                    if (h <= 0) null else VideoOption(
                        label = "${h}p",
                        height = h,
                        videoUrl = stream.getContent(),
                        audioUrl = audioOptions.firstOrNull {
                            (stream.getFormat()?.mimeType == "video/mp4" && it.mimeType == "audio/mp4") ||
                                (stream.getFormat()?.mimeType == "video/webm" && it.mimeType == "audio/webm")
                        }?.url,
                        videoMimeType = stream.getFormat()?.mimeType,
                        audioMimeType = when (stream.getFormat()?.mimeType) {
                            "video/mp4" -> "audio/mp4"
                            "video/webm" -> "audio/webm"
                            else -> null
                        }
                    )
                }

            val options = (progressive + adaptive)
                .groupBy { it.height }
                .mapNotNull { (_, candidates) ->
                    candidates.firstOrNull { it.audioUrl == null } ?: candidates.firstOrNull { it.audioUrl != null }
                }
                .sortedByDescending { it.height }

            val downloadOptions = (progressive + adaptive.filter { it.audioUrl != null })
                .filter { it.videoMimeType == "video/mp4" || it.videoMimeType == "video/webm" }
                .groupBy { it.height }
                .mapNotNull { (_, candidates) ->
                    candidates.minWithOrNull(compareBy<VideoOption> { if (it.audioUrl == null) 0 else 1 }
                        .thenBy { if (it.videoMimeType == "video/mp4") 0 else 1 })
                }
                .sortedByDescending { it.height }

            val subtitles = info.subtitles
                .filter { it.isUrl() && it.getContent().startsWith("https://") }
                .mapNotNull { sub ->
                    val mime = when (sub.getExtension().lowercase()) {
                        "vtt" -> "text/vtt"
                        "ttml" -> "application/ttml+xml"
                        "srt" -> "application/x-subrip"
                        else -> return@mapNotNull null
                    }
                    Subtitle(
                        url = sub.getContent(),
                        languageTag = sub.getLanguageTag(),
                        displayName = sub.getDisplayLanguageName(),
                        mimeType = mime,
                        autoGenerated = sub.isAutoGenerated()
                    )
                }
                .distinctBy { it.languageTag + ":" + it.autoGenerated }

            val chapters = info.streamSegments
                .mapNotNull { segment ->
                    val start = segment.startTimeSeconds.coerceAtLeast(0)
                    val title = segment.title.orEmpty().trim()
                    if (title.isBlank()) null else Chapter(
                        title = title,
                        startTimeSeconds = start,
                        previewUrl = segment.previewUrl
                    )
                }
                .distinctBy { it.startTimeSeconds }
                .sortedBy { it.startTimeSeconds }

            val previewStoryboard = info.previewFrames
                .filter { frames ->
                    frames.urls.isNotEmpty() &&
                        frames.frameWidth > 0 &&
                        frames.frameHeight > 0 &&
                        frames.durationPerFrame > 0 &&
                        frames.framesPerPageX > 0 &&
                        frames.framesPerPageY > 0
                }
                .maxByOrNull { frames -> frames.frameWidth * frames.frameHeight }
                ?.let { frames ->
                    PreviewStoryboard(
                        urls = frames.urls,
                        frameWidth = frames.frameWidth,
                        frameHeight = frames.frameHeight,
                        totalCount = frames.totalCount,
                        durationPerFrameMs = frames.durationPerFrame,
                        framesPerPageX = frames.framesPerPageX,
                        framesPerPageY = frames.framesPerPageY
                    )
                }

            val relatedVideos = info.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { item ->
                    val id = YoutubeUrls.videoId(item.url) ?: return@mapNotNull null
                    RelatedVideo(
                        videoId = id,
                        title = item.name,
                        url = item.url,
                        thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                        channelTitle = item.uploaderName.orEmpty().ifBlank { "YouTube" },
                        durationSeconds = item.duration.coerceAtLeast(0L)
                    )
                }
                .distinctBy { it.videoId }
                .take(30)

            ResolvedMedia(
                title = info.name,
                description = runCatching { info.description.content }.getOrDefault(""),
                descriptionIsHtml = runCatching { info.description.type == org.schabi.newpipe.extractor.stream.Description.HTML }.getOrDefault(false),
                uploaderName = info.uploaderName.orEmpty().ifBlank { "YouTube" },
                viewCount = info.viewCount,
                uploadDateText = info.textualUploadDate.orEmpty(),
                durationSeconds = info.duration,
                progressiveUrl = progressive.maxByOrNull { it.height }?.videoUrl,
                progressiveMimeType = progressive.maxByOrNull { it.height }?.videoMimeType,
                progressiveOptions = progressive.filter { it.videoMimeType == "video/mp4" }.sortedByDescending { it.height },
                downloadOptions = downloadOptions,
                audioOptions = audioOptions,
                options = options,
                subtitles = subtitles,
                chapters = chapters,
                previewStoryboard = previewStoryboard,
                relatedVideos = relatedVideos,
                isLive = isLive,
                liveManifestUrl = liveManifestUrl
            )
        }

    suspend fun comments(context: Context, url: String): List<NativeComment> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            ensureInitialized()
            require(YoutubeUrls.videoId(url) != null) { "Enter a valid YouTube video URL" }

            val collected = mutableListOf<NativeComment>()
            var extractorFailure: Throwable? = null
            var commentsDisabledByVideo = false

            runCatching {
                val info = CommentsInfo.getInfo(url)
                if (info != null && info.isCommentsDisabled) {
                    commentsDisabledByVideo = true
                }
                if (info != null && !info.isCommentsDisabled) {
                    fun append(items: List<org.schabi.newpipe.extractor.comments.CommentsInfoItem>) {
                        items.forEach { item ->
                            val text = item.commentText?.content.orEmpty().trim()
                            if (text.isBlank()) return@forEach
                            collected += NativeComment(
                                id = item.commentId.orEmpty()
                                    .ifBlank { item.url.orEmpty() + ":" + item.uploaderName.orEmpty() + ":" + collected.size },
                                author = item.uploaderName.orEmpty().ifBlank { "YouTube user" },
                                text = text,
                                publishedText = item.textualUploadDate.orEmpty(),
                                likeText = item.textualLikeCount.orEmpty(),
                                replyCount = item.replyCount.coerceAtLeast(0),
                                pinned = item.isPinned,
                                channelOwner = item.isChannelOwner
                            )
                        }
                    }

                    append(info.relatedItems)
                    var next = info.nextPage
                    var pages = 0
                    while (collected.size < 60 && next != null && pages < 4) {
                        currentCoroutineContext().ensureActive()
                        val page = CommentsInfo.getMoreItems(info, next)
                        append(page.items)
                        next = page.nextPage
                        pages += 1
                    }
                }
            }.onFailure { extractorFailure = it }

            if (collected.isNotEmpty()) {
                return@withContext collected.distinctBy { it.id }.take(60)
            }
            if (commentsDisabledByVideo) return@withContext emptyList()

            val fallback = runCatching { ytDlpComments(context.applicationContext, url) }
                .getOrElse { ytError ->
                    val cause = extractorFailure?.message?.takeIf { it.isNotBlank() }
                    val fallbackCause = ytError.message?.takeIf { it.isNotBlank() }
                    throw IOException(
                        listOfNotNull(
                            "Could not load YouTube comments",
                            cause?.let { "Extractor: " + it },
                            fallbackCause?.let { "Download Engine: " + it }
                        ).joinToString(" • "),
                        ytError
                    )
                }

            fallback.distinctBy { it.id }.take(60)
        }

    private suspend fun ytDlpComments(context: Context, url: String): List<NativeComment> {
        YtDlpUpdateManager.ensureInitialized(context)
        val request = YoutubeDLRequest(url)
            .addOption("--skip-download")
            .addOption("--write-comments")
            .addOption("--dump-single-json")
            .addOption("--no-warnings")

        val response = YoutubeDL.execute(request)
        val root = JSONObject(response.out)
        val comments = root.optJSONArray("comments") ?: throw IOException("Download Engine returned no comments data")
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
            .withZone(ZoneId.systemDefault())

        return buildList {
            for (index in 0 until comments.length()) {
                val item = comments.optJSONObject(index) ?: continue
                val text = item.optString("text").trim()
                if (text.isBlank()) continue

                val timestamp = item.optLong("timestamp", 0L)
                val published = if (timestamp > 0L) {
                    runCatching { formatter.format(Instant.ofEpochSecond(timestamp)) }.getOrDefault("")
                } else {
                    item.optString("time_text")
                }

                val likes = item.optLong("like_count", -1L)
                add(
                    NativeComment(
                        id = item.optString("id").ifBlank { "yt-dlp-" + index + "-" + text.hashCode() },
                        author = item.optString("author").ifBlank { "YouTube user" },
                        text = text,
                        publishedText = published,
                        likeText = if (likes > 0L) likes.toString() else "",
                        replyCount = 0,
                        pinned = item.optBoolean("is_pinned", false),
                        channelOwner = item.optBoolean("author_is_uploader", false)
                    )
                )
                if (size >= 60) break
            }
        }
    }

    suspend fun discoverPlaylists(
        url: String,
        titleHint: String
    ): List<DiscoveredPlaylist> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            ensureInitialized()
            val currentVideoId = YoutubeUrls.videoId(url)
                ?: throw IllegalArgumentException("Enter a valid YouTube video URL")

            val currentInfo = StreamInfo.getInfo(url)
            val sourceTitle = currentInfo.name.takeIf { it.isNotBlank() } ?: titleHint
            val sourceUploader = currentInfo.uploaderName.orEmpty()
            val seriesName = seriesSearchQuery(sourceTitle)
            val seriesTokens = relevanceTokens(seriesName)

            val watchNext = runCatching {
                YoutubeWatchNextClient.resolve(url, currentVideoId)
            }.getOrNull()

            val candidates = mutableListOf<DiscoveredPlaylist>()

            // Priority 1: YouTube's own Watch Next playlist context. This is the same class of
            // server-side context used by YouTube/SmartTube and is stronger than search ranking.
            watchNext?.playlists
                ?.distinctBy { it.playlistId }
                ?.forEach { playlist ->
                    val embedded = playlist.items.map { item ->
                        DiscoveredEpisode(
                            videoId = item.videoId,
                            title = item.title,
                            uploader = item.uploader.ifBlank { playlist.owner.ifBlank { sourceUploader.ifBlank { "YouTube" } } },
                            durationSeconds = item.durationSeconds,
                            thumbnailUrl = item.thumbnailUrl.ifBlank {
                                "https://i.ytimg.com/vi/${item.videoId}/hqdefault.jpg"
                            },
                            episodeNumber = episodeNumber(item.title)
                        )
                    }

                    candidates += DiscoveredPlaylist(
                        title = playlist.title.ifBlank { "Current YouTube playlist" },
                        url = "https://www.youtube.com/playlist?list=${playlist.playlistId}",
                        uploader = playlist.owner.ifBlank { sourceUploader.ifBlank { "YouTube" } },
                        itemCount = (playlist.totalVideos ?: embedded.size).toLong(),
                        thumbnailUrl = embedded.firstOrNull { it.videoId == currentVideoId }?.thumbnailUrl
                            ?: embedded.firstOrNull()?.thumbnailUrl
                            ?: currentInfo.thumbnails.firstOrNull()?.url
                            ?: "https://i.ytimg.com/vi/$currentVideoId/hqdefault.jpg",
                        kind = "YouTube Watch Next playlist",
                        episodes = embedded
                    )
                }

            if (seriesTokens.isEmpty()) {
                return@withContext candidates.distinctBy { it.url }.take(6)
            }

            data class ScoredVideo(
                val videoId: String,
                val title: String,
                val uploader: String,
                val durationSeconds: Long,
                val thumbnailUrl: String,
                val score: Double,
                val episode: Int?
            )

            fun channelMatches(candidate: String): Boolean {
                if (sourceUploader.isBlank() || candidate.isBlank()) return false
                val left = normalizeForMatch(sourceUploader)
                val right = normalizeForMatch(candidate)
                return left == right || left.contains(right) || right.contains(left)
            }

            fun scoreFields(
                videoId: String,
                title: String,
                uploader: String,
                durationSeconds: Long,
                thumbnailUrl: String,
                serverContextBonus: Double
            ): ScoredVideo? {
                if (videoId == currentVideoId || title.isBlank()) return null
                val coverage = tokenCoverage(seriesTokens, relevanceTokens(title))
                val channelMatch = channelMatches(uploader)
                val episode = episodeNumber(title)

                // Same-channel Watch Next entries are highly trusted. Cross-channel entries still
                // need near-exact series-title overlap and an episode marker.
                val accepted = when {
                    channelMatch && coverage >= 0.45 -> true
                    coverage >= 0.90 && episode != null -> true
                    else -> false
                }
                if (!accepted) return null

                val score =
                    coverage * 100.0 +
                    (if (channelMatch) 60.0 else 0.0) +
                    (if (episode != null) 20.0 else 0.0) +
                    (if (containsEpisodeWord(title)) 8.0 else 0.0) +
                    serverContextBonus

                return ScoredVideo(
                    videoId = videoId,
                    title = title,
                    uploader = uploader,
                    durationSeconds = durationSeconds,
                    thumbnailUrl = thumbnailUrl.ifBlank {
                        "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                    },
                    score = score,
                    episode = episode
                )
            }

            val scored = mutableMapOf<String, ScoredVideo>()

            fun absorb(candidate: ScoredVideo?) {
                candidate ?: return
                val old = scored[candidate.videoId]
                if (old == null || candidate.score > old.score) {
                    scored[candidate.videoId] = candidate
                }
            }

            // Priority 2: YouTube's own Watch Next recommendations. These are contextual to the
            // exact current video and therefore receive a score bonus over generic search results.
            watchNext?.relatedVideos.orEmpty().forEach { item ->
                absorb(
                    scoreFields(
                        videoId = item.videoId,
                        title = item.title,
                        uploader = item.uploader,
                        durationSeconds = item.durationSeconds,
                        thumbnailUrl = item.thumbnailUrl,
                        serverContextBonus = 28.0
                    )
                )
            }

            // Priority 3: NewPipe's parsed related shelf from the same watch page.
            currentInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .forEach { item ->
                    val id = YoutubeUrls.videoId(item.url) ?: return@forEach
                    absorb(
                        scoreFields(
                            videoId = id,
                            title = item.name,
                            uploader = item.uploaderName.orEmpty(),
                            durationSeconds = item.duration.coerceAtLeast(0L),
                            thumbnailUrl = item.thumbnails.firstOrNull()?.url.orEmpty(),
                            serverContextBonus = 14.0
                        )
                    )
                }

            // Priority 4: same-series search only as a fallback/expansion source. Search never gets
            // enough score to override a strong Watch Next/same-channel result.
            runCatching {
                val service = NewPipe.getService("YouTube")
                val query = buildString {
                    append(seriesName)
                    if (sourceUploader.isNotBlank()) append(" ").append(sourceUploader)
                    append(" episode")
                }
                val handler = service.searchQHFactory.fromQuery(query, listOf("videos"), "")
                val info = SearchInfo.getInfo(service, handler)

                fun absorbSearch(items: List<org.schabi.newpipe.extractor.InfoItem>) {
                    items.filterIsInstance<StreamInfoItem>().forEach { item ->
                        val id = YoutubeUrls.videoId(item.url) ?: return@forEach
                        absorb(
                            scoreFields(
                                videoId = id,
                                title = item.name,
                                uploader = item.uploaderName.orEmpty(),
                                durationSeconds = item.duration.coerceAtLeast(0L),
                                thumbnailUrl = item.thumbnails.firstOrNull()?.url.orEmpty(),
                                serverContextBonus = 0.0
                            )
                        )
                    }
                }

                absorbSearch(info.relatedItems)
                var next = info.nextPage
                var pages = 0
                while (next != null && pages < 2 && scored.size < 50) {
                    currentCoroutineContext().ensureActive()
                    val page = SearchInfo.getMoreItems(service, handler, next)
                    absorbSearch(page.items)
                    next = page.nextPage
                    pages += 1
                }
            }

            val episodeItems = scored.values
                .groupBy { candidate ->
                    candidate.episode?.let { "episode:$it" } ?: "video:${candidate.videoId}"
                }
                .values
                .mapNotNull { duplicates -> duplicates.maxByOrNull { it.score } }
                .sortedWith(
                    compareBy<ScoredVideo> { it.episode == null }
                        .thenBy { it.episode ?: Int.MAX_VALUE }
                        .thenByDescending { it.score }
                )
                .take(50)
                .map { item ->
                    DiscoveredEpisode(
                        videoId = item.videoId,
                        title = item.title,
                        uploader = item.uploader.ifBlank { sourceUploader.ifBlank { "YouTube" } },
                        durationSeconds = item.durationSeconds,
                        thumbnailUrl = item.thumbnailUrl,
                        episodeNumber = item.episode
                    )
                }

            if (episodeItems.isNotEmpty()) {
                val currentEpisode = DiscoveredEpisode(
                    videoId = currentVideoId,
                    title = sourceTitle,
                    uploader = sourceUploader.ifBlank { "YouTube" },
                    durationSeconds = currentInfo.duration.coerceAtLeast(0L),
                    thumbnailUrl = currentInfo.thumbnails.firstOrNull()?.url
                        ?: "https://i.ytimg.com/vi/$currentVideoId/hqdefault.jpg",
                    episodeNumber = episodeNumber(sourceTitle)
                )
                val completeEpisodes = (listOf(currentEpisode) + episodeItems)
                    .distinctBy { it.videoId }
                    .groupBy { episode ->
                        episode.episodeNumber?.let { "episode:$it" } ?: "video:${episode.videoId}"
                    }
                    .values
                    .mapNotNull { duplicates ->
                        duplicates.firstOrNull { channelMatches(it.uploader) } ?: duplicates.firstOrNull()
                    }
                    .sortedWith(
                        compareBy<DiscoveredEpisode> { it.episodeNumber == null }
                            .thenBy { it.episodeNumber ?: Int.MAX_VALUE }
                            .thenBy { it.title }
                    )

                candidates += DiscoveredPlaylist(
                    title = "$seriesName • Related episodes",
                    url = "mytube://related-episodes/$currentVideoId",
                    uploader = sourceUploader.ifBlank { "YouTube" },
                    itemCount = completeEpisodes.size.toLong(),
                    thumbnailUrl = currentInfo.thumbnails.firstOrNull()?.url
                        ?: "https://i.ytimg.com/vi/$currentVideoId/hqdefault.jpg",
                    kind = if (watchNext?.relatedVideos?.isNotEmpty() == true) {
                        "YouTube Watch Next episodes"
                    } else {
                        "Related episodes"
                    },
                    episodes = completeEpisodes
                )
            }

            // Final fallback: public playlist search remains available but is heavily filtered.
            // It is intentionally lower priority than Watch Next and server-related episodes.
            runCatching {
                val service = NewPipe.getService("YouTube")
                val query = buildString {
                    append(seriesName)
                    if (sourceUploader.isNotBlank()) append(" ").append(sourceUploader)
                }
                val handler = service.searchQHFactory.fromQuery(query, listOf("playlists"), "")
                val info = SearchInfo.getInfo(service, handler)

                info.relatedItems
                    .filterIsInstance<PlaylistInfoItem>()
                    .mapNotNull { item ->
                        val coverage = tokenCoverage(seriesTokens, relevanceTokens(item.name))
                        val channelMatch = channelMatches(item.uploaderName.orEmpty())
                        val accepted = (channelMatch && coverage >= 0.70) || coverage >= 0.95
                        if (!accepted) return@mapNotNull null

                        val score = coverage * 100.0 + if (channelMatch) 50.0 else 0.0
                        score to DiscoveredPlaylist(
                            title = item.name,
                            url = item.url,
                            uploader = item.uploaderName.orEmpty().ifBlank { "YouTube" },
                            itemCount = item.streamCount.coerceAtLeast(0L),
                            thumbnailUrl = item.thumbnails.firstOrNull()?.url.orEmpty(),
                            kind = if (channelMatch) "Matching channel playlist" else "Matching playlist"
                        )
                    }
                    .sortedByDescending { it.first }
                    .take(3)
                    .forEach { candidates += it.second }
            }

            candidates
                .distinctBy { candidate ->
                    YoutubeUrls.playlistId(candidate.url) ?: candidate.url
                }
                .take(8)
        }

    /**
     * Returns the strongest verified episode/playlist shelf for the active video.
     *
     * This deliberately reuses discoverPlaylists(), so the in-player episode shelf and the
     * playlist downloader make the same decisions. When YouTube exposes a canonical playlist,
     * expand it with full NewPipe pagination so the player can show the remaining episodes
     * instead of only the small Watch Next panel.
     */
    suspend fun discoverEpisodeShelf(
        url: String,
        titleHint: String
    ): DiscoveredPlaylist? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val currentVideoId = YoutubeUrls.videoId(url)
                ?: return@withContext null
            val candidates = discoverPlaylists(url, titleHint)
            if (candidates.isEmpty()) return@withContext null

            val selected = candidates.firstOrNull { candidate ->
                candidate.episodes.size > 1 &&
                    candidate.episodes.any { it.videoId == currentVideoId } &&
                    YoutubeUrls.playlistId(candidate.url) != null
            } ?: candidates.firstOrNull { candidate ->
                candidate.episodes.size > 1 &&
                    candidate.episodes.any { it.videoId == currentVideoId }
            } ?: candidates.firstOrNull { candidate ->
                candidate.episodes.size > 1
            } ?: candidates.firstOrNull { candidate ->
                YoutubeUrls.playlistId(candidate.url) != null
            } ?: return@withContext null

            val playlistId = YoutubeUrls.playlistId(selected.url)
            if (playlistId != null) {
                val expanded = runCatching { playlistItems(selected.url) }
                    .getOrDefault(emptyList())
                    .mapNotNull { item ->
                        val videoId = YoutubeUrls.videoId(item.url) ?: return@mapNotNull null
                        DiscoveredEpisode(
                            videoId = videoId,
                            title = item.name,
                            uploader = item.uploaderName.orEmpty()
                                .ifBlank { selected.uploader.ifBlank { "YouTube" } },
                            durationSeconds = item.duration.coerceAtLeast(0L),
                            thumbnailUrl = item.thumbnails.firstOrNull()?.url
                                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                            episodeNumber = episodeNumber(item.name)
                        )
                    }
                    .distinctBy { it.videoId }

                if (expanded.size > 1 && expanded.any { it.videoId == currentVideoId }) {
                    return@withContext selected.copy(
                        itemCount = expanded.size.toLong(),
                        episodes = expanded
                    )
                }
            }

            selected.takeIf { it.episodes.size > 1 }
        }

    private fun normalizeForMatch(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun relevanceTokens(value: String): Set<String> {
        val ignored = setOf(
            "episode", "episodes", "ep", "part", "full", "official", "video", "videos",
            "drama", "serial", "series", "eng", "english", "sub", "subtitle", "subtitles",
            "promo", "teaser", "hd", "new", "latest"
        )
        return normalizeForMatch(value)
            .split(' ')
            .filter { token -> token.length >= 2 && token !in ignored && token.toIntOrNull() == null }
            .toSet()
    }

    private fun tokenCoverage(reference: Set<String>, candidate: Set<String>): Double {
        if (reference.isEmpty() || candidate.isEmpty()) return 0.0
        return reference.count { it in candidate }.toDouble() / reference.size.toDouble()
    }

    private fun containsEpisodeWord(title: String): Boolean =
        Regex("(?i)\\b(episode|episodes|ep\\.?|part)\\b").containsMatchIn(title)

    private fun episodeNumber(title: String): Int? =
        Regex("(?i)\\b(?:episode|ep\\.?|part)\\s*[-:#]?\\s*(\\d{1,4})\\b")
            .find(title)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

    private fun seriesSearchQuery(title: String): String {
        var value = title
            .substringBefore('|')
            .substringBefore('•')
            .replace(Regex("\\[[^]]*]"), " ")
            .replace(Regex("\\([^)]*\\)"), " ")
            .trim()

        value = value
            .replace(
                Regex(
                    "(?i)\\s*[-–—:]?\\s*(episode|ep\\.?|part)\\s*[-:#]?\\s*\\d+[A-Za-z]?\\b.*$"
                ),
                ""
            )
            .replace(
                Regex("(?i)\\s*[-–—:]?\\s*(season|series)\\s*\\d+\\b.*$"),
                ""
            )
            .replace(
                Regex("(?i)\\b(full episode|eng sub|urdu dubbed|official video|promo|teaser)\\b"),
                " "
            )
            .replace(Regex("\\s{2,}"), " ")
            .trim(' ', '-', '–', '—', ':')

        return value.take(90)
    }

    suspend fun searchVideos(query: String, limit: Int = 20): List<RelatedVideo> =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val clean = query.trim()
            if (clean.isBlank()) return@withContext emptyList()
            ensureInitialized()
            val service = NewPipe.getService("YouTube")
            val handler = service.searchQHFactory.fromQuery(clean, listOf("videos"), "")
            val info = SearchInfo.getInfo(service, handler)
            info.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { item ->
                    val id = YoutubeUrls.videoId(item.url) ?: return@mapNotNull null
                    RelatedVideo(
                        videoId = id,
                        title = item.name,
                        url = YoutubeUrls.canonicalVideoUrl(item.url)
                            ?: "https://www.youtube.com/watch?v=$id",
                        thumbnailUrl = item.thumbnails.firstOrNull()?.url
                            ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                        channelTitle = item.uploaderName.orEmpty().ifBlank { "YouTube" },
                        durationSeconds = item.duration.coerceAtLeast(0L)
                    )
                }
                .distinctBy { it.videoId }
                .take(limit.coerceIn(1, 50))
        }

    suspend fun playlistItems(url: String): List<org.schabi.newpipe.extractor.stream.StreamInfoItem> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            require(YoutubeUrls.playlist(url)) { "Enter a valid YouTube playlist URL" }
            ensureInitialized()
            val info = PlaylistInfo.getInfo(url)
            val result = mutableListOf<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
            val seen = mutableSetOf<String>()
            info.relatedItems.forEach { if (seen.add(it.url)) result.add(it) }
            var next = info.nextPage
            while (next != null) {
                currentCoroutineContext().ensureActive()
                val page = PlaylistInfo.getMoreItems(info.service, info.url, next)
                val fresh = page.items.filter { seen.add(it.url) }
                if (fresh.isEmpty()) break
                result.addAll(fresh)
                next = page.nextPage
            }
            result
        }

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/140.0 Mobile Safari/537.36"
}
