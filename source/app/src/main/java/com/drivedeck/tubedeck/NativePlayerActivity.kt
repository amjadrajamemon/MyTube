package com.drivedeck.tubedeck

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.drivedeck.tubedeck.data.model.OfflineMedia
import com.drivedeck.tubedeck.data.repository.TubeDeckRepository
import com.drivedeck.tubedeck.ui.screens.PlayerScreen
import com.drivedeck.tubedeck.ui.components.DownloadModal
import com.drivedeck.tubedeck.player.YoutubeUrls
import com.drivedeck.tubedeck.ui.theme.TubeDeckTheme
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NativePlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MEDIA_ID = "native_player_media_id"
        const val EXTRA_TITLE = "native_player_title"
        const val EXTRA_VIDEO_ID = "native_player_video_id"
        const val EXTRA_THUMBNAIL = "native_player_thumbnail"
        const val EXTRA_MEDIA_URI = "native_player_media_uri"
        const val EXTRA_IS_AUDIO_ONLY = "native_player_audio_only"
        const val EXTRA_QUALITY_LABEL = "native_player_quality"
    }

    private lateinit var repository: TubeDeckRepository
    private var isPipMode = false
    private var playerActive = true
    private var closingPlayer = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = TubeDeckRepository.get(applicationContext)

        val media = OfflineMedia(
            id = intent.getStringExtra(EXTRA_MEDIA_ID) ?: UUID.randomUUID().toString(),
            title = intent.getStringExtra(EXTRA_TITLE) ?: "Now Playing",
            videoId = intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty(),
            thumbnailUrl = intent.getStringExtra(EXTRA_THUMBNAIL).orEmpty(),
            mediaUri = intent.getStringExtra(EXTRA_MEDIA_URI).orEmpty(),
            isAudioOnly = intent.getBooleanExtra(EXTRA_IS_AUDIO_ONLY, false),
            qualityLabel = intent.getStringExtra(EXTRA_QUALITY_LABEL) ?: "Auto"
        )

        repository.recordWatch(media)

        setContent {
            TubeDeckTheme {
                val settings by repository.settings.collectAsStateWithLifecycle()
                val sponsorSegments by repository.currentSponsorSegments.collectAsStateWithLifecycle()
                var showDownload by remember { mutableStateOf(false) }
                var playlistMode by remember { mutableStateOf(false) }

                PlayerScreen(
                    media = media,
                    playerMode = com.drivedeck.tubedeck.data.model.PlayerMode.NATIVE,
                    sponsorSegments = sponsorSegments,
                    pipEnabled = settings.pipEnabled,
                    onClose = { finishPlayer() },
                    onEnterPip = { enterPlayerPip() },
                    onDownloadVideo = if (YoutubeUrls.videoId(media.mediaUri) != null) {
                        { playlistMode = false; showDownload = true }
                    } else null,
                    onDownloadPlaylist = if (YoutubeUrls.videoId(media.mediaUri) != null) {
                        { playlistMode = true; showDownload = true }
                    } else null,
                    onPlayRelated = { related -> launchRelatedPlayer(related) }
                )
                if (showDownload) {
                    androidx.compose.runtime.key(playlistMode) {
                        DownloadModal(
                            initialUrl = media.mediaUri,
                            initialPlaylistMode = playlistMode,
                            onDismiss = { showDownload = false },
                            onStartDownload = { url, title, mode, quality ->
                                runCatching { repository.enqueueDownload(url, title, mode, quality) }
                                    .onSuccess { Toast.makeText(this@NativePlayerActivity, "Download queued", Toast.LENGTH_SHORT).show() }
                                    .onFailure { Toast.makeText(this@NativePlayerActivity, it.message ?: "Download unavailable", Toast.LENGTH_LONG).show() }
                            },
                            onDownloadPlaylist = { items, mode, quality ->
                                lifecycleScope.launch(Dispatchers.IO) {
                                    repository.enqueuePlaylist(items, mode, quality)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@NativePlayerActivity, "Selected videos queued", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onFetchPlaylistItems = { repository.fetchPlaylistItems(it) },
                            onFetchDownloadChoices = { repository.fetchDownloadChoices(it) },
                            settings = settings
                        )
                    }
                }
            }
        }

        if (media.videoId.isNotBlank()) {
            repository.fetchSponsorSegments(media.videoId)
        }
    }


    private fun launchRelatedPlayer(media: OfflineMedia) {
        if (closingPlayer) return
        repository.recordWatch(media)
        if (media.videoId.isNotBlank()) repository.fetchSponsorSegments(media.videoId)
        val next = android.content.Intent(this, NativePlayerActivity::class.java).apply {
            putExtra(EXTRA_MEDIA_ID, media.id)
            putExtra(EXTRA_TITLE, media.title)
            putExtra(EXTRA_VIDEO_ID, media.videoId)
            putExtra(EXTRA_THUMBNAIL, media.thumbnailUrl)
            putExtra(EXTRA_MEDIA_URI, media.mediaUri)
            putExtra(EXTRA_IS_AUDIO_ONLY, media.isAudioOnly)
            putExtra(EXTRA_QUALITY_LABEL, media.qualityLabel)
        }
        startActivity(next)
        finishPlayer()
    }


    private fun finishPlayer() {
        if (closingPlayer) return
        closingPlayer = true
        playerActive = false
        finish()
    }

    private fun enterPlayerPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        } else {
            finish()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Preserve the existing auto-PiP behavior for the native player Activity.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playerActive && !closingPlayer && !isFinishing && !isPipMode && repository.settings.value.pipEnabled) {
            enterPlayerPip()
        }
    }

    override fun onDestroy() {
        playerActive = false
        super.onDestroy()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipMode = isInPictureInPictureMode
    }
}
