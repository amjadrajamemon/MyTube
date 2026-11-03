package com.drivedeck.tubedeck.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.drivedeck.tubedeck.MainActivity
import com.drivedeck.tubedeck.data.model.PlaybackContinuationMode
import com.drivedeck.tubedeck.data.repository.TubeDeckRepository
import androidx.core.app.ServiceCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import kotlinx.coroutines.withTimeout
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import kotlinx.coroutines.withTimeoutOrNull
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground MediaSession playback for Audio-only and user-initiated Home transitions.
 *
 * The service keeps both the original YouTube watch URL and the temporary direct audio URL.
 * If a signed CDN URL expires, the original watch URL is re-resolved and playback resumes from
 * the current position instead of leaving a broken/zombie MediaSession behind.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class BackgroundAudioPlaybackService : MediaSessionService() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "mytube_background_audio"
        private const val EXTRA_REQUEST_ID = "request_id"
        private const val ACTION_PLAY = "com.drivedeck.tubedeck.action.PLAY_BACKGROUND_AUDIO"
        const val ACTION_RETURN_TO_VIDEO = "com.drivedeck.tubedeck.action.RETURN_TO_VIDEO"
        private const val EXTRA_SOURCE_URL = "source_url"
        private const val EXTRA_PLAY_URL = "play_url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ARTWORK = "artwork"
        private const val EXTRA_POSITION_MS = "position_ms"

        fun start(
            context: Context,
            sourceUrl: String,
            playUrl: String,
            title: String,
            artworkUrl: String?,
            positionMs: Long,
            requestId: Long
        ) {
            val intent = Intent(context, BackgroundAudioPlaybackService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_REQUEST_ID, requestId)
                putExtra(EXTRA_SOURCE_URL, sourceUrl)
                putExtra(EXTRA_PLAY_URL, playUrl)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTWORK, artworkUrl)
                putExtra(EXTRA_POSITION_MS, positionMs.coerceAtLeast(0L))
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(
                Intent(context.applicationContext, BackgroundAudioPlaybackService::class.java)
            )
        }
    }

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var sourceUrl: String = ""
    private var currentTitle: String = "MyTube audio"
    private var currentArtwork: String? = null
    private var requestId = 0L
    private var startupPending = true
    private var recovering = false
    private var recoveryAttempts = 0
    private var resolveJob: kotlinx.coroutines.Job? = null
    private var nextAudioLookupInProgress = false
    private val nextSession = AudioNextSessionPolicy()
    private var positionSnapshotJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        // Fulfil startForegroundService immediately, before player/session creation or buffering.
        promoteToForeground()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(NOTIFICATION_ID)
                .setChannelId(CHANNEL_ID)
                .build()
        )
        val fastAudioLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1_000,
                20_000,
                250,
                500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        player = ExoPlayer.Builder(this)
            .setTrackSelector(DefaultTrackSelector(this).apply {
                setParameters(buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true))
            })
            .setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setLoadControl(fastAudioLoadControl)
            .build().apply {
            playWhenReady = false
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    // Snapshots are event-driven when paused, so a sleeping TV box
                    // has no periodically waking coroutine during audio pause.
                    positionSnapshotJob?.cancel()
                    positionSnapshotJob = null
                    if (sourceUrl.isNotBlank()) {
                        BackgroundAudioController.markPosition(
                            sourceUrl, player.currentPosition.coerceAtLeast(0L)
                        )
                    }
                    if (isPlaying) {
                        positionSnapshotJob = serviceScope.launch {
                            while (isActive && player.isPlaying) {
                                if (sourceUrl.isNotBlank()) {
                                    BackgroundAudioController.markPosition(
                                        sourceUrl, player.currentPosition.coerceAtLeast(0L)
                                    )
                                }
                                delay(2_000L)
                            }
                        }
                    }
                    if (isPlaying && startupPending) {
                        startupPending = false
                        BackgroundAudioController.markStarted(sourceUrl, currentTitle, currentArtwork)
                        BackgroundAudioController.markPosition(sourceUrl, player.currentPosition)
                        BackgroundAudioController.startup.ready(requestId)
                        PlaybackDiagnostics.record(this@BackgroundAudioPlaybackService,
                            "audio_service_started", "position=${player.currentPosition}")
                        val startedRequest = requestId
                        serviceScope.launch {
                            delay(4_000L)
                            if (requestId == startedRequest && player.isPlaying) {
                                PlaybackDiagnostics.record(this@BackgroundAudioPlaybackService,
                                    "audio_background_progress", "position=${player.currentPosition}")
                            }
                        }
                    }
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) recoveryAttempts = 0
                    if (state == Player.STATE_ENDED) {
                        // A foreground audio session must never remain an idle zombie.
                        // Honor the user's continuation mode just like native video.
                        when (TubeDeckRepository.get(applicationContext)
                            .settings.value.playbackContinuationMode) {
                            PlaybackContinuationMode.AUTO_NEXT -> {
                                if (!skipBackgroundAudioNext()) stopSelf()
                            }
                            PlaybackContinuationMode.LOOP_CURRENT -> {
                                player.seekTo(0L)
                                player.play()
                            }
                            PlaybackContinuationMode.EPISODES -> stopSelf()
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    val retryable =
                        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                            error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED

                    if (retryable && recoveryAttempts < 3) {
                        recoveryAttempts += 1
                        refreshExpiredYoutubeAudio()
                    } else {
                        player.pause()
                        stopSelf()
                    }
                }
            })
        }
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(returnToVideoPendingIntent())
            .setCallback(object : MediaSession.Callback {
                override fun onMediaButtonEvent(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    intent: Intent
                ): Boolean {
                    @Suppress("DEPRECATION")
                    val event: KeyEvent? = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    if (event?.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
                    return when (event.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_NEXT -> skipBackgroundAudioNext()
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            player.seekTo(0L)
                            true
                        }
                        else -> false
                    }
                }
            })
            .build()
    }

    /**
     * Unlike an ExoPlayer playlist, background audio uses a single short-lived
     * signed CDN URL. Media3's default seekToNext() is therefore a no-op.
     * Resolve the next YouTube suggestion before touching the current stream.
     */
    private fun skipBackgroundAudioNext(): Boolean {
        val original = sourceUrl
        if (YoutubeUrls.videoId(original) == null) return false
        if (nextAudioLookupInProgress) return true
        nextAudioLookupInProgress = true
        val searchHint = currentTitle
        serviceScope.launch {
            try {
                val currentVideoId = YoutubeUrls.videoId(original).orEmpty()
                val cached = YoutubeStreamResolver.peekForHandoff(original)
                val suggestedCandidates = cached?.relatedVideos.orEmpty()
                val suggestedId = nextSession.firstUnplayed(
                    currentVideoId, suggestedCandidates.map { it.videoId }
                )
                val suggested = suggestedCandidates.firstOrNull { it.videoId == suggestedId }

                val watchNext = if (suggested == null) {
                    runCatching {
                        withTimeoutOrNull(8_000L) {
                            val candidates = YoutubeWatchNextClient.resolve(original, currentVideoId)
                                .relatedVideos
                            val selected = nextSession.firstUnplayed(
                                currentVideoId, candidates.map { it.videoId }
                            )
                            candidates.firstOrNull { it.videoId == selected }
                        }
                    }.getOrNull()
                } else null
                val searchNext = if (suggested == null && watchNext == null &&
                    searchHint.isNotBlank() && searchHint != "Now Playing"
                ) {
                    withTimeoutOrNull(8_000L) {
                        val candidates = YoutubeStreamResolver.searchVideos(searchHint, 12)
                        val selected = nextSession.firstUnplayed(
                            currentVideoId, candidates.map { it.videoId }
                        )
                        candidates.firstOrNull { it.videoId == selected }
                    }
                } else null
                val nextUrl = suggested?.url
                    ?: watchNext?.videoId?.let { "https://www.youtube.com/watch?v=$it" }
                    ?: searchNext?.url
                val nextTitle = suggested?.title ?: watchNext?.title ?: searchNext?.title
                val nextArtwork = suggested?.thumbnailUrl ?: watchNext?.thumbnailUrl ?: searchNext?.thumbnailUrl
                if (nextUrl.isNullOrBlank()) {
                    // End of queue: don't strand an ended ExoPlayer/foreground service.
                    if (player.playbackState == Player.STATE_ENDED) stopSelf()
                    return@launch
                }
                if (sourceUrl != original) return@launch

                val nextPlayable = withContext(Dispatchers.IO) {
                    val resolved = YoutubeStreamResolver.resolve(applicationContext, nextUrl)
                    YoutubeStreamResolver.preferredAudioPlaybackUrl(resolved)
                }
                if (sourceUrl != original) return@launch
                if (nextPlayable.isNullOrBlank()) {
                    if (player.playbackState == Player.STATE_ENDED) stopSelf()
                    return@launch
                }

                player.pause()
                BackgroundAudioController.markStopped(original)
                sourceUrl = nextUrl
                nextSession.recordPlayed(YoutubeUrls.videoId(nextUrl).orEmpty())
                currentTitle = nextTitle.orEmpty().ifBlank { "YouTube audio" }
                currentArtwork = nextArtwork
                // The existing foreground session and audio focus remain active.
                // onIsPlayingChanged updates the controller snapshot for Return to Video.
                startupPending = true
                loadPlayableUrl(nextPlayable, 0L)
                promoteToForeground()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                PlaybackDiagnostics.record(this@BackgroundAudioPlaybackService,
                    "background_audio_next_error", error.javaClass.simpleName)
                // If autoplay has already exhausted the current audio stream,
                // an unavailable network/next suggestion must not leave a
                // permanently idle foreground service behind.
                if (player.playbackState == Player.STATE_ENDED) stopSelf()
            } finally {
                nextAudioLookupInProgress = false
            }
        }
        return true
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PLAY) {
            promoteToForeground()
            requestId = intent.getLongExtra(EXTRA_REQUEST_ID, 0L)
            startupPending = true
        }
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_PLAY) {
            resolveJob?.cancel()
            player.stop()
            val directUrl = intent.getStringExtra(EXTRA_PLAY_URL).orEmpty()
            sourceUrl = intent.getStringExtra(EXTRA_SOURCE_URL).orEmpty()
            nextSession.startExplicitPlayback(YoutubeUrls.videoId(sourceUrl).orEmpty())
            if (sourceUrl.isNotBlank() && BackgroundAudioController.startup.isCurrent(requestId)) {
                currentTitle = intent.getStringExtra(EXTRA_TITLE)
                    .orEmpty()
                    .ifBlank { "MyTube audio" }
                currentArtwork = intent.getStringExtra(EXTRA_ARTWORK)
                val position = intent.getLongExtra(EXTRA_POSITION_MS, 0L).coerceAtLeast(0L)
                val requestedId = requestId
                val requestedSource = sourceUrl
                resolveJob = serviceScope.launch {
                    try {
                        val playable = directUrl.ifBlank {
                            withContext(Dispatchers.IO) {
                                val resolved = YoutubeStreamResolver.resolve(applicationContext, requestedSource)
                                YoutubeStreamResolver.preferredAudioPlaybackUrl(resolved)
                                    ?: error("No playable audio stream is available for this video.")
                            }
                        }
                        if (requestedId == requestId &&
                            BackgroundAudioController.startup.isCurrent(requestedId)) {
                            loadPlayableUrl(playable, position)
                        }
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (error: Exception) {
                        BackgroundAudioController.startup.fail(requestedId,
                            error.message ?: "Background audio could not start")
                        if (requestedId == requestId) stopSelf()
                    }
                }
            } else {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun returnToVideoPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_RETURN_TO_VIDEO
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            this,
            1002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun promoteToForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "MyTube audio", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(currentTitle)
            .setContentText("Audio only • Tap to return to video")
            .setContentIntent(returnToVideoPendingIntent())
            .addAction(
                android.R.drawable.ic_menu_view,
                "Return to video",
                returnToVideoPendingIntent()
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        )
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        // An idle/not-yet-connected Media3 controller can remove the notification even when
        // foreground is requested. Keep our startup notification until playback is acknowledged.
        if (startupPending) {
            promoteToForeground()
        } else {
            super.onUpdateNotification(session, startInForegroundRequired)
        }
    }

    private fun loadPlayableUrl(playUrl: String, positionMs: Long) {
        val metadata = MediaMetadata.Builder()
            .setTitle(currentTitle)
            .setArtist("Audio only • Tap notification to return to video")
            .apply {
                currentArtwork?.takeIf { it.isNotBlank() }?.let {
                    setArtworkUri(Uri.parse(it))
                }
            }
            .build()

        val item = MediaItem.Builder()
            .setUri(playUrl)
            .setMediaId(sourceUrl)
            .setMediaMetadata(metadata)
            .build()

        player.setMediaItem(item, positionMs.coerceAtLeast(0L))
        player.prepare()
        player.playWhenReady = true
    }

    private fun refreshExpiredYoutubeAudio() {
        val originalUrl = sourceUrl
        if (recovering || YoutubeUrls.videoId(originalUrl) == null) return

        val resumePosition = player.currentPosition.coerceAtLeast(0L)
        recovering = true
        serviceScope.launch {
            val freshUrl = runCatching {
                withContext(Dispatchers.IO) {
                    YoutubeStreamResolver.invalidate(originalUrl)
                    val resolved = YoutubeStreamResolver.resolve(
                        applicationContext,
                        originalUrl,
                        forceRefresh = true
                    )
                    YoutubeStreamResolver.preferredAudioPlaybackUrl(resolved)
                        ?: error("No refreshed audio stream is available.")
                }
            }

            freshUrl.onSuccess { loadPlayableUrl(it, resumePosition) }
                .onFailure {
                    player.pause()
                    stopSelf()
                }
            recovering = false
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!startupPending && !player.isPlaying && !player.playWhenReady) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        positionSnapshotJob?.cancel()
        positionSnapshotJob = null
        BackgroundAudioController.startup.fail(requestId, "Audio playback stopped before it was ready")
        BackgroundAudioController.markStopped(sourceUrl)
        serviceScope.cancel()
        mediaSession?.release()
        mediaSession = null
        player.release()
        super.onDestroy()
    }
}

data class BackgroundAudioSnapshot(
    val sourceUrl: String,
    val positionMs: Long,
    val title: String = "Now Playing",
    val artworkUrl: String = ""
)

object BackgroundAudioController {
    internal val startup = AudioHandoffStartup()
    @Volatile
    private var activeSourceUrl: String? = null
    @Volatile
    private var activePositionMs: Long = 0L
    private var activeTitle = "Now Playing"
    private var activeArtwork = ""

    internal fun markStarted(sourceUrl: String, title: String, artworkUrl: String?) {
        activeTitle = title
        activeArtwork = artworkUrl.orEmpty()
        activeSourceUrl = sourceUrl.takeIf { it.isNotBlank() }
        activePositionMs = 0L
    }

    internal fun markPosition(sourceUrl: String, positionMs: Long) {
        if (activeSourceUrl == sourceUrl && sourceUrl.isNotBlank()) {
            activePositionMs = positionMs.coerceAtLeast(0L)
        }
    }

    internal fun markStopped(sourceUrl: String) {
        if (activeSourceUrl == sourceUrl || sourceUrl.isBlank()) {
            activeSourceUrl = null
            activePositionMs = 0L
        }
    }

    fun currentSourceUrl(): String? = activeSourceUrl

    fun snapshot(): BackgroundAudioSnapshot? =
        activeSourceUrl?.takeIf { it.isNotBlank() }?.let {
            BackgroundAudioSnapshot(it, activePositionMs.coerceAtLeast(0L), activeTitle, activeArtwork)
        }

    fun takeSnapshotAndStop(context: Context): BackgroundAudioSnapshot? {
        val snapshot = snapshot()
        startup.cancelPending("Audio playback was stopped")
        activeSourceUrl = null
        activePositionMs = 0L
        BackgroundAudioPlaybackService.stop(context)
        return snapshot
    }

    fun stop(context: Context): String? =
        takeSnapshotAndStop(context)?.sourceUrl

    suspend fun handoff(
        context: Context,
        sourceUrl: String,
        title: String,
        artworkUrl: String?,
        positionMs: Long
    ): Result<Unit> {
        val request = startup.begin()
            ?: return Result.failure(IllegalStateException("Audio playback is already starting"))
        var serviceRequested = false
        val result = runCatching {
            val playUrl = if (YoutubeUrls.videoId(sourceUrl) != null) {
                val cached = YoutubeStreamResolver.peekForHandoff(sourceUrl)
                cached?.let { YoutubeStreamResolver.preferredAudioPlaybackUrl(it) }.orEmpty()
            } else {
                sourceUrl
            }

            // Start while the Activity is still user-visible. Slow network resolution belongs
            // inside the already-promoted service, not after the Home transition has finished.
            check(startup.isCurrent(request.id)) { "Audio playback was cancelled" }
            BackgroundAudioPlaybackService.start(
                context = context.applicationContext,
                sourceUrl = sourceUrl,
                playUrl = playUrl,
                title = title,
                artworkUrl = artworkUrl,
                positionMs = positionMs,
                requestId = request.id
            )
            serviceRequested = true
            check(startup.isCurrent(request.id)) { "Audio playback was cancelled" }
            // Keep the video host alive until audio actually plays, or return a recoverable failure.
            withTimeout(20_000L) { request.ready.await().getOrThrow() }
        }
        if (result.isFailure && serviceRequested && startup.isCurrent(request.id)) {
            BackgroundAudioPlaybackService.stop(context)
        }
        startup.finish(request.id)
        result.exceptionOrNull()?.let {
            // A handshake timeout is a recoverable startup failure. Only cancellation of the
            // caller (returning to video, closing or replacement playback) aborts its coroutine.
            if (it is CancellationException && it !is kotlinx.coroutines.TimeoutCancellationException) throw it
        }
        return result
    }
}


