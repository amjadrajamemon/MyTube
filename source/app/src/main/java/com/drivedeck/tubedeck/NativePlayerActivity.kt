package com.drivedeck.tubedeck

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.drivedeck.tubedeck.auth.GoogleAccountSession
import com.drivedeck.tubedeck.data.model.OfflineMedia
import com.drivedeck.tubedeck.data.repository.TubeDeckRepository
import com.drivedeck.tubedeck.ui.screens.PlayerScreen
import com.drivedeck.tubedeck.ui.components.DownloadModal
import com.drivedeck.tubedeck.ui.components.GoogleAccountDialog
import com.drivedeck.tubedeck.ui.components.PlaybackBackChoiceOverlay
import com.drivedeck.tubedeck.player.YoutubeUrls
import com.drivedeck.tubedeck.player.BackgroundAudioController
import com.drivedeck.tubedeck.player.ConsecutiveBackPressDetector
import com.drivedeck.tubedeck.ui.theme.TubeDeckTheme
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private var playbackBackDecisionJob: Job? = null
    private var showPlaybackExitChoices by mutableStateOf(false)
    private var currentPlaybackPositionMs = 0L
    private var currentMedia by mutableStateOf(
        OfflineMedia(
            id = "pending",
            title = "Now Playing",
            videoId = "",
            thumbnailUrl = "",
            mediaUri = ""
        )
    )
    private var pipEnteredByBack = false
    private val playbackBackDetector = ConsecutiveBackPressDetector()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = TubeDeckRepository.get(applicationContext)

        // One Back pipeline for gesture navigation, 3-button navigation and legacy devices.
        // ComponentActivity bridges this dispatcher to Android's modern back dispatcher on API 33+,
        // so the system cannot pop this Activity before our PiP/double-Back state machine runs.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handlePlaybackBack()
            }
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pipBuilder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                pipBuilder.setAutoEnterEnabled(false)
                pipBuilder.setSeamlessResizeEnabled(true)
            }
            setPictureInPictureParams(pipBuilder.build())
        }

        replaceMedia(mediaFromIntent(intent), recordWatch = true)

        setContent {
            TubeDeckTheme {
                val media = currentMedia
                val settings by repository.settings.collectAsStateWithLifecycle()
                val sponsorSegments by repository.currentSponsorSegments.collectAsStateWithLifecycle()
                val googleSession = remember { GoogleAccountSession.get(applicationContext) }
                val googleAccount by googleSession.account.collectAsStateWithLifecycle()
                var showGoogleAccountDialog by remember { mutableStateOf(false) }
                var showDownload by remember { mutableStateOf(false) }
                var playlistMode by remember { mutableStateOf(false) }
                val onGoogleAccountClick: () -> Unit = {
                    if (googleAccount != null) {
                        showGoogleAccountDialog = true
                    } else {
                        lifecycleScope.launch {
                            googleSession.signIn(this@NativePlayerActivity, BuildConfig.GOOGLE_WEB_CLIENT_ID)
                                .onSuccess {
                                    Toast.makeText(this@NativePlayerActivity, "Signed in with Google", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure {
                                    Toast.makeText(
                                        this@NativePlayerActivity,
                                        it.message ?: "Google sign-in failed",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
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
                    onPlayRelated = { related -> launchRelatedPlayer(related) },
                    googleAccount = googleAccount,
                    onGoogleAccountClick = onGoogleAccountClick,
                    onPlaybackPositionChanged = { currentPlaybackPositionMs = it },
                    onResolvedTitleChanged = { title ->
                        if (title.isNotBlank() && currentMedia.title != title) {
                            currentMedia = currentMedia.copy(title = title)
                            repository.recordWatch(currentMedia)
                        }
                    }
                )
                if (showGoogleAccountDialog) {
                    googleAccount?.let { account ->
                        GoogleAccountDialog(
                            account = account,
                            onSignOut = {
                                lifecycleScope.launch {
                                    googleSession.signOut()
                                    showGoogleAccountDialog = false
                                }
                            },
                            onDismiss = { showGoogleAccountDialog = false }
                        )
                    }
                }

                if (showPlaybackExitChoices) {
                    PlaybackBackChoiceOverlay(
                        onCloseVideo = {
                            showPlaybackExitChoices = false
                            closePlaybackCompletely()
                        },
                        onAudioOnly = {
                            showPlaybackExitChoices = false
                            handoffToBackgroundAudio()
                        },
                        onDismiss = { showPlaybackExitChoices = false },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                if (showDownload) {
                    androidx.compose.runtime.key(playlistMode) {
                        DownloadModal(
                            initialUrl = media.mediaUri,
                            initialTitle = media.title,
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
                            onDiscoverPlaylistCandidates = { url, title -> repository.discoverPlaylistCandidates(url, title) },
                            onFetchPlaylistItems = { repository.fetchPlaylistItems(it) },
                            onFetchDownloadChoices = { repository.fetchDownloadChoices(it) },
                            settings = settings
                        )
                    }
                }
                }
            }
        }

    }


    private fun launchRelatedPlayer(media: OfflineMedia) {
        if (closingPlayer) return
        replaceMedia(media, recordWatch = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        replaceMedia(mediaFromIntent(intent), recordWatch = true)
    }

    private fun mediaFromIntent(intent: Intent): OfflineMedia =
        OfflineMedia(
            id = intent.getStringExtra(EXTRA_MEDIA_ID) ?: UUID.randomUUID().toString(),
            title = intent.getStringExtra(EXTRA_TITLE) ?: "Now Playing",
            videoId = intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty(),
            thumbnailUrl = intent.getStringExtra(EXTRA_THUMBNAIL).orEmpty(),
            mediaUri = intent.getStringExtra(EXTRA_MEDIA_URI).orEmpty(),
            isAudioOnly = intent.getBooleanExtra(EXTRA_IS_AUDIO_ONLY, false),
            qualityLabel = intent.getStringExtra(EXTRA_QUALITY_LABEL) ?: "Auto"
        )

    private fun replaceMedia(media: OfflineMedia, recordWatch: Boolean) {
        playbackBackDecisionJob?.cancel()
        playbackBackDecisionJob = null
        playbackBackDetector.clear()
        showPlaybackExitChoices = false
        closingPlayer = false
        playerActive = true
        pipEnteredByBack = false
        currentPlaybackPositionMs = 0L
        currentMedia = media
        if (recordWatch && !media.title.equals("Now Playing", ignoreCase = true)) {
            repository.recordWatch(media)
        }
        if (media.videoId.isNotBlank()) {
            repository.fetchSponsorSegments(media.videoId)
        }
    }


    private fun handlePlaybackBack() {
        if (!playerActive || closingPlayer) {
            finishPlayer()
            return
        }

        if (showPlaybackExitChoices) {
            playbackBackDecisionJob?.cancel()
            playbackBackDecisionJob = null
            playbackBackDetector.clear()
            showPlaybackExitChoices = false
            closePlaybackCompletely()
            return
        }

        if (isPipMode) {
            playbackBackDecisionJob?.cancel()
            playbackBackDecisionJob = null
            playbackBackDetector.clear()
            showPlaybackExitChoices = true
            return
        }

        if (pipEnteredByBack) {
            pipEnteredByBack = false
            playbackBackDecisionJob?.cancel()
            playbackBackDecisionJob = null
            playbackBackDetector.clear()
            showPlaybackExitChoices = true
            return
        }

        val isSecondPress = playbackBackDetector.registerPress(SystemClock.elapsedRealtime())

        if (isSecondPress) {
            playbackBackDecisionJob?.cancel()
            playbackBackDecisionJob = null
            playbackBackDetector.clear()
            showPlaybackExitChoices = true
            return
        }

        playbackBackDecisionJob?.cancel()
        playbackBackDecisionJob = lifecycleScope.launch {
            delay(ConsecutiveBackPressDetector.DEFAULT_WINDOW_MS)
            if (!playerActive || closingPlayer) return@launch
            playbackBackDetector.clear()
            playbackBackDecisionJob = null
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                repository.settings.value.pipEnabled
            ) {
                enterPlayerPip(fromBack = true)
            } else {
                showPlaybackExitChoices = true
            }
        }
    }

    private fun closePlaybackCompletely() {
        if (closingPlayer) return
        closingPlayer = true
        playerActive = false
        playbackBackDecisionJob?.cancel()
        playbackBackDecisionJob = null
        playbackBackDetector.clear()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask()
        } else {
            finishAffinity()
        }
    }

    private fun handoffToBackgroundAudio() {
        val media = currentMedia
        Toast.makeText(this, "Switching to background audio…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            BackgroundAudioController.handoff(
                context = this@NativePlayerActivity,
                sourceUrl = media.mediaUri,
                title = media.title,
                artworkUrl = media.thumbnailUrl,
                positionMs = currentPlaybackPositionMs
            ).onSuccess {
                closingPlayer = true
                playerActive = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    finishAndRemoveTask()
                } else {
                    finishAffinity()
                }
            }.onFailure {
                Toast.makeText(
                    this@NativePlayerActivity,
                    it.message ?: "Background audio could not start",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun finishPlayer() {
        if (closingPlayer) return
        closingPlayer = true
        playerActive = false
        finish()
    }

    private fun enterPlayerPip(fromBack: Boolean = false) {
        if (fromBack) pipEnteredByBack = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(false)
                builder.setSeamlessResizeEnabled(true)
            }
            val params = builder.build()
            setPictureInPictureParams(params)
            val entered = enterPictureInPictureMode(params)
            if (!entered) {
                pipEnteredByBack = false
                // Do not fall through to Activity finish: preserving playback is preferable.
                showPlaybackExitChoices = true
                Toast.makeText(this, "Picture-in-picture is unavailable", Toast.LENGTH_SHORT).show()
            }
        } else {
            showPlaybackExitChoices = true
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
        playbackBackDecisionJob?.cancel()
        playbackBackDecisionJob = null
        playerActive = false
        super.onDestroy()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipMode = isInPictureInPictureMode
    }
}
