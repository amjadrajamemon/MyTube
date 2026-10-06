@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.drivedeck.tubedeck.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.text.style.ClickableSpan
import android.text.util.Linkify
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.drivedeck.tubedeck.auth.GoogleAccountProfile
import com.drivedeck.tubedeck.data.model.OfflineMedia
import com.drivedeck.tubedeck.data.model.PlayerMode
import com.drivedeck.tubedeck.data.model.PlaybackContinuationMode
import com.drivedeck.tubedeck.data.model.PlaylistItem
import com.drivedeck.tubedeck.data.model.SponsorSegment
import com.drivedeck.tubedeck.player.LiveDubEngine
import com.drivedeck.tubedeck.player.LiveDubLanguages
import com.drivedeck.tubedeck.player.LiveDubInputMode
import com.drivedeck.tubedeck.player.PlaybackPcmTapProcessor
import com.drivedeck.tubedeck.player.PlaybackEqualizerController
import com.drivedeck.tubedeck.player.PlaybackStartupTelemetry
import com.drivedeck.tubedeck.player.PlaybackSurfaceGesture
import com.drivedeck.tubedeck.player.PlaybackSurfaceGesturePolicy
import com.drivedeck.tubedeck.player.LiveDubVoiceProfiles
import com.drivedeck.tubedeck.player.YoutubeStreamResolver
import com.drivedeck.tubedeck.player.YoutubeUrls
import com.drivedeck.tubedeck.ui.components.GoogleAccountButton
import com.drivedeck.tubedeck.ui.theme.*
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    media: OfflineMedia,
    playerMode: PlayerMode,
    sponsorSegments: List<SponsorSegment> = emptyList(),
    pipEnabled: Boolean = true,
    autoRotatePlayback: Boolean = true,
    inPictureInPicture: Boolean = false,
    autoLoadEpisodes: Boolean = false,
    autoLoadUpNext: Boolean = false,
    playbackContinuationMode: PlaybackContinuationMode = PlaybackContinuationMode.AUTO_NEXT,
    onPlaybackContinuationModeChanged: (PlaybackContinuationMode) -> Unit = {},
    preferredPlaybackQualityHeight: Int = 0,
    preferredPlaybackSpeed: Float = 1.0f,
    seekIntervalSeconds: Int = 10,
    initialPositionMs: Long = 0L,
    resumeGeneration: Int = 0,
    pauseGeneration: Int = 0,
    foregroundPlaybackAllowed: Boolean = true,
    onClose: () -> Unit,
    onEnterPip: () -> Unit,
    onAudioOnly: () -> Unit = {},
    onPlaybackPreferenceChanged: (Float, Int, Int) -> Unit = { _, _, _ -> },
    onDownloadVideo: (() -> Unit)? = null,
    onDownloadPlaylist: (() -> Unit)? = null,
    onPlayRelated: ((OfflineMedia) -> Unit)? = null,
    onPreviousItem: (() -> Unit)? = null,
    onNextItem: (() -> Unit)? = null,
    onPlaybackEnded: (() -> Unit)? = null,
    playlistItems: List<PlaylistItem> = emptyList(),
    playlistIndex: Int = -1,
    onPlayPlaylistItem: ((Int) -> Unit)? = null,
    googleAccount: GoogleAccountProfile? = null,
    onGoogleAccountClick: () -> Unit = {},
    onPlaybackPositionChanged: (Long) -> Unit = {},
    onResolvedTitleChanged: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Normal playback Back is intentionally owned by the parent Activity so its
    // single/double-Back state machine cannot be bypassed by a nested Compose handler.

    // Sensor rotation applies only while the player is visible, even with Android rotation locked.
    val originalOrientation = remember(activity) {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    DisposableEffect(activity, inPictureInPicture, autoRotatePlayback) {
        activity?.requestedOrientation = if (inPictureInPicture || !autoRotatePlayback) originalOrientation
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR
        val tiltListener = object : OrientationEventListener(context) {
            private var lastQuadrant: Int? = null
            override fun onOrientationChanged(orientation: Int) {
                val quadrant = when (orientation) {
                    in 0..30, in 330..359 -> 0
                    in 60..120 -> 1
                    in 150..210 -> 2
                    in 240..300 -> 3
                    else -> return // Ignore flat/unknown angles and boundaries to prevent jitter.
                }
                if (lastQuadrant != null && lastQuadrant != quadrant) {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                }
                lastQuadrant = quadrant
            }
        }
        if (autoRotatePlayback && !inPictureInPicture && tiltListener.canDetectOrientation()) {
            tiltListener.enable()
        }
        onDispose {
            tiltListener.disable()
            activity?.let { setFullscreen(it, false) }
            activity?.requestedOrientation = originalOrientation
        }
    }

    var useWebFallback by remember(media.id) { mutableStateOf(false) }
    val youtube = YoutubeUrls.videoId(media.mediaUri) != null

    when {
        youtube && (playerMode == PlayerMode.WEB || useWebFallback) -> WebPlayer(
            media = media,
            onClose = onClose,
            onDownloadVideo = onDownloadVideo,
            onDownloadPlaylist = onDownloadPlaylist,
            googleAccount = googleAccount,
            onGoogleAccountClick = onGoogleAccountClick,
            modifier = modifier.fillMaxSize()
        )

        else -> NativeExoPlayer(
            media = media,
            sponsorSegments = sponsorSegments,
            pipEnabled = pipEnabled,
            inPictureInPicture = inPictureInPicture,
            autoLoadEpisodes = autoLoadEpisodes,
            autoLoadUpNext = autoLoadUpNext,
            playbackContinuationMode = playbackContinuationMode,
            onPlaybackContinuationModeChanged = onPlaybackContinuationModeChanged,
            preferredPlaybackQualityHeight = preferredPlaybackQualityHeight,
            preferredPlaybackSpeed = preferredPlaybackSpeed,
            seekIntervalSeconds = seekIntervalSeconds,
            initialPositionMs = initialPositionMs,
            resumeGeneration = resumeGeneration,
            pauseGeneration = pauseGeneration,
            foregroundPlaybackAllowed = foregroundPlaybackAllowed,
            onClose = onClose,
            onEnterPip = onEnterPip,
            onAudioOnly = onAudioOnly,
            onPlaybackPreferenceChanged = onPlaybackPreferenceChanged,
            onWebFallback = { useWebFallback = true },
            onDownloadVideo = onDownloadVideo,
            onDownloadPlaylist = onDownloadPlaylist,
            onPlayRelated = onPlayRelated,
            onPreviousItem = onPreviousItem,
            onNextItem = onNextItem,
            onPlaybackEnded = onPlaybackEnded,
            playlistItems = playlistItems,
            playlistIndex = playlistIndex,
            onPlayPlaylistItem = onPlayPlaylistItem,
            googleAccount = googleAccount,
            onGoogleAccountClick = onGoogleAccountClick,
            onPlaybackPositionChanged = onPlaybackPositionChanged,
            onResolvedTitleChanged = onResolvedTitleChanged,
            modifier = modifier.fillMaxSize()
        )
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun WebPlayer(
    media: OfflineMedia,
    onClose: () -> Unit,
    onDownloadVideo: (() -> Unit)? = null,
    onDownloadPlaylist: (() -> Unit)? = null,
    googleAccount: GoogleAccountProfile? = null,
    onGoogleAccountClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var isCustomViewShowing by remember { mutableStateOf(false) }
    var downloadActionsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(downloadActionsVisible, isCustomViewShowing) {
        if (downloadActionsVisible && !isCustomViewShowing) {
            delay(3800)
            downloadActionsVisible = false
        }
    }

    BackHandler(enabled = isCustomViewShowing) { customViewCallback?.onCustomViewHidden() }
    DisposableEffect(Unit) {
        onDispose {
            customView?.let { view -> (activity?.window?.decorView as? FrameLayout)?.removeView(view) }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    setOnTouchListener { _, event ->
                        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP) {
                            downloadActionsVisible = true
                        }
                        false
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                            request?.url?.scheme != "https"
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            val decor = activity?.window?.decorView as? FrameLayout ?: return
                            if (view == null) return
                            customView?.let { decor.removeView(it) }
                            customView = view
                            customViewCallback = callback
                            isCustomViewShowing = true
                            decor.addView(view, FrameLayout.LayoutParams(-1, -1))
                        }

                        override fun onHideCustomView() {
                            val decor = activity?.window?.decorView as? FrameLayout
                            customView?.let { decor?.removeView(it) }
                            customView = null
                            customViewCallback = null
                            isCustomViewShowing = false
                            downloadActionsVisible = true
                        }
                    }
                    val videoId = YoutubeUrls.videoId(media.mediaUri) ?: media.videoId
                    val embedHtml = """
                        <!doctype html><html><head>
                        <meta name="referrer" content="strict-origin-when-cross-origin">
                        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
                        <style>html,body{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden}iframe{position:fixed;inset:0;width:100%;height:100%;border:0}</style>
                        </head><body>
                        <iframe type="text/html" src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1&enablejsapi=1&rel=0&origin=https%3A%2F%2Fwww.youtube-nocookie.com&widget_referrer=https%3A%2F%2Fwww.youtube-nocookie.com%2F"
                            referrerpolicy="strict-origin-when-cross-origin"
                            allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen></iframe>
                        </body></html>
                    """.trimIndent()
                    loadDataWithBaseURL("https://www.youtube-nocookie.com/", embedHtml, "text/html", "UTF-8", "https://www.youtube-nocookie.com/")
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { view -> view.stopLoading(); view.destroy() }
        )

        if (!isCustomViewShowing) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp)
                    .background(Color.Black.copy(alpha = .55f), CircleShape)
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }

            GoogleAccountButton(
                account = googleAccount,
                onClick = onGoogleAccountClick,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 12.dp, end = 12.dp)
                    .background(Color.Black.copy(alpha = .55f), CircleShape)
            )

            AnimatedVisibility(
                visible = downloadActionsVisible && (onDownloadVideo != null || onDownloadPlaylist != null),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 68.dp, end = 12.dp)
            ) {
                DownloadActionStrip(onDownloadVideo, onDownloadPlaylist)
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun NativeExoPlayer(
    media: OfflineMedia,
    sponsorSegments: List<SponsorSegment>,
    pipEnabled: Boolean,
    inPictureInPicture: Boolean,
    autoLoadEpisodes: Boolean,
    autoLoadUpNext: Boolean,
    playbackContinuationMode: PlaybackContinuationMode,
    onPlaybackContinuationModeChanged: (PlaybackContinuationMode) -> Unit,
    preferredPlaybackQualityHeight: Int,
    preferredPlaybackSpeed: Float,
    seekIntervalSeconds: Int,
    initialPositionMs: Long,
    resumeGeneration: Int,
    pauseGeneration: Int,
    foregroundPlaybackAllowed: Boolean,
    onClose: () -> Unit,
    onEnterPip: () -> Unit,
    onAudioOnly: () -> Unit,
    onPlaybackPreferenceChanged: (Float, Int, Int) -> Unit,
    onWebFallback: () -> Unit,
    onDownloadVideo: (() -> Unit)? = null,
    onDownloadPlaylist: (() -> Unit)? = null,
    onPlayRelated: ((OfflineMedia) -> Unit)? = null,
    onPreviousItem: (() -> Unit)? = null,
    onNextItem: (() -> Unit)? = null,
    onPlaybackEnded: (() -> Unit)? = null,
    playlistItems: List<PlaylistItem> = emptyList(),
    playlistIndex: Int = -1,
    onPlayPlaylistItem: ((Int) -> Unit)? = null,
    googleAccount: GoogleAccountProfile? = null,
    onGoogleAccountClick: () -> Unit = {},
    onPlaybackPositionChanged: (Long) -> Unit = {},
    onResolvedTitleChanged: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val trackSelector = remember(media.id) { DefaultTrackSelector(context) }
    val bandwidthMeter = remember(media.id) { DefaultBandwidthMeter.Builder(context).build() }
    val dataSourceFactory = remember(media.id) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/140.0 Mobile Safari/537.36")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
        DefaultDataSource.Factory(context, httpFactory).setTransferListener(bandwidthMeter)
    }

    var resolved by remember(media.id) { mutableStateOf<YoutubeStreamResolver.ResolvedMedia?>(null) }
    var resolveError by remember(media.id) { mutableStateOf<String?>(null) }
    var selectedHeight by remember(media.id) { mutableIntStateOf(preferredPlaybackQualityHeight.coerceAtLeast(0)) }
    var autoQuality by remember(media.id) { mutableStateOf(preferredPlaybackQualityHeight <= 0) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isFullscreen by rememberSaveable(media.id) { mutableStateOf(false) }
    var resizeMode by rememberSaveable(media.id) { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var playbackSpeed by remember(media.id) { mutableFloatStateOf(preferredPlaybackSpeed.coerceIn(0.25f, 2.0f)) }
    var captionsEnabled by remember { mutableStateOf(true) }
    var captionLanguageTag by rememberSaveable(media.id) { mutableStateOf("auto") }
    var qualityLabel by remember { mutableStateOf("Auto") }
    var hasCaptions by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showQuickActions by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showPlaybackModeCards by remember { mutableStateOf(false) }
    var continuationEpisodeShelf by remember(media.id) { mutableStateOf<YoutubeStreamResolver.DiscoveredPlaylist?>(null) }
    var continuationEpisodesLoading by remember(media.id) { mutableStateOf(false) }
    var continuationEpisodesLoaded by remember(media.id) { mutableStateOf(false) }
    var scrubFraction by remember(media.id) { mutableStateOf<Float?>(null) }
    var gestureEnabled by remember { mutableStateOf(true) }
    var autoHideControls by remember { mutableStateOf(true) }
    var gestureOverlayVisible by remember { mutableStateOf(false) }
    var gestureIcon by remember { mutableStateOf(Icons.AutoMirrored.Filled.VolumeUp) }
    var gestureValue by remember { mutableFloatStateOf(0f) }
    var showComments by rememberSaveable(media.id) { mutableStateOf(false) }
    var liveDubEnabled by rememberSaveable(media.id) { mutableStateOf(false) }
    var dubSourceCode by rememberSaveable(media.id) { mutableStateOf("auto") }
    var dubMethod by rememberSaveable(media.id) { mutableStateOf("auto") }
    var dubTargetCode by rememberSaveable(media.id) { mutableStateOf("ur") }
    var dubVoiceProfile by rememberSaveable(media.id) { mutableStateOf("natural") }
    var dubOriginalVolume by rememberSaveable(media.id) { mutableFloatStateOf(0.18f) }
    var dubStatus by remember(media.id) { mutableStateOf("Live Dub off") }
    var showEqualizer by rememberSaveable(media.id) { mutableStateOf(false) }
    val equalizerController = remember(media.id) { PlaybackEqualizerController(context.applicationContext) }
    var equalizerSnapshot by remember(media.id) { mutableStateOf(equalizerController.snapshot()) }
    var currentCueText by remember(media.id) { mutableStateOf("") }
    var currentCuePresentationUs by remember(media.id) { mutableLongStateOf(-1L) }
    var playbackRecoveryAttempts by remember(media.id) { mutableIntStateOf(0) }
    var playbackRecoveryActive by remember(media.id) { mutableStateOf(false) }
    var firstFrameRendered by remember(media.id) { mutableStateOf(false) }
    var endActionDispatched by remember(media.id) { mutableStateOf(false) }
    var isPreparing by remember(media.id) { mutableStateOf(true) }
    var isBuffering by remember(media.id) { mutableStateOf(false) }
    var lastAutoSwitchMs by remember(media.id) { mutableLongStateOf(0L) }
    var autoStableSamples by remember(media.id) { mutableIntStateOf(0) }
    var autoUpgradeCount by remember(media.id) { mutableIntStateOf(0) }
    var lifecycleResumePending by remember(media.id) { mutableStateOf(false) }
    val playbackScope = rememberCoroutineScope()
    val latestOnPlaybackEnded by rememberUpdatedState(onPlaybackEnded)
    val liveDubEngine = remember(media.id) { LiveDubEngine(context.applicationContext) }
    val tvSafePlayback = remember(context) { isTvLikePlaybackDevice(context) }
    val pcmTap = remember(media.id) {
        PlaybackPcmTapProcessor(
            shouldCapture = liveDubEngine::isAudioCaptureEnabled,
            consumer = liveDubEngine::onPlaybackPcm
        )
    }

    val exoPlayer = remember(media.id) {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(pcmTap))
                    .build()
            }
        }.setEnableDecoderFallback(true)
        // Fast first frame, but keep a deep reservoir once playback is running. The small
        // playback/rebuffer thresholds avoid long blank waits on weak links; the 90 s ceiling
        // gives unstable networks room to download ahead whenever bandwidth is temporarily good.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                18_000,
                90_000,
                500,
                1_250
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .setLoadControl(loadControl)
            .build()
    }
    LaunchedEffect(exoPlayer, preferredPlaybackSpeed) {
        playbackSpeed = preferredPlaybackSpeed.coerceIn(0.25f, 2.0f)
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(exoPlayer, playbackContinuationMode) {
        exoPlayer.repeatMode = if (playbackContinuationMode == PlaybackContinuationMode.LOOP_CURRENT) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
    }

    LaunchedEffect(showPlaybackModeCards) {
        if (showPlaybackModeCards) {
            delay(6_000L)
            showPlaybackModeCards = false
        }
    }

    LaunchedEffect(
        showPlaybackModeCards,
        playbackContinuationMode,
        firstFrameRendered,
        media.mediaUri,
        resolved?.title
    ) {
        if (!youtubeVideo || !firstFrameRendered) return@LaunchedEffect
        if (!showPlaybackModeCards && playbackContinuationMode != PlaybackContinuationMode.EPISODES) {
            return@LaunchedEffect
        }
        if (continuationEpisodesLoaded || continuationEpisodesLoading) return@LaunchedEffect

        val titleHint = resolved?.title?.takeIf { it.isNotBlank() }
            ?: media.title.takeIf { it.isNotBlank() }
            ?: return@LaunchedEffect

        continuationEpisodesLoading = true
        try {
            continuationEpisodeShelf = YoutubeStreamResolver.discoverEpisodeShelf(
                url = media.mediaUri,
                titleHint = titleHint
            )
            continuationEpisodesLoaded = true
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            continuationEpisodeShelf = null
        } finally {
            continuationEpisodesLoading = false
        }
    }

    LaunchedEffect(pauseGeneration) {
        if (pauseGeneration > 0) {
            exoPlayer.pause()
            controlsVisible = true
        }
    }

    val mediaSession = remember(exoPlayer) {
        MediaSession.Builder(context, exoPlayer).setId("drivedeck-${media.id}").build()
    }
    val youtubeVideo = YoutubeUrls.videoId(media.mediaUri) != null
    val compactPortrait = isPortrait && !isFullscreen && !inPictureInPicture && !isPipMode(activity)
    val centerControlDiameter = if (compactPortrait) 52.dp else 60.dp

    fun loadResolvedStream(
        sourceMedia: YoutubeStreamResolver.ResolvedMedia,
        requestedHeight: Int,
        autoSelected: Boolean,
        preservePosition: Boolean
    ) {
        val liveManifest = sourceMedia.liveManifestUrl
            ?.takeIf { sourceMedia.isLive && it.startsWith("https://") }
        if (liveManifest != null) {
            PlaybackStartupTelemetry.markPrepare(media.mediaUri)
            isPreparing = true
            firstFrameRendered = false
            exoPlayer.setMediaItem(
                MediaItem.Builder()
                    .setUri(Uri.parse(liveManifest))
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(sourceMedia.title)
                            .build()
                    )
                    .build()
            )
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            selectedHeight = 0
            autoQuality = true
            qualityLabel = "LIVE"
            return
        }

        val option = sourceMedia.options
            .filter { it.height <= requestedHeight }
            .maxByOrNull { it.height }
            ?: sourceMedia.options.maxByOrNull { it.height }
            ?: return
        val position = if (preservePosition) {
            exoPlayer.currentPosition
        } else {
            initialPositionMs.coerceAtLeast(0L)
        }
        PlaybackStartupTelemetry.markPrepare(media.mediaUri)
        isPreparing = true
        firstFrameRendered = false
        exoPlayer.setMediaSource(buildResolvedSource(dataSourceFactory, sourceMedia, option.height), position)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        selectedHeight = option.height
        autoQuality = autoSelected
        qualityLabel = if (autoSelected) "Auto" else "${option.height}p"
    }

    fun switchQuality(height: Int?) {
        val sourceMedia = resolved ?: return
        if (sourceMedia.isLive && !sourceMedia.liveManifestUrl.isNullOrBlank()) {
            controlsVisible = true
            return
        }
        if (height == null) {
            val target = chooseAutoHeight(context, sourceMedia.options.map { it.height }, bandwidthMeter.bitrateEstimate)
            loadResolvedStream(sourceMedia, target, autoSelected = true, preservePosition = true)
            onPlaybackPreferenceChanged(playbackSpeed, 0, seekIntervalSeconds)
        } else {
            loadResolvedStream(sourceMedia, height, autoSelected = false, preservePosition = true)
            onPlaybackPreferenceChanged(playbackSpeed, height, seekIntervalSeconds)
        }
        controlsVisible = true
    }

    fun rotatePlayer() {
        activity ?: return
        activity.requestedOrientation = if (isPortrait) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
        controlsVisible = true
    }

    val latestContinuationMode by rememberUpdatedState(playbackContinuationMode)
    val latestResolvedMedia by rememberUpdatedState(resolved)
    val latestContinuationEpisodeShelf by rememberUpdatedState(continuationEpisodeShelf)
    val latestOnNextItem by rememberUpdatedState(onNextItem)
    val latestOnPlayRelated by rememberUpdatedState(onPlayRelated)
    val latestOnPlayPlaylistItem by rememberUpdatedState(onPlayPlaylistItem)
    val latestPlaylistItems by rememberUpdatedState(playlistItems)
    val latestPlaylistIndex by rememberUpdatedState(playlistIndex)

    DisposableEffect(exoPlayer, mediaSession) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing && !exoPlayer.playWhenReady && liveDubEnabled) liveDubEngine.pauseForPlayback()
            }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    // READY can arrive before the decoder has rendered a visible frame.
                    // Keep the central loading indicator until onRenderedFirstFrame().
                    duration = exoPlayer.duration.coerceAtLeast(1L)
                    playbackRecoveryAttempts = 0
                    endActionDispatched = false
                    if (resolveError?.startsWith("Media3 playback error") == true) resolveError = null
                } else if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) {
                    isPreparing = false
                    isBuffering = false
                    if (state == Player.STATE_ENDED && !endActionDispatched) {
                        endActionDispatched = true
                        when (latestContinuationMode) {
                            PlaybackContinuationMode.LOOP_CURRENT -> {
                                exoPlayer.seekTo(0L)
                                exoPlayer.playWhenReady = true
                                exoPlayer.play()
                                endActionDispatched = false
                            }

                            PlaybackContinuationMode.EPISODES -> {
                                val currentVideoId = YoutubeUrls.videoId(media.mediaUri).orEmpty()
                                val episodes = latestContinuationEpisodeShelf?.episodes.orEmpty()
                                    .distinctBy { it.videoId }
                                val currentEpisodeIndex = episodes.indexOfFirst { it.videoId == currentVideoId }
                                val nextEpisode = if (
                                    currentEpisodeIndex >= 0 &&
                                    currentEpisodeIndex < episodes.lastIndex
                                ) {
                                    episodes[currentEpisodeIndex + 1]
                                } else {
                                    null
                                }

                                when {
                                    nextEpisode != null && latestOnPlayRelated != null -> {
                                        latestOnPlayRelated?.invoke(
                                            OfflineMedia(
                                                id = "episode-${nextEpisode.videoId}-${System.nanoTime()}",
                                                title = nextEpisode.title,
                                                videoId = nextEpisode.videoId,
                                                thumbnailUrl = nextEpisode.thumbnailUrl,
                                                mediaUri = "https://www.youtube.com/watch?v=${nextEpisode.videoId}"
                                            )
                                        )
                                    }

                                    latestPlaylistIndex >= 0 &&
                                        latestPlaylistIndex < latestPlaylistItems.lastIndex &&
                                        latestOnPlayPlaylistItem != null -> {
                                        latestOnPlayPlaylistItem?.invoke(latestPlaylistIndex + 1)
                                    }

                                    latestOnNextItem != null -> latestOnNextItem?.invoke()
                                    else -> latestOnPlaybackEnded?.invoke()
                                }
                            }

                            PlaybackContinuationMode.AUTO_NEXT -> {
                                val currentVideoId = YoutubeUrls.videoId(media.mediaUri).orEmpty()
                                val suggested = latestResolvedMedia?.relatedVideos
                                    ?.firstOrNull { it.videoId.isNotBlank() && it.videoId != currentVideoId }

                                when {
                                    latestOnPlaybackEnded != null -> latestOnPlaybackEnded?.invoke()
                                    latestOnNextItem != null -> latestOnNextItem?.invoke()
                                    suggested != null && latestOnPlayRelated != null -> {
                                        latestOnPlayRelated?.invoke(
                                            OfflineMedia(
                                                id = "auto-next-${suggested.videoId}-${System.nanoTime()}",
                                                title = suggested.title,
                                                videoId = suggested.videoId,
                                                thumbnailUrl = suggested.thumbnailUrl,
                                                mediaUri = suggested.url
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            override fun onRenderedFirstFrame() {
                isPreparing = false
                isBuffering = false
                if (!firstFrameRendered) {
                    firstFrameRendered = true
                    PlaybackStartupTelemetry.markFirstFrame(media.mediaUri)
                }
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                hasCaptions = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSupported } || resolved?.subtitles?.isNotEmpty() == true
            }
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                equalizerSnapshot = equalizerController.attach(audioSessionId)
            }
            override fun onCues(cueGroup: CueGroup) {
                val cue = cueGroup.cues
                    .mapNotNull { it.text?.toString()?.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .trim()
                if (cue.isNotBlank()) {
                    currentCueText = cue
                    currentCuePresentationUs = cueGroup.presentationTimeUs
                }
            }
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                    reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT ||
                    reason == Player.DISCONTINUITY_REASON_SKIP
                ) {
                    currentCueText = ""
                    currentCuePresentationUs = -1L
                    if (liveDubEnabled) liveDubEngine.resetForSeek()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                isPreparing = false
                isBuffering = false
                val retryable = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                val decoderFailure =
                    error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED

                if ((retryable || decoderFailure) && youtubeVideo && playbackRecoveryAttempts < 3 && !playbackRecoveryActive) {
                    playbackRecoveryAttempts += 1
                    playbackRecoveryActive = true
                    resolveError = null
                    isPreparing = true
                    playbackScope.launch {
                        try {
                            delay(if (decoderFailure) 120L else 350L)
                            val fresh = YoutubeStreamResolver.resolve(
                                context,
                                media.mediaUri,
                                forceRefresh = true
                            )
                            resolved = fresh
                            hasCaptions = fresh.subtitles.isNotEmpty()

                            val decoderSafeHeight = fresh.progressiveOptions
                                .filter { it.videoMimeType == "video/mp4" && it.height <= 720 }
                                .maxByOrNull { it.height }
                                ?.height
                                ?: fresh.progressiveOptions
                                    .filter { it.videoMimeType == "video/mp4" }
                                    .minByOrNull { it.height }
                                    ?.height

                            val target = when {
                                decoderFailure && decoderSafeHeight != null -> decoderSafeHeight
                                autoQuality -> chooseAutoHeight(
                                    context,
                                    fresh.options.map { it.height },
                                    bandwidthMeter.bitrateEstimate
                                )
                                else -> selectedHeight.takeIf { it > 0 }
                                    ?: fresh.options.maxOfOrNull { it.height }
                                    ?: 720
                            }

                            loadResolvedStream(
                                sourceMedia = fresh,
                                requestedHeight = target,
                                autoSelected = if (decoderFailure) false else autoQuality,
                                preservePosition = true
                            )
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (recoveryError: Throwable) {
                            resolveError = "Playback recovery failed: ${recoveryError.message ?: recoveryError.javaClass.simpleName}"
                        } finally {
                            playbackRecoveryActive = false
                        }
                    }
                } else if (retryable && playbackRecoveryAttempts < 3) {
                    playbackRecoveryAttempts += 1
                    resolveError = null
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                } else {
                    resolveError = "Media3 playback error: ${error.errorCodeName}"
                    if (youtubeVideo) onWebFallback()
                }
            }
        }
        exoPlayer.addListener(listener)
        equalizerSnapshot = equalizerController.attach(exoPlayer.audioSessionId)
        onDispose {
            exoPlayer.removeListener(listener)
            equalizerController.release()
            liveDubEngine.close()
            mediaSession.release()
            exoPlayer.release()
        }
    }

    LaunchedEffect(media.id, media.mediaUri) {
        isPreparing = true
        isBuffering = false
        val online = YoutubeUrls.videoId(media.mediaUri) != null
        if (online) {
            resolveError = null
            try {
                val result = YoutubeStreamResolver.resolve(context, media.mediaUri)
                resolved = result
                result.title.takeIf { it.isNotBlank() }?.let(onResolvedTitleChanged)
                hasCaptions = result.subtitles.isNotEmpty()
                if (tvSafePlayback) {
                    // TV boxes frequently ship fragile vendor codecs. Start on a single-file MP4
                    // representation at a conservative height; higher qualities remain selectable.
                    val requestedTarget = if (preferredPlaybackQualityHeight > 0) {
                        preferredPlaybackQualityHeight
                    } else {
                        chooseAutoHeight(
                            context,
                            result.options.map { it.height },
                            bandwidthMeter.bitrateEstimate
                        )
                    }
                    val safeTarget = chooseFastStartHeight(result, requestedTarget.coerceAtMost(720))
                    loadResolvedStream(
                        result,
                        safeTarget,
                        autoSelected = preferredPlaybackQualityHeight <= 0,
                        preservePosition = false
                    )
                } else if (preferredPlaybackQualityHeight > 0) {
                    loadResolvedStream(
                        result,
                        preferredPlaybackQualityHeight,
                        autoSelected = false,
                        preservePosition = false
                    )
                } else {
                    val autoTarget = chooseAutoHeight(
                        context,
                        result.options.map { it.height },
                        bandwidthMeter.bitrateEstimate
                    )
                    val startupTarget = chooseFastStartHeight(result, autoTarget)
                    loadResolvedStream(
                        result,
                        startupTarget,
                        autoSelected = true,
                        preservePosition = false
                    )
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Exception) {
                isPreparing = false
                isBuffering = false
                resolveError = "Could not resolve video stream: ${t.message ?: t.javaClass.simpleName}"
                onWebFallback()
            }
        } else {
            if (media.mediaUri.isBlank()) {
                resolveError = "The downloaded file is missing."
                return@LaunchedEffect
            }
            val targetUri = Uri.parse(media.mediaUri)
            val subtitleConfigurations = emptyList<SubtitleConfiguration>()
            isPreparing = true
            firstFrameRendered = false
            exoPlayer.setMediaItem(
                MediaItem.Builder().setUri(targetUri).setSubtitleConfigurations(subtitleConfigurations).build(),
                initialPositionMs.coerceAtLeast(0L)
            )
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            autoQuality = false
            qualityLabel = media.qualityLabel.ifBlank { "Local" }
        }
    }

    LaunchedEffect(foregroundPlaybackAllowed) {
        if (!foregroundPlaybackAllowed) {
            lifecycleResumePending = exoPlayer.playWhenReady || exoPlayer.isPlaying
            exoPlayer.pause()
        } else if (lifecycleResumePending) {
            lifecycleResumePending = false
            exoPlayer.playWhenReady = true
        }
    }

    LaunchedEffect(resumeGeneration) {
        if (resumeGeneration <= 0 || YoutubeUrls.videoId(media.mediaUri) == null) return@LaunchedEffect
        val positionBeforeRefresh = exoPlayer.currentPosition.coerceAtLeast(0L)
        resolveError = null
        try {
            // A UI/lifecycle resume is not a stream failure. Reuse the existing resolver entry
            // and let the normal playback-error recovery perform a forced refresh only if needed.
            YoutubeStreamResolver.promoteForHandoffResume(media.mediaUri)
            val fresh = YoutubeStreamResolver.resolve(
                context,
                media.mediaUri
            )
            resolved = fresh
            hasCaptions = fresh.subtitles.isNotEmpty()
            val target = if (autoQuality) {
                chooseAutoHeight(
                    context,
                    fresh.options.map { it.height },
                    bandwidthMeter.bitrateEstimate
                )
            } else {
                selectedHeight.takeIf { it > 0 }
                    ?: fresh.options.maxOfOrNull { it.height }
                    ?: 720
            }
            loadResolvedStream(
                sourceMedia = fresh,
                requestedHeight = target,
                autoSelected = autoQuality,
                preservePosition = true
            )
            if (positionBeforeRefresh > 0L) exoPlayer.seekTo(positionBeforeRefresh)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            resolveError = "Could not refresh video after returning to MyTube: ${t.message ?: t.javaClass.simpleName}"
        }
    }

    LaunchedEffect(firstFrameRendered, autoQuality, resolved, inPictureInPicture) {
        // Never switch representations while Android is shrinking/holding the decoder in PiP.
        // Several OEM codec stacks fail a decoder re-init during that surface transition.
        if (!firstFrameRendered || !autoQuality || !youtubeVideo || inPictureInPicture) return@LaunchedEffect
        // SmartTube-style resilience for extracted progressive/DASH URLs: continuously reassess
        // the sustainable representation. Downshifts are fast; upgrades require repeated stable
        // samples so fluctuating mobile links do not bounce between qualities.
        while (isActive && autoQuality) {
            delay(3_000)
            val source = resolved ?: continue
            val heights = source.options.map { it.height }.filter { it > 0 }
            if (heights.isEmpty()) continue

            val recommended = chooseAutoHeight(context, heights, bandwidthMeter.bitrateEstimate)
            val current = selectedHeight.takeIf { it > 0 } ?: recommended
            val now = android.os.SystemClock.elapsedRealtime()
            val bufferedAheadMs = (exoPlayer.bufferedPosition - exoPlayer.currentPosition).coerceAtLeast(0L)

            val urgentDownshift = recommended < current &&
                (bufferedAheadMs < 8_000L || exoPlayer.playbackState == Player.STATE_BUFFERING)
            val stableUpgrade =
                autoUpgradeCount < 1 &&
                    recommended > current &&
                    bufferedAheadMs >= 30_000L &&
                    exoPlayer.isPlaying

            if (stableUpgrade) autoStableSamples += 1 else autoStableSamples = 0

            val canSwitch = now - lastAutoSwitchMs >= 15_000L
            val upgradeReady = autoStableSamples >= 6
            if (canSwitch && (urgentDownshift || upgradeReady)) {
                val isUpgrade = recommended > current
                loadResolvedStream(
                    sourceMedia = source,
                    requestedHeight = recommended,
                    autoSelected = true,
                    preservePosition = true
                )
                if (isUpgrade) autoUpgradeCount += 1
                lastAutoSwitchMs = now
                autoStableSamples = 0
            }
        }
    }

    LaunchedEffect(firstFrameRendered, resolved?.relatedVideos, autoLoadUpNext) {
        if (!firstFrameRendered || !autoLoadUpNext) return@LaunchedEffect
        // Up Next prefetch is optional because even post-first-frame extraction can compete for
        // network/CPU on modest devices. When disabled in Settings no related item is prewarmed.
        resolved?.relatedVideos.orEmpty()
            .asSequence()
            .map { it.url }
            .distinct()
            .take(2)
            .forEach { YoutubeStreamResolver.prefetch(context, it) }
    }

    val activeDubSourceTag = remember(resolved, dubSourceCode) {
        chooseDubSourceTag(resolved, dubSourceCode)
    }
    val captionDubbingActive = liveDubEnabled && (
        dubMethod == "captions" || (dubMethod == "auto" && activeDubSourceTag != null)
    )
    val audioDubbingActive = liveDubEnabled && !captionDubbingActive

    LaunchedEffect(
        liveDubEnabled, activeDubSourceTag, dubTargetCode, dubVoiceProfile, dubMethod,
        captionsEnabled, captionLanguageTag
    ) {
        if (!liveDubEnabled) {
            liveDubEngine.stop()
            dubStatus = "Live Dub off"
            exoPlayer.volume = 1f
        } else if (captionDubbingActive) {
            val sourceTag = activeDubSourceTag
            if (sourceTag == null) {
                liveDubEngine.stop()
                dubStatus = "Caption dubbing unavailable • choose Audio (Whisper)"
            } else {
                exoPlayer.volume = dubOriginalVolume.coerceIn(0f, 1f)
                liveDubEngine.configure(
                    sourceTag,
                    dubTargetCode,
                    dubVoiceProfile,
                    LiveDubInputMode.CAPTIONS
                ) { status -> dubStatus = status }
            }
        } else if (audioDubbingActive) {
            val sourceHint = dubSourceCode.ifBlank { "auto" }
            exoPlayer.volume = dubOriginalVolume.coerceIn(0f, 1f)
            liveDubEngine.configure(
                sourceHint,
                dubTargetCode,
                dubVoiceProfile,
                LiveDubInputMode.AUDIO
            ) { status -> dubStatus = status }
        }

        val textBuilder = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !captionsEnabled && !captionDubbingActive)
        when {
            captionDubbingActive && activeDubSourceTag != null -> textBuilder.setPreferredTextLanguage(activeDubSourceTag)
            captionsEnabled && captionLanguageTag != "auto" -> textBuilder.setPreferredTextLanguage(captionLanguageTag)
            else -> textBuilder.setPreferredTextLanguage(null)
        }
        exoPlayer.trackSelectionParameters = textBuilder.build()
    }

    LaunchedEffect(liveDubEnabled, dubOriginalVolume) {
        exoPlayer.volume = if (liveDubEnabled) {
            dubOriginalVolume.coerceIn(0f, 1f)
        } else {
            1f
        }
    }

    LaunchedEffect(currentCueText, currentCuePresentationUs, captionDubbingActive, activeDubSourceTag, dubTargetCode) {
        if (captionDubbingActive && currentCueText.isNotBlank() && activeDubSourceTag != null) {
            liveDubEngine.speak(currentCueText, currentCuePresentationUs)
        }
    }

    // Direct YouTube URLs are discrete representations. Rebuilding Media3 every few seconds
    // caused visible black frames and decoder churn, so Auto chooses the safest representation
    // when playback starts (or when the user explicitly re-selects Auto) and keeps it stable.

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            onPlaybackPositionChanged(currentPosition)
            val seconds = currentPosition / 1000f
            sponsorSegments.firstOrNull { seconds >= it.startTime && seconds < it.endTime - .1f }?.let {
                exoPlayer.seekTo((it.endTime * 1000).toLong())
            }
            delay(250)
        }
    }

    LaunchedEffect(controlsVisible, autoHideControls) {
        if (controlsVisible && autoHideControls) {
            delay(4200)
            controlsVisible = false
        }
    }

    LaunchedEffect(gestureOverlayVisible, gestureValue) {
        if (gestureOverlayVisible && !inPictureInPicture) {
            delay(900)
            gestureOverlayVisible = false
        }
    }

    BackHandler(
        enabled = !inPictureInPicture && !isPipMode(activity) &&
            (showEqualizer || showSettings || (showComments && compactPortrait))
    ) {
        when {
            showEqualizer -> showEqualizer = false
            showSettings -> showSettings = false
            showComments && compactPortrait -> showComments = false
            isFullscreen -> isFullscreen = false
            else -> Unit
        }
    }

    LaunchedEffect(inPictureInPicture) {
        if (inPictureInPicture) {
            isFullscreen = false
            controlsVisible = false
            showSettings = false
            showQuickActions = false
            showEqualizer = false
            gestureOverlayVisible = false
        }
    }

    LaunchedEffect(activity, isFullscreen) {
        activity?.let { setFullscreen(it, isFullscreen) }
    }

    Column(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = if (compactPortrait) {
                Modifier.fillMaxWidth().aspectRatio(16f / 13f).background(Color.Black)
            } else {
                Modifier.fillMaxSize().background(Color.Black)
            }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        setKeepContentOnPlayerReset(true)
                        this.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(-1, -1)
                    }
                },
                update = { view ->
                    view.resizeMode = resizeMode
                    view.subtitleView?.visibility = if (captionsEnabled) View.VISIBLE else View.GONE
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { view -> view.player = null }
            )

            if (gestureEnabled) {
                Box(
                    Modifier.fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .pointerInput(gestureEnabled, duration) {
                            detectTapGestures(
                                onTap = { controlsVisible = !controlsVisible },
                                onLongPress = {
                                    showQuickActions = true
                                    controlsVisible = true
                                },
                                onDoubleTap = { position ->
                                    val action = PlaybackSurfaceGesturePolicy.doubleTap(position.x, size.width.toFloat())
                                    when (action) {
                                        PlaybackSurfaceGesture.SEEK_BACK, PlaybackSurfaceGesture.SEEK_FORWARD -> {
                                            val direction = if (action == PlaybackSurfaceGesture.SEEK_FORWARD) 1 else -1
                                            exoPlayer.seekTo(
                                                (exoPlayer.currentPosition + direction * seekIntervalSeconds * 1_000L)
                                                    .coerceIn(0L, duration.coerceAtLeast(0L))
                                            )
                                            gestureIcon = if (direction > 0) Icons.Default.FastForward else Icons.Default.FastRewind
                                            gestureValue = (exoPlayer.currentPosition.toFloat() / duration.coerceAtLeast(1L)).coerceIn(0f, 1f)
                                            gestureOverlayVisible = true
                                        }
                                        PlaybackSurfaceGesture.TOGGLE_FULLSCREEN -> {
                                            isFullscreen = !isFullscreen
                                            gestureIcon = if (isFullscreen) Icons.Default.Fullscreen else Icons.Default.FullscreenExit
                                            gestureValue = 1f
                                            gestureOverlayVisible = true
                                        }
                                        else -> Unit
                                    }
                                    controlsVisible = true
                                }
                            )
                        }
                        .pointerInput(gestureEnabled, duration) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var totalDy = 0f
                                var dragging = false
                                var pinching = false
                                var pinchDistance = 0f
                                val rightSide = down.position.x > size.width / 2f
                                val centerZone = down.position.x in (size.width * .30f)..(size.width * .70f)
                                val playbackCardsSwipeZone =
                                    down.position.y >= size.height * .68f &&
                                    down.position.x in (size.width * .12f)..(size.width * .88f)
                                val startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                val startBrightness = activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: .5f
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.size >= 2) {
                                        pinching = true
                                        val previousDistance = (pressed[0].previousPosition - pressed[1].previousPosition).getDistance()
                                        val currentDistance = (pressed[0].position - pressed[1].position).getDistance()
                                        if (previousDistance > 1f) pinchDistance += currentDistance - previousDistance
                                        val steps = (pinchDistance / 32f).toInt()
                                        if (steps != 0) {
                                            pinchDistance -= steps * 32f
                                            exoPlayer.seekTo(
                                                (exoPlayer.currentPosition + steps * (seekIntervalSeconds.coerceAtLeast(5) * 500L))
                                                    .coerceIn(0L, duration.coerceAtLeast(0L))
                                            )
                                            gestureIcon = if (steps > 0) Icons.Default.FastForward else Icons.Default.FastRewind
                                            gestureValue = (exoPlayer.currentPosition.toFloat() / duration.coerceAtLeast(1L)).coerceIn(0f, 1f)
                                            gestureOverlayVisible = true
                                            controlsVisible = true
                                        }
                                        pressed.forEach { it.consume() }
                                        continue
                                    }
                                    if (pinching && pressed.isEmpty()) break
                                    if (pinching) continue
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    val dy = change.position.y - change.previousPosition.y
                                    totalDy += dy
                                    if (!dragging && kotlin.math.abs(totalDy) > 12f) {
                                        dragging = true
                                        gestureOverlayVisible = true
                                        controlsVisible = true
                                    }
                                    if (dragging) {
                                        change.consume()
                                        val fraction = (-totalDy / size.height.toFloat()).coerceIn(-1f, 1f)
                                        if (playbackCardsSwipeZone && totalDy < -12f) {
                                            showPlaybackModeCards = true
                                            gestureOverlayVisible = false
                                            controlsVisible = true
                                        } else if (centerZone) {
                                            val action = PlaybackSurfaceGesturePolicy.verticalRelease(
                                                down.position.x, size.width.toFloat(), totalDy,
                                                size.height.toFloat(), isFullscreen, pipEnabled
                                            )
                                            gestureIcon = when (action) {
                                                PlaybackSurfaceGesture.ENTER_FULLSCREEN -> Icons.Default.Fullscreen
                                                PlaybackSurfaceGesture.EXIT_FULLSCREEN -> Icons.Default.FullscreenExit
                                                PlaybackSurfaceGesture.ENTER_PIP -> Icons.Default.PictureInPictureAlt
                                                else -> if (totalDy < 0f) Icons.Default.Fullscreen
                                                    else if (isFullscreen) Icons.Default.FullscreenExit
                                                    else Icons.Default.PictureInPictureAlt
                                            }
                                            gestureValue = (kotlin.math.abs(totalDy) / (size.height * .30f)).coerceIn(0f, 1f)
                                        } else if (rightSide) {
                                            val value = (startVolume + fraction * maxVolume).toInt().coerceIn(0, maxVolume)
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
                                            gestureIcon = if (value == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp
                                            gestureValue = value / maxVolume.toFloat()
                                        } else {
                                            val value = (startBrightness + fraction).coerceIn(.01f, 1f)
                                            activity?.window?.attributes = activity.window.attributes.apply { screenBrightness = value }
                                            gestureIcon = Icons.Default.Brightness6
                                            gestureValue = value
                                        }
                                    }
                                }
                                if (
                                    playbackCardsSwipeZone &&
                                    dragging &&
                                    totalDy < -(size.height * .08f)
                                ) {
                                    showPlaybackModeCards = true
                                    controlsVisible = true
                                    gestureOverlayVisible = false
                                } else if (centerZone && dragging) {
                                    when (PlaybackSurfaceGesturePolicy.verticalRelease(
                                        down.position.x, size.width.toFloat(), totalDy,
                                        size.height.toFloat(), isFullscreen, pipEnabled
                                    )) {
                                        PlaybackSurfaceGesture.ENTER_FULLSCREEN -> {
                                            isFullscreen = true
                                            gestureIcon = Icons.Default.Fullscreen
                                            gestureValue = 1f
                                            gestureOverlayVisible = true
                                        }
                                        PlaybackSurfaceGesture.EXIT_FULLSCREEN -> {
                                            isFullscreen = false
                                            gestureIcon = Icons.Default.FullscreenExit
                                            gestureValue = 1f
                                            gestureOverlayVisible = true
                                        }
                                        PlaybackSurfaceGesture.ENTER_PIP -> {
                                            if (pipEnabled) {
                                                gestureIcon = Icons.Default.PictureInPictureAlt
                                                gestureValue = 1f
                                                gestureOverlayVisible = true
                                                onEnterPip()
                                            }
                                        }
                                        else -> gestureOverlayVisible = false
                                    }
                                } else {
                                    gestureOverlayVisible = false
                                }
                            }
                        }
                )
            }

            if ((isPreparing || !firstFrameRendered || isBuffering || playbackRecoveryActive) &&
                resolveError == null && !inPictureInPicture) {
                Surface(
                    color = Color.Black.copy(alpha = .68f),
                    shape = CircleShape,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = if (compactPortrait) 12.dp else 10.dp)
                        .zIndex(50f)
                        .size(centerControlDiameter)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(centerControlDiameter),
                            strokeWidth = if (compactPortrait) 4.dp else 5.dp,
                            color = TubeDeckAccentCyan,
                            trackColor = Color.White.copy(alpha = .26f)
                        )
                    }
                }
            }

            if (gestureOverlayVisible) {
                Surface(
                    color = Color.Black.copy(alpha = .78f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.align(Alignment.Center).width(180.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(22.dp)) {
                        Icon(gestureIcon, null, tint = TubeDeckAccentCyan, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (gestureIcon) {
                                Icons.Default.Brightness6 -> "Brightness"
                                Icons.Default.FastForward -> "Forward"
                                Icons.Default.FastRewind -> "Rewind"
                                Icons.Default.Fullscreen -> "Fullscreen"
                                Icons.Default.FullscreenExit -> "Mini player"
                                Icons.Default.PictureInPictureAlt -> "Picture in Picture"
                                else -> "Volume"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { gestureValue },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                        )
                    }
                }
            }

            FadeVisibility(visible = controlsVisible && !inPictureInPicture) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = .35f))
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    Row(
                        Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            liveDubEngine.stop()
                            exoPlayer.pause()
                            onClose()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Text(media.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${if (autoQuality) "Auto • ${selectedHeight}p" else qualityLabel} • ${playbackSpeed}x • ${resizeModeLabel(resizeMode)}${if (liveDubEnabled) " • Dub ${LiveDubLanguages.label(dubTargetCode)}" else ""}",
                                color = Color.White.copy(alpha = .72f),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = ::rotatePlayer) {
                            Icon(Icons.Default.ScreenRotation, "Rotate portrait/landscape", tint = Color.White)
                        }
                        if (!compactPortrait) {
                            IconButton(onClick = {
                                if (pipEnabled) {
                                    isFullscreen = false
                                    activity?.let { setFullscreen(it, false) }
                                    onEnterPip()
                                } else {
                                    Toast.makeText(context, "PiP is disabled in Settings", Toast.LENGTH_SHORT).show()
                                }
                            }) { Icon(Icons.Default.PictureInPictureAlt, "Picture in picture", tint = Color.White) }
                        }
                        GoogleAccountButton(account = googleAccount, onClick = onGoogleAccountClick)
                        IconButton(onClick = { showQuickActions = true }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.MoreVert, "Video actions", tint = Color.White, modifier = Modifier.size(21.dp))
                        }
                        IconButton(onClick = { showSettings = true }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Settings, "Player settings", tint = Color.White, modifier = Modifier.size(21.dp))
                        }
                    }

                    if (!compactPortrait && (onDownloadVideo != null || onDownloadPlaylist != null)) {
                        Box(
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 54.dp, end = 8.dp)
                        ) {
                            DownloadActionStrip(onDownloadVideo, onDownloadPlaylist)
                        }
                    }

                    if (firstFrameRendered) {
                        val currentVideoId = YoutubeUrls.videoId(media.mediaUri).orEmpty()
                        val suggestedNext = resolved?.relatedVideos
                            ?.firstOrNull { it.videoId.isNotBlank() && it.videoId != currentVideoId }

                        Row(
                            Modifier.align(Alignment.Center)
                                .offset(y = if (compactPortrait) 12.dp else 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (compactPortrait) 12.dp else 20.dp)
                        ) {
                            IconButton(
                                onClick = { onPreviousItem?.invoke() },
                                enabled = onPreviousItem != null,
                                modifier = Modifier.size(if (compactPortrait) 44.dp else 50.dp)
                            ) {
                                Icon(
                                    Icons.Default.SkipPrevious,
                                    "Previous video",
                                    tint = if (onPreviousItem != null) Color.White else Color.White.copy(alpha = .28f),
                                    modifier = Modifier.size(if (compactPortrait) 34.dp else 40.dp)
                                )
                            }

                            Surface(
                                onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                                shape = CircleShape,
                                color = TubeDeckAccentCyan,
                                modifier = Modifier.size(centerControlDiameter)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        "Play/Pause",
                                        tint = Color.Black,
                                        modifier = Modifier.size(if (compactPortrait) 30.dp else 34.dp)
                                    )
                                }
                            }

                            val nextEnabled = onNextItem != null || (suggestedNext != null && onPlayRelated != null)
                            IconButton(
                                onClick = {
                                    when {
                                        onNextItem != null -> onNextItem.invoke()
                                        suggestedNext != null && onPlayRelated != null -> {
                                            onPlayRelated.invoke(
                                                OfflineMedia(
                                                    id = "next-${suggestedNext.videoId}-${System.nanoTime()}",
                                                    title = suggestedNext.title,
                                                    videoId = suggestedNext.videoId,
                                                    thumbnailUrl = suggestedNext.thumbnailUrl,
                                                    mediaUri = suggestedNext.url
                                                )
                                            )
                                        }
                                    }
                                },
                                enabled = nextEnabled,
                                modifier = Modifier.size(if (compactPortrait) 44.dp else 50.dp)
                            ) {
                                Icon(
                                    Icons.Default.SkipNext,
                                    "Next video",
                                    tint = if (nextEnabled) Color.White else Color.White.copy(alpha = .28f),
                                    modifier = Modifier.size(if (compactPortrait) 34.dp else 40.dp)
                                )
                            }
                        }
                    }

                    Column(
                        Modifier.fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .then(if (compactPortrait) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                            .padding(horizontal = 10.dp, vertical = 1.dp)
                    ) {
                        // Keep the timestamps above the auxiliary actions. This deliberately pushes
                        // quality/speed/Zoom/fullscreen/CC/Dub down next to the seek bar so they cannot
                        // collide with the central rewind/play/forward cluster.
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(formatDuration(currentPosition), color = Color.White, fontSize = 9.sp)
                            Spacer(Modifier.weight(1f))
                            Text(formatDuration(duration), color = Color.White.copy(alpha = .75f), fontSize = 9.sp)
                        }
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    playbackSpeed = cycleSpeed(exoPlayer)
                                    onPlaybackPreferenceChanged(
                                        playbackSpeed,
                                        if (autoQuality) 0 else selectedHeight,
                                        seekIntervalSeconds
                                    )
                                    controlsVisible = true
                                },
                                modifier = Modifier.height(30.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("${playbackSpeed}x", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                            Box {
                                TextButton(
                                    onClick = {
                                        showQualityMenu = true
                                        controlsVisible = true
                                    },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        if (autoQuality) "Auto" else qualityLabel,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        "Video quality",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showQualityMenu,
                                    onDismissRequest = { showQualityMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (autoQuality) {
                                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(17.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                }
                                                Text("Auto")
                                            }
                                        },
                                        onClick = {
                                            showQualityMenu = false
                                            switchQuality(null)
                                        }
                                    )
                                    resolved?.options
                                        ?.map { it.height }
                                        ?.distinct()
                                        ?.sortedDescending()
                                        .orEmpty()
                                        .forEach { height ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        if (!autoQuality && selectedHeight == height) {
                                                            Icon(Icons.Default.Check, null, modifier = Modifier.size(17.dp))
                                                            Spacer(Modifier.width(6.dp))
                                                        }
                                                        Text(height.toString() + "p")
                                                    }
                                                },
                                                onClick = {
                                                    showQualityMenu = false
                                                    switchQuality(height)
                                                }
                                            )
                                        }
                                }
                            }
                            Surface(
                                color = Color.Black.copy(alpha = .42f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = {
                                            resizeMode = nextResizeMode(resizeMode)
                                            controlsVisible = true
                                        },
                                        modifier = Modifier.height(30.dp),
                                        contentPadding = PaddingValues(start = 7.dp, end = 3.dp, top = 0.dp, bottom = 0.dp)
                                    ) {
                                        Icon(Icons.Default.AspectRatio, "Video size", tint = Color.White, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text(resizeModeLabel(resizeMode), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    }
                                    IconButton(
                                        onClick = {
                                            isFullscreen = !isFullscreen
                                            controlsVisible = true
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                            if (isFullscreen) "Exit fullscreen" else "Enter fullscreen",
                                            tint = Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = {
                                    captionsEnabled = !captionsEnabled
                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !captionsEnabled && !captionDubbingActive)
                                        .build()
                                    controlsVisible = true
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Default.ClosedCaption,
                                    "Captions",
                                    tint = if (captionsEnabled && hasCaptions) TubeDeckAccentCyan else Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            TextButton(
                                onClick = {
                                    liveDubEnabled = !liveDubEnabled
                                    controlsVisible = true
                                },
                                modifier = Modifier.height(30.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    Icons.Default.RecordVoiceOver,
                                    "Live Dub",
                                    tint = if (liveDubEnabled) TubeDeckAccentMint else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (liveDubEnabled) "Dub ${LiveDubLanguages.byCode(dubTargetCode)?.code?.uppercase(Locale.ROOT) ?: dubTargetCode.uppercase(Locale.ROOT)}" else "Dub",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                            TextButton(
                                onClick = {
                                    if (!showEqualizer) {
                                        equalizerSnapshot = equalizerController.prepareEqualizer()
                                        equalizerSnapshot = equalizerController.probeDts()
                                        showEqualizer = true
                                    } else {
                                        showEqualizer = false
                                    }
                                    controlsVisible = true
                                },
                                modifier = Modifier.height(30.dp),
                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    "EQ",
                                    color = if (equalizerSnapshot.enabled) TubeDeckAccentCyan else Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp
                                )
                                Icon(
                                    if (showEqualizer) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    if (showEqualizer) "Minimize equalizer" else "Open equalizer",
                                    tint = if (equalizerSnapshot.enabled) TubeDeckAccentCyan else Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        resolved?.chapters
                            ?.lastOrNull { it.startTimeSeconds * 1_000L <= currentPosition }
                            ?.title
                            ?.takeIf { it.isNotBlank() }
                            ?.let { chapterTitle ->
                                Text(
                                    chapterTitle,
                                    color = Color.White.copy(alpha = .82f),
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        scrubFraction?.let { fraction ->
                            SeekPreviewThumbnail(
                                fraction = fraction,
                                durationMs = duration,
                                storyboard = resolved?.previewStoryboard,
                                chapters = resolved?.chapters.orEmpty(),
                                fallbackThumbnailUrl = media.thumbnailUrl.ifBlank {
                                    YoutubeUrls.videoId(media.mediaUri)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }.orEmpty()
                                }
                            )
                        }
                        CompactSeekBar(
                            progress = scrubFraction
                                ?: if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                            chapterFractions = resolved?.chapters.orEmpty()
                                .mapNotNull { chapter ->
                                    if (duration <= 0L) null
                                    else (chapter.startTimeSeconds * 1000f / duration.toFloat()).coerceIn(0f, 1f)
                                },
                            sponsorRanges = sponsorSegments.mapNotNull { segment ->
                                if (duration <= 0L) null
                                else Pair(
                                    (segment.startTime * 1000f / duration.toFloat()).coerceIn(0f, 1f),
                                    (segment.endTime * 1000f / duration.toFloat()).coerceIn(0f, 1f)
                                )
                            },
                            onScrubChange = { scrubFraction = it },
                            onSeekFinished = { fraction ->
                                exoPlayer.seekTo((fraction * duration).toLong())
                                scrubFraction = null
                            }
                        )
                    }
                }
            }

            if (showEqualizer && !inPictureInPicture) {
                PlaybackEqualizerOverlay(
                    snapshot = equalizerSnapshot,
                    onEnabled = { enabled ->
                        equalizerSnapshot = equalizerController.setEnabled(enabled)
                    },
                    onPreset = { preset ->
                        equalizerSnapshot = equalizerController.applyPreset(preset)
                    },
                    onBandChanged = { index, value ->
                        equalizerSnapshot = equalizerController.setBandNormalized(index, value)
                    },
                    onGraphicBandChanged = { index, value ->
                        equalizerSnapshot = equalizerController.setBandNormalizedRealtime(index, value)
                    },
                    onGraphicEditFinished = {
                        equalizerSnapshot = equalizerController.commitCustomCurve()
                    },
                    onLayout = { layout ->
                        equalizerSnapshot = equalizerController.setLayout(layout)
                    },
                    onStyle = { style ->
                        equalizerSnapshot = equalizerController.setStyle(style)
                    },
                    onSaveUser = { name ->
                        equalizerSnapshot = equalizerController.saveUserPreset(name)
                    },
                    onRecallUser = {
                        equalizerSnapshot = equalizerController.recallUserPreset()
                    },
                    onSurroundEnabled = { enabled ->
                        equalizerSnapshot = equalizerController.setSurroundEnabled(enabled)
                    },
                    onSurroundStrength = { strength ->
                        equalizerSnapshot = equalizerController.setSurroundStrength(strength)
                    },
                    onDtsEnabled = { enabled ->
                        equalizerSnapshot = equalizerController.setDtsEnabled(enabled)
                    },
                    onMinimize = { showEqualizer = false },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(horizontal = 8.dp, vertical = 44.dp)
                        .zIndex(70f)
                        .fillMaxWidth(if (compactPortrait) .95f else .50f)
                        .widthIn(max = 430.dp)
                )
            }

            if (resolveError != null) {
                Surface(
                    color = Color.Black.copy(alpha = .92f),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Native stream unavailable", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(resolveError.orEmpty(), color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        if (YoutubeUrls.videoId(media.mediaUri) != null) {
                            Text("The web player may work for this video.", color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
                            TextButton(onClick = onWebFallback) { Text("Open web player") }
                        }
                    }
                }
            }
        }

        if (compactPortrait) {
            PortraitCompanionPanel(
                media = media,
                displayTitle = resolved?.title?.takeIf { it.isNotBlank() } ?: media.title,
                showComments = showComments,
                commentsAvailable = youtubeVideo,
                onToggleComments = { showComments = !showComments },
                onEnterPip = {
                    if (pipEnabled) onEnterPip()
                    else Toast.makeText(context, "PiP is disabled in Settings", Toast.LENGTH_SHORT).show()
                },
                onDownloadVideo = onDownloadVideo,
                onDownloadPlaylist = onDownloadPlaylist,
                description = resolved?.description.orEmpty(),
                descriptionIsHtml = resolved?.descriptionIsHtml == true,
                uploaderName = resolved?.uploaderName.orEmpty(),
                viewCount = resolved?.viewCount ?: -1L,
                uploadDateText = resolved?.uploadDateText.orEmpty(),
                onSeekDescriptionTimestamp = { seconds ->
                    exoPlayer.seekTo((seconds * 1_000L).coerceAtLeast(0L))
                    controlsVisible = true
                },
                relatedVideos = if (autoLoadUpNext && firstFrameRendered) resolved?.relatedVideos.orEmpty() else emptyList(),
                playlistItems = playlistItems,
                playlistIndex = playlistIndex,
                onPlayPlaylistItem = onPlayPlaylistItem,
                autoLoadEpisodes = autoLoadEpisodes,
                autoLoadUpNext = autoLoadUpNext,
                playbackReadyForCompanionLoads = firstFrameRendered,
                onPlayRelated = onPlayRelated,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }

    if (showQuickActions) {
        AlertDialog(
            onDismissRequest = { showQuickActions = false },
            title = { Text("Video actions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            showQuickActions = false
                            if (pipEnabled) onEnterPip()
                        },
                        enabled = pipEnabled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PictureInPictureAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Picture in Picture")
                    }
                    TextButton(
                        onClick = {
                            showQuickActions = false
                            onAudioOnly()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Headphones, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Audio only")
                    }
                    onDownloadVideo?.let { download ->
                        TextButton(
                            onClick = {
                                showQuickActions = false
                                download()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Download video")
                        }
                    }
                    onDownloadPlaylist?.let { downloadPlaylist ->
                        TextButton(
                            onClick = {
                                showQuickActions = false
                                downloadPlaylist()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlaylistAdd, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Download playlist")
                        }
                    }
                    TextButton(
                        onClick = {
                            showQuickActions = false
                            showSettings = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Player settings")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQuickActions = false }) { Text("Close") }
            }
        )
    }

    if (showSettings) {
        PlayerSettingsDialog(
            captionsEnabled = captionsEnabled,
            hasCaptions = hasCaptions,
            gestureEnabled = gestureEnabled,
            autoHideControls = autoHideControls,
            fullscreen = isFullscreen,
            qualityOptions = resolved?.options?.map { it.height }?.distinct()?.sortedDescending().orEmpty(),
            currentQuality = qualityLabel,
            autoQuality = autoQuality,
            resizeMode = resizeMode,
            liveDubEnabled = liveDubEnabled,
            dubSourceCode = dubSourceCode,
            dubMethod = dubMethod,
            captionLanguageTag = captionLanguageTag,
            dubTargetCode = dubTargetCode,
            dubVoiceProfile = dubVoiceProfile,
            dubOriginalVolume = dubOriginalVolume,
            dubStatus = dubStatus,
            availableSubtitles = resolved?.subtitles.orEmpty(),
            onLiveDub = { liveDubEnabled = it },
            onDubMethod = { dubMethod = it },
            onCaptionLanguage = { tag ->
                captionLanguageTag = tag
                if (tag != "auto") captionsEnabled = true
            },
            onDubSource = { dubSourceCode = it },
            onDubTarget = { dubTargetCode = it },
            onDubVoiceProfile = { dubVoiceProfile = it; liveDubEngine.setVoiceProfile(it) },
            onDubOriginalVolume = { dubOriginalVolume = it },
            onCaptions = { enabled ->
                captionsEnabled = enabled
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled && !captionDubbingActive)
                    .build()
            },
            onGestures = { gestureEnabled = it },
            onAutoHide = { autoHideControls = it },
            onQuality = { height -> switchQuality(height) },
            onResizeMode = { resizeMode = it },
            onRotate = ::rotatePlayer,
            onFullscreen = { isFullscreen = it },
            onPip = {
                if (pipEnabled) {
                    isFullscreen = false
                    activity?.let { setFullscreen(it, false) }
                    onEnterPip()
                } else {
                    Toast.makeText(context, "PiP is disabled in Settings", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun PlaybackEqualizerOverlay(
    snapshot: PlaybackEqualizerController.Snapshot,
    onEnabled: (Boolean) -> Unit,
    onPreset: (String) -> Unit,
    onBandChanged: (Int, Float) -> Unit,
    onGraphicBandChanged: (Int, Float) -> Unit,
    onGraphicEditFinished: () -> Unit,
    onLayout: (String) -> Unit,
    onStyle: (String) -> Unit,
    onSaveUser: (String) -> Unit,
    onRecallUser: () -> Unit,
    onSurroundEnabled: (Boolean) -> Unit,
    onSurroundStrength: (Float) -> Unit,
    onDtsEnabled: (Boolean) -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier
) {
    var userPresetName by rememberSaveable { mutableStateOf(snapshot.userPresetName) }

    LaunchedEffect(snapshot.userPresetName) {
        if (userPresetName.isBlank() || userPresetName == "User") {
            userPresetName = snapshot.userPresetName
        }
    }

    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = .92f),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 8.dp,
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 390.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Audio Equalizer",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = snapshot.enabled,
                    onCheckedChange = onEnabled,
                    enabled = snapshot.supported,
                    modifier = Modifier.height(30.dp)
                )
                IconButton(onClick = onMinimize, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        "Minimize equalizer",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("View", color = Color.White.copy(alpha = .66f), fontSize = 9.sp)
                FilterChip(
                    selected = snapshot.layoutId == "horizontal",
                    onClick = { onLayout("horizontal") },
                    label = { Text("↔ H", fontSize = 9.sp) }
                )
                FilterChip(
                    selected = snapshot.layoutId == "vertical",
                    onClick = { onLayout("vertical") },
                    label = { Text("↕ V", fontSize = 9.sp) }
                )
                Spacer(Modifier.width(4.dp))
                FilterChip(
                    selected = snapshot.styleId == "classic",
                    onClick = { onStyle("classic") },
                    label = { Text("Classic", fontSize = 9.sp) }
                )
                FilterChip(
                    selected = snapshot.styleId == "graphic",
                    onClick = { onStyle("graphic") },
                    label = { Text("Graphic", fontSize = 9.sp) }
                )
            }

            if (!snapshot.supported) {
                Text(
                    "Equalizer will become available when the playback audio session is ready.",
                    color = Color.White.copy(alpha = .72f),
                    fontSize = 10.sp
                )
            } else {
                val presets = listOf(
                    "flat" to "Flat",
                    "bass" to "Bass",
                    "voice" to "Voice",
                    "music" to "Music",
                    "treble" to "Treble"
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    presets.forEach { (id, label) ->
                        FilterChip(
                            selected = snapshot.presetId == id,
                            onClick = { onPreset(id) },
                            label = { Text(label, fontSize = 9.sp) }
                        )
                    }
                    if (snapshot.presetId == "custom") {
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = { Text("Custom", fontSize = 9.sp) }
                        )
                    }
                    if (snapshot.hasUserPreset) {
                        FilterChip(
                            selected = snapshot.presetId == "user",
                            onClick = onRecallUser,
                            label = { Text(snapshot.userPresetName, fontSize = 9.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = userPresetName,
                        onValueChange = { userPresetName = it.take(24) },
                        singleLine = true,
                        label = { Text("Custom / User preset", fontSize = 9.sp) },
                        placeholder = { Text("e.g. My Speakers", fontSize = 9.sp) },
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(fontSize = 10.sp)
                    )
                    Button(
                        onClick = { onSaveUser(userPresetName) },
                        enabled = snapshot.bands.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Text("Save User", fontSize = 9.sp)
                    }
                }

                Surface(
                    color = Color.White.copy(alpha = .045f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Surround / Spatial", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                                Text(
                                    if (snapshot.surroundAvailable) "Session surround" else "Not supported on this audio route",
                                    color = Color.White.copy(alpha = .58f),
                                    fontSize = 8.sp
                                )
                            }
                            Switch(
                                checked = snapshot.surroundEnabled,
                                onCheckedChange = onSurroundEnabled,
                                enabled = snapshot.surroundAvailable,
                                modifier = Modifier.height(28.dp)
                            )
                        }
                        if (snapshot.surroundAvailable && snapshot.surroundEnabled) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Depth", color = Color.White.copy(alpha = .72f), fontSize = 8.sp, modifier = Modifier.width(46.dp))
                                Slider(
                                    value = snapshot.surroundStrength,
                                    onValueChange = onSurroundStrength,
                                    valueRange = 0f..1f,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${(snapshot.surroundStrength * 100).roundToInt()}%",
                                    color = Color.White.copy(alpha = .72f),
                                    fontSize = 8.sp,
                                    modifier = Modifier.width(34.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = .08f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("DTS", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                                Text(
                                    when {
                                        snapshot.dtsAvailable && !snapshot.dtsEffectName.isNullOrBlank() ->
                                            snapshot.dtsEffectName
                                        snapshot.dtsAvailable -> "Device DTS effect"
                                        else -> "No DTS effect exposed by this device"
                                    },
                                    color = if (snapshot.dtsAvailable) TubeDeckAccentMint else Color.White.copy(alpha = .52f),
                                    fontSize = 8.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Switch(
                                checked = snapshot.dtsEnabled,
                                onCheckedChange = onDtsEnabled,
                                enabled = snapshot.dtsAvailable,
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }

                if (snapshot.styleId == "graphic") {
                    EqualizerGraphicCurve(
                        snapshot = snapshot,
                        onBandChanged = onGraphicBandChanged,
                        onEditFinished = onGraphicEditFinished
                    )
                }

                if (snapshot.layoutId == "vertical") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        snapshot.bands.forEach { band ->
                            Column(
                                modifier = Modifier.width(60.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    equalizerBandName(band.centerFrequencyHz),
                                    color = TubeDeckAccentCyan,
                                    fontSize = 8.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    String.format(Locale.ROOT, "%+.1f dB", band.levelDb),
                                    color = Color.White.copy(alpha = .72f),
                                    fontSize = 8.sp
                                )
                                Box(
                                    modifier = Modifier.height(112.dp).width(50.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Slider(
                                        value = band.normalizedLevel,
                                        onValueChange = { onBandChanged(band.index, it) },
                                        valueRange = -1f..1f,
                                        modifier = Modifier.width(105.dp).rotate(-90f)
                                    )
                                }
                                Text(
                                    equalizerFrequencyLabel(band.centerFrequencyHz),
                                    color = Color.White.copy(alpha = .82f),
                                    fontSize = 8.sp
                                )
                            }
                        }
                    }
                } else {
                    snapshot.bands.forEach { band ->
                        Row(
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.width(86.dp)) {
                                Text(
                                    equalizerBandName(band.centerFrequencyHz),
                                    color = TubeDeckAccentCyan,
                                    fontSize = 8.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    equalizerFrequencyLabel(band.centerFrequencyHz),
                                    color = Color.White.copy(alpha = .82f),
                                    fontSize = 8.sp
                                )
                            }
                            Slider(
                                value = band.normalizedLevel,
                                onValueChange = { onBandChanged(band.index, it) },
                                valueRange = -1f..1f,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                String.format(Locale.ROOT, "%+.1f", band.levelDb),
                                color = Color.White.copy(alpha = .78f),
                                fontSize = 9.sp,
                                modifier = Modifier.width(42.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EqualizerGraphicCurve(
    snapshot: PlaybackEqualizerController.Snapshot,
    onBandChanged: (Int, Float) -> Unit,
    onEditFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (snapshot.bands.isEmpty()) return

    val latestSnapshot by rememberUpdatedState(snapshot)
    val latestOnBandChanged by rememberUpdatedState(onBandChanged)
    val latestOnEditFinished by rememberUpdatedState(onEditFinished)
    var activeGraphBand by remember { mutableIntStateOf(-1) }

    val dbTicks = listOf(
        snapshot.maxDb,
        snapshot.maxDb / 2f,
        0f,
        snapshot.minDb / 2f,
        snapshot.minDb
    )

    Surface(
        color = Color.White.copy(alpha = .055f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Graphic equalizer",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    when (snapshot.presetId) {
                        "user" -> snapshot.userPresetName
                        "custom" -> "Custom"
                        else -> snapshot.presetId.replaceFirstChar { it.uppercase() }
                    },
                    color = TubeDeckAccentCyan,
                    fontSize = 9.sp
                )
            }

            Row(modifier = Modifier.fillMaxWidth().height(168.dp)) {
                Column(
                    modifier = Modifier.width(34.dp).fillMaxHeight().padding(top = 2.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    dbTicks.forEach { value ->
                        Text(
                            String.format(Locale.ROOT, "%.1f", value),
                            color = Color.White.copy(alpha = .54f),
                            fontSize = 7.sp
                        )
                    }
                }

                Spacer(Modifier.width(5.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                            .pointerInput(snapshot.bands.size) {
                                awaitEachGesture {
                                    val initialSnapshot = latestSnapshot
                                    if (initialSnapshot.bands.isEmpty()) return@awaitEachGesture

                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var previousBand: Int? = null
                                    var previousValue = 0f

                                    fun applyPosition(position: Offset) {
                                        val current = latestSnapshot
                                        if (current.bands.isEmpty()) return

                                        val width = size.width.toFloat().coerceAtLeast(1f)
                                        val height = size.height.toFloat().coerceAtLeast(9f)
                                        val top = 4f
                                        val bottom = (height - 4f).coerceAtLeast(top + 1f)
                                        val centerY = (top + bottom) / 2f
                                        val halfRange = ((bottom - top) / 2f).coerceAtLeast(1f)
                                        val normalized = ((centerY - position.y) / halfRange).coerceIn(-1f, 1f)

                                        val localBand = if (current.bands.size <= 1) {
                                            0
                                        } else {
                                            ((position.x.coerceIn(0f, width) / width) * current.bands.lastIndex)
                                                .roundToInt()
                                                .coerceIn(0, current.bands.lastIndex)
                                        }

                                        val previous = previousBand
                                        if (previous != null && previous != localBand) {
                                            val direction = if (localBand > previous) 1 else -1
                                            val distance = kotlin.math.abs(localBand - previous).coerceAtLeast(1)
                                            var band = previous
                                            var step = 0
                                            while (band != localBand) {
                                                band += direction
                                                step += 1
                                                val t = step.toFloat() / distance.toFloat()
                                                val interpolated = previousValue + (normalized - previousValue) * t
                                                val target = current.bands[band]
                                                latestOnBandChanged(target.index, interpolated.coerceIn(-1f, 1f))
                                            }
                                        } else {
                                            latestOnBandChanged(current.bands[localBand].index, normalized)
                                        }

                                        activeGraphBand = localBand
                                        previousBand = localBand
                                        previousValue = normalized
                                    }

                                    applyPosition(down.position)
                                    down.consume()

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        applyPosition(change.position)
                                        change.consume()
                                    }

                                    activeGraphBand = -1
                                    latestOnEditFinished()
                                }
                            }
                    ) {
                        val top = 4f
                        val bottom = size.height - 4f
                        val centerY = (top + bottom) / 2f
                        val usableHeight = bottom - top
                        val stepX = if (snapshot.bands.size <= 1) 0f else
                            size.width / (snapshot.bands.size - 1).toFloat()

                        val points = snapshot.bands.mapIndexed { index, band ->
                            val x = if (snapshot.bands.size <= 1) size.width / 2f else index * stepX
                            val y = centerY - band.normalizedLevel.coerceIn(-1f, 1f) * usableHeight / 2f
                            Offset(x, y.coerceIn(top, bottom))
                        }

                        repeat(5) { row ->
                            val y = top + (usableHeight * row / 4f)
                            drawLine(
                                color = Color.White.copy(alpha = if (row == 2) .22f else .10f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = if (row == 2) 1.5f else 1f
                            )
                        }

                        points.forEach { point ->
                            drawLine(
                                color = Color.White.copy(alpha = .065f),
                                start = Offset(point.x, top),
                                end = Offset(point.x, bottom),
                                strokeWidth = 1f
                            )
                        }

                        if (points.isNotEmpty()) {
                            val curve = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                for (index in 0 until points.lastIndex) {
                                    val p1 = points[index]
                                    val p2 = points[index + 1]
                                    val midX = (p1.x + p2.x) / 2f
                                    cubicTo(midX, p1.y, midX, p2.y, p2.x, p2.y)
                                }
                            }

                            val fill = Path().apply {
                                moveTo(points.first().x, bottom)
                                lineTo(points.first().x, points.first().y)
                                for (index in 0 until points.lastIndex) {
                                    val p1 = points[index]
                                    val p2 = points[index + 1]
                                    val midX = (p1.x + p2.x) / 2f
                                    cubicTo(midX, p1.y, midX, p2.y, p2.x, p2.y)
                                }
                                lineTo(points.last().x, bottom)
                                close()
                            }

                            drawPath(
                                path = fill,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        TubeDeckAccentCyan.copy(alpha = .32f),
                                        TubeDeckAccentCyan.copy(alpha = .06f)
                                    ),
                                    startY = top,
                                    endY = bottom
                                )
                            )

                            drawPath(
                                path = curve,
                                color = TubeDeckAccentCyan,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 4f,
                                    cap = StrokeCap.Round
                                )
                            )

                            points.forEachIndexed { index, point ->
                                val active = index == activeGraphBand
                                if (active) {
                                    drawCircle(
                                        color = TubeDeckAccentCyan.copy(alpha = .22f),
                                        radius = 11f,
                                        center = point
                                    )
                                }
                                drawCircle(
                                    color = if (active) TubeDeckAccentCyan else TubeDeckAccentMint,
                                    radius = if (active) 6.5f else 4.5f,
                                    center = point
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        val indices = when {
                            snapshot.bands.size <= 5 -> snapshot.bands.indices.toList()
                            else -> listOf(
                                0,
                                snapshot.bands.lastIndex / 4,
                                snapshot.bands.lastIndex / 2,
                                snapshot.bands.lastIndex * 3 / 4,
                                snapshot.bands.lastIndex
                            ).distinct()
                        }
                        indices.forEachIndexed { itemIndex, bandIndex ->
                            val band = snapshot.bands[bandIndex]
                            Text(
                                equalizerFrequencyCompactLabel(band.centerFrequencyHz),
                                color = Color.White.copy(alpha = .67f),
                                fontSize = 7.sp,
                                textAlign = when (itemIndex) {
                                    0 -> androidx.compose.ui.text.style.TextAlign.Start
                                    indices.lastIndex -> androidx.compose.ui.text.style.TextAlign.End
                                    else -> androidx.compose.ui.text.style.TextAlign.Center
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun equalizerFrequencyCompactLabel(hz: Int): String =
    if (hz >= 1000) {
        val khz = hz / 1000f
        if (khz >= 10f) "${khz.roundToInt()}k" else String.format(Locale.ROOT, "%.1fk", khz)
    } else {
        hz.toString()
    }

private fun equalizerBandName(hz: Int): String = when {
    hz < 45 -> "Sub Woofer"
    hz < 80 -> "Sub Bass"
    hz < 160 -> "Deep Bass"
    hz < 300 -> "Bass"
    hz < 600 -> "Low Mid"
    hz < 1_200 -> "Mid"
    hz < 2_500 -> "Upper Mid"
    hz < 5_000 -> "Presence"
    hz < 10_000 -> "Treble"
    else -> "Air / High"
}

private fun equalizerFrequencyLabel(hz: Int): String {
    return if (hz >= 1000) {
        val khz = hz / 1000f
        if (khz >= 10f) "${khz.roundToInt()} kHz" else String.format(Locale.ROOT, "%.1f kHz", khz)
    } else {
        "${hz} Hz"
    }
}

@Composable
private fun DownloadActionStrip(
    onDownloadVideo: (() -> Unit)?,
    onDownloadPlaylist: (() -> Unit)?
) {
    Surface(
        color = Color.Black.copy(alpha = .64f),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            onDownloadVideo?.let { action ->
                IconButton(onClick = action, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.Download, "Download this video", tint = TubeDeckAccentCyan, modifier = Modifier.size(21.dp))
                }
            }
            onDownloadPlaylist?.let { action ->
                IconButton(onClick = action, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.PlaylistAdd, "Choose playlist videos", tint = TubeDeckAccentMint, modifier = Modifier.size(21.dp))
                }
            }
        }
    }
}

@Composable
private fun PortraitCompanionPanel(
    media: OfflineMedia,
    displayTitle: String,
    showComments: Boolean,
    commentsAvailable: Boolean,
    onToggleComments: () -> Unit,
    onEnterPip: () -> Unit,
    onDownloadVideo: (() -> Unit)?,
    onDownloadPlaylist: (() -> Unit)?,
    description: String,
    descriptionIsHtml: Boolean,
    uploaderName: String,
    viewCount: Long,
    uploadDateText: String,
    onSeekDescriptionTimestamp: (Long) -> Unit,
    relatedVideos: List<YoutubeStreamResolver.RelatedVideo>,
    playlistItems: List<PlaylistItem>,
    playlistIndex: Int,
    onPlayPlaylistItem: ((Int) -> Unit)?,
    autoLoadEpisodes: Boolean,
    autoLoadUpNext: Boolean,
    playbackReadyForCompanionLoads: Boolean,
    onPlayRelated: ((OfflineMedia) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var comments by remember(media.id) { mutableStateOf<List<YoutubeStreamResolver.NativeComment>>(emptyList()) }
    var commentsLoading by remember(media.id) { mutableStateOf(false) }
    var commentsLoaded by remember(media.id) { mutableStateOf(false) }
    var commentsError by remember(media.id) { mutableStateOf<String?>(null) }
    var commentsDisabled by remember(media.id) { mutableStateOf(false) }
    var commentsHasMore by remember(media.id) { mutableStateOf(true) }
    var commentsLimit by remember(media.id) { mutableIntStateOf(20) }
    var commentsForceRefresh by remember(media.id) { mutableStateOf(false) }
    var showDescription by rememberSaveable(media.id) { mutableStateOf(false) }
    var episodeShelf by remember(media.id) { mutableStateOf<YoutubeStreamResolver.DiscoveredPlaylist?>(null) }
    var episodesLoading by remember(media.id) { mutableStateOf(false) }

    fun refreshComments() {
        if (commentsLoading) return
        comments = emptyList()
        commentsLoaded = false
        commentsError = null
        commentsDisabled = false
        commentsHasMore = true
        commentsLimit = 20
        commentsForceRefresh = true
    }

    fun loadMoreComments() {
        if (commentsLoading || !commentsHasMore || commentsLimit >= 80) return
        commentsLimit = (commentsLimit + 20).coerceAtMost(80)
        commentsLoaded = false
        commentsError = null
        commentsForceRefresh = false
    }

    BackHandler(enabled = showDescription) {
        showDescription = false
    }

    LaunchedEffect(showComments, media.mediaUri, commentsLoaded, commentsLimit) {
        if (!showComments || !commentsAvailable || commentsLoaded || commentsLoading) return@LaunchedEffect
        commentsLoading = true
        commentsError = null
        val previousCount = comments.size
        try {
            val batch = YoutubeStreamResolver.commentsBatch(
                context = context,
                url = media.mediaUri,
                limit = commentsLimit,
                forceRefresh = commentsForceRefresh
            )
            commentsForceRefresh = false
            comments = batch.items
            commentsDisabled = batch.commentsDisabled
            commentsHasMore =
                batch.hasMore &&
                batch.items.size < 80 &&
                (previousCount == 0 || batch.items.size > previousCount)
            commentsLoaded = true
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            commentsError = t.message ?: "Comments are temporarily unavailable"
            commentsLoaded = true
        } finally {
            commentsLoading = false
        }
    }

    LaunchedEffect(
        media.id,
        media.mediaUri,
        commentsAvailable,
        displayTitle,
        autoLoadEpisodes,
        playbackReadyForCompanionLoads
    ) {
        episodeShelf = null
        episodesLoading = false
        if (!autoLoadEpisodes || !playbackReadyForCompanionLoads) return@LaunchedEffect
        if (!commentsAvailable || YoutubeUrls.videoId(media.mediaUri) == null) return@LaunchedEffect
        // Browser-intercepted native playback initially arrives as "Now Playing"; wait for the
        // resolver's real YouTube title so episode matching does not search for that placeholder.
        if (displayTitle.isBlank() || displayTitle.equals("Now Playing", ignoreCase = true)) {
            episodesLoading = false
            return@LaunchedEffect
        }
        episodesLoading = true
        try {
            episodeShelf = YoutubeStreamResolver.discoverEpisodeShelf(
                url = media.mediaUri,
                titleHint = displayTitle
            )
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            // Keep the ordinary Up next feed available when episode discovery is unavailable.
            episodeShelf = null
        } finally {
            episodesLoading = false
        }
    }

    val currentVideoId = YoutubeUrls.videoId(media.mediaUri).orEmpty()
    val episodeItems = episodeShelf?.episodes.orEmpty()
        .filterNot { it.videoId == currentVideoId }
        .distinctBy { it.videoId }

    Column(modifier = modifier.background(TubeDeckSurface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 9.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    displayTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            IconButton(onClick = onEnterPip, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.PictureInPictureAlt,
                    "Picture in picture",
                    tint = Color.White,
                    modifier = Modifier.size(21.dp)
                )
            }
        }

        if (commentsAvailable || onDownloadVideo != null || onDownloadPlaylist != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (commentsAvailable) {
                    FilledTonalButton(
                        onClick = {
                            if (showDescription) showDescription = false
                            onToggleComments()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                        modifier = Modifier.heightIn(min = 34.dp)
                    ) {
                        Icon(
                            if (showComments) Icons.Default.Close else Icons.Default.Comment,
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (showComments) "Hide comments" else "Comments", fontSize = 10.5.sp)
                    }

                    FilledTonalButton(
                        onClick = {
                            val opening = !showDescription
                            showDescription = opening
                            if (opening && showComments) onToggleComments()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                        modifier = Modifier.heightIn(min = 34.dp)
                    ) {
                        Icon(
                            if (showDescription) Icons.Default.Close else Icons.Default.Description,
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (showDescription) "Hide description" else "Description", fontSize = 10.5.sp)
                    }
                }

                if (onDownloadVideo != null || onDownloadPlaylist != null) {
                    DownloadActionStrip(onDownloadVideo, onDownloadPlaylist)
                }

                if (showComments) {
                    IconButton(onClick = ::refreshComments, enabled = !commentsLoading, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Default.Refresh,
                            "Refresh comments",
                            tint = TubeDeckTextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = TubeDeckBorder)

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                showDescription -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                displayTitle,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        if (uploaderName.isNotBlank()) {
                            item {
                                Text(
                                    uploaderName,
                                    color = TubeDeckAccentMint,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                        item {
                            val metadata = buildString {
                                if (viewCount >= 0L) {
                                    append(String.format(Locale.US, "%,d views", viewCount))
                                }
                                if (uploadDateText.isNotBlank()) {
                                    if (isNotEmpty()) append(" • ")
                                    append(uploadDateText)
                                }
                            }
                            if (metadata.isNotBlank()) {
                                Text(metadata, color = TubeDeckTextSecondary, fontSize = 10.5.sp)
                            }
                        }
                        item { HorizontalDivider(color = TubeDeckBorder.copy(alpha = .72f)) }
                        item {
                            RichYoutubeDescription(
                                description = description.ifBlank { "No video description was provided." },
                                isHtml = descriptionIsHtml,
                                onSeekToSeconds = onSeekDescriptionTimestamp
                            )
                        }
                    }
                }

                showComments && commentsLoading && comments.isEmpty() -> {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Loading comments…", color = TubeDeckTextSecondary, fontSize = 12.sp)
                    }
                }

                showComments && commentsDisabled -> {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Comment, null, tint = TubeDeckTextSecondary)
                        Spacer(Modifier.height(8.dp))
                        Text("Comments are disabled for this video", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                showComments && commentsError != null -> {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Text("Comments unavailable", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text(commentsError.orEmpty(), color = TubeDeckTextSecondary, fontSize = 11.sp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = ::refreshComments, enabled = !commentsLoading) { Text("Retry") }
                    }
                }

                showComments -> {
                    if (comments.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopStart) {
                            Text("No comments available", color = TubeDeckTextSecondary, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(comments, key = { it.id }) { comment ->
                                NativeCommentRow(comment)
                            }

                            if (commentsLoading) {
                                item(key = "comments-loading-more") {
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Loading more comments…", color = TubeDeckTextSecondary, fontSize = 11.sp)
                                    }
                                }
                            } else if (commentsHasMore && comments.size < 80) {
                                item(key = "comments-load-more") {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        OutlinedButton(onClick = ::loadMoreComments) {
                                            Text("Load more")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    if (playlistItems.isNotEmpty() && playlistIndex >= 0) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            item(key = "playlist-header") {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PlaylistPlay, null, tint = TubeDeckAccentCyan)
                                    Spacer(Modifier.width(7.dp))
                                    Text(
                                        "Playlist • ${(playlistIndex + 1).coerceAtMost(playlistItems.size)}/${playlistItems.size}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            items(playlistItems.indices.toList(), key = { playlistItems[it].videoId }) { index ->
                                val item = playlistItems[index]
                                val current = index == playlistIndex
                                Surface(
                                    color = if (current) TubeDeckAccentCyan.copy(alpha = .16f) else TubeDeckSurfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            enabled = !current && onPlayPlaylistItem != null,
                                            onClick = { onPlayPlaylistItem?.invoke(index) }
                                        )
                                ) {
                                    Row(
                                        Modifier.padding(7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            Modifier.size(76.dp, 44.dp).clip(RoundedCornerShape(6.dp)),
                                            color = Color.Black
                                        ) {
                                            AsyncImage(
                                                model = item.thumbnailUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Column(Modifier.weight(1f).padding(start = 9.dp)) {
                                            Text(
                                                item.title.ifBlank { "YouTube video" },
                                                color = Color.White,
                                                fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 12.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val detail = listOfNotNull(
                                                item.channel.takeIf { it.isNotBlank() && it != "YouTube" },
                                                item.duration.takeIf { it.isNotBlank() }
                                            ).joinToString(" • ")
                                            if (detail.isNotBlank()) {
                                                Text(
                                                    detail,
                                                    color = TubeDeckTextSecondary,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (current) {
                                            Icon(Icons.Default.Equalizer, "Playing", tint = TubeDeckAccentCyan)
                                        } else {
                                            Text(
                                                (index + 1).toString(),
                                                color = TubeDeckTextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (!playbackReadyForCompanionLoads && (autoLoadEpisodes || autoLoadUpNext)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            // This companion panel exists only below the compact portrait player,
                            // whose center Play/Pause outer diameter is exactly 52 dp.
                            CircularProgressIndicator(
                                modifier = Modifier.size(52.dp),
                                strokeWidth = 4.dp,
                                color = TubeDeckAccentCyan,
                                trackColor = Color.White.copy(alpha = .26f)
                            )
                        }
                    } else if (!autoLoadEpisodes && !autoLoadUpNext) {
                        Box(Modifier.fillMaxSize())
                    } else if (relatedVideos.isEmpty() && episodeItems.isEmpty() && !episodesLoading) {
                        Box(
                            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
                            contentAlignment = Alignment.TopStart
                        ) {
                            Text(
                                if (autoLoadUpNext) "Up next" else "Episodes",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (autoLoadEpisodes && episodesLoading) {
                                item(key = "episodes-loading") {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Episodes",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }

                            if (autoLoadEpisodes && episodeItems.isNotEmpty()) {
                                item(key = "episode-shelf") {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "More episodes",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                Icons.Default.PlaylistPlay,
                                                contentDescription = null,
                                                tint = TubeDeckAccentMint,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        LazyRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(end = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(
                                                items = episodeItems,
                                                key = { "episode-${it.videoId}" }
                                            ) { episode ->
                                                EpisodeVideoCard(episode) {
                                                    onPlayRelated?.invoke(
                                                        OfflineMedia(
                                                            id = "episode-${episode.videoId}-${System.nanoTime()}",
                                                            title = episode.title,
                                                            videoId = episode.videoId,
                                                            thumbnailUrl = episode.thumbnailUrl,
                                                            mediaUri = "https://www.youtube.com/watch?v=${episode.videoId}"
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                item(key = "episode-divider") {
                                    HorizontalDivider(color = TubeDeckBorder.copy(alpha = .72f))
                                }
                            }

                            if (autoLoadUpNext) item(key = "up-next-header") {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Up next",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )

                                }
                            }

                            if (autoLoadUpNext && relatedVideos.isNotEmpty()) {
                                items(relatedVideos, key = { "related-${it.videoId}" }) { related ->
                                    RelatedVideoRow(related) {
                                        onPlayRelated?.invoke(
                                            OfflineMedia(
                                                id = "related-${related.videoId}-${System.nanoTime()}",
                                                title = related.title,
                                                videoId = related.videoId,
                                                thumbnailUrl = related.thumbnailUrl,
                                                mediaUri = related.url
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun normalizeYoutubeDescriptionMarkup(source: String): String =
    source
        .replace(
            Regex("(?i)\\bhash\\s*tag\\s*:?\\s*#?([\\p{L}\\p{N}_]+)"),
        ) { match -> "#${match.groupValues[1]}" }
        .replace(
            Regex("(?i)\\bhashtag\\s*:?\\s*#?([\\p{L}\\p{N}_]+)"),
        ) { match -> "#${match.groupValues[1]}" }

@Composable
private fun RichYoutubeDescription(
    description: String,
    isHtml: Boolean,
    onSeekToSeconds: (Long) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            android.widget.TextView(ctx).apply {
                setTextColor(android.graphics.Color.WHITE)
                setLinkTextColor(android.graphics.Color.rgb(62, 166, 255))
                textSize = 12f
                setLineSpacing(0f, 1.18f)
                setTextIsSelectable(true)
                linksClickable = true
                movementMethod = LinkMovementMethod.getInstance()
                setPadding(0, 0, 0, 0)
            }
        },
        update = { textView ->
            val normalized = normalizeYoutubeDescriptionMarkup(description)
            val parsed = if (isHtml) {
                Html.fromHtml(normalized, Html.FROM_HTML_MODE_LEGACY)
            } else {
                normalized
            }
            val rich = SpannableStringBuilder(parsed)

            // Keep explicit URLs/email addresses actionable even when the extractor returned plain text.
            Linkify.addLinks(rich, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)

            // YouTube hashtags should remain real #hashtags and behave as links.
            Regex("(?<![\\p{L}\\p{N}_])#([\\p{L}\\p{N}_]+)")
                .findAll(rich.toString())
                .forEach { match ->
                    val start = match.range.first
                    val end = match.range.last + 1
                    val existing = rich.getSpans(start, end, URLSpan::class.java)
                    if (existing.isEmpty()) {
                        val tag = match.groupValues[1]
                        rich.setSpan(
                            URLSpan("https://www.youtube.com/hashtag/$tag"),
                            start,
                            end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }

            // Make YouTube-style timestamps actionable in the native player.
            Regex("(?<!\\d)(?:(\\d{1,3}):)?([0-5]?\\d):([0-5]\\d)(?!\\d)")
                .findAll(rich.toString())
                .forEach { match ->
                    val start = match.range.first
                    val end = match.range.last + 1
                    if (rich.getSpans(start, end, URLSpan::class.java).isEmpty()) {
                        val hours = match.groupValues[1].toLongOrNull() ?: 0L
                        val minutes = match.groupValues[2].toLongOrNull() ?: 0L
                        val seconds = match.groupValues[3].toLongOrNull() ?: 0L
                        val totalSeconds = hours * 3_600L + minutes * 60L + seconds
                        rich.setSpan(
                            object : ClickableSpan() {
                                override fun onClick(widget: View) {
                                    onSeekToSeconds(totalSeconds)
                                }

                                override fun updateDrawState(ds: TextPaint) {
                                    ds.color = android.graphics.Color.rgb(62, 166, 255)
                                    ds.isUnderlineText = false
                                }
                            },
                            start,
                            end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }

            textView.text = rich
        }
    )
}

@Composable
private fun EpisodeVideoCard(
    episode: YoutubeStreamResolver.DiscoveredEpisode,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clickable(enabled = true, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            AsyncImage(
                model = episode.thumbnailUrl,
                contentDescription = episode.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
            )
            if (episode.durationSeconds > 0L) {
                Surface(
                    color = Color.Black.copy(alpha = .82f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp)
                ) {
                    Text(
                        formatDuration(episode.durationSeconds * 1000L),
                        color = Color.White,
                        fontSize = 8.5.sp,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }
            episode.episodeNumber?.let { number ->
                Surface(
                    color = TubeDeckAccentMint.copy(alpha = .92f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(3.dp)
                ) {
                    Text(
                        "EP ${number}",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
        Text(
            episode.title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.5.sp,
            lineHeight = 13.sp
        )
        Text(
            episode.uploader,
            color = TubeDeckTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun RelatedVideoRow(
    video: YoutubeStreamResolver.RelatedVideo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = true, onClick = onClick),
        verticalAlignment = Alignment.Top
    ) {
        Box(modifier = Modifier.width(128.dp).aspectRatio(16f / 9f)) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
            )
            if (video.durationSeconds > 0L) {
                Surface(
                    color = Color.Black.copy(alpha = .82f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp)
                ) {
                    Text(
                        formatDuration(video.durationSeconds * 1000L),
                        color = Color.White,
                        fontSize = 8.5.sp,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                video.title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 15.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                video.channelTitle,
                color = TubeDeckTextSecondary,
                fontSize = 10.sp
            )
            if (video.viewCount >= 0L) {
                Text(
                    String.format(Locale.US, "%,d views", video.viewCount),
                    color = TubeDeckTextSecondary,
                    fontSize = 9.5.sp
                )
            }
        }
        Icon(
            Icons.Default.MoreVert,
            null,
            tint = TubeDeckTextSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun NativeCommentRow(comment: YoutubeStreamResolver.NativeComment) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    comment.author.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.author,
                    color = Color.White,
                    fontWeight = if (comment.channelOwner) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (comment.pinned) {
                    Spacer(Modifier.width(6.dp))
                    Text("Pinned", color = TubeDeckAccentCyan, fontSize = 9.sp)
                }
                if (comment.publishedText.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(comment.publishedText, color = TubeDeckTextSecondary, fontSize = 10.sp)
                }
            }
            Text(comment.text, color = Color.White.copy(alpha = .92f), fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (comment.likeText.isNotBlank()) {
                    Icon(Icons.Default.ThumbUp, null, tint = TubeDeckTextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(comment.likeText, color = TubeDeckTextSecondary, fontSize = 10.sp)
                }
                if (comment.replyCount > 0) {
                    if (comment.likeText.isNotBlank()) Spacer(Modifier.width(14.dp))
                    Icon(Icons.Default.ChatBubbleOutline, null, tint = TubeDeckTextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${comment.replyCount} replies", color = TubeDeckTextSecondary, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun PlayerSettingsDialog(
    captionsEnabled: Boolean,
    hasCaptions: Boolean,
    gestureEnabled: Boolean,
    autoHideControls: Boolean,
    fullscreen: Boolean,
    qualityOptions: List<Int>,
    currentQuality: String,
    autoQuality: Boolean,
    resizeMode: Int,
    liveDubEnabled: Boolean,
    dubSourceCode: String,
    dubMethod: String,
    captionLanguageTag: String,
    dubTargetCode: String,
    dubVoiceProfile: String,
    dubOriginalVolume: Float,
    dubStatus: String,
    availableSubtitles: List<YoutubeStreamResolver.Subtitle>,
    onLiveDub: (Boolean) -> Unit,
    onDubMethod: (String) -> Unit,
    onCaptionLanguage: (String) -> Unit,
    onDubSource: (String) -> Unit,
    onDubTarget: (String) -> Unit,
    onDubVoiceProfile: (String) -> Unit,
    onDubOriginalVolume: (Float) -> Unit,
    onCaptions: (Boolean) -> Unit,
    onGestures: (Boolean) -> Unit,
    onAutoHide: (Boolean) -> Unit,
    onQuality: (Int?) -> Unit,
    onResizeMode: (Int) -> Unit,
    onRotate: () -> Unit,
    onFullscreen: (Boolean) -> Unit,
    onPip: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Player settings") },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Gestures", Modifier.weight(1f))
                    Switch(gestureEnabled, onGestures)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-hide controls", Modifier.weight(1f))
                    Switch(autoHideControls, onAutoHide)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (hasCaptions) "Captions" else "Captions unavailable", Modifier.weight(1f))
                    Switch(captionsEnabled, onCaptions, enabled = hasCaptions)
                }
                if (hasCaptions) {
                    var captionMenuExpanded by remember { mutableStateOf(false) }
                    Text("Caption language", fontWeight = FontWeight.SemiBold)
                    Box {
                        OutlinedButton(onClick = { captionMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (captionLanguageTag == "auto") "Auto"
                                else availableSubtitles.firstOrNull { it.languageTag == captionLanguageTag }?.displayName
                                    ?: captionLanguageTag
                            )
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = captionMenuExpanded, onDismissRequest = { captionMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Auto") },
                                onClick = { captionMenuExpanded = false; onCaptionLanguage("auto") }
                            )
                            availableSubtitles.distinctBy { it.languageTag + ":" + it.autoGenerated }.forEach { subtitle ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            subtitle.displayName + if (subtitle.autoGenerated) " • Auto-generated" else ""
                                        )
                                    },
                                    onClick = { captionMenuExpanded = false; onCaptionLanguage(subtitle.languageTag) }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Live Dub", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Switch(liveDubEnabled, onLiveDub)
                }

                var dubMethodMenuExpanded by remember { mutableStateOf(false) }
                Text("Dubbing method", fontWeight = FontWeight.SemiBold)
                Box {
                    val methodLabel = when (dubMethod) {
                        "audio" -> "Audio (Whisper)"
                        "captions" -> "Captions"
                        else -> "Auto • prefer captions"
                    }
                    OutlinedButton(onClick = { dubMethodMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(methodLabel)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = dubMethodMenuExpanded, onDismissRequest = { dubMethodMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Auto • prefer captions") },
                            onClick = { dubMethodMenuExpanded = false; onDubMethod("auto") }
                        )
                        DropdownMenuItem(
                            text = { Text("Captions") },
                            onClick = { dubMethodMenuExpanded = false; onDubMethod("captions") }
                        )
                        DropdownMenuItem(
                            text = { Text("Audio (Whisper)") },
                            onClick = { dubMethodMenuExpanded = false; onDubMethod("audio") }
                        )
                    }
                }

                Text("Dub source", fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = dubSourceCode == "auto",
                        onClick = { onDubSource("auto") },
                        label = { Text("Auto") }
                    )
                    LiveDubLanguages.supported.forEach { lang ->
                        val present = availableSubtitles.any { it.languageTag.substringBefore('-').equals(lang.code, ignoreCase = true) }
                        FilterChip(
                            selected = dubSourceCode == lang.code,
                            onClick = { onDubSource(lang.code) },
                            enabled = true,
                            label = { Text(lang.label) }
                        )
                    }
                }

                Text("Dub target", fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LiveDubLanguages.supported.forEach { lang ->
                        FilterChip(
                            selected = dubTargetCode == lang.code,
                            onClick = { onDubTarget(lang.code) },
                            label = { Text(lang.label) }
                        )
                    }
                }

                Text("Voice profile", fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LiveDubVoiceProfiles.supported.forEach { voice ->
                        FilterChip(
                            selected = dubVoiceProfile == voice.id,
                            onClick = { onDubVoiceProfile(voice.id) },
                            label = { Text(voice.label) }
                        )
                    }
                }

                Text("Original audio while dubbing: ${(dubOriginalVolume * 100).toInt()}%", fontSize = 12.sp)
                Slider(
                    value = dubOriginalVolume,
                    onValueChange = onDubOriginalVolume,
                    valueRange = 0f..0.45f
                )
                HorizontalDivider()
                Text("Quality", fontWeight = FontWeight.Bold)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(selected = autoQuality, onClick = { onQuality(null) }, label = { Text("Auto") })
                    qualityOptions.forEach { h ->
                        FilterChip(
                            selected = !autoQuality && currentQuality == "${h}p",
                            onClick = { onQuality(h) },
                            label = { Text("${h}p") }
                        )
                    }
                }

                Text("Video size / aspect", fontWeight = FontWeight.Bold)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
                        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch"
                    ).forEach { (mode, label) ->
                        FilterChip(selected = resizeMode == mode, onClick = { onResizeMode(mode) }, label = { Text(label) })
                    }
                }

                OutlinedButton(onClick = onRotate, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ScreenRotation, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Switch portrait / landscape")
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Fullscreen", Modifier.weight(1f))
                    Switch(fullscreen, onFullscreen)
                }
                Button(onClick = onPip, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PictureInPictureAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Enter Picture-in-Picture")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun HoldSeekButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    direction: Int,
    exoPlayer: ExoPlayer,
    duration: Long,
    seekIntervalSeconds: Int
) {
    var holding by remember { mutableStateOf(false) }
    LaunchedEffect(holding) {
        while (holding) {
            val target = exoPlayer.currentPosition + direction * 350L
            exoPlayer.seekTo(target.coerceIn(0L, duration.coerceAtLeast(0L)))
            delay(60)
        }
    }
    IconButton(
        onClick = {
            val target = exoPlayer.currentPosition + direction * seekIntervalSeconds.coerceIn(5, 60) * 1_000L
            exoPlayer.seekTo(target.coerceIn(0L, duration.coerceAtLeast(0L)))
        },
        modifier = Modifier.size(44.dp).pointerInput(direction, duration, seekIntervalSeconds) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val releasedQuickly = withTimeoutOrNull(450L) { waitForUpOrCancellation() } != null
                if (!releasedQuickly) {
                    holding = true
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                    }
                    holding = false
                }
            }
        }
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(34.dp))
    }
}

@Composable
private fun SeekPreviewThumbnail(
    fraction: Float,
    durationMs: Long,
    storyboard: YoutubeStreamResolver.PreviewStoryboard?,
    chapters: List<YoutubeStreamResolver.Chapter>,
    fallbackThumbnailUrl: String
) {
    val normalized = fraction.coerceIn(0f, 1f)
    val positionMs = (normalized * durationMs.coerceAtLeast(0L)).toLong()
    val previewWidth = 160.dp
    val storyboardHeight = storyboard?.let {
        (160f * it.frameHeight.toFloat() / it.frameWidth.coerceAtLeast(1).toFloat()).dp
    } ?: 90.dp
    val previewHeight = storyboardHeight.coerceIn(76.dp, 110.dp)
    val chapter = chapters.lastOrNull { it.startTimeSeconds * 1_000L <= positionMs }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = Color.Black.copy(alpha = .92f),
            shape = RoundedCornerShape(10.dp),
            tonalElevation = 4.dp
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(previewWidth, previewHeight)
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        .background(Color.Black)
                ) {
                    if (storyboard != null && storyboard.urls.isNotEmpty()) {
                        val frameDuration = storyboard.durationPerFrameMs.coerceAtLeast(1)
                        val frameNumber = (positionMs / frameDuration)
                            .toInt()
                            .coerceIn(0, (storyboard.totalCount - 1).coerceAtLeast(0))
                        val framesPerPage = (storyboard.framesPerPageX * storyboard.framesPerPageY).coerceAtLeast(1)
                        val page = (frameNumber / framesPerPage).coerceIn(0, storyboard.urls.lastIndex)
                        val relative = frameNumber % framesPerPage
                        val column = relative % storyboard.framesPerPageX.coerceAtLeast(1)
                        val row = relative / storyboard.framesPerPageX.coerceAtLeast(1)
                        val sheetWidth = previewWidth * storyboard.framesPerPageX.toFloat()
                        val sheetHeight = previewHeight * storyboard.framesPerPageY.toFloat()

                        AsyncImage(
                            model = storyboard.urls[page],
                            contentDescription = "Seek preview",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .requiredSize(sheetWidth, sheetHeight)
                                .offset(
                                    x = (-previewWidth.value * column).dp,
                                    y = (-previewHeight.value * row).dp
                                )
                        )
                    } else if (fallbackThumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = fallbackThumbnailUrl,
                            contentDescription = "Seek preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Row(
                    modifier = Modifier.width(previewWidth).padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatDuration(positionMs),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                    chapter?.title?.takeIf { it.isNotBlank() }?.let { title ->
                        Spacer(Modifier.width(7.dp))
                        Text(
                            title,
                            color = Color.White.copy(alpha = .82f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 9.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(3.dp))
    }
}

@Composable
private fun CompactSeekBar(
    progress: Float,
    chapterFractions: List<Float> = emptyList(),
    sponsorRanges: List<Pair<Float, Float>> = emptyList(),
    onScrubChange: (Float?) -> Unit,
    onSeekFinished: (Float) -> Unit
) {
    val normalized = progress.coerceIn(0f, 1f)
    Canvas(
        modifier = Modifier.fillMaxWidth()
            .height(20.dp)
            .pointerInput(chapterFractions, sponsorRanges) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var latest = 0f
                    fun update(x: Float) {
                        if (size.width > 0) {
                            latest = (x / size.width.toFloat()).coerceIn(0f, 1f)
                            onScrubChange(latest)
                        }
                    }
                    update(down.position.x)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        update(change.position.x)
                        change.consume()
                        if (!change.pressed) break
                    }
                    onSeekFinished(latest)
                    onScrubChange(null)
                }
            }
    ) {
        val y = size.height / 2f
        val activeX = size.width * normalized
        drawLine(
            color = Color.White.copy(alpha = .32f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
        sponsorRanges.forEach { range ->
            val startX = size.width * range.first.coerceIn(0f, 1f)
            val endX = size.width * range.second.coerceIn(0f, 1f)
            if (endX > startX) {
                drawLine(
                    color = TubeDeckAccentMint,
                    start = Offset(startX, y),
                    end = Offset(endX, y),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Butt
                )
            }
        }
        chapterFractions.forEach { fraction ->
            val x = size.width * fraction.coerceIn(0f, 1f)
            drawLine(
                color = Color.White.copy(alpha = .82f),
                start = Offset(x, y - 5.dp.toPx()),
                end = Offset(x, y + 5.dp.toPx()),
                strokeWidth = 1.5.dp.toPx()
            )
        }
        drawLine(
            color = TubeDeckAccentCyan,
            start = Offset(0f, y),
            end = Offset(activeX, y),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = TubeDeckAccentCyan,
            radius = 4.5.dp.toPx(),
            center = Offset(activeX, y)
        )
    }
}

@Composable
private fun FadeVisibility(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        content()
    }
}

private fun chooseDubSourceTag(
    resolved: YoutubeStreamResolver.ResolvedMedia?,
    requestedCode: String
): String? {
    val subtitles = resolved?.subtitles.orEmpty()
    if (requestedCode != "auto") {
        return subtitles.firstOrNull {
            it.languageTag.substringBefore('-').equals(requestedCode, ignoreCase = true)
        }?.languageTag ?: requestedCode
    }
    if (subtitles.isEmpty()) return null

    // Prefer a human-created supported track, then an auto-generated supported track. English
    // is not forced, so the video's original-language captions can remain the source whenever
    // the extractor exposes them first.
    return subtitles.firstOrNull { !it.autoGenerated && LiveDubLanguages.byCode(it.languageTag) != null }?.languageTag
        ?: subtitles.firstOrNull { LiveDubLanguages.byCode(it.languageTag) != null }?.languageTag
}

private fun isPipMode(activity: Activity?): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        activity?.isInPictureInPictureMode == true
    } else {
        false
    }
}

@OptIn(UnstableApi::class)
private fun buildResolvedSource(
    factory: DefaultDataSource.Factory,
    resolved: YoutubeStreamResolver.ResolvedMedia,
    requestedHeight: Int
): MediaSource {
    val option = resolved.options.filter { it.height <= requestedHeight }.maxByOrNull { it.height }
        ?: resolved.options.maxByOrNull { it.height }
        ?: throw IllegalStateException("No playable video stream was extracted")
    val subtitles = resolved.subtitles.map { subtitle ->
        SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
            .setMimeType(subtitle.mimeType)
            .setLanguage(subtitle.languageTag)
            .setLabel(subtitle.displayName)
            .build()
    }
    val videoItem = MediaItem.Builder()
        .setUri(Uri.parse(option.videoUrl))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(resolved.title)
                .build()
        )
        .setSubtitleConfigurations(subtitles)
        .build()
    // DefaultMediaSourceFactory is responsible for merging side-loaded subtitle sources.
    val videoSource = DefaultMediaSourceFactory(factory).createMediaSource(videoItem)
    val audioUrl = option.audioUrl
    if (audioUrl.isNullOrBlank()) return videoSource
    val audioItem = MediaItem.fromUri(Uri.parse(audioUrl))
    val audioSource = ProgressiveMediaSource.Factory(factory).createMediaSource(audioItem)
    return MergingMediaSource(videoSource, audioSource)
}

private fun isTvLikePlaybackDevice(context: Context): Boolean {
    val uiType = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    val leanback = runCatching {
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
    }.getOrDefault(false)
    val television = runCatching {
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEVISION)
    }.getOrDefault(false)
    return uiType == Configuration.UI_MODE_TYPE_TELEVISION || leanback || television
}

private fun chooseFastStartHeight(
    resolved: YoutubeStreamResolver.ResolvedMedia,
    autoTargetHeight: Int
): Int {
    val progressiveHeights = resolved.progressiveOptions
        .map { it.height }
        .filter { it > 0 }
        .distinct()
        .sorted()

    if (progressiveHeights.isEmpty()) return autoTargetHeight

    // Prefer a single progressive audio+video representation for startup. This avoids waiting
    // for two independent adaptive sources to buffer/merge before the first frame. Higher
    // extracted resolutions remain available through the quality chooser.
    return progressiveHeights
        .filter { it <= autoTargetHeight }
        .maxOrNull()
        ?: progressiveHeights.first()
}

private fun chooseAutoHeight(context: Context, heights: List<Int>, measuredBitrate: Long): Int {
    if (heights.isEmpty()) return 720
    val available = heights.distinct().sorted()
    val connectionEstimate = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return@runCatching 0L
        val capabilities = manager.getNetworkCapabilities(network) ?: return@runCatching 0L
        val linkEstimate = capabilities.linkDownstreamBandwidthKbps.toLong().coerceAtLeast(0L) * 1000L
        when {
            linkEstimate > 0L -> linkEstimate
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 5_000_000L
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 12_000_000L
            else -> 7_000_000L
        }
    }.getOrDefault(0L)

    val measured = measuredBitrate.takeIf { it in 250_000L..500_000_000L } ?: 0L
    val estimate = when {
        measured > 0L && connectionEstimate > 0L -> minOf(measured, connectionEstimate)
        measured > 0L -> measured
        connectionEstimate > 0L -> connectionEstimate
        else -> 6_000_000L
    }
    val budget = (estimate * 0.68).toLong()
    val targetHeight = when {
        budget >= 35_000_000L -> 4320
        budget >= 16_000_000L -> 2160
        budget >= 8_000_000L -> 1440
        budget >= 4_500_000L -> 1080
        budget >= 2_500_000L -> 720
        budget >= 1_200_000L -> 480
        budget >= 650_000L -> 360
        budget >= 350_000L -> 240
        else -> 144
    }
    return available.filter { it <= targetHeight }.maxOrNull() ?: available.first()
}

private fun nextResizeMode(current: Int): Int = when (current) {
    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
}

private fun resizeModeLabel(mode: Int): String = when (mode) {
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
    else -> "Fit"
}

private fun cycleSpeed(player: ExoPlayer): Float {
    val speeds = floatArrayOf(.25f, .5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    val next = speeds.firstOrNull { it > player.playbackParameters.speed + 0.001f } ?: speeds.first()
    player.setPlaybackSpeed(next)
    return next
}

private fun setFullscreen(activity: Activity, fullscreen: Boolean) {
    val window = activity.window
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.systemBarsBehavior =
        androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

    if (fullscreen) {
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

