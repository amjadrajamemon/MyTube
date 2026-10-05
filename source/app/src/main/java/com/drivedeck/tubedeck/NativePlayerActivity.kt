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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.drivedeck.tubedeck.auth.GoogleAccountSession
import com.drivedeck.tubedeck.auth.YoutubeAuthorizationState
import com.drivedeck.tubedeck.data.model.OfflineMedia
import com.drivedeck.tubedeck.data.model.PlaylistItem
import com.drivedeck.tubedeck.data.model.PlayerMode
import com.drivedeck.tubedeck.data.repository.TubeDeckRepository
import com.drivedeck.tubedeck.ui.screens.PlayerScreen
import com.drivedeck.tubedeck.ui.components.DownloadModal
import com.drivedeck.tubedeck.ui.components.GoogleAccountDialog
import com.drivedeck.tubedeck.ui.components.GoogleAuthorizationProblemDialog
import com.drivedeck.tubedeck.ui.components.GoogleDeviceCodeDialog
import com.drivedeck.tubedeck.ui.components.PlaybackBackChoiceOverlay
import com.drivedeck.tubedeck.player.YoutubeUrls
import com.drivedeck.tubedeck.player.YoutubeStreamResolver
import com.drivedeck.tubedeck.player.BackgroundAudioController
import com.drivedeck.tubedeck.player.NativePipSessionBridge
import com.drivedeck.tubedeck.player.PlaybackBackAction
import com.drivedeck.tubedeck.player.PlaybackBackCoordinator
import com.drivedeck.tubedeck.player.PlaybackBackSequenceTracker
import com.drivedeck.tubedeck.player.ParentPipBackAction
import com.drivedeck.tubedeck.player.PlaybackParentPipBackPolicy
import com.drivedeck.tubedeck.player.PlaybackHost
import com.drivedeck.tubedeck.player.PlaybackHostRouter
import com.drivedeck.tubedeck.player.PlaybackPipActions
import com.drivedeck.tubedeck.player.PlaybackSessionRegistry
import com.drivedeck.tubedeck.ui.theme.TubeDeckTheme
import java.util.UUID
import java.util.ArrayDeque
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
    private var pipExitResolutionJob: Job? = null
    private var showPlaybackExitChoices by mutableStateOf(false)
    private var currentPlaybackPositionMs = 0L
    private var initialPlaybackPositionMs = 0L
    private var playbackSessionId: String = ""
    private var resumeGeneration by mutableStateOf(0)
    private var pauseGeneration by mutableStateOf(0)
    private var foregroundPlaybackAllowed by mutableStateOf(true)
    private var enteringPip = false
    private var pipChoicePending = false
    private var pipExitPendingStop = false
    private var explicitAudioHandoff = false
    private var pendingFreshResumeFromPip = false
    private var playlistLoadJob: Job? = null
    private var activePlaylistId by mutableStateOf<String?>(null)
    private var activePlaylistItems by mutableStateOf<List<PlaylistItem>>(emptyList())
    private var activePlaylistIndex by mutableStateOf(-1)
    private val playbackBackStack = ArrayDeque<OfflineMedia>()
    private val playbackForwardStack = ArrayDeque<OfflineMedia>()
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
                    NativePipSessionBridge.Action.SHOW_CHOOSER -> expandNativePlayerChooser()
                    NativePipSessionBridge.Action.PAUSE -> {
                        pauseGeneration += 1
                        showPlaybackExitChoices = false
                        playbackBackCoordinator.onChooserDismissed()
                    }
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
                    activityClass = NativePlayerActivity::class.java
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
                val googleDevicePrompt by googleSession.devicePrompt.collectAsStateWithLifecycle()
                val googleDeviceStatus by googleSession.deviceStatus.collectAsStateWithLifecycle()
                var showGoogleAccountDialog by remember { mutableStateOf(false) }
                var showGoogleAuthorizationProblem by remember { mutableStateOf(false) }
                var triedExplicitGoogleAddAccount by remember { mutableStateOf(false) }

                fun fallBackToGoogleCodeOrShowProblem() {
                    if (googleSession.isDeviceCodeConfigured()) {
                        showGoogleAuthorizationProblem = false
                        googleSession.startDeviceCodeAuthorization()
                    } else {
                        showGoogleAuthorizationProblem = true
                    }
                }
                val youtubeAuthorizationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    lifecycleScope.launch {
                        googleSession.completeYoutubeAuthorization(
                            this@NativePlayerActivity, result.data, result.resultCode
                        ).onSuccess {
                            Toast.makeText(
                                this@NativePlayerActivity,
                                "Google + YouTube connected",
                                Toast.LENGTH_SHORT
                            ).show()
                        }.onFailure {
                            fallBackToGoogleCodeOrShowProblem()
                        }
                    }
                }

                val googleAuthRecoveryLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == RESULT_OK) {
                        lifecycleScope.launch {
                            googleSession.authorizeYoutubeForCurrentIdentity(this@NativePlayerActivity)
                                .onSuccess { outcome ->
                                    outcome.resolution?.let { request ->
                                            runCatching { youtubeAuthorizationLauncher.launch(request) }
                                                .onFailure { error ->
                                                    googleSession.failYoutubeAuthorization(error)
                                                    fallBackToGoogleCodeOrShowProblem()
                                                }
                                        }
                                    if (outcome.recoveryIntent != null) {
                                        fallBackToGoogleCodeOrShowProblem()
                                    }
                                }
                                .onFailure {
                                    fallBackToGoogleCodeOrShowProblem()
                                }
                        }
                    } else {
                        googleSession.cancelYoutubeAuthorization()
                    }
                }

                val googleAccountChooserLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == RESULT_OK) {
                        googleSession.completeGoogleAccountChooser(result.data)
                            .onSuccess {
                                Toast.makeText(
                                    this@NativePlayerActivity,
                                    "Signed in with Google",
                                    Toast.LENGTH_SHORT
                                ).show()
                                lifecycleScope.launch {
                                    googleSession.authorizeYoutubeForCurrentIdentity(this@NativePlayerActivity)
                                        .onSuccess { outcome ->
                                            outcome.resolution?.let { request ->
                                            runCatching { youtubeAuthorizationLauncher.launch(request) }
                                                .onFailure { error ->
                                                    googleSession.failYoutubeAuthorization(error)
                                                    fallBackToGoogleCodeOrShowProblem()
                                                }
                                        }
                                            outcome.recoveryIntent?.let(googleAuthRecoveryLauncher::launch)
                                        }
                                        .onFailure {
                                            fallBackToGoogleCodeOrShowProblem()
                                        }
                                }
                            }
                            .onFailure {
                                if (!triedExplicitGoogleAddAccount) {
                                    triedExplicitGoogleAddAccount = true
                                } else {
                                    fallBackToGoogleCodeOrShowProblem()
                                }
                            }
                    } else {
                        googleSession.cancelYoutubeAuthorization("Google account selection cancelled")
                    }
                }

                val googleAddAccountLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) {
                    // Android's account Settings activity does not consistently return RESULT_OK.
                    // On any return, reopen the Google account picker once and continue from the
                    // newly added account when present.
                    googleAccountChooserLauncher.launch(
                        googleSession.googleAccountChooserIntent()
                    )
                }

                LaunchedEffect(triedExplicitGoogleAddAccount) {
                    if (triedExplicitGoogleAddAccount) {
                        googleAddAccountLauncher.launch(
                            googleSession.googleAddAccountIntent()
                        )
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
                val onGoogleAccountClick: () -> Unit = {
                    when {
                        googleAuthStatus.state == YoutubeAuthorizationState.CONNECTING -> {
                            googleSession.cancelYoutubeAuthorization()
                        }
                        googleAccount != null && !googleAuthStatus.youtubeAuthorized -> {
                            showGoogleAuthorizationProblem = false
                            lifecycleScope.launch {
                                googleSession.authorizeYoutubeForCurrentIdentity(this@NativePlayerActivity)
                                    .onSuccess { outcome ->
                                        outcome.resolution?.let { request ->
                                            runCatching { youtubeAuthorizationLauncher.launch(request) }
                                                .onFailure { error ->
                                                    googleSession.failYoutubeAuthorization(error)
                                                    fallBackToGoogleCodeOrShowProblem()
                                                }
                                        }
                                        outcome.recoveryIntent?.let(googleAuthRecoveryLauncher::launch)
                                        if (
                                            outcome.resolution == null &&
                                            outcome.recoveryIntent == null &&
                                            googleSession.status.value.youtubeAuthorized
                                        ) {
                                            Toast.makeText(
                                                this@NativePlayerActivity,
                                                "Google + YouTube connected",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                    .onFailure {
                                        fallBackToGoogleCodeOrShowProblem()
                                    }
                            }
                        }

                        googleAccount != null -> {
                            showGoogleAccountDialog = true
                        }

                        googleSession.isIdentityConfigured() -> {
                            showGoogleAuthorizationProblem = false
                            lifecycleScope.launch {
                                googleSession.beginYoutubeAuthorization(this@NativePlayerActivity)
                                    .onSuccess { outcome ->
                                        outcome.profile?.let {
                                            Toast.makeText(
                                                this@NativePlayerActivity,
                                                "Signed in with Google",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        outcome.resolution?.let { request ->
                                            runCatching { youtubeAuthorizationLauncher.launch(request) }
                                                .onFailure { error ->
                                                    googleSession.failYoutubeAuthorization(error)
                                                    fallBackToGoogleCodeOrShowProblem()
                                                }
                                        }
                                        outcome.recoveryIntent?.let(googleAuthRecoveryLauncher::launch)
                                    }
                                    .onFailure {
                                        if (googleSession.account.value != null) {
                                            fallBackToGoogleCodeOrShowProblem()
                                        } else {
                                            triedExplicitGoogleAddAccount = false
                                            googleAccountChooserLauncher.launch(
                                                googleSession.googleAccountChooserIntent()
                                            )
                                        }
                                    }
                            }
                        }

                        else -> {
                            triedExplicitGoogleAddAccount = false
                            googleAccountChooserLauncher.launch(
                                googleSession.googleAccountChooserIntent()
                            )
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                key(media.id) {
                PlayerScreen(
                    media = media,
                    playerMode = com.drivedeck.tubedeck.data.model.PlayerMode.NATIVE,
                    sponsorSegments = sponsorSegments,
                    pipEnabled = settings.pipEnabled,
                    inPictureInPicture = isPipMode,
                    autoLoadEpisodes = settings.autoLoadEpisodes,
                    autoLoadUpNext = settings.autoLoadUpNext,
                    preferredPlaybackQualityHeight = settings.preferredPlaybackQualityHeight,
                    preferredPlaybackSpeed = settings.preferredPlaybackSpeed,
                    seekIntervalSeconds = settings.seekIntervalSeconds,
                    autoRotatePlayback = settings.autoRotatePlayback,
                    initialPositionMs = initialPlaybackPositionMs,
                    resumeGeneration = resumeGeneration,
                    pauseGeneration = pauseGeneration,
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
                    onPreviousItem = when {
                        activePlaylistId != null && activePlaylistIndex > 0 ->
                            { { playPlaylistItem(activePlaylistIndex - 1) } }
                        playbackBackStack.isNotEmpty() ->
                            { { playPreviousItem() } }
                        else -> null
                    },
                    onNextItem = when {
                        activePlaylistId != null &&
                            activePlaylistIndex >= 0 &&
                            activePlaylistIndex < activePlaylistItems.lastIndex ->
                            { { playPlaylistItem(activePlaylistIndex + 1) } }
                        playbackForwardStack.isNotEmpty() ->
                            { { playForwardItem() } }
                        else -> null
                    },
                    onPlaybackEnded = if (
                        activePlaylistId != null &&
                        activePlaylistIndex >= 0 &&
                        activePlaylistIndex < activePlaylistItems.lastIndex
                    ) {
                        { playPlaylistItem(activePlaylistIndex + 1) }
                    } else null,
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
                        onDismiss = { showGoogleAuthorizationProblem = false },
                        onSignInWithCode = if (googleSession.isDeviceCodeConfigured()) {
                            {
                                showGoogleAuthorizationProblem = false
                                googleSession.startDeviceCodeAuthorization()
                            }
                        } else {
                            null
                        }
                    )
                }

                googleDevicePrompt?.let { prompt ->
                    GoogleDeviceCodeDialog(
                        prompt = prompt,
                        status = googleDeviceStatus,
                        onDismiss = { googleSession.cancelDeviceCodeAuthorization() }
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

                }
                if (showPlaybackExitChoices && !isPipMode) {
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
                        pipLabel = "Keep PiP",
                        modifier = Modifier.align(Alignment.CenterHorizontally)
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
        if (currentMedia.mediaUri.isNotBlank() && currentMedia.mediaUri != media.mediaUri) {
            playbackBackStack.addLast(currentMedia)
            while (playbackBackStack.size > 40) playbackBackStack.removeFirst()
            playbackForwardStack.clear()
        }
        com.drivedeck.tubedeck.player.PlaybackStartupTelemetry.markTap(media.mediaUri)
        YoutubeStreamResolver.prefetch(applicationContext, media.mediaUri)
        replaceMedia(media, recordWatch = true)
    }

    private fun playPreviousItem() {
        if (closingPlayer || playbackBackStack.isEmpty()) return
        val target = playbackBackStack.removeLast()
        if (currentMedia.mediaUri.isNotBlank()) {
            playbackForwardStack.addLast(currentMedia)
            while (playbackForwardStack.size > 40) playbackForwardStack.removeFirst()
        }
        com.drivedeck.tubedeck.player.PlaybackStartupTelemetry.markTap(target.mediaUri)
        YoutubeStreamResolver.prefetch(applicationContext, target.mediaUri)
        replaceMedia(target, recordWatch = true)
    }

    private fun playForwardItem() {
        if (closingPlayer || playbackForwardStack.isEmpty()) return
        val target = playbackForwardStack.removeLast()
        if (currentMedia.mediaUri.isNotBlank()) {
            playbackBackStack.addLast(currentMedia)
            while (playbackBackStack.size > 40) playbackBackStack.removeFirst()
        }
        com.drivedeck.tubedeck.player.PlaybackStartupTelemetry.markTap(target.mediaUri)
        YoutubeStreamResolver.prefetch(applicationContext, target.mediaUri)
        replaceMedia(target, recordWatch = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when (intent.action) {
            PlaybackPipActions.ACTION_EXPAND_PLAYER -> {
                playbackBackDecisionJob?.cancel()
                playbackBackDecisionJob = null
                pipExitPendingStop = false
                pipChoicePending = false
                NativePipSessionBridge.clear(playbackSessionId)
                playbackBackCoordinator.forceChooserVisible()
                showPlaybackExitChoices = true
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
        PlaybackBackSequenceTracker.reset()
        showPlaybackExitChoices = false
        closingPlayer = false
        playerActive = true
        currentPlaybackPositionMs = initialPlaybackPositionMs
        currentMedia = media
        syncPlaylistQueue(media)
        if (recordWatch && !media.title.equals("Now Playing", ignoreCase = true)) {
            repository.recordWatch(media)
        }
        val videoId = media.videoId.ifBlank { YoutubeUrls.videoId(media.mediaUri).orEmpty() }
        if (videoId.isNotBlank()) {
            repository.fetchSponsorSegments(videoId)
        }
    }

    private fun syncPlaylistQueue(media: OfflineMedia) {
        val playlistId = YoutubeUrls.playlistId(media.mediaUri)
        val currentVideoId = media.videoId.ifBlank { YoutubeUrls.videoId(media.mediaUri).orEmpty() }
        if (playlistId == null) {
            playlistLoadJob?.cancel()
            playlistLoadJob = null
            activePlaylistId = null
            activePlaylistItems = emptyList()
            activePlaylistIndex = -1
            return
        }

        if (activePlaylistId == playlistId && activePlaylistItems.isNotEmpty()) {
            activePlaylistIndex = activePlaylistItems.indexOfFirst { it.videoId == currentVideoId }
            prefetchNextPlaylistItem()
            return
        }

        playlistLoadJob?.cancel()
        activePlaylistId = playlistId
        activePlaylistItems = emptyList()
        activePlaylistIndex = -1
        playlistLoadJob = lifecycleScope.launch(Dispatchers.IO) {
            val items = runCatching { repository.fetchPlaylistItems(media.mediaUri) }
                .getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                if (activePlaylistId != playlistId || closingPlayer) return@withContext
                activePlaylistItems = items
                activePlaylistIndex = items.indexOfFirst { it.videoId == currentVideoId }
                prefetchNextPlaylistItem()
            }
        }
    }

    private fun prefetchNextPlaylistItem() {
        val playlistId = activePlaylistId ?: return
        val next = activePlaylistItems.getOrNull(activePlaylistIndex + 1) ?: return
        val url = YoutubeUrls.playlistVideoUrl(next.videoId, playlistId) ?: return
        YoutubeStreamResolver.prefetch(applicationContext, url)
    }

    private fun playPlaylistItem(index: Int) {
        if (closingPlayer) return
        val playlistId = activePlaylistId ?: return
        val item = activePlaylistItems.getOrNull(index) ?: return
        val url = YoutubeUrls.playlistVideoUrl(item.videoId, playlistId) ?: return
        val target = OfflineMedia(
            id = UUID.randomUUID().toString(),
            title = item.title.ifBlank { "YouTube video" },
            videoId = item.videoId,
            thumbnailUrl = item.thumbnailUrl.ifBlank {
                repository.getVideoThumbnail(item.videoId)
            },
            mediaUri = url
        )
        initialPlaybackPositionMs = 0L
        currentPlaybackPositionMs = 0L
        com.drivedeck.tubedeck.player.PlaybackStartupTelemetry.markTap(url)
        YoutubeStreamResolver.prefetch(applicationContext, url)
        replaceMedia(target, recordWatch = true)
    }


    private fun handlePlaybackBack() {
        if (!playerActive || closingPlayer) {
            finishPlayer()
            return
        }

        val now = SystemClock.elapsedRealtime()
        val rapidCount = PlaybackBackSequenceTracker.record(now)
        if (rapidCount >= 3) {
            playbackBackDecisionJob?.cancel()
            playbackBackDecisionJob = null
            pauseGeneration += 1
            showPlaybackExitChoices = false
            playbackBackCoordinator.onChooserDismissed()
            PlaybackBackSequenceTracker.reset()
            if (isPipMode) moveTaskToBack(true)
            return
        }

        if (enteringPip) {
            // PiP entry is asynchronous. Do not reinterpret a second rapid Back as a chooser
            // request while Android is still animating the first Back into PiP.
            return
        }

        if (isPipMode) {
            playbackBackDecisionJob?.cancel()
            playbackBackDecisionJob = null
            when (PlaybackParentPipBackPolicy.onBack(rapidCount, showPlaybackExitChoices)) {
                ParentPipBackAction.PAUSE_AND_EXIT -> {
                    pauseGeneration += 1
                    PlaybackBackSequenceTracker.reset()
                    moveTaskToBack(true)
                }
                ParentPipBackAction.KEEP_PIP_AND_EXIT -> {
                    // Slow Back behaves like a normal app exit: keep the existing PiP small.
                    PlaybackBackSequenceTracker.reset()
                    moveTaskToBack(true)
                }
                ParentPipBackAction.SHOW_CHOICES -> {
                    // Only a deliberate quick second Back brings the compact player forward.
                    expandNativePlayerChooser()
                }
            }
            return
        }

        dispatchPlaybackBackAction(
            playbackBackCoordinator.onBack(
                nowMs = now,
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
                expandNativePlayerChooser()
            }

            PlaybackBackAction.SHOW_EXIT_CHOOSER -> {
                playbackBackDecisionJob?.cancel()
                playbackBackDecisionJob = null
                showPlaybackExitChoices = true
            }

            PlaybackBackAction.CLOSE_PLAYBACK -> closePlaybackCompletely()

            PlaybackBackAction.IGNORE -> Unit
        }
    }

    private fun closePlaybackCompletely() {
        if (closingPlayer) return
        pipExitResolutionJob?.cancel()
        pipExitResolutionJob = null
        pipChoicePending = false
        pipExitPendingStop = false
        explicitAudioHandoff = false
        foregroundPlaybackAllowed = false
        pauseGeneration += 1
        closingPlayer = true
        playerActive = false
        playbackBackDecisionJob?.cancel()
        playbackBackDecisionJob = null
        playbackBackCoordinator.reset()
        PlaybackBackSequenceTracker.reset()
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
                explicitAudioHandoff = true
                pipExitPendingStop = false
                closingPlayer = true
                playerActive = false
                NativePipSessionBridge.clear(playbackSessionId)
                PlaybackSessionRegistry.close(playbackSessionId)
                moveTaskToBack(true)
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
        pipExitResolutionJob?.cancel()
        pipExitResolutionJob = null
        pipChoicePending = false
        pipExitPendingStop = false
        explicitAudioHandoff = false
        closingPlayer = true
        playerActive = false
        NativePipSessionBridge.clear(playbackSessionId)
        PlaybackSessionRegistry.close(playbackSessionId)
        BackgroundAudioController.stop(applicationContext)
        finish()
    }

    private fun expandNativePlayerChooser() {
        if (closingPlayer) return
        val intent = Intent(this, NativePlayerActivity::class.java).apply {
            action = PlaybackPipActions.ACTION_EXPAND_PLAYER
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun enterPlayerPip() {
        if (isPipMode || enteringPip || closingPlayer) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enteringPip = true
            pipChoicePending = true
            pipExitPendingStop = false
            foregroundPlaybackAllowed = true
            val params = PlaybackPipActions.buildParams(
                context = this,
                activityClass = NativePlayerActivity::class.java
            )
            val entered = runCatching {
                setPictureInPictureParams(params)
                enterPictureInPictureMode(params)
            }.getOrDefault(false)
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
        if (!closingPlayer && pipExitPendingStop) {
            // Reaching RESUMED means PiP was expanded, not dismissed by the system X.
            // Cancel the close watchdog and clear the bridge only after expansion is confirmed.
            pipExitResolutionJob?.cancel()
            pipExitResolutionJob = null
            pipExitPendingStop = false
            pipChoicePending = false
            NativePipSessionBridge.clear(playbackSessionId)
        }
        if (!closingPlayer && pendingFreshResumeFromPip) {
            pendingFreshResumeFromPip = false
            // Expanding PiP uses the same player, stream, position and user pause state.
            // Refreshing every signed stream here caused blank frames and needless rebuffering.
            foregroundPlaybackAllowed = true
        }
    }

    override fun onStop() {
        if (!isPipMode && !enteringPip) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (pipExitPendingStop && !closingPlayer) {
            // Android's system PiP X does not invoke our RemoteAction. A PiP exit followed by
            // onStop (without onResume) is therefore a dismissal: stop every playback path.
            pipExitPendingStop = false
            foregroundPlaybackAllowed = false
            closePlaybackCompletely()
            super.onStop()
            return
        }
        if (!isPipMode && !enteringPip && !closingPlayer) {
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
        // Preserve the resolved native stream metadata. Switching presentation hosts is not a
        // stream failure; if the user switches back to Native shortly afterward, reuse the cache.
        YoutubeStreamResolver.promoteForHandoffResume(url)

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
        playlistLoadJob?.cancel()
        playlistLoadJob = null
        pipExitResolutionJob?.cancel()
        pipExitResolutionJob = null
        playbackBackDecisionJob?.cancel()
        playbackBackDecisionJob = null
        playbackBackCoordinator.reset()
        playerActive = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (isFinishing && !isChangingConfigurations) {
            NativePipSessionBridge.clear(playbackSessionId)
            PlaybackSessionRegistry.close(playbackSessionId)
            if (!explicitAudioHandoff) {
                BackgroundAudioController.stop(applicationContext)
            }
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
            // Keep the native bridge alive during the exit transition. Android 13+ can deliver
            // the system Close/X PendingIntent after this callback; clearing the bridge here
            // races that broadcast and can leave ExoPlayer playing after the PiP window vanishes.
            pipExitPendingStop = true
            pendingFreshResumeFromPip = true

            pipExitResolutionJob?.cancel()
            pipExitResolutionJob = lifecycleScope.launch {
                // Normal PiP expansion reaches onResume quickly and cancels this watchdog.
                // If neither the close PendingIntent nor onStop arrives (observed on some OEMs),
                // treat a non-resumed PiP exit as dismissal and hard-stop every playback path.
                delay(850L)
                if (
                    pipExitPendingStop &&
                    !isPipMode &&
                    playerActive &&
                    !closingPlayer
                ) {
                    foregroundPlaybackAllowed = false
                    BackgroundAudioController.stop(applicationContext)
                    closePlaybackCompletely()
                }
            }
        }
    }
}


