package com.drivedeck.tubedeck.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Foreground MediaSession playback used when the user chooses "Audio only" from the Back menu.
 * The service receives a direct playable audio/media URL so playback can start immediately and
 * remain alive after the Activity/task is removed.
 */
class BackgroundAudioPlaybackService : MediaSessionService() {

    companion object {
        private const val ACTION_PLAY = "com.drivedeck.tubedeck.action.PLAY_BACKGROUND_AUDIO"
        private const val EXTRA_PLAY_URL = "play_url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ARTWORK = "artwork"
        private const val EXTRA_POSITION_MS = "position_ms"

        fun start(
            context: Context,
            playUrl: String,
            title: String,
            artworkUrl: String?,
            positionMs: Long
        ) {
            val intent = Intent(context, BackgroundAudioPlaybackService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_PLAY_URL, playUrl)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTWORK, artworkUrl)
                putExtra(EXTRA_POSITION_MS, positionMs.coerceAtLeast(0L))
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().apply {
            playWhenReady = false
        }
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_PLAY) {
            val url = intent.getStringExtra(EXTRA_PLAY_URL).orEmpty()
            if (url.isNotBlank()) {
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "MyTube audio" }
                val artwork = intent.getStringExtra(EXTRA_ARTWORK)
                val position = intent.getLongExtra(EXTRA_POSITION_MS, 0L).coerceAtLeast(0L)

                val metadata = MediaMetadata.Builder()
                    .setTitle(title)
                    .apply {
                        artwork?.takeIf { it.isNotBlank() }?.let {
                            setArtworkUri(Uri.parse(it))
                        }
                    }
                    .build()

                val item = MediaItem.Builder()
                    .setUri(url)
                    .setMediaMetadata(metadata)
                    .build()

                player.setMediaItem(item)
                player.prepare()
                if (position > 0L) player.seekTo(position)
                player.playWhenReady = true
            }
        }
        // The playable URL is intentionally supplied by the handoff action. If Android kills
        // this process there is no safe media state to reconstruct from a null restart intent, so
        // avoid creating an idle/zombie playback service.
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Deliberately keep the service/player alive while playback is active.
        if (!player.isPlaying && !player.playWhenReady) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        player.release()
        super.onDestroy()
    }
}

object BackgroundAudioController {
    suspend fun handoff(
        context: Context,
        sourceUrl: String,
        title: String,
        artworkUrl: String?,
        positionMs: Long
    ): Result<Unit> = runCatching {
        val playUrl = withContext(Dispatchers.IO) {
            if (YoutubeUrls.videoId(sourceUrl) != null) {
                val resolved = YoutubeStreamResolver.resolve(context.applicationContext, sourceUrl)
                resolved.audioOptions.firstOrNull()?.url
                    ?: resolved.progressiveUrl
                    ?: error("No playable audio stream is available for this video.")
            } else {
                sourceUrl
            }
        }

        BackgroundAudioPlaybackService.start(
            context = context.applicationContext,
            playUrl = playUrl,
            title = title,
            artworkUrl = artworkUrl,
            positionMs = positionMs
        )
    }
}
