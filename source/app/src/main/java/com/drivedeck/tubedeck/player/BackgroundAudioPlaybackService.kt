package com.drivedeck.tubedeck.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground MediaSession playback used only after the user explicitly chooses Audio-only.
 *
 * The service keeps both the original YouTube watch URL and the temporary direct audio URL.
 * If a signed CDN URL expires, the original watch URL is re-resolved and playback resumes from
 * the current position instead of leaving a broken/zombie MediaSession behind.
 */
class BackgroundAudioPlaybackService : MediaSessionService() {

    companion object {
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
            positionMs: Long
        ) {
            val intent = Intent(context, BackgroundAudioPlaybackService::class.java).apply {
                action = ACTION_PLAY
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
    private var recovering = false

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().apply {
            playWhenReady = false
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    refreshExpiredYoutubeAudio()
                }
            })
        }
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
                BackgroundAudioController.markStarted(sourceUrl)
                loadPlayableUrl(directUrl, position)
            }
        }
        return START_NOT_STICKY
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
                    resolved.audioOptions.firstOrNull()?.url
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
        BackgroundAudioController.markStopped(sourceUrl)
        serviceScope.cancel()
        mediaSession?.release()
        mediaSession = null
        player.release()
        super.onDestroy()
    }
}

object BackgroundAudioController {
    @Volatile
    private var activeSourceUrl: String? = null

    internal fun markStarted(sourceUrl: String) {
        activeSourceUrl = sourceUrl.takeIf { it.isNotBlank() }
    }

    internal fun markStopped(sourceUrl: String) {
        if (activeSourceUrl == sourceUrl || sourceUrl.isBlank()) {
            activeSourceUrl = null
        }
    }

    fun currentSourceUrl(): String? = activeSourceUrl

    fun stop(context: Context): String? {
        val previous = activeSourceUrl
        activeSourceUrl = null
        BackgroundAudioPlaybackService.stop(context)
        return previous
    }

    suspend fun handoff(
        context: Context,
        sourceUrl: String,
        title: String,
        artworkUrl: String?,
        positionMs: Long
    ): Result<Unit> = runCatching {
        val playUrl = withContext(Dispatchers.IO) {
            if (YoutubeUrls.videoId(sourceUrl) != null) {
                YoutubeStreamResolver.invalidate(sourceUrl)
                val resolved = YoutubeStreamResolver.resolve(
                    context.applicationContext,
                    sourceUrl,
                    forceRefresh = true
                )
                resolved.audioOptions.firstOrNull()?.url
                    ?: resolved.progressiveUrl
                    ?: error("No playable audio stream is available for this video.")
            } else {
                sourceUrl
            }
        }

        activeSourceUrl = sourceUrl
        BackgroundAudioPlaybackService.start(
            context = context.applicationContext,
            sourceUrl = sourceUrl,
            playUrl = playUrl,
            title = title,
            artworkUrl = artworkUrl,
            positionMs = positionMs
        )
    }
}
