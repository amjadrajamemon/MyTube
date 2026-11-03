package com.drivedeck.tubedeck

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import com.drivedeck.tubedeck.data.repository.FileClipboard
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.webkit.WebView
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.drivedeck.tubedeck.auth.GoogleAccountSession
import com.drivedeck.tubedeck.auth.YoutubeAuthorizationState
import com.drivedeck.tubedeck.data.model.*
import com.drivedeck.tubedeck.data.repository.TubeDeckRepository
import com.drivedeck.tubedeck.player.YoutubeUrls
import com.drivedeck.tubedeck.player.YoutubeSearchPolicy
import com.drivedeck.tubedeck.player.YoutubeStreamResolver
import com.drivedeck.tubedeck.player.YtDlpUpdateManager
import com.drivedeck.tubedeck.player.BackgroundAudioController
import com.drivedeck.tubedeck.player.BackgroundAudioPlaybackService
import com.drivedeck.tubedeck.player.NativePipSessionBridge
import com.drivedeck.tubedeck.player.PlaybackBackAction
import com.drivedeck.tubedeck.player.ParentPipBackAction
import com.drivedeck.tubedeck.player.PlaybackParentPipBackPolicy
import com.drivedeck.tubedeck.player.PlaybackBackCoordinator
import com.drivedeck.tubedeck.player.PlaybackBackSequenceTracker
import com.drivedeck.tubedeck.player.PlaybackHost
import com.drivedeck.tubedeck.player.PlaybackHostRouter
import com.drivedeck.tubedeck.player.PlaybackPipActions
import com.drivedeck.tubedeck.player.PlaybackPipCommandBus
import com.drivedeck.tubedeck.player.PlaybackPipExitPolicy
import com.drivedeck.tubedeck.player.PlaybackSessionRegistry
import com.drivedeck.tubedeck.ui.components.AdBlockShieldSheet
import com.drivedeck.tubedeck.ui.components.DownloadModal
import com.drivedeck.tubedeck.ui.components.GlobalProgressBar
import com.drivedeck.tubedeck.ui.components.GoogleAccountDialog
import com.drivedeck.tubedeck.ui.components.GoogleAuthorizationProblemDialog
import com.drivedeck.tubedeck.ui.components.GoogleDeviceCodeDialog
import com.drivedeck.tubedeck.ui.components.PlaybackBackChoiceOverlay
import com.drivedeck.tubedeck.ui.components.TopNavBar
import com.drivedeck.tubedeck.ui.components.TubeDeckBottomBar
import com.drivedeck.tubedeck.ui.components.isTvLikeNavigationDevice
import com.drivedeck.tubedeck.ui.components.tvInputHighlight
import com.drivedeck.tubedeck.ui.screens.*
import com.drivedeck.tubedeck.ui.theme.TubeDeckBackground
import com.drivedeck.tubedeck.ui.theme.TubeDeckTheme
import java.net.URLEncoder
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ActiveScreen { HOME, MIXES, BROWSER, DOWNLOADS, OFFLINE_VAULT, SETTINGS, PLAYER }

class MainActivity : ComponentActivity() {

    private lateinit var repository: TubeDeckRepository
    private var webViewInstance: WebView? = null
    private var isPipMode by mutableStateOf(false)
    private var enteringPip = false
    private var playerActive = false
    private var autoPipEnabled = true
    private var lastHomePipEligibility: Boolean? = null
    private var webPlaybackActive by mutableStateOf(false)
    private var webPlaybackPositionMs by mutableLongStateOf(0L)
    private var lastWebResolverWarmVideoId: String? = null
    private var inboundUrl by mutableStateOf<String?>(null)
    private var resumeAudioRequest by mutableIntStateOf(0)
    private var homeRefreshRequest by mutableIntStateOf(0)
    private var openDownloadsQueueRequest by mutableStateOf(false)
    private var lastNativeLaunchVideoId: String? = null
    private var lastNativeLaunchAt = 0L
    private var playbackBackDecisionJob: Job? = null
    private var nativePipBackExitJob: Job? = null
    private var webPipExitResolutionJob: Job? = null
    private var showPlaybackExitChoices by mutableStateOf(false)
    private var pipChoicePending = false
    private var pendingChooserAfterPipExit = false
    private var pipExitPendingStop = false
    private var activePlaybackUrl: String = ""
    private var activePlaybackTitle: String = "YouTube audio"
    private var activePlaybackArtwork: String = ""
    private var activePlaybackPositionMs: Long = 0L
    private var automaticAudioHandoff by mutableStateOf(false)
    private var audioHandoffJob: Job? = null
    private var embeddedPlaybackRequested = false
    private var automaticEmbeddedHandoff = false
    private var closeActivePlaybackUi: (() -> Unit)? = null
    private var tvNavigateBackBehindPip: (() -> Boolean)? = null
    private val playbackBackCoordinator = PlaybackBackCoordinator()
    private val legacyPlaybackBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            handlePlaybackBack()
        }
    }

    // ComponentActivity implements the public Android key dispatch hook through
    // androidx.core. OEM head units sometimes send media keys only to this hook.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // STOP is a hardware-remote escape if the TV shell hides its PiP X.
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            event.keyCode == KeyEvent.KEYCODE_MEDIA_STOP
        ) {
            NativePipSessionBridge.activeSessionId.value?.let { session ->
                NativePipSessionBridge.request(session, NativePipSessionBridge.Action.CLOSE)
                return true
            }
            if (isPipMode && playerActive) {
                closeActivePlaybackCompletely()
                return true
            }
        }
        // Some TV/XY AUTO ROMs send transport keys to the Activity, not MediaSession.
        if (playerActive &&
            com.drivedeck.tubedeck.player.PlaybackHardwareButtonBridge.handle(event)
        ) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        com.drivedeck.tubedeck.player.PlaybackDiagnostics.install(applicationContext)
        enableEdgeToEdge()
        repository = TubeDeckRepository.get(applicationContext)
        // The notification may create this Activity afresh on some car ROMs, instead
        // of delivering onNewIntent. Always restore the existing native task first.
        if (intent?.action == BackgroundAudioPlaybackService.ACTION_RETURN_TO_VIDEO &&
            !returnToExistingNativePlayerIfPresent()) {
            lastNativeLaunchAt = 0L
            resumeAudioRequest += 1
        }

        lifecycleScope.launch {
            PlaybackPipCommandBus.commands.collect { action ->
                // Native PiP is owned by NativePlayerActivity and handled through its session bridge.
                if (NativePipSessionBridge.isActive() || !playerActive) return@collect
                when (action) {
                    PlaybackPipCommandBus.Action.KEEP_PIP -> {
                        showPlaybackExitChoices = false
                        PlaybackBackSequenceTracker.reset()
                        moveTaskToBack(true)
                    }
                    PlaybackPipCommandBus.Action.AUDIO_ONLY -> {
                        pipChoicePending = false
                        handoffActivePlaybackToAudio()
                    }
                    PlaybackPipCommandBus.Action.CLOSE -> {
                        pipChoicePending = false
                        closeActivePlaybackCompletely()
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, legacyPlaybackBackCallback)
        YoutubeStreamResolver.prewarm()
        YtDlpUpdateManager.scheduleWeekly(applicationContext)
        inboundUrl = intent?.dataString?.takeIf { YoutubeUrls.videoId(it) != null || YoutubeUrls.playlist(it) }
        openDownloadsQueueRequest = intent?.getBooleanExtra("open_downloads_queue", false) == true

        setContent {
            com.drivedeck.tubedeck.ui.components.PlaybackBlackoutHost(repository) {
            val settings by repository.settings.collectAsStateWithLifecycle()
            val nativePipSessionId by NativePipSessionBridge.activeSessionId.collectAsStateWithLifecycle()
            var previewAppearance by remember { mutableStateOf<TubeDeckSettings?>(null) }
            val activeAppearance = previewAppearance ?: settings
            TubeDeckTheme(
                fontScale = activeAppearance.fontScale,
                accentArgb = activeAppearance.themeAccentArgb,
                backgroundArgb = activeAppearance.themeBackgroundArgb,
                textArgb = activeAppearance.themeTextArgb
            ) {
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
                            this@MainActivity, result.data, result.resultCode
                        ).onSuccess {
                            Toast.makeText(
                                this@MainActivity,
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
                            googleSession.authorizeYoutubeForCurrentIdentity(this@MainActivity)
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
                                    this@MainActivity,
                                    "Signed in with Google",
                                    Toast.LENGTH_SHORT
                                ).show()
                                lifecycleScope.launch {
                                    googleSession.authorizeYoutubeForCurrentIdentity(this@MainActivity)
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
                val downloadQueue by repository.downloadQueue.collectAsStateWithLifecycle()
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) repository.refreshDownloadNotification()
                }
                val hasLiveDownloadWork = downloadQueue.any {
                    it.status == TaskStatus.QUEUED || it.status == TaskStatus.DOWNLOADING
                }
                LaunchedEffect(hasLiveDownloadWork) {
                    if (
                        hasLiveDownloadWork &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else if (downloadQueue.isNotEmpty()) {
                        repository.refreshDownloadNotification()
                    }
                }
                var fileOperationBusy by remember { mutableStateOf(false) }
                var pendingFileTransfer by remember { mutableStateOf<FileClipboard?>(null) }
                val fileClipboard by repository.fileClipboard.collectAsStateWithLifecycle()
                val destinationFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                    val request = pendingFileTransfer
                    pendingFileTransfer = null
                    if (uri != null && request != null && !fileOperationBusy) {
                        runCatching { contentResolver.takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                        lifecycleScope.launch {
                            fileOperationBusy = true
                            try {
                                val result = repository.transferOfflineFiles(request.ids, uri, request.move)
                                Toast.makeText(this@MainActivity,
                                    "${result.succeeded} ${if (request.move) "moved" else "copied"}" +
                                        if (result.failed > 0) " · ${result.failed} failed: ${result.detail}" else "",
                                    Toast.LENGTH_LONG).show()
                            } finally { fileOperationBusy = false }
                        }
                    }
                }
                fun chooseFileDestination(request: FileClipboard) {
                    if (request.ids.isEmpty() || fileOperationBusy) return
                    pendingFileTransfer = request
                    destinationFolderLauncher.launch(null)
                }

                val offlineMediaList by repository.offlineMediaList.collectAsStateWithLifecycle()
                val downloadHistory by repository.downloadHistory.collectAsStateWithLifecycle()
                val watchHistory by repository.watchHistory.collectAsStateWithLifecycle()
                val recentSearches by repository.recentSearches.collectAsStateWithLifecycle()
                val homeSuggestions by repository.homeSuggestions.collectAsStateWithLifecycle()
                val homeTrending by repository.homeTrending.collectAsStateWithLifecycle()
                val homeLive by repository.homeLive.collectAsStateWithLifecycle()
                val homeLiveStatus by repository.homeLiveStatus.collectAsStateWithLifecycle()
                val videoSearchResults by repository.videoSearchResults.collectAsStateWithLifecycle()
                val videoSearchQuery by repository.videoSearchQuery.collectAsStateWithLifecycle()
                val videoSearchStatus by repository.videoSearchStatus.collectAsStateWithLifecycle()
                val liveSearchResults by repository.liveSearchResults.collectAsStateWithLifecycle()
                val liveSearchQuery by repository.liveSearchQuery.collectAsStateWithLifecycle()
                val liveSearchStatus by repository.liveSearchStatus.collectAsStateWithLifecycle()
                val playlistSearchResults by repository.playlistSearchResults.collectAsStateWithLifecycle()
                val playlistSearchQuery by repository.playlistSearchQuery.collectAsStateWithLifecycle()
                val playlistSearchStatus by repository.playlistSearchStatus.collectAsStateWithLifecycle()
                val homeCountry by repository.homeCountry.collectAsStateWithLifecycle()
                val homeTrendingStatus by repository.homeTrendingStatus.collectAsStateWithLifecycle()
                val sponsorSegments by repository.currentSponsorSegments.collectAsStateWithLifecycle()
                val blockedAdsCount by repository.blockedAdsCount.collectAsStateWithLifecycle()

                var currentScreen by rememberSaveable { mutableStateOf(ActiveScreen.HOME) }
                var downloadsInitialTab by rememberSaveable { mutableIntStateOf(0) }
                var playingMedia by remember { mutableStateOf<OfflineMedia?>(null) }
                var isBrowserFullscreen by rememberSaveable { mutableStateOf(false) }

                var currentUrl by rememberSaveable { mutableStateOf("https://www.youtube.com/") }
                // URL to restore underneath the native player. Native /watch URLs are never
                // inserted into NavigationCoordinator history.
                var browserReturnUrl by rememberSaveable { mutableStateOf("https://www.youtube.com/") }
                var homeSearchQuery by rememberSaveable { mutableStateOf("") }
                var mixesSearchQuery by rememberSaveable { mutableStateOf("Playlist") }
                var browserSessionCreated by rememberSaveable {
                    mutableStateOf(currentScreen == ActiveScreen.BROWSER)
                }
                var browserNavigationGeneration by rememberSaveable { mutableIntStateOf(0) }
                var browserHistoryResetGeneration by rememberSaveable { mutableIntStateOf(0) }
                var desktopTheaterEnabled by rememberSaveable { mutableStateOf(false) }
                var requestedDesktopModeAfterSave by remember { mutableStateOf<Boolean?>(null) }
                val screenStateHolder = rememberSaveableStateHolder()
                val navigation = remember {
                    NavigationCoordinator(
                        NavigationEntry(
                            screen = currentScreen,
                            browserUrl = if (currentScreen == ActiveScreen.BROWSER) currentUrl else null
                        )
                    )
                }
                var appCanGoBack by remember { mutableStateOf(false) }
                var appCanGoForward by remember { mutableStateOf(false) }
                var webCanGoBack by remember { mutableStateOf(false) }
                var webCanGoForward by remember { mutableStateOf(false) }

                fun applyNavigationEntry(entry: NavigationEntry) {
                    if (currentScreen == ActiveScreen.PLAYER && entry.screen != ActiveScreen.PLAYER) {
                        // Navigation away from the in-UI ExoPlayer is a hard playback boundary.
                        // Explicit PiP/audio continuation is handled separately by the playback controls.
                        playingMedia = null
                        activePlaybackPositionMs = 0L
                    }
                    currentScreen = entry.screen
                    if (entry.screen == ActiveScreen.BROWSER) {
                        browserSessionCreated = true
                        entry.browserUrl?.let { url ->
                            currentUrl = url
                            if (repository.extractVideoId(url) == null) browserReturnUrl = url
                        }
                    }
                    appCanGoBack = navigation.canGoBack()
                    appCanGoForward = navigation.canGoForward()
                }

                fun navigateTo(screen: ActiveScreen, browserUrl: String? = null) {
                    if (screen == ActiveScreen.BROWSER && browserUrl != null) {
                        browserNavigationGeneration += 1
                    }
                    val entry = NavigationEntry(
                        screen = screen,
                        browserUrl = if (screen == ActiveScreen.BROWSER) {
                            browserUrl ?: currentUrl
                        } else {
                            null
                        }
                    )
                    applyNavigationEntry(navigation.navigate(entry))
                }

                fun replaceCurrentRoute(screen: ActiveScreen = currentScreen, browserUrl: String? = null) {
                    val entry = NavigationEntry(
                        screen = screen,
                        browserUrl = if (screen == ActiveScreen.BROWSER) {
                            browserUrl ?: currentUrl
                        } else {
                            null
                        }
                    )
                    applyNavigationEntry(navigation.replace(entry))
                }

                fun navigateBack(): Boolean {
                    val entry = navigation.back() ?: return false
                    applyNavigationEntry(entry)
                    return true
                }

                fun navigateForward(): Boolean {
                    val entry = navigation.forward() ?: return false
                    applyNavigationEntry(entry)
                    return true
                }

                fun navigateBrowserBackOrApp(): Boolean {
                    if (currentScreen == ActiveScreen.BROWSER && webCanGoBack) {
                        webViewInstance?.goBack()
                        return true
                    }
                    return navigateBack()
                }

                // The TV Back key navigates the parent UI beneath native PiP.
                SideEffect { tvNavigateBackBehindPip = { navigateBrowserBackOrApp() } }

                fun navigateBrowserForwardOrApp(): Boolean {
                    if (currentScreen == ActiveScreen.BROWSER && webCanGoForward) {
                        webViewInstance?.goForward()
                        return true
                    }
                    return navigateForward()
                }

                fun openOnlineMedia(media: OfflineMedia) {
                    val playbackHost = if (settings.desktopYoutubeUiEnabled) PlaybackHost.WEB
                        else PlaybackHostRouter.choose(settings.playerMode, media.mediaUri)
                    // Preserve the complete watch URL, including list=/mix context. The resolver
                    // canonicalizes internally for stream extraction; the player needs the original
                    // playlist context for queue loading, Previous/Next and continuous playback.
                    when (playbackHost) {
                        PlaybackHost.NATIVE -> launchNativePlayer(media)
                        PlaybackHost.WEB -> {
                            PlaybackSessionRegistry.stopForWeb()
                            pauseBrowserMediaForSwitch()
                            val previousAudio = BackgroundAudioController.takeSnapshotAndStop(applicationContext)
                            val targetUrl =
                                previousAudio
                                    ?.takeIf {
                                        YoutubeUrls.videoId(it.sourceUrl) == YoutubeUrls.videoId(media.mediaUri)
                                    }
                                    ?.let { YoutubeUrls.withStartPosition(media.mediaUri, it.positionMs) }
                                    ?: media.mediaUri
                            if (currentScreen != ActiveScreen.BROWSER) {
                                // A video launched from Home/external intent starts its own Web
                                // session. A video clicked inside Browser keeps the results page
                                // as its real Back destination.
                                browserHistoryResetGeneration += 1
                            }
                            navigateTo(ActiveScreen.BROWSER, targetUrl)
                        }
                    }
                }

                fun openNativePlaylist(url: String) {
                    val playlistId = YoutubeUrls.playlistId(url) ?: return
                    lifecycleScope.launch {
                        val result = runCatching { repository.fetchPlaylistItems(url) }
                        val items = result.getOrNull().orEmpty()
                        val first = items.firstOrNull()
                        if (first == null) {
                            Toast.makeText(
                                this@MainActivity,
                                result.exceptionOrNull()?.message ?: "Playlist or Mix could not be loaded",
                                Toast.LENGTH_LONG
                            ).show()
                            return@launch
                        }
                        val watchUrl = YoutubeUrls.playlistVideoUrl(first.videoId, playlistId)
                            ?: return@launch
                        openOnlineMedia(
                            OfflineMedia(
                                id = UUID.randomUUID().toString(),
                                title = first.title,
                                videoId = first.videoId,
                                thumbnailUrl = first.thumbnailUrl.ifBlank {
                                    repository.getVideoThumbnail(first.videoId)
                                },
                                mediaUri = watchUrl
                            )
                        )
                    }
                }

                LaunchedEffect(settings.desktopYoutubeUiEnabled, currentScreen) {
                    if (settings.desktopYoutubeUiEnabled &&
                        (currentScreen == ActiveScreen.HOME || currentScreen == ActiveScreen.MIXES)
                    ) {
                        val home = "https://www.youtube.com/"
                        navigation.reset(NavigationEntry(ActiveScreen.BROWSER, home))
                        browserNavigationGeneration += 1
                        applyNavigationEntry(navigation.current)
                    }
                }

                LaunchedEffect(currentScreen, homeRefreshRequest, settings.homeSuggestionsEnabled,
                    settings.homeTrendingEnabled, googleAuthStatus.youtubeAuthorized) {
                    if (currentScreen == ActiveScreen.HOME) {
                        repository.refreshHomeFeed(googleSession.currentYoutubeAccessToken())
                    }
                }

                LaunchedEffect(resumeAudioRequest) {
                    if (resumeAudioRequest == 0 || NativePipSessionBridge.isActive() ||
                        PlaybackSessionRegistry.hasActiveSession()) return@LaunchedEffect
                    val audio = BackgroundAudioController.snapshot() ?: return@LaunchedEffect
                    lastNativeLaunchAt = 0L
                    openOnlineMedia(OfflineMedia(
                        id = UUID.randomUUID().toString(),
                        title = audio.title,
                        videoId = YoutubeUrls.videoId(audio.sourceUrl).orEmpty(),
                        thumbnailUrl = audio.artworkUrl,
                        mediaUri = audio.sourceUrl
                    ))
                }

                val onGoogleAccountClick: () -> Unit = {
                    when {
                        googleAuthStatus.state == YoutubeAuthorizationState.CONNECTING -> {
                            googleSession.cancelYoutubeAuthorization()
                        }
                        googleAccount != null && !googleAuthStatus.youtubeAuthorized -> {
                            showGoogleAuthorizationProblem = false
                            lifecycleScope.launch {
                                googleSession.authorizeYoutubeForCurrentIdentity(this@MainActivity)
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
                                                this@MainActivity,
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
                                googleSession.beginYoutubeAuthorization(this@MainActivity)
                                    .onSuccess { outcome ->
                                        outcome.profile?.let {
                                            Toast.makeText(
                                                this@MainActivity,
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

                LaunchedEffect(openDownloadsQueueRequest) {
                    if (openDownloadsQueueRequest) {
                        downloadsInitialTab = 1
                        navigateTo(ActiveScreen.DOWNLOADS)
                        openDownloadsQueueRequest = false
                    }
                }

                LaunchedEffect(inboundUrl, settings.playerMode, settings.desktopYoutubeUiEnabled) {
                    inboundUrl?.let {
                        val videoId = repository.extractVideoId(it)
                        if (videoId != null) {
                            openOnlineMedia(
                                OfflineMedia(
                                    id = UUID.randomUUID().toString(),
                                    title = "Now Playing",
                                    videoId = videoId,
                                    thumbnailUrl = repository.getVideoThumbnail(videoId),
                                    mediaUri = it
                                )
                            )
                        } else if (
                            YoutubeUrls.playlistId(it) != null &&
                            settings.playerMode == PlayerMode.NATIVE && !settings.desktopYoutubeUiEnabled
                        ) {
                            openNativePlaylist(it)
                        } else {
                            browserHistoryResetGeneration += 1
                            navigateTo(ActiveScreen.BROWSER, it)
                        }
                        inboundUrl = null
                    }
                }
                LaunchedEffect(currentScreen) {
                    if (currentScreen != ActiveScreen.SETTINGS) {
                        previewAppearance = null
                    }
                }
                LaunchedEffect(currentScreen, playingMedia) {
                    if (currentScreen == ActiveScreen.PLAYER && playingMedia == null) {
                        if (!navigateBack()) {
                            navigation.reset(NavigationEntry(ActiveScreen.HOME))
                            applyNavigationEntry(navigation.current)
                        }
                    }
                }

                var showDownloadModal by remember { mutableStateOf(false) }
                var downloadModalTargetUrl by remember { mutableStateOf("") }
                var downloadPlaylistMode by remember { mutableStateOf(false) }
                var showAdBlockSheet by remember { mutableStateOf(false) }
                
                SideEffect {
                    playerActive = currentScreen == ActiveScreen.PLAYER ||
                        (currentScreen == ActiveScreen.BROWSER && webPlaybackActive)
                    autoPipEnabled = settings.pipEnabled
                    // Android 12+ can start PiP on the Home gesture before onUserLeaveHint.
                    updateHomePipEligibility(settings.pipEnabled && (
                        (currentScreen == ActiveScreen.PLAYER && embeddedPlaybackRequested) ||
                        (currentScreen == ActiveScreen.BROWSER && webPlaybackActive)
                    ))
                    if (playerActive) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }

                    // ComponentActivity bridges this dispatcher to the modern Android back
                    // dispatcher on API 33+, so one path handles gesture and 3-button navigation.
                    legacyPlaybackBackCallback.isEnabled = playerActive || nativePipSessionId != null

                    closeActivePlaybackUi = {
                        when {
                            currentScreen == ActiveScreen.PLAYER -> {
                                playingMedia = null
                                if (!navigateBack()) {
                                    navigation.reset(NavigationEntry(ActiveScreen.HOME))
                                    applyNavigationEntry(navigation.current)
                                }
                            }
                            currentScreen == ActiveScreen.BROWSER -> {
                                val web = webViewInstance
                                if (web?.canGoBack() == true) {
                                    // Preserve the actual search page DOM, scroll position and
                                    // WebView history. Do not synthesize a fresh search request.
                                    web.goBack()
                                } else {
                                    val destination = browserReturnUrl.ifBlank {
                                        "https://www.youtube.com/"
                                    }
                                    browserNavigationGeneration += 1
                                    currentUrl = destination
                                    replaceCurrentRoute(ActiveScreen.BROWSER, destination)
                                }
                            }
                            else -> Unit
                        }
                    }

                    when {
                        currentScreen == ActiveScreen.PLAYER && playingMedia != null -> {
                            val media = playingMedia!!
                            activePlaybackUrl = media.mediaUri
                            activePlaybackTitle = media.title
                            activePlaybackArtwork = media.thumbnailUrl
                        }
                        currentScreen == ActiveScreen.BROWSER && webPlaybackActive -> {
                            val videoId = YoutubeUrls.videoId(currentUrl).orEmpty()
                            activePlaybackUrl = currentUrl
                            activePlaybackPositionMs = webPlaybackPositionMs
                            activePlaybackTitle = webViewInstance?.title?.takeIf { !it.isNullOrBlank() } ?: "YouTube audio"
                            activePlaybackArtwork = if (videoId.isNotBlank()) {
                                repository.getVideoThumbnail(videoId)
                            } else ""
                        }
                        else -> {
                            activePlaybackUrl = ""
                            activePlaybackPositionMs = 0L
                        }
                    }
                }

                val activeDownloads = downloadQueue.filter { it.status == TaskStatus.DOWNLOADING }
                val hasActiveDownloads = activeDownloads.isNotEmpty()
                val averageProgress = if (hasActiveDownloads) {
                    activeDownloads.map { it.progress }.average().toFloat()
                } else 0f

                // Playback Back is owned exclusively by legacyPlaybackBackCallback while
                // media is active. Keeping this general navigation handler disabled in that state
                // prevents two Compose/Activity callbacks from competing for the same system Back.
                BackHandler(enabled = !playerActive && nativePipSessionId == null && !isBrowserFullscreen) {
                    when {
                        showAdBlockSheet -> showAdBlockSheet = false
                        showDownloadModal -> showDownloadModal = false
                        showGoogleAccountDialog -> showGoogleAccountDialog = false
                        navigateBrowserBackOrApp() -> Unit
                        else -> finish()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = TubeDeckBackground,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = {
                        if (!isPipMode && currentScreen == ActiveScreen.BROWSER &&
                            settings.desktopYoutubeUiEnabled && !isBrowserFullscreen
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = {
                                    navigateTo(ActiveScreen.BROWSER, "https://www.youtube.com/")
                                }) { Text("YouTube") }
                                OutlinedButton(
                                    onClick = { desktopTheaterEnabled = false },
                                    enabled = desktopTheaterEnabled
                                ) { Text("Standard") }
                                OutlinedButton(
                                    onClick = { desktopTheaterEnabled = true },
                                    enabled = !desktopTheaterEnabled
                                ) { Text("Theater") }
                                OutlinedButton(onClick = {
                                    webViewInstance?.evaluateJavascript(
                                        "(function(){var b=document.querySelector('.ytp-fullscreen-button');if(b){b.click();return true;}return false;})()", null
                                    )
                                }) { Text("Fullscreen") }
                                TextButton(onClick = {
                                    downloadsInitialTab = 1
                                    navigateTo(ActiveScreen.DOWNLOADS)
                                }) { Text("Downloads") }
                                TextButton(onClick = { navigateTo(ActiveScreen.SETTINGS) }) {
                                    Text("Settings")
                                }
                                TextButton(onClick = {
                                    repository.updateSettings(settings.copy(desktopYoutubeUiEnabled = false))
                                    navigation.reset(NavigationEntry(ActiveScreen.HOME))
                                    applyNavigationEntry(navigation.current)
                                }) { Text("MyTube UI") }
                            }
                        } else if (!isPipMode && currentScreen == ActiveScreen.BROWSER && !isBrowserFullscreen) {
                            Column {
                                TopNavBar(
                                    currentUrl = currentUrl,
                                    recentSearches = recentSearches,
                                    onRemoveRecentSearch = repository::removeRecentSearch,
                                    onClearRecentSearches = repository::clearRecentSearches,
                                    canGoBack = webCanGoBack || appCanGoBack,
                                    canGoForward = webCanGoForward || appCanGoForward,
                                    activeDownloadCount = activeDownloads.size,
                                    blockedAdsCount = blockedAdsCount,
                                    isAdBlockActive = settings.adBlockEnabled,
                                    onBack = {
                                        if (!navigateBrowserBackOrApp()) {
                                            navigation.reset(NavigationEntry(ActiveScreen.HOME))
                                            applyNavigationEntry(navigation.current)
                                        }
                                    },
                                    onForward = { navigateBrowserForwardOrApp() },
                                    onHome = {
                                        navigateTo(ActiveScreen.HOME)
                                    },
                                    onReload = { webViewInstance?.reload() },
                                    onNavigate = { input ->
                                        val destination = if (input.startsWith("https://")) {
                                            input
                                        } else {
                                            repository.recordSearch(input)
                                            YoutubeSearchPolicy.buildSearchUrl(input)
                                        }
                                        navigateTo(ActiveScreen.BROWSER, destination)
                                    },
                                    onOpenShield = { showAdBlockSheet = true },
                                    onOpenDownloads = {
                                        downloadsInitialTab = 1
                                        navigateTo(ActiveScreen.DOWNLOADS)
                                    },
                                    onOpenOffline = {
                                        downloadsInitialTab = 0
                                        navigateTo(ActiveScreen.OFFLINE_VAULT)
                                    },
                                    onOpenSettings = {
                                        navigateTo(ActiveScreen.SETTINGS)
                                    },
                                    googleAccount = googleAccount,
                                    onGoogleAccountClick = onGoogleAccountClick
                                )
                                GlobalProgressBar(isVisible = hasActiveDownloads, progress = averageProgress)
                            }
                        }
                    },
                    bottomBar = {
                        Column {
                        if (!isPipMode && nativePipSessionId != null &&
                            isTvLikeNavigationDevice(this@MainActivity)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(TubeDeckBackground)
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Mini-player active", modifier = Modifier.weight(1f))
                                OutlinedButton(
                                    onClick = {
                                        NativePipSessionBridge.request(
                                            nativePipSessionId.orEmpty(), NativePipSessionBridge.Action.SHOW_CHOOSER
                                        )
                                    }
                                ) { Text("Expand") }
                                Button(
                                    onClick = {
                                        NativePipSessionBridge.request(
                                            nativePipSessionId.orEmpty(), NativePipSessionBridge.Action.CLOSE
                                        )
                                    },
                                    modifier = Modifier.tvInputHighlight()
                                ) { Text("Close mini-player") }
                            }
                        }
                        if (!isPipMode && currentScreen != ActiveScreen.PLAYER && !isBrowserFullscreen &&
                            !(settings.desktopYoutubeUiEnabled && currentScreen == ActiveScreen.BROWSER)) {
                            TubeDeckBottomBar(
                                currentScreen = currentScreen,
                                activeDownloadCount = activeDownloads.size,
                                blockedAdsCount = blockedAdsCount,
                                onNavigate = { screen ->
                                    if (screen == ActiveScreen.MIXES &&
                                        playlistSearchResults.isEmpty() &&
                                        playlistSearchStatus.isBlank()
                                    ) {
                                        repository.searchPlaylists(mixesSearchQuery)
                                    }
                                    if (screen == ActiveScreen.DOWNLOADS) downloadsInitialTab = 1
                                    else if (screen == ActiveScreen.OFFLINE_VAULT) downloadsInitialTab = 0
                                    navigateTo(screen)
                                }
                            )
                        }
                        }
                    }
                ) { innerPadding ->
                    val contentPadding = if (isPipMode || currentScreen == ActiveScreen.PLAYER || isBrowserFullscreen) PaddingValues(0.dp) else innerPadding
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                            .consumeWindowInsets(contentPadding)
                    ) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (browserSessionCreated) {
                            BrowserScreen(
                                    currentUrl = currentUrl,
                                    onUrlChanged = {
                                        currentUrl = it
                                        if (repository.extractVideoId(it) == null) {
                                            browserReturnUrl = it
                                            if (currentScreen == ActiveScreen.BROWSER) {
                                                replaceCurrentRoute(ActiveScreen.BROWSER, it)
                                            }
                                        }
                                    },
                                    onNativeVideo = { url ->
                                        val id = repository.extractVideoId(url)
                                        if (id != null && currentScreen == ActiveScreen.BROWSER) {
                                            webViewInstance?.stopLoading()
                                            openOnlineMedia(
                                                OfflineMedia(
                                                    id = UUID.randomUUID().toString(),
                                                    title = "Now Playing",
                                                    videoId = id,
                                                    thumbnailUrl = repository.getVideoThumbnail(id),
                                                    mediaUri = url
                                                )
                                            )
                                        }
                                    },
                                    onNativePlaylist = { url ->
                                        if (currentScreen == ActiveScreen.BROWSER) {
                                            webViewInstance?.stopLoading()
                                            openNativePlaylist(url)
                                        }
                                    },
                                    onCanGoBackChanged = { webCanGoBack = it },
                                    onCanGoForwardChanged = { webCanGoForward = it },
                                    settings = if (settings.desktopYoutubeUiEnabled) {
                                        settings.copy(playerMode = PlayerMode.WEB)
                                    } else settings,
                                    desktopTheaterEnabled = desktopTheaterEnabled,
                                    onAdBlocked = { repository.incrementBlockedAds(it) },
                                    webViewRef = {
                                        webViewInstance = it
                                        if (it == null) {
                                            webCanGoBack = false
                                            webCanGoForward = false
                                            webPlaybackActive = false
                                            webPlaybackPositionMs = 0L
                                        }
                                    },
                                    onPlaybackStateChanged = { playing, positionMs ->
                                        webPlaybackActive = playing
                                        webPlaybackPositionMs = positionMs
                                        if (playing) {
                                            val warmUrl = webViewInstance?.url ?: currentUrl
                                            val warmVideoId = YoutubeUrls.videoId(warmUrl)
                                            if (warmVideoId != null && warmVideoId != lastWebResolverWarmVideoId) {
                                                lastWebResolverWarmVideoId = warmVideoId
                                                YoutubeStreamResolver.prefetch(applicationContext, warmUrl)
                                            }
                                        }
                                    },
                                    isActive = currentScreen == ActiveScreen.BROWSER,
                                    navigationGeneration = browserNavigationGeneration,
                                    historyResetGeneration = browserHistoryResetGeneration,
                                    modifier = if (currentScreen == ActiveScreen.BROWSER) {
                                        Modifier.fillMaxSize()
                                    } else {
                                        Modifier.size(0.dp)
                                    },
                                    onFullscreenChanged = { isBrowserFullscreen = it }
                                )
                        }
                        screenStateHolder.SaveableStateProvider(currentScreen.name) {
                        when (currentScreen) {
                            ActiveScreen.HOME, ActiveScreen.MIXES -> {
                                DriveDeckScreen(
                                    recentSearches = recentSearches,
                                    onRemoveRecentSearch = repository::removeRecentSearch,
                                    onClearRecentSearches = repository::clearRecentSearches,
                                    searchQuery = if (currentScreen == ActiveScreen.MIXES) {
                                        mixesSearchQuery
                                    } else {
                                        homeSearchQuery
                                    },
                                    onSearchQueryChange = { value ->
                                        if (currentScreen == ActiveScreen.MIXES) mixesSearchQuery = value
                                        else homeSearchQuery = value
                                    },
                                    onExecuteSearch = { query ->
                                        if (query.isNotBlank()) {
                                            repository.recordSearch(query)
                                            when {
                                                currentScreen == ActiveScreen.MIXES -> {
                                                    repository.clearLiveSearch()
                                                    repository.clearVideoSearch()
                                                    repository.searchPlaylists(query)
                                                }
                                                YoutubeSearchPolicy.isLiveIntent(query) -> {
                                                    // Live discovery stays native so it is not dependent on
                                                    // YouTube's volatile web Live-filter token.
                                                    repository.clearVideoSearch()
                                                    repository.searchLive(
                                                        query,
                                                        googleSession.currentYoutubeAccessToken()
                                                    )
                                                }
                                                isTvLikeNavigationDevice(this@MainActivity) &&
                                                    !settings.desktopYoutubeUiEnabled -> {
                                                    repository.clearLiveSearch()
                                                    repository.searchVideos(query)
                                                }
                                                else -> {
                                                    repository.clearLiveSearch()
                                                    repository.clearVideoSearch()
                                                    browserHistoryResetGeneration += 1
                                                    val destination = YoutubeSearchPolicy.buildSearchUrl(query)
                                                    navigateTo(ActiveScreen.BROWSER, destination)
                                                }
                                            }
                                        }
                                    },
                                    onOpenDownloads = {
                                        downloadsInitialTab = 1
                                        navigateTo(ActiveScreen.DOWNLOADS)
                                    },
                                    googleAccount = googleAccount,
                                    onGoogleAccountClick = onGoogleAccountClick,
                                    watchHistory = watchHistory,
                                    suggestedVideos = homeSuggestions,
                                    trendingVideos = homeTrending,
                                    liveNowVideos = homeLive,
                                    liveNowStatus = homeLiveStatus,
                                    liveSearchResults = liveSearchResults,
                                    liveSearchQuery = liveSearchQuery,
                                    liveSearchStatus = liveSearchStatus,
                                    videoSearchResults = videoSearchResults,
                                    videoSearchQuery = videoSearchQuery,
                                    videoSearchStatus = videoSearchStatus,
                                    onClearVideoSearch = repository::clearVideoSearch,
                                    onOpenWebSearch = { query ->
                                        browserHistoryResetGeneration += 1
                                        navigateTo(ActiveScreen.BROWSER, YoutubeSearchPolicy.buildSearchUrl(query))
                                    },
                                    playlistMode = currentScreen == ActiveScreen.MIXES,
                                    playlistResults = playlistSearchResults,
                                    playlistSearchQuery = playlistSearchQuery,
                                    playlistSearchStatus = playlistSearchStatus,
                                    onOpenPlaylist = { playlist -> openNativePlaylist(playlist.url) },
                                    suggestionsEnabled = settings.homeSuggestionsEnabled,
                                    historyEnabled = settings.homeHistoryEnabled,
                                    trendingEnabled = settings.homeTrendingEnabled,
                                    countryCode = homeCountry,
                                    trendingStatus = homeTrendingStatus,
                                    viewStateKey = currentScreen.name,
                                    onRefreshFeed = { repository.refreshHomeFeed(googleSession.currentYoutubeAccessToken(), force = true) },
                                    onClearLiveSearch = { repository.clearLiveSearch() },
                                    onOpenVideo = { item ->
                                        val media = OfflineMedia(
                                            id = UUID.randomUUID().toString(),
                                            title = item.title,
                                            videoId = item.videoId,
                                            thumbnailUrl = item.thumbnailUrl,
                                            mediaUri = item.url
                                        )
                                        openOnlineMedia(media)
                                    },
                                    onClearHistory = { repository.clearWatchHistory() }
                                )
                            }

                            ActiveScreen.BROWSER -> Unit
                            ActiveScreen.DOWNLOADS, ActiveScreen.OFFLINE_VAULT -> {
                                key(currentScreen) { DownloadsScreen(
                                    downloadQueue = downloadQueue,
                                    offlineMediaList = offlineMediaList,
                                    downloadHistory = downloadHistory,
                                    initialTab = if (currentScreen == ActiveScreen.OFFLINE_VAULT) 0 else 1,
                                    onPauseTask = { repository.pauseTask(it) },
                                    onResumeTask = { repository.resumeTask(it) },
                                    onCancelTask = { repository.cancelTask(it) },
                                    onRetryTask = { repository.retryTask(it) },
                                    onPauseTasks = { repository.pauseTasks(it) },
                                    onResumeOrRetryTasks = { repository.resumeOrRetryTasks(it) },
                                    onDeleteTasks = { repository.deleteTasks(it) },
                                    onPauseAllTasks = { repository.pauseAllTasks() },
                                    onResumeOrRetryAllTasks = { repository.resumeOrRetryAllTasks() },
                                    onDeleteAllTasks = { repository.deleteAllQueueTasks() },
                                    onClearFinished = { repository.clearFinished() },
                                    onClearHistory = { repository.clearHistory() },
                                    clipboardCount = fileClipboard?.ids?.size ?: 0,
                                    fileOperationBusy = fileOperationBusy,
                                    onCopyFiles = { ids, move ->
                                        val request = FileClipboard(ids, move)
                                        repository.copyFilesToClipboard(ids, move)
                                        // Copy/Cut is immediately followed by Android's folder browser so the
                                        // user can choose internal storage, SD card or USB destination directly.
                                        chooseFileDestination(request)
                                    },
                                    onPasteFiles = { fileClipboard?.let { chooseFileDestination(it) } },
                                    onMoveFiles = { chooseFileDestination(FileClipboard(it, true)) },
                                    onDeleteFiles = { ids ->
                                        if (!fileOperationBusy) lifecycleScope.launch {
                                            fileOperationBusy = true
                                            try {
                                                val result = repository.deleteOfflineFiles(ids)
                                                Toast.makeText(this@MainActivity, "${result.succeeded} deleted · ${result.failed} failed", Toast.LENGTH_SHORT).show()
                                            } finally { fileOperationBusy = false }
                                        }
                                    },
                                    onPlayMedia = { media ->
                                        playingMedia = media
                                        navigateTo(ActiveScreen.PLAYER)
                                    },
                                    onCustomDownload = { targetUrl ->
                                        downloadModalTargetUrl = targetUrl
                                        downloadPlaylistMode = false
                                        showDownloadModal = true
                                    },
                                    onClose = {
                                        if (!navigateBack()) {
                                            navigation.reset(NavigationEntry(ActiveScreen.HOME))
                                            applyNavigationEntry(navigation.current)
                                        }
                                    }
                                ) }
                            }

                            ActiveScreen.SETTINGS -> {
                                SettingsScreen(
                                    currentSettings = settings,
                                    blockedAdsCount = blockedAdsCount,
                                    googleAccount = googleAccount,
                                    onGoogleAccountClick = onGoogleAccountClick,
                                    onPreviewSettings = { previewAppearance = it },
                                    onSaveSettings = {
                                        requestedDesktopModeAfterSave = it.desktopYoutubeUiEnabled
                                            .takeIf { selected -> selected != settings.desktopYoutubeUiEnabled }
                                        repository.updateSettings(it)
                                        previewAppearance = null
                                    },
                                    onResetAdBlockStats = { repository.resetBlockedAdsCount() },
                                    onClearWatchHistory = {
                                        repository.clearWatchHistory()
                                        repository.refreshHomeFeed(
                                            googleSession.currentYoutubeAccessToken(),
                                            force = true
                                        )
                                        homeRefreshRequest += 1
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Watch history cleared · Relevant feed refreshed",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onClose = {
                                        previewAppearance = null
                                        val useDesktop = requestedDesktopModeAfterSave ?: settings.desktopYoutubeUiEnabled
                                        if (requestedDesktopModeAfterSave != null || useDesktop) {
                                            val homeScreen = if (useDesktop) ActiveScreen.BROWSER else ActiveScreen.HOME
                                            navigation.reset(
                                                NavigationEntry(
                                                    homeScreen,
                                                    if (homeScreen == ActiveScreen.BROWSER) "https://www.youtube.com/" else null
                                                )
                                            )
                                            if (homeScreen == ActiveScreen.BROWSER) browserNavigationGeneration += 1
                                            requestedDesktopModeAfterSave = null
                                            applyNavigationEntry(navigation.current)
                                        } else if (!navigateBack()) {
                                            navigation.reset(NavigationEntry(ActiveScreen.HOME))
                                            applyNavigationEntry(navigation.current)
                                        }
                                    }
                                )
                            }

                            ActiveScreen.PLAYER -> {
                                playingMedia?.let { media ->
                                    key(media.id) {
                                    PlayerScreen(
                                        media = media,
                                        playerMode = settings.playerMode,
                                        xyAutoCompatibilityMode = settings.xyAutoCompatibilityMode,
                                        sponsorSegments = sponsorSegments,
                                        pipEnabled = settings.pipEnabled,
                    inPictureInPicture = isPipMode,
                                        autoLoadEpisodes = settings.autoLoadEpisodes,
                                        autoLoadUpNext = settings.autoLoadUpNext,
                                        sideBySideEpisodeFeeds = settings.sideBySideEpisodeFeeds,
                                        showRelevantShelf = settings.showRelevantShelf,
                                        showEpisodesShelf = settings.showEpisodesShelf,
                                        playerControllerScale = settings.playerControllerScale,
                                        equalizerTextScale = settings.equalizerTextScale,
                                        playbackContinuationMode = settings.playbackContinuationMode,
                                        onPlaybackContinuationModeChanged = { mode ->
                                            repository.updateSettings(
                                                repository.settings.value.copy(playbackContinuationMode = mode)
                                            )
                                        },
                                        preferredPlaybackQualityHeight = settings.preferredPlaybackQualityHeight,
                                        preferredPlaybackSpeed = settings.preferredPlaybackSpeed,
                                        seekIntervalSeconds = settings.seekIntervalSeconds,
                                        autoRotatePlayback = settings.autoRotatePlayback,
                                        onClose = {
                                            playingMedia = null
                                            if (!navigateBack()) {
                                            navigation.reset(NavigationEntry(ActiveScreen.HOME))
                                            applyNavigationEntry(navigation.current)
                                        }
                                        },
                                        onEnterPip = { triggerPip() },
                                        onAudioOnly = { handoffActivePlaybackToAudio() },
                                        onPlaybackPreferenceChanged = { speed, qualityHeight, seekSeconds ->
                                            val latest = repository.settings.value
                                            repository.updateSettings(
                                                latest.copy(
                                                    preferredPlaybackSpeed = speed.coerceIn(0.25f, 5.0f),
                                                    preferredPlaybackQualityHeight = qualityHeight.coerceAtLeast(0),
                                                    seekIntervalSeconds = seekSeconds.coerceIn(5, 60)
                                                )
                                            )
                                        },
                                        onDownloadVideo = if (repository.extractVideoId(media.mediaUri) != null) {
                                            { downloadModalTargetUrl = media.mediaUri; downloadPlaylistMode = false; showDownloadModal = true }
                                        } else null,
                                        onDownloadPlaylist = if (repository.extractVideoId(media.mediaUri) != null) {
                                            { downloadModalTargetUrl = media.mediaUri; downloadPlaylistMode = true; showDownloadModal = true }
                                        } else null,
                                        onPlayRelated = { related ->
                                            repository.recordWatch(related)
                                            repository.fetchSponsorSegments(related.videoId)
                                            playingMedia = related
                                            replaceCurrentRoute(ActiveScreen.PLAYER)
                                        },
                                        googleAccount = googleAccount,
                                        onGoogleAccountClick = onGoogleAccountClick,
                                        onPlaybackPositionChanged = { activePlaybackPositionMs = it },
                                        onPlaybackRequestedChanged = { embeddedPlaybackRequested = it },
                                        foregroundPlaybackAllowed = !automaticAudioHandoff
                                    )
                                    }
                                }
                            }
                        }
                        }

                        if (showDownloadModal) {
                            DownloadModal(
                                initialUrl = downloadModalTargetUrl,
                                initialTitle = playingMedia?.title.orEmpty(),
                                initialPlaylistMode = downloadPlaylistMode,
                                onDismiss = { showDownloadModal = false },
                                onStartDownload = { url, title, mode, quality ->
                                    runCatching { repository.enqueueDownload(url, title, mode, quality) }
                                        .onSuccess { Toast.makeText(applicationContext, "Download queued", Toast.LENGTH_SHORT).show() }
                                        .onFailure { Toast.makeText(applicationContext, it.message ?: "Invalid video URL", Toast.LENGTH_LONG).show() }
                                },
                                onDownloadPlaylist = { items, mode, quality ->
                                    lifecycleScope.launch {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                repository.enqueuePlaylist(items, mode, quality)
                                            }
                                        }.onSuccess { queued ->
                                            Toast.makeText(
                                                applicationContext,
                                                if (queued > 0) "$queued playlist items queued" else "No playlist items selected",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }.onFailure { error ->
                                            Toast.makeText(
                                                applicationContext,
                                                error.message ?: "Playlist download could not start",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                onDiscoverPlaylistCandidates = { url, title ->
                                    repository.discoverPlaylistCandidates(url, title)
                                },
                                onFetchPlaylistItems = { url ->
                                    repository.fetchPlaylistItems(url)
                                },
                                onFetchDownloadChoices = { url -> repository.fetchDownloadChoices(url) },
                                settings = settings
                            )
                        }

                        if (showAdBlockSheet) {
                            AdBlockShieldSheet(
                                blockedCount = blockedAdsCount,
                                settings = settings,
                                onUpdateSettings = { repository.updateSettings(it) },
                                onResetStats = { repository.resetBlockedAdsCount() },
                                onDismiss = { showAdBlockSheet = false }
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
                            val bridgedSession = nativePipSessionId
                            PlaybackBackChoiceOverlay(
                                onCloseVideo = {
                                    showPlaybackExitChoices = false
                                    if (bridgedSession != null) {
                                        NativePipSessionBridge.request(
                                            bridgedSession,
                                            NativePipSessionBridge.Action.CLOSE
                                        )
                                    } else {
                                        closeActivePlaybackCompletely()
                                    }
                                },
                                onAudioOnly = {
                                    showPlaybackExitChoices = false
                                    if (bridgedSession != null) {
                                        NativePipSessionBridge.request(
                                            bridgedSession,
                                            NativePipSessionBridge.Action.AUDIO_ONLY
                                        )
                                    } else {
                                        handoffActivePlaybackToAudio()
                                    }
                                },
                                onPictureInPicture = {
                                    showPlaybackExitChoices = false
                                    playbackBackCoordinator.onChooserDismissed()
                                    if (bridgedSession == null) {
                                        triggerPip()
                                    } else {
                                        PlaybackBackSequenceTracker.reset()
                                        moveTaskToBack(true)
                                    }
                                },
                                onTimeout = {
                                    showPlaybackExitChoices = false
                                    playbackBackCoordinator.onChooserDismissed()
                                },
                                pipLabel = if (bridgedSession != null) "Keep PiP" else "PiP",
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }
            }
        }
    }

    /** Preserve browser history while silencing any older WebView media. */
    private fun pauseBrowserMediaForSwitch() {
        webViewInstance?.runCatching {
            evaluateJavascript(
                """(function(){
                   document.querySelectorAll('video,audio').forEach(function(v){v.pause();});
                   document.querySelectorAll('iframe').forEach(function(f){
                     try { f.contentWindow.postMessage(JSON.stringify({event:'command',func:'pauseVideo',args:[]}), '*'); } catch(e){}
                   });
                   return true;
                })();""".trimIndent(), null
            )
        }
    }

    private fun launchNativePlayer(media: OfflineMedia) {
        if (
            PlaybackHostRouter.choose(
                repository.settings.value.playerMode,
                media.mediaUri
            ) == PlaybackHost.WEB
        ) {
            inboundUrl = media.mediaUri
            return
        }

        val videoId = media.videoId.ifBlank { YoutubeUrls.videoId(media.mediaUri).orEmpty() }
        val now = SystemClock.elapsedRealtime()
        if (videoId.isNotBlank() && videoId == lastNativeLaunchVideoId && now - lastNativeLaunchAt < 1_800L) return
        lastNativeLaunchVideoId = videoId.takeIf { it.isNotBlank() }
        lastNativeLaunchAt = now

        // Audio-only -> video is a continuation of the same media. Keep the resolver entry
        // warm so returning to the visible player does not perform another YouTube extraction.
        val previousAudio = BackgroundAudioController.takeSnapshotAndStop(applicationContext)
        val resumeFromAudio =
            previousAudio?.takeIf {
                YoutubeUrls.videoId(it.sourceUrl) == YoutubeUrls.videoId(media.mediaUri)
            }
        if (resumeFromAudio != null) {
            YoutubeStreamResolver.promoteForHandoffResume(media.mediaUri)
        }

        pauseBrowserMediaForSwitch()
        // Start the measurable tap-to-first-frame path before extraction or Activity launch.
        com.drivedeck.tubedeck.player.PlaybackStartupTelemetry.markTap(media.mediaUri)
        YoutubeStreamResolver.prefetch(applicationContext, media.mediaUri)
        val playbackSessionId = PlaybackSessionRegistry.open()

        val intent = Intent(this, NativePlayerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(NativePlayerActivity.EXTRA_MEDIA_ID, media.id)
            putExtra(NativePlayerActivity.EXTRA_TITLE, media.title)
            putExtra(NativePlayerActivity.EXTRA_VIDEO_ID, media.videoId)
            putExtra(NativePlayerActivity.EXTRA_THUMBNAIL, media.thumbnailUrl)
            putExtra(NativePlayerActivity.EXTRA_MEDIA_URI, media.mediaUri)
            putExtra(NativePlayerActivity.EXTRA_IS_AUDIO_ONLY, media.isAudioOnly)
            putExtra(NativePlayerActivity.EXTRA_QUALITY_LABEL, media.qualityLabel)
            putExtra(NativePlayerActivity.EXTRA_SESSION_ID, playbackSessionId)
            putExtra(
                NativePlayerActivity.EXTRA_START_POSITION_MS,
                resumeFromAudio?.positionMs ?: 0L
            )
        }
        runCatching { startActivity(intent) }.onFailure { error ->
            PlaybackSessionRegistry.close(playbackSessionId)
            android.util.Log.e("MyTubePlayback", "Native Activity launch failed", error)
            Toast.makeText(
                this,
                "Native player could not start. Select Web player in MyTube Settings.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun returnToExistingNativePlayerIfPresent(): Boolean {
        if (!PlaybackSessionRegistry.hasActiveSession()) return false
        return runCatching {
            startActivity(Intent(this, NativePlayerActivity::class.java).apply {
                action = BackgroundAudioPlaybackService.ACTION_RETURN_TO_VIDEO
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            true
        }.getOrDefault(false)
    }

    override fun onStart() {
        super.onStart()
        if (::repository.isInitialized) {
            repository.resumeDownloadsAfterSystemTimeout()
        }
    }

    override fun onResume() {
        super.onResume()
        if (automaticAudioHandoff) {
            automaticAudioHandoff = false
            audioHandoffJob?.cancel()
            audioHandoffJob = null
            if (automaticEmbeddedHandoff && BackgroundAudioController.snapshot() != null) {
                closeActivePlaybackUi?.invoke()
            } else {
                val audio = BackgroundAudioController.takeSnapshotAndStop(applicationContext)
                val position = audio?.positionMs ?: activePlaybackPositionMs
                webViewInstance?.evaluateJavascript(
                    "(function(){var v=document.querySelector('video');if(v){v.currentTime=${position.coerceAtLeast(0L)}/1000;var p=v.play();if(p&&p.catch){p.catch(function(){});}}})()", null
                )
            }
            automaticEmbeddedHandoff = false
        }
        homeRefreshRequest += 1
        if (BackgroundAudioController.snapshot() != null && !NativePipSessionBridge.isActive() &&
            !PlaybackSessionRegistry.hasActiveSession()) {
            resumeAudioRequest += 1
        }
        legacyPlaybackBackCallback.isEnabled = playerActive || NativePipSessionBridge.isActive()
        if (playerActive) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (pipExitPendingStop && playerActive) {
            // Reaching RESUMED means PiP was expanded rather than dismissed. Cancel the delayed
            // dismissal check so a transient OEM stop cannot remove the whole MyTube task.
            webPipExitResolutionJob?.cancel()
            webPipExitResolutionJob = null
            pipExitPendingStop = false
        }
        if (pendingChooserAfterPipExit) {
            // A normal PiP expansion/resume must never summon the playback chooser by itself.
            // The chooser is reserved for an explicit second Back / explicit PiP action.
            pendingChooserAfterPipExit = false
        }
    }

    override fun onStop() {
        if (!isPipMode) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (pipExitPendingStop && playerActive) {
            // Some OEMs transiently STOP the parent while expanding/restoring PiP. Do not remove
            // the task here; the delayed PiP-exit resolver confirms that no RESUME followed.
            pendingChooserAfterPipExit = false
        }
        if (!isPipMode && !enteringPip && playerActive) {
            // The visible Web player is paused; the foreground audio service owns continuation
            // after Home. Other stops (dialogs, calls, lifecycle changes) do not start audio.
            webViewInstance?.runCatching {
                evaluateJavascript(
                    "(function(){var v=document.querySelector('video');if(v){v.pause();}return true;})()",
                    null
                )
            }
        }
        super.onStop()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isFinishing || isChangingConfigurations || isPipMode || enteringPip ||
            pipExitPendingStop || PlaybackSessionRegistry.hasActiveSession()) return

        // Home must retain the *existing video decoder and audio track* in PiP.
        // Never pause the WebView or switch to a second ExoPlayer just for Home.
        // A paused video should not automatically start playing in the background.
        if (playerActive && (webPlaybackActive || embeddedPlaybackRequested)) {
            if (autoPipEnabled) triggerPip(fallbackToAudioOnFailure = true)
            else handoffActivePlaybackToAudio(automatic = true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when (intent.action) {
            BackgroundAudioPlaybackService.ACTION_RETURN_TO_VIDEO -> {
                // Reopen the current NativePlayerActivity (same session, same stream)
                // rather than replacing it and accidentally closing the whole task.
                if (!returnToExistingNativePlayerIfPresent()) {
                    lastNativeLaunchAt = 0L
                    resumeAudioRequest += 1
                }
                return
            }
            PlaybackPipActions.ACTION_EXPAND_PLAYER -> {
                playbackBackDecisionJob?.cancel()
                playbackBackDecisionJob = null
                pipChoicePending = false
                pipExitPendingStop = false
                NativePipSessionBridge.activeSessionId.value?.let { sessionId ->
                    NativePipSessionBridge.request(sessionId, NativePipSessionBridge.Action.SHOW_CHOOSER)
                    return
                }
                playbackBackCoordinator.forceChooserVisible()
                showPlaybackExitChoices = true
                return
            }
            PlaybackPipActions.ACTION_AUDIO_ONLY -> {
                pipChoicePending = false
                NativePipSessionBridge.activeSessionId.value?.let { sessionId ->
                    NativePipSessionBridge.request(sessionId, NativePipSessionBridge.Action.AUDIO_ONLY)
                    return
                }
                handoffActivePlaybackToAudio()
                return
            }
            PlaybackPipActions.ACTION_CLOSE -> {
                pipChoicePending = false
                NativePipSessionBridge.activeSessionId.value?.let { sessionId ->
                    NativePipSessionBridge.request(sessionId, NativePipSessionBridge.Action.CLOSE)
                    return
                }
                closeActivePlaybackCompletely()
                return
            }
        }
        if (intent.getBooleanExtra("open_downloads_queue", false)) {
            openDownloadsQueueRequest = true
        }
        inboundUrl = intent.dataString?.takeIf { YoutubeUrls.videoId(it) != null || YoutubeUrls.playlist(it) }
    }

    private fun handlePlaybackBack() {
        val now = SystemClock.elapsedRealtime()

        NativePipSessionBridge.activeSessionId.value?.let { sessionId ->
            if (isTvLikeNavigationDevice(this)) {
                // Preserve SmartTube-style background navigation while the mini-player
                // floats; at the navigation root, Back closes playback immediately.
                if (tvNavigateBackBehindPip?.invoke() != true) {
                    NativePipSessionBridge.request(sessionId, NativePipSessionBridge.Action.CLOSE)
                }
                PlaybackBackSequenceTracker.reset()
                return
            }
            val rapidCount = PlaybackBackSequenceTracker.record(now)
            nativePipBackExitJob?.cancel()
            nativePipBackExitJob = null
            when (PlaybackParentPipBackPolicy.onBack(rapidCount, showPlaybackExitChoices)) {
                ParentPipBackAction.PAUSE_AND_EXIT -> {
                    showPlaybackExitChoices = false
                    NativePipSessionBridge.request(sessionId, NativePipSessionBridge.Action.PAUSE)
                    PlaybackBackSequenceTracker.reset()
                    moveTaskToBack(true)
                }
                ParentPipBackAction.KEEP_PIP_AND_EXIT -> {
                    // A further slow Back leaves the app while the existing PiP keeps playing.
                    showPlaybackExitChoices = false
                    PlaybackBackSequenceTracker.reset()
                    moveTaskToBack(true)
                }
                ParentPipBackAction.SHOW_CHOICES -> {
                    // Bring the existing player forward so its choices sit below the video.
                    showPlaybackExitChoices = false
                    NativePipSessionBridge.request(sessionId, NativePipSessionBridge.Action.SHOW_CHOOSER)
                }
            }
            return
        }

        if (!playerActive) return
        if (isPipMode && isTvLikeNavigationDevice(this)) {
            closeActivePlaybackCompletely()
            return
        }

        val rapidCount = PlaybackBackSequenceTracker.record(now)
        if (rapidCount >= 3) {
            playbackBackDecisionJob?.cancel()
            playbackBackDecisionJob = null
            pauseActivePlayback()
            PlaybackBackSequenceTracker.reset()
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
                triggerPip()
            }

            PlaybackBackAction.EXPAND_PIP -> {
                playbackBackDecisionJob?.cancel()
                playbackBackDecisionJob = null
                expandPlayerFromPip()
            }

            PlaybackBackAction.SHOW_EXIT_CHOOSER -> {
                playbackBackDecisionJob?.cancel()
                playbackBackDecisionJob = null
                showPlaybackExitChoices = true
            }

            PlaybackBackAction.CLOSE_PLAYBACK -> closeActivePlaybackCompletely()

            PlaybackBackAction.IGNORE -> Unit
        }
    }

    private fun expandPlayerFromPip() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = PlaybackPipActions.ACTION_EXPAND_PLAYER
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun pauseActivePlayback() {
        NativePipSessionBridge.activeSessionId.value?.let { sessionId ->
            NativePipSessionBridge.request(sessionId, NativePipSessionBridge.Action.PAUSE)
            return
        }
        webViewInstance?.runCatching {
            evaluateJavascript(
                "(function(){var v=document.querySelector('video');if(v){v.pause();return true;}return false;})()",
                null
            )
        }
    }

    private fun closeActivePlaybackCompletely() {
        pipChoicePending = false
        pipExitPendingStop = false
        playbackBackDecisionJob?.cancel()
        playbackBackDecisionJob = null
        nativePipBackExitJob?.cancel()
        nativePipBackExitJob = null
        playbackBackCoordinator.reset()
        PlaybackBackSequenceTracker.reset()
        BackgroundAudioController.stop(applicationContext)
        webViewInstance?.runCatching {
            evaluateJavascript(
                "(function(){var v=document.querySelector('video');if(v){v.pause();v.removeAttribute('src');v.load();}return true;})()",
                null
            )
        }
        playerActive = false
        closeActivePlaybackUi?.invoke()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask()
        } else {
            finishAffinity()
        }
    }

    private fun handoffActivePlaybackToAudio(automatic: Boolean = false) {
        if (audioHandoffJob?.isActive == true || automaticAudioHandoff) return
        pipChoicePending = false
        val sourceUrl = activePlaybackUrl.ifBlank {
            if (automatic) webViewInstance?.url.orEmpty() else ""
        }
        if (sourceUrl.isBlank()) {
            Toast.makeText(this, "No active video is available for audio-only playback", Toast.LENGTH_SHORT).show()
            return
        }

        fun startAt(positionMs: Long) {
            if (isFinishing) return
            automaticAudioHandoff = automatic
            automaticEmbeddedHandoff = automatic && YoutubeUrls.videoId(sourceUrl) == null
            // Pause the Web player before requesting the service so two media engines never play
            // the same audio during the handoff handshake.
            webViewInstance?.runCatching {
                evaluateJavascript(
                    "(function(){var v=document.querySelector('video');if(v){v.pause();}return true;})()",
                    null
                )
            }
            audioHandoffJob = lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
                BackgroundAudioController.handoff(
                    context = this@MainActivity,
                    sourceUrl = sourceUrl,
                    title = activePlaybackTitle,
                    artworkUrl = activePlaybackArtwork,
                    positionMs = positionMs.coerceAtLeast(0L)
                ).onSuccess {
                    playerActive = false
                    if (automatic) {
                        automaticAudioHandoff = false
                        finish()
                        return@onSuccess
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        finishAndRemoveTask()
                    } else {
                        finishAffinity()
                    }
                }.onFailure {
                    val visible = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
                    automaticAudioHandoff = automatic && !visible
                    if (playerActive && visible) {
                        webViewInstance?.runCatching {
                            evaluateJavascript(
                                "(function(){var v=document.querySelector('video');if(v){var p=v.play();if(p&&p.catch){p.catch(function(){});} }return true;})()",
                                null
                            )
                        }
                    }
                    Toast.makeText(
                        this@MainActivity,
                        it.message ?: "Background audio could not start",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        val web = webViewInstance
        if (web != null && YoutubeUrls.videoId(sourceUrl) != null) {
            web.evaluateJavascript(
                "(function(){var v=document.querySelector('video');return v?((!v.paused&&!v.ended)?'1':'0')+'|'+Math.floor(v.currentTime*1000):'0|0';})()"
            ) { value ->
                val parts = value?.trim()?.trim('"')?.split('|').orEmpty()
                // A user-paused video must remain paused when Home is pressed.
                if (automatic && parts.firstOrNull() != "1") return@evaluateJavascript
                val parsed = parts.getOrNull(1)?.toDoubleOrNull()
                    ?.toLong()
                    ?: activePlaybackPositionMs
                startAt(parsed)
            }
        } else {
            startAt(activePlaybackPositionMs)
        }
    }

    private fun updateHomePipEligibility(eligible: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || lastHomePipEligibility == eligible) return
        lastHomePipEligibility = eligible
        runCatching {
            setPictureInPictureParams(
                PlaybackPipActions.buildParams(
                    context = this,
                    activityClass = MainActivity::class.java,
                    autoEnterEnabled = eligible
                )
            )
        }.onFailure { android.util.Log.w("MyTubePlayback", "Auto PiP not available", it) }
    }

    private fun triggerPip(fallbackToAudioOnFailure: Boolean = false) {
        if (isPipMode || enteringPip) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enteringPip = true
            pipChoicePending = true
            pipExitPendingStop = false
            val entered = runCatching {
                val params = PlaybackPipActions.buildParams(
                    context = this,
                    activityClass = MainActivity::class.java,
                    autoEnterEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        (webPlaybackActive || embeddedPlaybackRequested)
                )
                setPictureInPictureParams(params)
                enterPictureInPictureMode(params)
            }.getOrElse {
                android.util.Log.w("MyTubePlayback", "Home PiP entry failed", it)
                false
            }
            if (entered) return
            enteringPip = false
            pipChoicePending = false
            playbackBackCoordinator.onPipEnterFailed()
        }
        // Audio-only is a last resort when the ROM rejects PiP, not the Home default.
        if (fallbackToAudioOnFailure) {
            handoffActivePlaybackToAudio(automatic = true)
        } else {
            showPlaybackExitChoices = true
            Toast.makeText(this, "Picture-in-picture is unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val wasInPip = isPipMode
        isPipMode = isInPictureInPictureMode
        enteringPip = false
        playbackBackCoordinator.onPipModeChanged(isInPictureInPictureMode)

        if (isInPictureInPictureMode && playerActive) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        if (wasInPip && !isInPictureInPictureMode && playerActive) {
            // PiP exit alone is not a request to show the 3-option chooser. Wait briefly for
            // onResume before deciding that the system PiP X was used; OEMs can emit STOP during
            // a normal expansion transition.
            pipChoicePending = false
            pendingChooserAfterPipExit = false
            pipExitPendingStop = true
            webPipExitResolutionJob?.cancel()
            webPipExitResolutionJob = lifecycleScope.launch {
                delay(1_800L)
                if (pipExitPendingStop && !isPipMode && playerActive) {
                    pipExitPendingStop = false
                    closeActivePlaybackCompletely()
                }
            }
        }
    }

    override fun onDestroy() {
        tvNavigateBackBehindPip = null
        playbackBackDecisionJob?.cancel()
        playbackBackDecisionJob = null
        webPipExitResolutionJob?.cancel()
        webPipExitResolutionJob = null
        playbackBackCoordinator.reset()
        closeActivePlaybackUi = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }
}



