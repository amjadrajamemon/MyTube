package com.drivedeck.tubedeck.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import kotlinx.coroutines.withTimeout
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground MediaSession playback used only after the user explicitly chooses Audio-only.
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
        player = ExoPlayer.Builder(this)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build().apply {
            playWhenReady = false
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying && startupPending) {
                        startupPending = false
                        BackgroundAudioController.markStarted(sourceUrl, currentTitle, currentArtwork)
                        BackgroundAudioController.markPosition(sourceUrl, player.currentPosition)
                        BackgroundAudioController.startup.ready(requestId)
                    }
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) recoveryAttempts = 0
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
        mediaSession = MediaSession.Builder(this, player).build()
        serviceScope.launch {
            while (isActive) {
                BackgroundAudioController.markPosition(sourceUrl, player.currentPosition.coerceAtLeast(0L))
                delay(500L)
            }
        }
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
            val directUrl = intent.getStringExtra(EXTRA_PLAY_URL).orEmpty()
            if (directUrl.isNotBlank()) {
                sourceUrl = intent.getStringExtra(EXTRA_SOURCE_URL).orEmpty()
                currentTitle = intent.getStringExtra(EXTRA_TITLE)
                    .orEmpty()
                    .ifBlank { "MyTube audio" }
                currentArtwork = intent.getStringExtra(EXTRA_ARTWORK)
                val position = intent.getLongExtra(EXTRA_POSITION_MS, 0L).coerceAtLeast(0L)
                loadPlayableUrl(directUrl, position)
            } else {
                stopSelf()
            }
        }
        return START_NOT_STICKY
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
            .setContentText("Starting audio playback…")
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

        player.setMediaItem(item)
        player.prepare()
        if (positionMs > 0L) player.seekTo(positionMs)
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
                    resolved.audioOptions.maxByOrNull { it.bitrate }?.url
                        ?: resolved.progressiveUrl
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
        if (!player.isPlaying && !player.playWhenReady) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
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
            val playUrl = withContext(Dispatchers.IO) {
                if (YoutubeUrls.videoId(sourceUrl) != null) {
                    YoutubeStreamResolver.invalidate(sourceUrl)
                    val resolved = YoutubeStreamResolver.resolve(
                        context.applicationContext,
                        sourceUrl,
                        forceRefresh = true
                    )
                    resolved.audioOptions.maxByOrNull { it.bitrate }?.url
                        ?: resolved.progressiveUrl
                        ?: error("No playable audio stream is available for this video.")
                } else {
                    sourceUrl
                }
            }

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
        if (result.isFailure && serviceRequested) BackgroundAudioPlaybackService.stop(context)
        startup.finish(request.id)
        return result
    }
}

