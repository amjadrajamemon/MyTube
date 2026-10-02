package com.drivedeck.tubedeck

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.drivedeck.tubedeck.player.YoutubeStreamResolver
import com.drivedeck.tubedeck.player.YtDlpUpdateManager
import com.drivedeck.tubedeck.player.BackgroundAudioController
import com.drivedeck.tubedeck.player.PlaybackBackAction
import com.drivedeck.tubedeck.player.PlaybackBackCoordinator
import com.drivedeck.tubedeck.player.PlaybackHost
import com.drivedeck.tubedeck.player.PlaybackHostRouter
import com.drivedeck.tubedeck.player.PlaybackPipActions
import com.drivedeck.tubedeck.player.PlaybackPipExitPolicy
import com.drivedeck.tubedeck.player.PlaybackSessionRegistry
import com.drivedeck.tubedeck.ui.components.AdBlockShieldSheet
import com.drivedeck.tubedeck.ui.components.DownloadModal
import com.drivedeck.tubedeck.ui.components.GlobalProgressBar
import com.drivedeck.tubedeck.ui.components.GoogleAccountDialog
import com.drivedeck.tubedeck.ui.components.GoogleAuthorizationProblemDialog
import com.drivedeck.tubedeck.ui.components.PlaybackBackChoiceOverlay
import com.drivedeck.tubedeck.ui.components.TopNavBar
import com.drivedeck.tubedeck.ui.components.TubeDeckBottomBar
import com.drivedeck.tubedeck.ui.screens.*
import com.drivedeck.tubedeck.ui.theme.TubeDeckBackground
import com.drivedeck.tubedeck.ui.theme.TubeDeckTheme
import java.net.URLEncoder
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ActiveScreen { HOME, MIXES, BROWSER, DOWNLOADS, OFFLINE_VAULT, SETTINGS, PLAYER }

class MainActivity : ComponentActivity() {

    private lateinit var repository: TubeDeckRepository
    private var webViewInstance: WebView? = null
    private var isPipMode by mutableStateOf(false)
    private var playerActive = false
    private var autoPipEnabled = true
    private var webPlaybackActive by mutableStateOf(false)
    private var webPlaybackPositionMs by mutableLongStateOf(0L)
    private var inboundUrl by mutableStateOf<String?>(null)
    private var openDownloadsQueueRequest by mutableStateOf(false)
    private var lastNativeLaunchVideoId: String? = null
    private var lastNativeLaunchAt = 0L
    private var playbackBackDecisionJob: Job? = null
    private var showPlaybackExitChoices by mutableStateOf(false)
    private var pipChoicePending = false
    private var activePlaybackUrl: String = ""
    private var activePlaybackTitle: String = "YouTube audio"
    private var activePlaybackArtwork: String = ""
    private var activePlaybackPositionMs: Long = 0L
    private var closeActivePlaybackUi: (() -> Unit)? = null
    private val playbackBackCoordinator = PlaybackBackCoordinator()
    private val legacyPlaybackBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            handlePlaybackBack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = TubeDeckRepository.get(applicationContext)
        onBackPressedDispatcher.addCallback(this, legacyPlaybackBackCallback)
        YoutubeStreamResolver.prewarm()
        YtDlpUpdateManager.scheduleWeekly(applicationContext)
        inboundUrl = intent?.dataString?.takeIf { YoutubeUrls.videoId(it) != null || YoutubeUrls.playlist(it) }
        openDownloadsQueueRequest = intent?.getBooleanExtra("open_downloads_queue", false) == true

        setContent {
            val settings by repository.settings.collectAsStateWithLifecycle()
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
                var showGoogleAccountDialog by remember { mutableStateOf(false) }
                var showGoogleAuthorizationProblem by remember { mutableStateOf(false) }
                val youtubeAuthorizationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    if (result.resultCode == RESULT_OK) {
                        lifecycleScope.launch {
                            googleSession.completeYoutubeAuthorization(this@MainActivity, result.data)
                                .onSuccess {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "YouTube account connected",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .onFailure {
                                    Toast.makeText(
                                        this@MainActivity,
                                        it.message ?: "YouTube authorization failed",
                                        Toast.LENGTH_LONG
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
                val offlineMediaList by repository.offlineMediaList.collectAsStateWithLifecycle()
                val downloadHistory by repository.downloadHistory.collectAsStateWithLifecycle()
                val watchHistory by repository.watchHistory.collectAsStateWithLifecycle()
                val homeSuggestions by repository.homeSuggestions.collectAsStateWithLifecycle()
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
                var deckSearchQuery by rememberSaveable { mutableStateOf("") }
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
                        entry.browserUrl?.let { url ->
                            currentUrl = url
                            if (repository.extractVideoId(url) == null) browserReturnUrl = url
                        }
                    }
                    appCanGoBack = navigation.canGoBack()
                    appCanGoForward = navigation.canGoForward()
                }

                fun navigateTo(screen: ActiveScreen, browserUrl: String? = null) {
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

                fun navigateBrowserForwardOrApp(): Boolean {
                    if (currentScreen == ActiveScreen.BROWSER && webCanGoForward) {
                        webViewInstance?.goForward()
                        return true
                    }
                    return navigateForward()
                }

                fun openOnlineMedia(media: OfflineMedia) {
                    when (PlaybackHostRouter.choose(settings.playerMode, media.mediaUri)) {
                        PlaybackHost.NATIVE -> launchNativePlayer(media)
                        PlaybackHost.WEB -> {
                            val previousAudio = BackgroundAudioController.takeSnapshotAndStop(applicationContext)
                            val targetUrl =
                                previousAudio
                                    ?.takeIf {
                                        YoutubeUrls.videoId(it.sourceUrl) == YoutubeUrls.videoId(media.mediaUri)
                                    }
                                    ?.let { YoutubeUrls.withStartPosition(media.mediaUri, it.positionMs) }
                                    ?: media.mediaUri
                            navigateTo(ActiveScreen.BROWSER, targetUrl)
                        }
                    }
                }

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
                    } else if (
                        googleAuthStatus.state == YoutubeAuthorizationState.CONFIGURATION_REQUIRED
                    ) {
                        showGoogleAuthorizationProblem = true
                    } else {
                        lifecycleScope.launch {
                            googleSession.beginYoutubeAuthorization(this@MainActivity)
                                .onSuccess { outcome ->
                                    outcome.profile?.let {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "YouTube account connected",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    outcome.resolution?.let(youtubeAuthorizationLauncher::launch)
                                }
                                .onFailure {
                                    Toast.makeText(
                                        this@MainActivity,
                                        it.message ?: "YouTube authorization failed",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
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

                LaunchedEffect(inboundUrl, settings.playerMode) {
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
                        } else {
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

                    // ComponentActivity bridges this dispatcher to the modern Android back
                    // dispatcher on API 33+, so one path handles gesture and 3-button navigation.
                    legacyPlaybackBackCallback.isEnabled = playerActive

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
                                val destination = browserReturnUrl.ifBlank { "https://www.youtube.com/" }
                                currentUrl = destination
                                replaceCurrentRoute(ActiveScreen.BROWSER, destination)
                                webViewInstance?.stopLoading()
                                if (webViewInstance?.url != destination) {
                                    webViewInstance?.loadUrl(destination)
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
                BackHandler(enabled = !playerActive && !isBrowserFullscreen) {
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
                        if (!isPipMode && currentScreen == ActiveScreen.BROWSER && !isBrowserFullscreen) {
                            Column {
                                TopNavBar(
                                    currentUrl = currentUrl,
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
                                            "https://www.youtube.com/results?search_query=" + URLEncoder.encode(input, "UTF-8")
                                        }
                                        navigateTo(ActiveScreen.BROWSER, destination)
                                        webViewInstance?.loadUrl(destination)
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
                        if (!isPipMode && currentScreen != ActiveScreen.PLAYER && !isBrowserFullscreen) {
                            TubeDeckBottomBar(
                                currentScreen = currentScreen,
                                activeDownloadCount = activeDownloads.size,
                                blockedAdsCount = blockedAdsCount,
                                onNavigate = { screen ->
                                    if (screen == ActiveScreen.MIXES && deckSearchQuery.isEmpty()) {
                                        deckSearchQuery = "Playlist"
                                    }
                                    if (screen == ActiveScreen.DOWNLOADS) downloadsInitialTab = 1
                                    else if (screen == ActiveScreen.OFFLINE_VAULT) downloadsInitialTab = 0
                                    navigateTo(screen)
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    val contentPadding = if (isPipMode || currentScreen == ActiveScreen.PLAYER || isBrowserFullscreen) PaddingValues(0.dp) else innerPadding
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                            .consumeWindowInsets(contentPadding)
                    ) {
                        when (currentScreen) {
                            ActiveScreen.HOME, ActiveScreen.MIXES -> {
                                DriveDeckScreen(
                                    searchQuery = deckSearchQuery,
                                    onSearchQueryChange = { deckSearchQuery = it },
                                    onExecuteSearch = { query ->
                                        if (query.isNotBlank()) {
                                            val destination = if (currentScreen == ActiveScreen.MIXES) {
                                                "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8") + "&sp=EgIQAw%253D%253D"
                                            } else {
                                                "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")
                                            }
                                            navigateTo(ActiveScreen.BROWSER, destination)
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

                            ActiveScreen.BROWSER -> {
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
                                    onCanGoBackChanged = { webCanGoBack = it },
                                    onCanGoForwardChanged = { webCanGoForward = it },
                                    onQuickDownload = { targetUrl ->
                                        downloadModalTargetUrl = targetUrl
                                        downloadPlaylistMode = false
                                        showDownloadModal = true
                                    },
                                    onSilentDownload = { targetUrl ->
                                        runCatching {
                                            repository.enqueueDownload(targetUrl, mode = settings.instantMode, quality = settings.instantQuality)
                                        }.onSuccess {
                                            Toast.makeText(applicationContext, "Download queued", Toast.LENGTH_SHORT).show()
                                        }.onFailure {
                                            Toast.makeText(applicationContext, it.message ?: "Open a video first", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    settings = settings,
                                    onUpdateFabPosition = { iX, iY, cX, cY ->
                                        repository.updateSettings(settings.copy(fabInstantX = iX, fabInstantY = iY, fabCustomX = cX, fabCustomY = cY))
                                    },
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
                                    },
                                    onFullscreenChanged = { isBrowserFullscreen = it }
                                )
                            }

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
                                    onDeleteOfflineMedia = { repository.deleteOfflineMedia(it) },
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
                                    onPreviewSettings = { previewAppearance = it },
                                    onSaveSettings = {
                                        repository.updateSettings(it)
                                        previewAppearance = null
                                    },
                                    onResetAdBlockStats = { repository.resetBlockedAdsCount() },
                                    onClose = {
                                        previewAppearance = null
                                        if (!navigateBack()) {
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
                                        pipEnabled = settings.pipEnabled,
                    autoLoadEpisodes = settings.autoLoadEpisodes,
                    autoLoadUpNext = settings.autoLoadUpNext,
                                        onClose = {
                                            playingMedia = null
                                            if (!navigateBack()) {
                                            navigation.reset(NavigationEntry(ActiveScreen.HOME))
                                            applyNavigationEntry(navigation.current)
                                        }
                                        },
                                        onEnterPip = { triggerPip() },
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
                                        onPlaybackPositionChanged = { activePlaybackPositionMs = it }
                                    )
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
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        repository.enqueuePlaylist(items, mode, quality)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(applicationContext, "Playlist items queued", Toast.LENGTH_SHORT).show()
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
                                    closeActivePlaybackCompletely()
                                },
                                onAudioOnly = {
                                    showPlaybackExitChoices = false
                                    handoffActivePlaybackToAudio()
                                },
                                onPictureInPicture = {
                                    showPlaybackExitChoices = false
                                    playbackBackCoordinator.onChooserDismissed()
                                    triggerPip()
                                },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            }
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

        // A foreground video supersedes explicit audio-only playback. If it is the same video,
        // discard the resolver entry created before/during the audio handoff so signed CDN URLs
        // cannot poison the new foreground session.
        val previousAudio = BackgroundAudioController.takeSnapshotAndStop(applicationContext)
        val resumeFromAudio =
            previousAudio?.takeIf {
                YoutubeUrls.videoId(it.sourceUrl) == YoutubeUrls.videoId(media.mediaUri)
            }
        if (resumeFromAudio != null) {
            YoutubeStreamResolver.invalidate(media.mediaUri)
        }

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
        startActivity(intent)
    }

    override fun onStart() {
        super.onStart()
        if (::repository.isInitialized) {
            repository.resumeDownloadsAfterSystemTimeout()
        }
    }

    override fun onStop() {
        if (!isPipMode && playerActive) {
            // Web playback must not silently survive task/background transitions. Only explicit
            // Audio-only is allowed to continue after the visible player leaves the foreground.
            webViewInstance?.runCatching {
                evaluateJavascript(
                    "(function(){var v=document.querySelector('video');if(v){v.pause();}return true;})()",
                    null
                )
            }
        }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when (intent.action) {
            PlaybackPipActions.ACTION_EXPAND_PLAYER -> {
                playbackBackDecisionJob?.cancel()
                playbackBackDecisionJob = null
                playbackBackCoordinator.forceChooserVisible()
                showPlaybackExitChoices = true
                return
            }
        }
        if (intent.getBooleanExtra("open_downloads_queue", false)) {
            openDownloadsQueueRequest = true
        }
        inboundUrl = intent.dataString?.takeIf { YoutubeUrls.videoId(it) != null || YoutubeUrls.playlist(it) }
    }

    private fun handlePlaybackBack() {
        if (!playerActive) return
        dispatchPlaybackBackAction(
            playbackBackCoordinator.onBack(
                nowMs = SystemClock.elapsedRealtime(),
                isInPip = isPipMode
            )
        )
    }

    private fun dispatchPlaybackBackAction(action: PlaybackBackAction) {
        when (action) {
            PlaybackBackAction.WAIT_FOR_SECOND_PRESS -> {
                playbackBackDecisionJob?.cancel()
                playbackBackDecisionJob = lifecycleScope.launch {
                    delay(PlaybackBackCoordinator.PIP_ENTRY_DELAY_MS)
                    if (!playerActive) return@launch
                    playbackBackDecisionJob = null
                    dispatchPlaybackBackAction(
                        playbackBackCoordinator.onSinglePressTimeout(
                            canEnterPip =
                                autoPipEnabled &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        )
                    )
                }
            }

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

    private fun closeActivePlaybackCompletely() {
        pipChoicePending = false
        playbackBackDecisionJob?.cancel()
        playbackBackDecisionJob = null
        playbackBackCoordinator.reset()
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

    private fun handoffActivePlaybackToAudio() {
        pipChoicePending = false
        val sourceUrl = activePlaybackUrl
        if (sourceUrl.isBlank()) {
            Toast.makeText(this, "No active video is available for audio-only playback", Toast.LENGTH_SHORT).show()
            return
        }

        fun startAt(positionMs: Long) {
            Toast.makeText(this, "Switching to background audio…", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                BackgroundAudioController.handoff(
                    context = this@MainActivity,
                    sourceUrl = sourceUrl,
                    title = activePlaybackTitle,
                    artworkUrl = activePlaybackArtwork,
                    positionMs = positionMs.coerceAtLeast(0L)
                ).onSuccess {
                    webViewInstance?.runCatching {
                        evaluateJavascript(
                            "(function(){var v=document.querySelector('video');if(v){v.pause();}return true;})()",
                            null
                        )
                    }
                    playerActive = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        finishAndRemoveTask()
                    } else {
                        finishAffinity()
                    }
                }.onFailure {
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
                "(function(){var v=document.querySelector('video');return v?Math.floor(v.currentTime*1000):0;})()"
            ) { value ->
                val parsed = value
                    ?.trim()
                    ?.trim('"')
                    ?.toDoubleOrNull()
                    ?.toLong()
                    ?: activePlaybackPositionMs
                startAt(parsed)
            }
        } else {
            startAt(activePlaybackPositionMs)
        }
    }

    private fun triggerPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pipChoicePending = true
            val params = PlaybackPipActions.buildParams(
                context = this,
                activityClass = MainActivity::class.java
            )
            setPictureInPictureParams(params)
            val entered = enterPictureInPictureMode(params)
            if (!entered) {
                pipChoicePending = false
                playbackBackCoordinator.onPipEnterFailed()
                showPlaybackExitChoices = true
                Toast.makeText(this, "Could not enter picture-in-picture", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val wasInPip = isPipMode
        isPipMode = isInPictureInPictureMode
        playbackBackCoordinator.onPipModeChanged(isInPictureInPictureMode)

        if (
            PlaybackPipExitPolicy.shouldRestoreChoices(
                wasInPip = wasInPip,
                isInPip = isInPictureInPictureMode,
                choicePending = pipChoicePending,
                playerActive = playerActive,
                closing = false
            )
        ) {
            // Device fallback for PiP implementations that consume Back inside the system PiP UI.
            pipChoicePending = false
            playbackBackDecisionJob?.cancel()
            playbackBackDecisionJob = null
            playbackBackCoordinator.forceChooserVisible()
            showPlaybackExitChoices = true
            if (!hasWindowFocus()) {
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        action = PlaybackPipActions.ACTION_EXPAND_PLAYER
                        addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        playbackBackDecisionJob?.cancel()
        playbackBackDecisionJob = null
        playbackBackCoordinator.reset()
        closeActivePlaybackUi = null
        super.onDestroy()
    }
}
