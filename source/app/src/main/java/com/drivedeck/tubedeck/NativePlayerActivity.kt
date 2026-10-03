package com.drivedeck.tubedeck

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.drivedeck.tubedeck.auth.YoutubeAuthorizationState
import com.drivedeck.tubedeck.data.model.OfflineMedia
import com.drivedeck.tubedeck.data.model.PlayerMode
import com.drivedeck.tubedeck.data.repository.TubeDeckRepository
import com.drivedeck.tubedeck.ui.screens.PlayerScreen
import com.drivedeck.tubedeck.ui.components.DownloadModal
import com.drivedeck.tubedeck.ui.components.GoogleAccountDialog
import com.drivedeck.tubedeck.ui.components.GoogleAuthorizationProblemDialog
import com.drivedeck.tubedeck.ui.components.PlaybackBackChoiceOverlay
import com.drivedeck.tubedeck.player.YoutubeUrls
import com.drivedeck.tubedeck.player.YoutubeStreamResolver
import com.drivedeck.tubedeck.player.BackgroundAudioController
import com.drivedeck.tubedeck.player.NativePipSessionBridge
import com.drivedeck.tubedeck.player.PlaybackBackAction
import com.drivedeck.tubedeck.player.PlaybackBackCoordinator
import com.drivedeck.tubedeck.player.PlaybackHost
import com.drivedeck.tubedeck.player.PlaybackHostRouter
import com.drivedeck.tubedeck.player.PlaybackPipActions
import com.drivedeck.tubedeck.player.PlaybackSessionRegistry
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
        const val EXTRA_SESSION_ID = "native_player_session_id"
        const val EXTRA_START_POSITION_MS = "native_player_start_position_ms"
    }

    private lateinit var repository: TubeDeckRepository
    private var isPipMode = false
    private var playerActive = true
    private var closingPlayer = false
    private var playbackBackDecisionJob: Job? = null
    private var showPlaybackExitChoices by mutableStateOf(false)
    private var currentPlaybackPositionMs = 0L
    private var initialPlaybackPositionMs = 0L
    private var playbackSessionId: String = ""
    private var resumeGeneration by mutableStateOf(0)
    private var foregroundPlaybackAllowed by mutableStateOf(true)
    private var enteringPip = false
    private var pipChoicePending = false
    private var pendingFreshResumeFromPip = false
    private var currentMedia by mutableStateOf(
        OfflineMedia(
            id = "pending",
            title = "Now Playing",
            videoId = "",
            thumbnailUrl = "",
            mediaUri = ""
        )
    )
    private val playbackBackCoordinator = PlaybackBackCoordinator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = TubeDeckRepository.get(applicationContext)
        playbackSessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        initialPlaybackPositionMs = intent.getLongExtra(EXTRA_START_POSITION_MS, 0L).coerceAtLeast(0L)
        if (!PlaybackSessionRegistry.isActive(playbackSessionId)) {
            finish()
            return
        }

        lifecycleScope.launch {
            NativePipSessionBridge.commands.collect { command ->
                if (command.sessionId != playbackSessionId || closingPlayer) return@collect
                when (command.action) {
                    NativePipSessionBridge.Action.AUDIO_ONLY -> handoffToBackgroundAudio()
                    NativePipSessionBridge.Action.CLOSE -> closePlaybackCompletely()
                }
            }
        }

        // One Back pipeline for gesture navigation, 3-button navigation and legacy devices.
        // ComponentActivity bridges this dispatcher to Android's modern back dispatcher on API 33+,
        // so the system cannot pop this Activity before our PiP/double-Back state machine runs.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handlePlaybackBack()
            }
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setPictureInPictureParams(
                PlaybackPipActions.buildParams(
                    context = this,
                    activityClass = MainActivity::class.java
                )
            )
        }

        replaceMedia(mediaFromIntent(intent), recordWatch = true)
        if (
            PlaybackHostRouter.choose(
                repository.settings.value.playerMode,
                currentMedia.mediaUri
            ) == PlaybackHost.WEB
        ) {
            returnCurrentVideoToWeb()
            return
        }

        setContent {
            val settings by repository.settings.collectAsStateWithLifecycle()
            TubeDeckTheme(
                fontScale = settings.fontScale,
                accentArgb = settings.themeAccentArgb,
                backgroundArgb = settings.themeBackgroundArgb,
                textArgb = settings.themeTextArgb
            ) {
                val media = currentMedia
                val sponsorSegments by repository.currentSponsorSegments.collectAsStateWithLifecycle()
                val googleSession = remember { GoogleAccountSession.get(applicationContext) }
                val googleAccount by googleSession.account.collectAsStateWithLifecycle()
                val googleAuthStatus by googleSession.status.collectAsStateWithLifecycle()
                var showGoogleAccountDialog by remember { mutableStateOf(false) }
                var showGoogleAuthorizationProblem by remember { mutableStateOf(false) }
                val youtubeAuthorizationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    if (result.resultCode == RESULT_OK) {
                        lifecycleScope.launch {
                            googleSession.completeYoutubeAuthorization(this@NativePlayerActivity, result.data)
                                .onSuccess {
                                    Toast.makeText(
                                        this@NativePlayerActivity,
                                        "Google account connected",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .onFailure {
                                    Toast.makeText(
                                        this@NativePlayerActivity,
                                        "Google sign-in failed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    // Restore only a real previously granted YouTube OAuth session. If Google
                    // requires account selection/consent, remain signed out until the user taps.
                    googleSession.restoreYoutubeAuthorization()
                }
                LaunchedEffect(settings.playerMode, media.mediaUri) {
                    if (
                        PlaybackHostRouter.choose(
                            settings.playerMode,
                            media.mediaUri
                        ) == PlaybackHost.WEB &&
                        !closingPlayer
                    ) {
                        returnCurrentVideoToWeb()
                    }
                }
                var showDownload by remember { mutableStateOf(false) }
                var playlistMode by remember { mutableStateOf(false) }
                LaunchedEffect(googleAuthStatus.state) {
                    if (
                        googleAuthStatus.state == YoutubeAuthorizationState.CONFIGURATION_REQUIRED ||
                        googleAuthStatus.state == YoutubeAuthorizationState.ERROR
                    ) {
                        showGoogleAuthorizationProblem = true
                    }
                }

                val onGoogleAccountClick: () -> Unit = {
                    if (googleAccount != null) {
                        showGoogleAccountDialog = true
                    } else {
                        // A previous OAuth/configuration error must never turn this into a dead-end.
                        // Every explicit tap performs a fresh Google/YouTube authorization attempt.
                        showGoogleAuthorizationProblem = false
                        lifecycleScope.launch {
                            googleSession.beginYoutubeAuthorization(this@NativePlayerActivity)
                                .onSuccess { outcome ->
                                    outcome.profile?.let {
                                        Toast.makeText(
                                            this@NativePlayerActivity,
                                            "Google account connected",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    outcome.resolution?.let(youtubeAuthorizationLauncher::launch)
                                }
                                .onFailure {
                                    showGoogleAuthorizationProblem = true
                                    Toast.makeText(
                                        this@NativePlayerActivity,
                                        it.message ?: "YouTube authorization failed",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                key(media.id) {
                PlayerScreen(
                    media = media,
                    playerMode = com.drivedeck.tubedeck.data.model.PlayerMode.NATIVE,
                    sponsorSegments = sponsorSegments,
                    pipEnabled = settings.pipEnabled,
                    autoLoadEpisodes = settings.autoLoadEpisodes,
                    autoLoadUpNext = settings.autoLoadUpNext,
                    preferredPlaybackQualityHeight = settings.preferredPlaybackQualityHeight,
                    preferredPlaybackSpeed = settings.preferredPlaybackSpeed,
                    seekIntervalSeconds = settings.seekIntervalSeconds,
                    initialPositionMs = initialPlaybackPositionMs,
                    resumeGeneration = resumeGeneration,
                    foregroundPlaybackAllowed = foregroundPlaybackAllowed,
                    onClose = { finishPlayer() },
                    onEnterPip = { enterPlayerPip() },
                    onAudioOnly = { handoffToBackgroundAudio() },
                    onPlaybackPreferenceChanged = { speed, qualityHeight, seekSeconds ->
                        val latest = repository.settings.value
                        repository.updateSettings(
                            latest.copy(
                                preferredPlaybackSpeed = speed.coerceIn(0.25f, 2.0f),
                                preferredPlaybackQualityHeight = qualityHeight.coerceAtLeast(0),
                                seekIntervalSeconds = seekSeconds.coerceIn(5, 60)
                            )
                        )
                    },
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
                }
                if (showGoogleAuthorizationProblem) {
                    GoogleAuthorizationProblemDialog(
                        message = googleAuthStatus.message,
                        registrationInfo = googleSession.oauthRegistrationInfo(),
                        onRetry = {
                            showGoogleAuthorizationProblem = false
                            onGoogleAccountClick()
                        },
                        onDismiss = { showGoogleAuthorizationProblem = false }
                    )
                }

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
                        onPictureInPicture = {
                            showPlaybackExitChoices = false
                            playbackBackCoordinator.onChooserDismissed()
                            enterPlayerPip()
                        },
                        onTimeout = {
                            showPlaybackExitChoices = false
                            playbackBackCoordinator.onChooserDismissed()
                        },
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
        com.drivedeck.tubedeck.player.PlaybackStartupTelemetry.markTap(media.mediaUri)
        YoutubeStreamResolver.prefetch(applicationContext, media.mediaUri)
        replaceMedia(media, recordWatch = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when (intent.action) {
            PlaybackPipActions.ACTION_EXPAND_PLAYER -> {
                playbackBackDecisionJob?.cancel()
                playbackBackDecisionJob = null
                pipChoicePending = false
                NativePipSessionBridge.activate(playbackSessionId)
                showParentPipChooser()
                return
            }
            PlaybackPipActions.ACTION_AUDIO_ONLY -> {
                pipChoicePending = false
                handoffToBackgroundAudio()
                return
            }
            PlaybackPipActions.ACTION_CLOSE -> {
                pipChoicePending = false
                closePlaybackCompletely()
                return
            }
        }
        val incomingSessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        initialPlaybackPositionMs = intent.getLongExtra(EXTRA_START_POSITION_MS, 0L).coerceAtLeast(0L)
        if (!PlaybackSessionRegistry.isActive(incomingSessionId)) {
            closePlaybackCompletely()
            return
        }
        if (incomingSessionId != playbackSessionId) {
            PlaybackSessionRegistry.close(playbackSessionId)
            playbackSessionId = incomingSessionId
        }
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
        playbackBackCoordinator.onNewMedia()
        showPlaybackExitChoices = false
        closingPlayer = false
        playerActive = true
        currentPlaybackPositionMs = initialPlaybackPositionMs
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

        dispatchPlaybackBackAction(
            playbackBackCoordinator.onBack(
                nowMs = SystemClock.elapsedRealtime(),
                isInPip = isPipMode
            )
        )
    }

    private fun dispatchPlaybackBackAction(action: PlaybackBackAction) {
        when (action) {
            PlaybackBackAction.ENTER_PIP -> {
                playbackBackDecisionJob?.cancel()
                playbackBackDecisionJob = null
                enterPlayerPip()
            }

            PlaybackBackAction.EXPAND_PIP -> {
                playbackBackDecisionJob?.cancel()
                playbackBackDecisionJob = null
                NativePipSessionBridge.activate(playbackSessionId)
                showParentPipChooser()
            }

            PlaybackBackAction.SHOW_EXIT_CHOOSER -> {
                playbackBackDecisionJob?.cancel()
                playbackBackDecisionJob = null
                showPlaybackExitChoices = true
            }

            PlaybackBackAction.IGNORE -> Unit
        }
    }

    private fun closePlaybackCompletely() {
        if (closingPlayer) return
        pipChoicePending = false
        closingPlayer = true
        playerActive = false
        playbackBackDecisionJob?.cancel()
        playbackBackDecisionJob = null
        playbackBackCoordinator.reset()
        NativePipSessionBridge.clear(playbackSessionId)
        PlaybackSessionRegistry.close(playbackSessionId)
        BackgroundAudioController.stop(applicationContext)
        finish()
    }

    private fun handoffToBackgroundAudio() {
        pipChoicePending = false
        val media = currentMedia
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
                NativePipSessionBridge.clear(playbackSessionId)
                PlaybackSessionRegistry.close(playbackSessionId)
                finish()
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
        pipChoicePending = false
        closingPlayer = true
        playerActive = false
        NativePipSessionBridge.clear(playbackSessionId)
        PlaybackSessionRegistry.close(playbackSessionId)
        finish()
    }

    private fun showParentBehindPip(showChooser: Boolean = false) {
        val intent = Intent(this, MainActivity::class.java).apply {
            if (showChooser) action = PlaybackPipActions.ACTION_EXPAND_PLAYER
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun showParentPipChooser() = showParentBehindPip(showChooser = true)

    private fun enterPlayerPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enteringPip = true
            pipChoicePending = true
            foregroundPlaybackAllowed = true
            val params = PlaybackPipActions.buildParams(
                context = this,
                activityClass = MainActivity::class.java
            )
            setPictureInPictureParams(params)
            val entered = enterPictureInPictureMode(params)
            if (!entered) {
                enteringPip = false
                pipChoicePending = false
                playbackBackCoordinator.onPipEnterFailed()
                showPlaybackExitChoices = true
                Toast.makeText(this, "Picture-in-picture is unavailable", Toast.LENGTH_SHORT).show()
            }
        } else {
            showPlaybackExitChoices = true
        }
    }

    override fun onStart() {
        super.onStart()
        if (!closingPlayer) {
            foregroundPlaybackAllowed = true
            if (playerActive) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!closingPlayer && playerActive) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (!closingPlayer && pendingFreshResumeFromPip) {
            pendingFreshResumeFromPip = false
            resumeGeneration += 1
            foregroundPlaybackAllowed = true
        }
    }

    override fun onStop() {
        if (!isPipMode && !enteringPip) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (!isPipMode && !enteringPip && !closingPlayer) {
            // If Android dismisses PiP/task UI without an explicit Audio choice, never let the
            // Activity's ExoPlayer silently continue as unintended audio-only playback.
            foregroundPlaybackAllowed = false
        }
        super.onStop()
    }

    private fun returnCurrentVideoToWeb() {
        if (closingPlayer) return
        val media = currentMedia
        val url = media.mediaUri
        if (YoutubeUrls.videoId(url) == null) return

        closingPlayer = true
        playerActive = false
        foregroundPlaybackAllowed = false
        NativePipSessionBridge.clear(playbackSessionId)
        PlaybackSessionRegistry.close(playbackSessionId)
        YoutubeStreamResolver.invalidate(url)

        val resumeUrl = YoutubeUrls.withStartPosition(
            url,
            currentPlaybackPositionMs.coerceAtLeast(0L)
        )
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse(resumeUrl)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }

    override fun onDestroy() {
        playbackBackDecisionJob?.cancel()
        playbackBackDecisionJob = null
        playbackBackCoordinator.reset()
        playerActive = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (isFinishing && !isChangingConfigurations) {
            NativePipSessionBridge.clear(playbackSessionId)
            PlaybackSessionRegistry.close(playbackSessionId)
        }
        super.onDestroy()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val wasInPip = isPipMode
        isPipMode = isInPictureInPictureMode
        enteringPip = false
        playbackBackCoordinator.onPipModeChanged(isInPictureInPictureMode)

        if (isInPictureInPictureMode && playerActive && !closingPlayer) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            NativePipSessionBridge.activate(playbackSessionId)
            // Do not launch/reorder MainActivity from the PiP transition callback.
            // Android reveals the existing parent Activity underneath the PiP window naturally.
            // Starting an Activity here races the system PiP animation and caused the observed
            // minimize -> maximize -> minimize bounce on the first Back press.
        } else if (wasInPip && !isInPictureInPictureMode) {
            NativePipSessionBridge.clear(playbackSessionId)
            pipChoicePending = false
        }

        if (wasInPip && !isInPictureInPictureMode && playerActive && !closingPlayer) {
            // Refresh signed media only after the Activity genuinely resumes foreground.
            pendingFreshResumeFromPip = true
        }
    }
}
