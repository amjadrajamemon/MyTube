package com.drivedeck.tubedeck

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Rational
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.drivedeck.tubedeck.auth.GoogleAccountSession
import com.drivedeck.tubedeck.data.model.*
import com.drivedeck.tubedeck.data.repository.TubeDeckRepository
import com.drivedeck.tubedeck.player.YoutubeUrls
import com.drivedeck.tubedeck.player.YtDlpUpdateManager
import com.drivedeck.tubedeck.ui.components.AdBlockShieldSheet
import com.drivedeck.tubedeck.ui.components.DownloadModal
import com.drivedeck.tubedeck.ui.components.GlobalProgressBar
import com.drivedeck.tubedeck.ui.components.GoogleAccountDialog
import com.drivedeck.tubedeck.ui.components.TopNavBar
import com.drivedeck.tubedeck.ui.components.TubeDeckBottomBar
import com.drivedeck.tubedeck.ui.screens.*
import com.drivedeck.tubedeck.ui.theme.TubeDeckBackground
import com.drivedeck.tubedeck.ui.theme.TubeDeckTheme
import java.net.URLEncoder
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ActiveScreen { HOME, MIXES, BROWSER, DOWNLOADS, OFFLINE_VAULT, SETTINGS, PLAYER }

class MainActivity : ComponentActivity() {

    private lateinit var repository: TubeDeckRepository
    private var webViewInstance: WebView? = null
    private var isPipMode by mutableStateOf(false)
    private var playerActive = false
    private var autoPipEnabled = true
    private var inboundUrl by mutableStateOf<String?>(null)
    private var lastNativeLaunchVideoId: String? = null
    private var lastNativeLaunchAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = TubeDeckRepository.get(applicationContext)
        YtDlpUpdateManager.scheduleWeekly(applicationContext)
        inboundUrl = intent?.dataString?.takeIf { YoutubeUrls.videoId(it) != null || YoutubeUrls.playlist(it) }

        setContent {
            TubeDeckTheme {
                val settings by repository.settings.collectAsStateWithLifecycle()
                val googleSession = remember { GoogleAccountSession.get(applicationContext) }
                val googleAccount by googleSession.account.collectAsStateWithLifecycle()
                var showGoogleAccountDialog by remember { mutableStateOf(false) }
                val downloadQueue by repository.downloadQueue.collectAsStateWithLifecycle()
                val offlineMediaList by repository.offlineMediaList.collectAsStateWithLifecycle()
                val downloadHistory by repository.downloadHistory.collectAsStateWithLifecycle()
                val watchHistory by repository.watchHistory.collectAsStateWithLifecycle()
                val homeSuggestions by repository.homeSuggestions.collectAsStateWithLifecycle()
                val sponsorSegments by repository.currentSponsorSegments.collectAsStateWithLifecycle()
                val blockedAdsCount by repository.blockedAdsCount.collectAsStateWithLifecycle()

                var currentScreen by rememberSaveable { mutableStateOf(ActiveScreen.HOME) }
                var previousScreen by rememberSaveable { mutableStateOf(ActiveScreen.HOME) }
                var playerReturnScreen by rememberSaveable { mutableStateOf(ActiveScreen.HOME) }
                var downloadsInitialTab by rememberSaveable { mutableIntStateOf(0) }
                var playingMedia by remember { mutableStateOf<OfflineMedia?>(null) }
                var isBrowserFullscreen by rememberSaveable { mutableStateOf(false) }

                var currentUrl by rememberSaveable { mutableStateOf("https://www.youtube.com/") }
                // URL to restore when returning from the native player. This must remain the
                // search/results page (or other browser page), never the video URL that launched playback.
                var browserReturnUrl by rememberSaveable { mutableStateOf("https://www.youtube.com/") }
                var deckSearchQuery by rememberSaveable { mutableStateOf("") }
                var canGoBack by remember { mutableStateOf(false) }
                var canGoForward by remember { mutableStateOf(false) }

                val onGoogleAccountClick: () -> Unit = {
                    if (googleAccount != null) {
                        showGoogleAccountDialog = true
                    } else {
                        lifecycleScope.launch {
                            googleSession.signIn(this@MainActivity, BuildConfig.GOOGLE_WEB_CLIENT_ID)
                                .onSuccess {
                                    Toast.makeText(this@MainActivity, "Signed in with Google", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure {
                                    Toast.makeText(
                                        this@MainActivity,
                                        it.message ?: "Google sign-in failed",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                    }
                }

                LaunchedEffect(inboundUrl, settings.playerMode) {
                    inboundUrl?.let {
                        currentScreen = ActiveScreen.BROWSER
                        val videoId = repository.extractVideoId(it)
                        if (videoId != null && settings.playerMode == PlayerMode.NATIVE) {
                            repository.fetchSponsorSegments(videoId)
                            launchNativePlayer(OfflineMedia(
                                id = UUID.randomUUID().toString(), title = "Now Playing", videoId = videoId,
                                thumbnailUrl = repository.getVideoThumbnail(videoId), mediaUri = it
                            ))
                        } else {
                            currentUrl = it
                            if (videoId == null) browserReturnUrl = it
                        }
                        inboundUrl = null
                    }
                }
                LaunchedEffect(currentScreen, playingMedia) {
                    if (currentScreen == ActiveScreen.PLAYER && playingMedia == null) currentScreen = ActiveScreen.HOME
                }

                var showDownloadModal by remember { mutableStateOf(false) }
                var downloadModalTargetUrl by remember { mutableStateOf("") }
                var downloadPlaylistMode by remember { mutableStateOf(false) }
                var showAdBlockSheet by remember { mutableStateOf(false) }
                
                SideEffect {
                    playerActive = currentScreen == ActiveScreen.PLAYER || (currentScreen == ActiveScreen.BROWSER && YoutubeUrls.videoId(currentUrl) != null)
                    autoPipEnabled = settings.pipEnabled
                }

                val activeDownloads = downloadQueue.filter { it.status == TaskStatus.DOWNLOADING }
                val hasActiveDownloads = activeDownloads.isNotEmpty()
                val averageProgress = if (hasActiveDownloads) {
                    activeDownloads.map { it.progress }.average().toFloat()
                } else 0f

                BackHandler {
                    when {
                        showAdBlockSheet -> { showAdBlockSheet = false }
                        showDownloadModal -> { showDownloadModal = false }
                        currentScreen == ActiveScreen.PLAYER -> {
                            if (settings.pipEnabled) {
                                triggerPip()
                            } else {
                                currentScreen = playerReturnScreen
                                playingMedia = null
                            }
                        }
                        currentScreen == ActiveScreen.BROWSER &&
                            YoutubeUrls.videoId(currentUrl) != null &&
                            settings.pipEnabled -> {
                            triggerPip()
                        }
                        webViewInstance?.canGoBack() == true && currentScreen == ActiveScreen.BROWSER -> {
                            webViewInstance?.goBack()
                        }
                        currentScreen != ActiveScreen.HOME -> {
                            currentScreen = ActiveScreen.HOME
                        }
                        else -> { finish() }
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
                                    canGoBack = canGoBack,
                                    canGoForward = canGoForward,
                                    activeDownloadCount = activeDownloads.size,
                                    blockedAdsCount = blockedAdsCount,
                                    isAdBlockActive = settings.adBlockEnabled,
                                    onBack = { 
                                        if (webViewInstance?.canGoBack() == true) {
                                            webViewInstance?.goBack()
                                        } else {
                                            previousScreen = currentScreen
                                            currentScreen = ActiveScreen.HOME
                                        }
                                    },
                                    onForward = { webViewInstance?.goForward() },
                                    onHome = { 
                                        previousScreen = currentScreen
                                        currentScreen = ActiveScreen.HOME 
                                    },
                                    onReload = { webViewInstance?.reload() },
                                    onNavigate = { input ->
                                        val destination = if (input.startsWith("https://")) {
                                            input
                                        } else {
                                            "https://www.youtube.com/results?search_query=" + URLEncoder.encode(input, "UTF-8")
                                        }
                                        webViewInstance?.loadUrl(destination)
                                        currentUrl = destination
                                    },
                                    onOpenShield = { showAdBlockSheet = true },
                                    onOpenDownloads = {
                                        previousScreen = currentScreen
                                        downloadsInitialTab = 1
                                        currentScreen = ActiveScreen.DOWNLOADS
                                    },
                                    onOpenOffline = {
                                        previousScreen = currentScreen
                                        downloadsInitialTab = 0
                                        currentScreen = ActiveScreen.OFFLINE_VAULT
                                    },
                                    onOpenSettings = { 
                                        previousScreen = currentScreen
                                        currentScreen = ActiveScreen.SETTINGS 
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
                                    previousScreen = currentScreen
                                    if (screen == ActiveScreen.MIXES && deckSearchQuery.isEmpty()) {
                                        deckSearchQuery = "Playlist"
                                    }
                                    if (screen == ActiveScreen.DOWNLOADS) downloadsInitialTab = 1
                                    else if (screen == ActiveScreen.OFFLINE_VAULT) downloadsInitialTab = 0
                                    currentScreen = screen
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
                                            previousScreen = currentScreen
                                            currentUrl = if (currentScreen == ActiveScreen.MIXES) {
                                                "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8") + "&sp=EgIQAw%253D%253D"
                                            } else {
                                                "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")
                                            }
                                            currentScreen = ActiveScreen.BROWSER
                                        }
                                    },
                                    onOpenDownloads = {
                                        previousScreen = currentScreen
                                        downloadsInitialTab = 1
                                        currentScreen = ActiveScreen.DOWNLOADS
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
                                        repository.fetchSponsorSegments(item.videoId)
                                        launchNativePlayer(media)
                                    },
                                    onClearHistory = { repository.clearWatchHistory() }
                                )
                            }

                            ActiveScreen.BROWSER -> {
                                BrowserScreen(
                                    currentUrl = currentUrl,
                                    onUrlChanged = {
                                        currentUrl = it
                                        if (repository.extractVideoId(it) == null) browserReturnUrl = it
                                    },
                                    onNativeVideo = { url ->
                                        val id = repository.extractVideoId(url)
                                        if (id != null && currentScreen == ActiveScreen.BROWSER) {
                                            repository.fetchSponsorSegments(id)
                                            val stableReturnUrl = browserReturnUrl.ifBlank { "https://www.youtube.com/" }
                                            currentUrl = stableReturnUrl
                                            webViewInstance?.stopLoading()
                                            if (webViewInstance?.url != stableReturnUrl) webViewInstance?.loadUrl(stableReturnUrl)
                                            launchNativePlayer(OfflineMedia(
                                                id = UUID.randomUUID().toString(), title = "Now Playing",
                                                videoId = id, thumbnailUrl = repository.getVideoThumbnail(id),
                                                mediaUri = url
                                            ))
                                        }
                                    },
                                    onCanGoBackChanged = { canGoBack = it },
                                    onCanGoForwardChanged = { canGoForward = it },
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
                                    webViewRef = { webViewInstance = it },
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
                                    onClearFinished = { repository.clearFinished() },
                                    onClearHistory = { repository.clearHistory() },
                                    onDeleteOfflineMedia = { repository.deleteOfflineMedia(it) },
                                    onPlayMedia = { media ->
                                        // Keep a dedicated player return target so ordinary Back navigation
                                        // does not become self-referential after returning from playback.
                                        playerReturnScreen = currentScreen
                                        playingMedia = media
                                        currentScreen = ActiveScreen.PLAYER
                                    },
                                    onCustomDownload = { targetUrl ->
                                        downloadModalTargetUrl = targetUrl
                                        downloadPlaylistMode = false
                                        showDownloadModal = true
                                    },
                                    onClose = { currentScreen = previousScreen }
                                ) }
                            }

                            ActiveScreen.SETTINGS -> {
                                SettingsScreen(
                                    currentSettings = settings,
                                    blockedAdsCount = blockedAdsCount,
                                    onSaveSettings = { repository.updateSettings(it) },
                                    onResetAdBlockStats = { repository.resetBlockedAdsCount() },
                                    onClose = { currentScreen = previousScreen }
                                )
                            }

                            ActiveScreen.PLAYER -> {
                                playingMedia?.let { media ->
                                    PlayerScreen(
                                        media = media,
                                        playerMode = settings.playerMode,
                                        pipEnabled = settings.pipEnabled,
                                        onClose = {
                                            currentScreen = playerReturnScreen
                                            playingMedia = null
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
                                            currentScreen = ActiveScreen.PLAYER
                                        },
                                        googleAccount = googleAccount,
                                        onGoogleAccountClick = onGoogleAccountClick
                                    )
                                }
                            }
                        }

                        if (showDownloadModal) {
                            DownloadModal(
                                initialUrl = downloadModalTargetUrl,
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
                }
            }
        }
    }

    private fun launchNativePlayer(media: OfflineMedia) {
        repository.recordWatch(media)
        val videoId = media.videoId.ifBlank { YoutubeUrls.videoId(media.mediaUri).orEmpty() }
        val now = SystemClock.elapsedRealtime()
        if (videoId.isNotBlank() && videoId == lastNativeLaunchVideoId && now - lastNativeLaunchAt < 1_800L) return
        lastNativeLaunchVideoId = videoId.takeIf { it.isNotBlank() }
        lastNativeLaunchAt = now

        val intent = Intent(this, NativePlayerActivity::class.java).apply {
            putExtra(NativePlayerActivity.EXTRA_MEDIA_ID, media.id)
            putExtra(NativePlayerActivity.EXTRA_TITLE, media.title)
            putExtra(NativePlayerActivity.EXTRA_VIDEO_ID, media.videoId)
            putExtra(NativePlayerActivity.EXTRA_THUMBNAIL, media.thumbnailUrl)
            putExtra(NativePlayerActivity.EXTRA_MEDIA_URI, media.mediaUri)
            putExtra(NativePlayerActivity.EXTRA_IS_AUDIO_ONLY, media.isAudioOnly)
            putExtra(NativePlayerActivity.EXTRA_QUALITY_LABEL, media.qualityLabel)
        }
        startActivity(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        inboundUrl = intent.dataString?.takeIf { YoutubeUrls.videoId(it) != null || YoutubeUrls.playlist(it) }
    }

    private fun triggerPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playerActive && autoPipEnabled && !isPipMode) {
            triggerPip()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipMode = isInPictureInPictureMode
    }
}
