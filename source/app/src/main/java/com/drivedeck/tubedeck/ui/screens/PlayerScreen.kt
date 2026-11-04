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
import android.view.LayoutInflater
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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import android.content.Intent
import android.os.SystemClock
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.drivedeck.tubedeck.R
import com.drivedeck.tubedeck.player.PlaybackHardwareButtonBridge
import com.drivedeck.tubedeck.player.PlaybackDiagnostics
import com.drivedeck.tubedeck.player.PlaybackQualityPolicy
import com.drivedeck.tubedeck.player.PlaybackResourcePolicy
import com.drivedeck.tubedeck.player.CarHeadUnitPolicy
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
import com.drivedeck.tubedeck.data.model.ViewingMode
import com.drivedeck.tubedeck.data.model.ViewingModePolicy
import com.drivedeck.tubedeck.data.model.PlayerMode
import com.drivedeck.tubedeck.data.model.PlaybackContinuationMode
import com.drivedeck.tubedeck.data.model.PlaylistItem
import com.drivedeck.tubedeck.data.model.SponsorSegment
import com.drivedeck.tubedeck.player.LiveDubEngine
import com.drivedeck.tubedeck.player.LiveDubLanguages
import com.drivedeck.tubedeck.player.LiveDubInputMode
import com.drivedeck.tubedeck.player.PlaybackPcmTapProcessor
import com.drivedeck.tubedeck.player.NightAudioController
import com.drivedeck.tubedeck.player.PlaybackEqualizerController
import com.drivedeck.tubedeck.player.PlaybackStartupTelemetry
import com.drivedeck.tubedeck.player.PlaybackSurfaceGesture
import com.drivedeck.tubedeck.player.PlaybackSurfaceGesturePolicy
import com.drivedeck.tubedeck.player.LiveDubVoiceProfiles
import com.drivedeck.tubedeck.player.YoutubeWatchNextClient
import com.drivedeck.tubedeck.player.PlaylistQueuePolicy
import com.drivedeck.tubedeck.player.YoutubeStreamResolver
import com.drivedeck.tubedeck.player.YoutubeUrls
import com.drivedeck.tubedeck.ui.components.GoogleAccountButton
import com.drivedeck.tubedeck.ui.components.isTvLikeNavigationDevice
import com.drivedeck.tubedeck.ui.components.tvInputHighlight
import com.drivedeck.tubedeck.ui.components.TvRemoteNavigationPolicy
import com.drivedeck.tubedeck.ui.components.TvPlayerKeyAction
import com.drivedeck.tubedeck.ui.components.TvRemoteKeyLearningPolicy
import com.drivedeck.tubedeck.ui.components.VideoBookmarksDialog
import com.drivedeck.tubedeck.ui.components.FocusAnimatedThumbs
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.compose.AsyncImagePainter
import com.drivedeck.tubedeck.player.PlaybackErrorAdvisor
import com.drivedeck.tubedeck.player.SeekPreviewPolicy
import com.drivedeck.tubedeck.player.VideoResumePolicy
import com.drivedeck.tubedeck.player.VideoResumeStore
import com.drivedeck.tubedeck.player.CreatorLinksPolicy
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
    sideBySideEpisodeFeeds: Boolean = true,
    showRelevantShelf: Boolean = true,
    showEpisodesShelf: Boolean = true,
    floatingCardsMode: String = "Manual",
    animatedCardPreviews: Boolean = false,
    hideWatchedRecommendations: Boolean = true,
    lowResourceMode: Boolean = false,
    resumeAfterInterruptions: Boolean = true,
    subtitleTextScale: Float = 1.0f,
    subtitleBottomInsetDp: Int = 0,
    remoteNextKeyCode: Int = 0,
    remotePreviousKeyCode: Int = 0,
    playerControllerScale: Float = 1.20f,
    equalizerTextScale: Float = 1.25f,
    playbackContinuationMode: PlaybackContinuationMode = PlaybackContinuationMode.AUTO_NEXT,
    onPlaybackContinuationModeChanged: (PlaybackContinuationMode) -> Unit = {},
    preferredPlaybackQualityHeight: Int = 0,
    preferredPlaybackSpeed: Float = 1.0f,
    seekIntervalSeconds: Int = 10,
    viewingMode: ViewingMode = ViewingMode.NORMAL,
    viewingDimPercent: Int = 55,
    nightAudioEnabled: Boolean = false,
    initialPositionMs: Long = 0L,
    resumeGeneration: Int = 0,
    pauseGeneration: Int = 0,
    foregroundPlaybackAllowed: Boolean = true,
    onClose: () -> Unit,
    onEnterPip: () -> Unit,
    onAudioOnly: () -> Unit = {},
    onPlaybackPreferenceChanged: (Float, Int, Int) -> Unit = { _, _, _ -> },
    onViewingPreferencesChanged: (ViewingMode, Int, Boolean) -> Unit = { _, _, _ -> },
    onDownloadVideo: (() -> Unit)? = null,
    onDownloadPlaylist: (() -> Unit)? = null,
    onPlayRelated: ((OfflineMedia) -> Unit)? = null,
    onPreviousItem: (() -> Unit)? = null,
    onNextItem: (() -> Unit)? = null,
    playedVideoIds: Set<String> = emptySet(),
    onPlaybackEnded: (() -> Unit)? = null,
    playlistItems: List<PlaylistItem> = emptyList(),
    playlistIndex: Int = -1,
    onPlayPlaylistItem: ((Int) -> Unit)? = null,
    googleAccount: GoogleAccountProfile? = null,
    onGoogleAccountClick: () -> Unit = {},
    onPlaybackPositionChanged: (Long) -> Unit = {},
    onPlaybackRequestedChanged: (Boolean) -> Unit = {},
    onResolvedTitleChanged: (String) -> Unit = {},
    xyAutoCompatibilityMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    // Firmware-based auto detection handles recognized XY AUTO units without
    // asking the user to enable a compatibility toggle manually.
    val carHeadUnit = xyAutoCompatibilityMode ||
        remember(context) { CarHeadUnitPolicy.isCarHeadUnit(context.applicationContext) }

    // Normal playback Back is intentionally owned by the parent Activity so its
    // single/double-Back state machine cannot be bypassed by a nested Compose handler.

    // Sensor rotation applies only while the player is visible, even with Android rotation locked.
    val originalOrientation = remember(activity) {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    DisposableEffect(activity, inPictureInPicture, autoRotatePlayback, carHeadUnit) {
        // Car displays are physically fixed. XY AUTO sensor/rotation callbacks can cause
        // destructive Activity lifecycle changes while a decoder is starting.
        val rotationAllowed = autoRotatePlayback && !carHeadUnit
        activity?.requestedOrientation = if (inPictureInPicture || !rotationAllowed) originalOrientation
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
        if (rotationAllowed && !inPictureInPicture && tiltListener.canDetectOrientation()) {
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
        youtube && (playerMode == PlayerMode.WEB || useWebFallback) -> Box(modifier.fillMaxSize()) {
            WebPlayer(
                media = media,
                onClose = onClose,
                onDownloadVideo = onDownloadVideo,
                onDownloadPlaylist = onDownloadPlaylist,
                googleAccount = googleAccount,
                onGoogleAccountClick = onGoogleAccountClick,
                modifier = Modifier.fillMaxSize()
            )
            // Native audio compression cannot process WebView's separate audio session.
            // Visual modes still apply without intercepting browser touch events.
            val dim = ViewingModePolicy.videoDim(viewingMode, viewingDimPercent, inPictureInPicture)
            if (dim > 0f) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = dim)))
            if (viewingMode == ViewingMode.NIGHT && !inPictureInPicture) {
                Box(Modifier.matchParentSize().background(Color(0xFFFFAD6A).copy(alpha = .055f)))
            }
        }

        else -> NativeExoPlayer(
            media = media,
            xyAutoCompatibilityMode = carHeadUnit,
            sponsorSegments = sponsorSegments,
            pipEnabled = pipEnabled,
            inPictureInPicture = inPictureInPicture,
            autoLoadEpisodes = autoLoadEpisodes,
            autoLoadUpNext = autoLoadUpNext,
            sideBySideEpisodeFeeds = sideBySideEpisodeFeeds,
            showRelevantShelf = showRelevantShelf,
            showEpisodesShelf = showEpisodesShelf,
            floatingCardsMode = floatingCardsMode,
            animatedCardPreviews = animatedCardPreviews,
            hideWatchedRecommendations = hideWatchedRecommendations,
            lowResourceMode = lowResourceMode,
            resumeAfterInterruptions = resumeAfterInterruptions,
            subtitleTextScale = subtitleTextScale,
            subtitleBottomInsetDp = subtitleBottomInsetDp,
            remoteNextKeyCode = remoteNextKeyCode,
            remotePreviousKeyCode = remotePreviousKeyCode,
            playerControllerScale = playerControllerScale,
            equalizerTextScale = equalizerTextScale,
            playbackContinuationMode = playbackContinuationMode,
            onPlaybackContinuationModeChanged = onPlaybackContinuationModeChanged,
            preferredPlaybackQualityHeight = preferredPlaybackQualityHeight,
            preferredPlaybackSpeed = preferredPlaybackSpeed,
            seekIntervalSeconds = seekIntervalSeconds,
            viewingMode = viewingMode,
            viewingDimPercent = viewingDimPercent,
            nightAudioEnabled = nightAudioEnabled,
            onViewingPreferencesChanged = onViewingPreferencesChanged,
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
            playedVideoIds = playedVideoIds,
            onPlaybackEnded = onPlaybackEnded,
            playlistItems = playlistItems,
            playlistIndex = playlistIndex,
            onPlayPlaylistItem = onPlayPlaylistItem,
            googleAccount = googleAccount,
            onGoogleAccountClick = onGoogleAccountClick,
            onPlaybackPositionChanged = onPlaybackPositionChanged,
            onPlaybackRequestedChanged = onPlaybackRequestedChanged,
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
    var webRenderCrashed by remember(media.id) { mutableStateOf(false) }
    var webViewGeneration by remember(media.id) { mutableIntStateOf(0) }

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
        key(webViewGeneration) {
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

                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: android.webkit.RenderProcessGoneDetail?
                        ): Boolean {
                            // Returning true contains a renderer crash inside the player instead of
                            // letting Android terminate the MyTube process. The dead WebView is
                            // replaced only when the user taps Retry.
                            webRenderCrashed = true
                            return true
                        }
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
            onRelease = { view ->
                // Embedded video must not keep its audio renderer after a player switch.
                runCatching {
                    view.evaluateJavascript(
                        """(function(){document.querySelectorAll('video,audio').forEach(function(v){v.pause();});return true;})();""", null
                    )
                    view.loadUrl("about:blank")
                    view.onPause()
                    view.stopLoading()
                }
                runCatching { view.destroy() }
            }
        )
        }

        if (webRenderCrashed) {
            Surface(
                color = Color.Black.copy(alpha = .92f),
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Text("Web player stopped", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        "The Android WebView renderer exited. MyTube stayed open.",
                        color = Color.White.copy(alpha = .8f),
                        fontSize = 12.sp
                    )
                    TextButton(
                        onClick = {
                            webRenderCrashed = false
                            webViewGeneration += 1
                        }
                    ) { Text("Retry web player") }
                }
            }
        }

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
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 68.dp, end = 12.dp)
            ) {
                DownloadActionStrip(onDownloadVideo, onDownloadPlaylist)
            }
            IconButton(
                onClick = { com.drivedeck.tubedeck.data.repository.TubeDeckRepository.get(context).enableScreenBlackout() },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(bottom = 10.dp).background(Color.Black.copy(alpha = .65f), CircleShape)
            ) {
                Icon(Icons.Default.VisibilityOff, "Black screen, continue audio", tint = Color.White)
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
    sideBySideEpisodeFeeds: Boolean,
    showRelevantShelf: Boolean,
    showEpisodesShelf: Boolean,
    floatingCardsMode: String,
    animatedCardPreviews: Boolean,
    hideWatchedRecommendations: Boolean,
    lowResourceMode: Boolean,
    resumeAfterInterruptions: Boolean,
    subtitleTextScale: Float,
    subtitleBottomInsetDp: Int,
    remoteNextKeyCode: Int,
    remotePreviousKeyCode: Int,
    playerControllerScale: Float,
    equalizerTextScale: Float,
    playbackContinuationMode: PlaybackContinuationMode,
    onPlaybackContinuationModeChanged: (PlaybackContinuationMode) -> Unit,
    preferredPlaybackQualityHeight: Int,
    preferredPlaybackSpeed: Float,
    seekIntervalSeconds: Int,
    viewingMode: ViewingMode,
    viewingDimPercent: Int,
    nightAudioEnabled: Boolean,
    onViewingPreferencesChanged: (ViewingMode, Int, Boolean) -> Unit,
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
    playedVideoIds: Set<String> = emptySet(),
    onPlaybackEnded: (() -> Unit)? = null,
    playlistItems: List<PlaylistItem> = emptyList(),
    playlistIndex: Int = -1,
    onPlayPlaylistItem: ((Int) -> Unit)? = null,
    googleAccount: GoogleAccountProfile? = null,
    onGoogleAccountClick: () -> Unit = {},
    onPlaybackPositionChanged: (Long) -> Unit = {},
    onPlaybackRequestedChanged: (Boolean) -> Unit = {},
    onResolvedTitleChanged: (String) -> Unit = {},
    xyAutoCompatibilityMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    // Firmware-based auto detection handles recognized XY AUTO units without
    // asking the user to enable a compatibility toggle manually.
    val carHeadUnit = xyAutoCompatibilityMode ||
        remember(context) { CarHeadUnitPolicy.isCarHeadUnit(context.applicationContext) }
    val resourceLimits = remember(context, carHeadUnit, lowResourceMode) {
        if (lowResourceMode) PlaybackResourcePolicy.choose(true, 160, 2, true)
        else PlaybackResourcePolicy.detect(context.applicationContext, carHeadUnit)
    }
    val latestForegroundPlaybackAllowed by rememberUpdatedState(foregroundPlaybackAllowed)
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    // Portrait controls must leave room for the fixed Lights Out affordance.
    // Cap the scale only here; landscape/TV retains its user-selected size.
    val controllerScale = if (isPortrait) playerControllerScale.coerceIn(0.85f, 1.05f) * .88f
        else playerControllerScale.coerceIn(0.85f, 1.50f)
    val controllerHeight = (34f * controllerScale).dp
    val controllerIconSize = (19f * controllerScale).dp
    val controllerFontSize = (11f * controllerScale).sp
    val controllerGap = (4f * controllerScale).dp
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val trackSelector = remember(media.id, carHeadUnit, resourceLimits) {
        DefaultTrackSelector(context).apply {
            if (carHeadUnit) {
                setParameters(buildUponParameters()
                    .setPreferredVideoMimeTypes(MimeTypes.VIDEO_H264)
                    .setPreferredAudioMimeTypes(MimeTypes.AUDIO_AAC)
                    .setMaxVideoSize(resourceLimits.maxAutoVideoWidth, resourceLimits.maxAutoVideoHeight))
            } else if (resourceLimits.constrained) {
                // Limit AUTO adaptive representations on low-end hardware, not manual user choices.
                setParameters(buildUponParameters()
                    .setMaxVideoSize(resourceLimits.maxAutoVideoWidth, resourceLimits.maxAutoVideoHeight))
            }
        }
    }
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
    val tvNavigation = remember(context) { isTvLikeNavigationDevice(context) }
    val tvSurfaceFocus = remember { FocusRequester() }
    val tvPlayPauseFocus = remember { FocusRequester() }
    val tvTopBackFocus = remember { FocusRequester() }
    val tvCardsFocus = remember { FocusRequester() }
    var tvSurfaceFocused by remember { mutableStateOf(false) }
    var tvControlsFocused by remember { mutableStateOf(false) }
    var tvPendingControlFocus by remember { mutableStateOf(false) }
    var tvPendingTopFocus by remember { mutableStateOf(false) }
    var tvRestoreSurfaceFocus by remember { mutableStateOf(false) }
    var isFullscreen by rememberSaveable(media.id) { mutableStateOf(false) }
    var resizeMode by rememberSaveable(media.id) { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var playbackSpeed by remember(media.id) { mutableFloatStateOf(preferredPlaybackSpeed.coerceIn(0.25f, 5.0f)) }
    // Right-side long press is transient and must not modify the saved playback-speed setting.
    var holdFastForwardActive by remember(media.id) { mutableStateOf(false) }
    var holdFastForwardOriginalSpeed by remember(media.id) { mutableFloatStateOf(1f) }
    var holdFastForwardSpeed by remember(media.id) { mutableFloatStateOf(2f) }
    // Standard player behavior: captions start off and are enabled explicitly from CC.
    // This also prevents every side-loaded subtitle source from becoming active at first frame.
    var captionsEnabled by remember { mutableStateOf(false) }
    var captionLanguageTag by rememberSaveable(media.id) { mutableStateOf("auto") }
    var qualityLabel by remember { mutableStateOf("Auto") }
    var hasCaptions by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showQuickActions by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showCaptionMenu by remember { mutableStateOf(false) }
    var viewingMenuVisible by remember { mutableStateOf(false) }
    // Selection must take effect on the very first tap. A repository update alone
    // can be delayed by the outer player host's lifecycle/state collection.
    var activeViewingMode by remember(media.id) { mutableStateOf(viewingMode) }
    var dimPreview by remember(media.id) { mutableIntStateOf(viewingDimPercent.coerceIn(10, 85)) }
    var activeNightAudio by remember(media.id) { mutableStateOf(nightAudioEnabled) }
    var viewingModeAcknowledgement by remember(media.id) { mutableStateOf(false) }
    LaunchedEffect(viewingMode, viewingDimPercent, nightAudioEnabled) {
        activeViewingMode = viewingMode
        dimPreview = viewingDimPercent.coerceIn(10, 85)
        activeNightAudio = nightAudioEnabled
    }
    fun applyViewingMode(mode: ViewingMode, dim: Int, nightAudio: Boolean) {
        val safeDim = dim.coerceIn(10, 85)
        // Update the actual player first, then persist to the repository.
        activeViewingMode = mode
        dimPreview = safeDim
        activeNightAudio = nightAudio
        viewingModeAcknowledgement = true
        PlaybackDiagnostics.record(context, "viewing_mode",
            "native=${mode.name}; dim=${safeDim}; audio=${nightAudio}")
        onViewingPreferencesChanged(mode, safeDim, nightAudio)
    }
    LaunchedEffect(viewingModeAcknowledgement, viewingMenuVisible) {
        if (viewingModeAcknowledgement && !viewingMenuVisible) {
            delay(2400L)
            viewingModeAcknowledgement = false
        }
    }
    var captionTrackFetchInProgress by remember(media.id) { mutableStateOf(false) }
    var pendingCaptionLanguage by remember(media.id) { mutableStateOf<String?>(null) }
    var showPlaybackModeCards by remember { mutableStateOf(false) }
    var showBookmarks by remember(media.id) { mutableStateOf(false) }
    var cardsDismissedThisVideo by remember(media.id) { mutableStateOf(false) }
    var fallbackRecommendations by remember(media.id) {
        mutableStateOf<List<YoutubeStreamResolver.RelatedVideo>>(emptyList())
    }
    var fallbackRecommendationsLoading by remember(media.id) { mutableStateOf(false) }
    var fallbackRecommendationsAttempted by remember(media.id) { mutableStateOf(false) }
    var fallbackRecommendationsError by remember(media.id) { mutableStateOf(false) }
    var manualRecoveryAttempts by remember(media.id) { mutableIntStateOf(0) }
    var continuationModeHint by remember { mutableStateOf<String?>(null) }
    var continuationEpisodeShelf by remember(media.id) { mutableStateOf<YoutubeStreamResolver.DiscoveredPlaylist?>(null) }
    var continuationEpisodesLoading by remember(media.id) { mutableStateOf(false) }
    var continuationEpisodesLoaded by remember(media.id) { mutableStateOf(false) }
    var scrubFraction by remember(media.id) { mutableStateOf<Float?>(null) }
    var remoteSeekPreviewSequence by remember(media.id) { mutableIntStateOf(0) }
    var remoteSeekPreviewActive by remember(media.id) { mutableStateOf(false) }
    LaunchedEffect(remoteSeekPreviewSequence) {
        if (remoteSeekPreviewSequence > 0) {
            delay(1700L)
            if (remoteSeekPreviewActive) {
                scrubFraction = null
                remoteSeekPreviewActive = false
            }
        }
    }
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
    val nightAudioController = remember(media.id) { NightAudioController() }
    var nightAudioActive by remember(media.id) { mutableStateOf(false) }
    val latestNightAudioEnabled by rememberUpdatedState(activeNightAudio)
    val nightAudioSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !carHeadUnit
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
    val tvSafePlayback = isTvLikePlaybackDevice(context) || resourceLimits.constrained
    val pcmTap = remember(media.id) {
        PlaybackPcmTapProcessor(
            shouldCapture = liveDubEngine::isAudioCaptureEnabled,
            consumer = liveDubEngine::onPlaybackPcm
        )
    }

    val exoPlayer = remember(media.id, carHeadUnit, resourceLimits) {
        val renderersFactory: DefaultRenderersFactory = if (carHeadUnit) {
            // Older vendor audio drivers can fail when custom PCM processors are attached.
            // Prefer the stock Media3 sink over the Live Dub PCM tap on these head units.
            DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true)
                .forceDisableMediaCodecAsynchronousQueueing()
                .setMediaCodecSelector(MediaCodecSelector.PREFER_SOFTWARE)
        } else {
            object : DefaultRenderersFactory(context) {
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
        }
        // Heap-aware buffering: small devices cannot afford a 90-second decode/input queue.
        // Faster devices keep the original buffer so streaming remains resilient.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                resourceLimits.minBufferMs,
                resourceLimits.maxBufferMs,
                500,
                1_250
            )
            .setTargetBufferBytes(if (resourceLimits.constrained) resourceLimits.targetBufferBytes else C.LENGTH_UNSET)
            .setPrioritizeTimeOverSizeThresholds(!resourceLimits.constrained)
            .build()
        ExoPlayer.Builder(context, renderersFactory)
            // Let Android stop/duck competing audio sessions immediately on player takeover.
            .setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .setLoadControl(loadControl)
            .build()
    }
    LaunchedEffect(exoPlayer, preferredPlaybackSpeed) {
        playbackSpeed = preferredPlaybackSpeed.coerceIn(0.25f, 5.0f)
        if (!holdFastForwardActive) exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(exoPlayer, playbackContinuationMode) {
        exoPlayer.repeatMode = if (playbackContinuationMode == PlaybackContinuationMode.LOOP_CURRENT) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
    }

    LaunchedEffect(showPlaybackModeCards, continuationEpisodesLoading, tvNavigation) {
        // Keep TV cards open while a remote user navigates them.
        if (showPlaybackModeCards && !continuationEpisodesLoading && !tvNavigation) {
            delay(15_000L)
            showPlaybackModeCards = false
            cardsDismissedThisVideo = true
        }
    }
    LaunchedEffect(showPlaybackModeCards, tvNavigation) {
        if (tvNavigation && showPlaybackModeCards) {
            delay(100)
            runCatching { tvCardsFocus.requestFocus() }
        }
    }
    LaunchedEffect(currentPosition, duration, firstFrameRendered, floatingCardsMode) {
        if (floatingCardsMode == "Last 15 seconds" && firstFrameRendered &&
            duration > 30_000L && currentPosition >= duration - 15_000L &&
            !cardsDismissedThisVideo && !showPlaybackModeCards && !inPictureInPicture
        ) showPlaybackModeCards = true
    }

    LaunchedEffect(continuationModeHint) {
        if (continuationModeHint != null) {
            delay(1_500L)
            continuationModeHint = null
        }
    }

    LaunchedEffect(
        showPlaybackModeCards,
        showEpisodesShelf,
        autoLoadEpisodes,
        playbackContinuationMode,
        firstFrameRendered,
        media.mediaUri,
        resolved?.title
    ) {
        if (YoutubeUrls.videoId(media.mediaUri) == null || !firstFrameRendered) return@LaunchedEffect
        if ((!showPlaybackModeCards || !showEpisodesShelf) &&
            playbackContinuationMode != PlaybackContinuationMode.EPISODES &&
            !autoLoadEpisodes
        ) {
            return@LaunchedEffect
        }
        if (continuationEpisodesLoaded || continuationEpisodesLoading) return@LaunchedEffect

        val titleHint = resolved?.title?.takeIf { it.isNotBlank() }
            ?: media.title.takeIf { it.isNotBlank() }
            ?: return@LaunchedEffect
        if (titleHint.equals("Now Playing", ignoreCase = true)) return@LaunchedEffect

        continuationEpisodesLoading = true
        try {
            continuationEpisodeShelf = YoutubeStreamResolver.discoverEpisodeShelf(
                url = media.mediaUri,
                titleHint = titleHint
            )
            continuationEpisodesLoaded = continuationEpisodeShelf?.episodes?.isNotEmpty() == true
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

    // The source player contains only one resolved CDN stream, not a Media3 playlist.
    // Consequently Media3's default seekToNext() silently does nothing for remote
    // Next/Previous buttons. Send both remote and pointer actions through one queue
    // selection path rather than seeking within that single source.
    val sourceVideoId = YoutubeUrls.videoId(media.mediaUri).orEmpty()
    val creatorDescriptionLinks = remember(resolved?.description) {
        CreatorLinksPolicy.extract(resolved?.description.orEmpty())
    }
    val readyRelated = resolved?.relatedVideos.orEmpty()
    val readyUnplayed = readyRelated.filter { it.videoId != sourceVideoId &&
        (!hideWatchedRecommendations || it.videoId !in playedVideoIds) }
    val availableRelated = if (readyUnplayed.isNotEmpty()) readyUnplayed else fallbackRecommendations
    val shelfRelated = availableRelated.asSequence()
        .filter { it.videoId != sourceVideoId &&
            (!hideWatchedRecommendations || it.videoId !in playedVideoIds)
        }.distinctBy { it.videoId }
        .take(if (lowResourceMode) 6 else 12).toList()

    // Do no extra network work until the user opens the cards.
    LaunchedEffect(showPlaybackModeCards, media.id, readyUnplayed.size, fallbackRecommendationsAttempted) {
        if (!showPlaybackModeCards || floatingCardsMode == "Off" ||
            sourceVideoId.isBlank() || readyUnplayed.isNotEmpty() ||
            fallbackRecommendationsAttempted
        ) return@LaunchedEffect
        fallbackRecommendationsAttempted = true
        fallbackRecommendationsLoading = true
        fallbackRecommendationsError = false
        try {
            val items = withTimeoutOrNull(9_000L) {
                YoutubeWatchNextClient.resolve(media.mediaUri, sourceVideoId).relatedVideos
            }.orEmpty()
            fallbackRecommendations = items.asSequence()
                .filter { it.videoId.matches(Regex("[A-Za-z0-9_-]{11}")) }
                .distinctBy { it.videoId }
                .take(if (lowResourceMode) 6 else 16)
                .map { item ->
                    YoutubeStreamResolver.RelatedVideo(
                        videoId = item.videoId,
                        title = item.title,
                        url = "https://www.youtube.com/watch?v=${item.videoId}",
                        thumbnailUrl = item.thumbnailUrl,
                        channelTitle = item.uploader,
                        durationSeconds = item.durationSeconds
                    )
                }.toList()
            fallbackRecommendationsError = fallbackRecommendations.isEmpty()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            fallbackRecommendationsError = true
        } finally {
            fallbackRecommendationsLoading = false
        }
    }
    val episodeList = continuationEpisodeShelf?.episodes.orEmpty().distinctBy { it.videoId }
    val episodeIndex = episodeList.indexOfFirst { it.videoId == sourceVideoId }
    val nextEpisode = if (episodeIndex in 0 until episodeList.lastIndex) {
        episodeList.drop(episodeIndex + 1).firstOrNull {
            PlaylistQueuePolicy.nextUnplayed(listOf(it.videoId), sourceVideoId, playedVideoIds) != null
        }
    } else null
    val relatedNextId = PlaylistQueuePolicy.nextUnplayed(
        resolved?.relatedVideos.orEmpty().map { it.videoId }, sourceVideoId, playedVideoIds
    )
    val relatedNext = resolved?.relatedVideos?.firstOrNull { it.videoId == relatedNextId }
    var nextLookupInProgress by remember(media.id) { mutableStateOf(false) }
    val sharedNextAction: (() -> Unit)? = when {
        onNextItem != null -> onNextItem
        playbackContinuationMode == PlaybackContinuationMode.EPISODES &&
            nextEpisode != null && onPlayRelated != null -> {
            {
                onPlayRelated(
                    OfflineMedia(
                        id = "episode-next-${nextEpisode.videoId}-${System.nanoTime()}",
                        title = nextEpisode.title,
                        videoId = nextEpisode.videoId,
                        thumbnailUrl = nextEpisode.thumbnailUrl,
                        mediaUri = "https://www.youtube.com/watch?v=${nextEpisode.videoId}"
                    )
                )
            }
        }
        relatedNext != null && onPlayRelated != null -> {
            {
                onPlayRelated(
                    OfflineMedia(
                        id = "remote-next-${relatedNext.videoId}-${System.nanoTime()}",
                        title = relatedNext.title,
                        videoId = relatedNext.videoId,
                        thumbnailUrl = relatedNext.thumbnailUrl,
                        mediaUri = relatedNext.url
                    )
                )
            }
        }
        // A single direct ExoPlayer item has no media-session next entry. The
        // native resolver may also finish after the Next button is pressed.
        // Provide a bounded on-demand fallback instead of a disabled button.
        onPlayRelated != null && sourceVideoId.isNotBlank() -> {
            {
                if (!nextLookupInProgress) {
                    val title = resolved?.title?.takeIf { it.isNotBlank() && it != "Now Playing" }
                        ?: media.title.takeIf { it.isNotBlank() && it != "Now Playing" }
                    if (title == null) {
                        Toast.makeText(context, "Next video is still loading", Toast.LENGTH_SHORT).show()
                    } else {
                        nextLookupInProgress = true
                        playbackScope.launch {
                            try {
                                // First use the current video's actual Watch-Next
                                // recommendations. Only fall back to title search when the
                                // watch endpoint returns nothing or is unavailable.
                                val directNext = runCatching {
                                    withTimeoutOrNull(8_000L) {
                                        YoutubeWatchNextClient.resolve(media.mediaUri, sourceVideoId)
                                            .relatedVideos.firstOrNull {
                                                PlaylistQueuePolicy.nextUnplayed(
                                                    listOf(it.videoId), sourceVideoId, playedVideoIds
                                                ) != null
                                            }
                                    }
                                }.getOrNull()
                                val nextMedia = if (directNext != null) {
                                    OfflineMedia(
                                        id = "watch-next-${directNext.videoId}-${System.nanoTime()}",
                                        title = directNext.title,
                                        videoId = directNext.videoId,
                                        thumbnailUrl = directNext.thumbnailUrl,
                                        mediaUri = "https://www.youtube.com/watch?v=${directNext.videoId}"
                                    )
                                } else {
                                    val candidates = withTimeoutOrNull(8_000L) {
                                        YoutubeStreamResolver.searchVideos(title, 16)
                                    }.orEmpty()
                                    candidates.firstOrNull {
                                        PlaylistQueuePolicy.nextUnplayed(
                                            listOf(it.videoId), sourceVideoId, playedVideoIds
                                        ) != null
                                    }?.let { next ->
                                        OfflineMedia(
                                            id = "search-next-${next.videoId}-${System.nanoTime()}",
                                            title = next.title,
                                            videoId = next.videoId,
                                            thumbnailUrl = next.thumbnailUrl,
                                            mediaUri = next.url
                                        )
                                    }
                                }
                                if (nextMedia == null) {
                                    Toast.makeText(context, "No next video available", Toast.LENGTH_SHORT).show()
                                } else {
                                    onPlayRelated(nextMedia)
                                }
                            } catch (cancel: CancellationException) {
                                throw cancel
                            } catch (_: Exception) {
                                Toast.makeText(context, "Unable to find next video", Toast.LENGTH_SHORT).show()
                            } finally {
                                nextLookupInProgress = false
                            }
                        }
                    }
                }
            }
        }
        else -> null
    }
    val currentNextAction by rememberUpdatedState(sharedNextAction)
    val latestSharedNextAction by rememberUpdatedState(sharedNextAction)
    val currentPreviousAction by rememberUpdatedState(onPreviousItem)
    var lastNextKeyTime by remember(media.id) { mutableLongStateOf(0L) }
    var lastPreviousKeyTime by remember(media.id) { mutableLongStateOf(0L) }

    fun dispatchNextKey(): Boolean {
        val action = currentNextAction ?: return false
        val now = SystemClock.elapsedRealtime()
        if (now - lastNextKeyTime >= 350L) {
            lastNextKeyTime = now
            action()
        }
        return true
    }

    fun dispatchPreviousKey(): Boolean {
        val action = currentPreviousAction ?: return false
        val now = SystemClock.elapsedRealtime()
        if (now - lastPreviousKeyTime >= 450L) {
            lastPreviousKeyTime = now
            action()
        }
        return true
    }

    val currentDispatchNext by rememberUpdatedState(::dispatchNextKey)
    val currentDispatchPrevious by rememberUpdatedState(::dispatchPreviousKey)
    val mediaSession = remember(exoPlayer) {
        MediaSession.Builder(context, exoPlayer)
            .setId("drivedeck-${media.id}-${System.identityHashCode(exoPlayer)}")
            .setCallback(object : MediaSession.Callback {
                override fun onMediaButtonEvent(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    intent: Intent
                ): Boolean {
                    @Suppress("DEPRECATION")
                    val event: AndroidKeyEvent? = if (android.os.Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, AndroidKeyEvent::class.java)
                    } else {
                        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }
                    if (event?.action != AndroidKeyEvent.ACTION_DOWN || event.repeatCount != 0) {
                        return false
                    }
                    return when (event.keyCode) {
                        AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> currentDispatchNext()
                        AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS -> currentDispatchPrevious()
                        else -> false // Media3 retains play/pause, headset and seek handling.
                    }
                }
            })
            .build()
    }
    val resumeStore = remember(context) { VideoResumeStore(context) }
    val privateSession by remember(context) {
        com.drivedeck.tubedeck.data.repository.TubeDeckRepository.get(context).privateSession
    }.collectAsState()
    // 'Resume after interruptions' controls app/phone interruptions only;
    // watch-position resume is independent and disabled solely for private sessions.
    val resumeEnabled = !privateSession
    val savedResume = remember(media.mediaUri, resumeEnabled, initialPositionMs) {
        if (resumeEnabled && initialPositionMs < 1_000L) {
            resumeStore.find(media.mediaUri)
        } else null
    }
    var showResumePrompt by remember(media.id) { mutableStateOf(savedResume != null) }
    // Mutable holder is read when the stream actually starts, even if the user
    // chooses Resume BEFORE the asynchronous stream resolver finishes.
    val selectedResumeStart = remember(media.id) {
        mutableLongStateOf(initialPositionMs.coerceAtLeast(0L))
    }
    val latestResumeEnabled by rememberUpdatedState(resumeEnabled)
    val latestResumePrompt by rememberUpdatedState(showResumePrompt)

    LaunchedEffect(isPlaying, resumeEnabled, showResumePrompt, media.mediaUri) {
        if (resumeEnabled && !showResumePrompt && isPlaying) {
            while (isActive) {
                delay(12_000L)
                if (exoPlayer.isPlaying) {
                    resumeStore.record(
                        media.mediaUri,
                        exoPlayer.currentPosition.coerceAtLeast(0L),
                        exoPlayer.duration
                    )
                }
            }
        }
    }
    LaunchedEffect(showResumePrompt, isPlaying) {
        if (showResumePrompt && isPlaying) exoPlayer.pause()
    }
    // The final resume snapshot is taken in the ExoPlayer release effect,
    // BEFORE release(). A separate earlier DisposableEffect disposed too late
    // on some Compose hosts and silently saved an IDLE/zero position.
    LaunchedEffect(privateSession) {
        if (privateSession) showResumePrompt = false
    }
    val youtubeVideo = YoutubeUrls.videoId(media.mediaUri) != null
    val themeProfile = LocalDeckThemeProfile.current
    val playerButtonShape = when (themeProfile.playerChrome) {
        DeckPlayerChrome.CONSOLE, DeckPlayerChrome.HUD -> RoundedCornerShape(5.dp)
        DeckPlayerChrome.ARCADE -> RoundedCornerShape(1.dp)
        DeckPlayerChrome.THEATER -> RoundedCornerShape(15.dp)
        DeckPlayerChrome.GLASS -> RoundedCornerShape(22.dp)
        DeckPlayerChrome.CLASSIC -> CircleShape
        DeckPlayerChrome.TV_CLASSIC -> RoundedCornerShape(4.dp)
        DeckPlayerChrome.YOUTUBE_MOBILE, DeckPlayerChrome.YOUTUBE_DESKTOP -> CircleShape
        DeckPlayerChrome.PLEX -> RoundedCornerShape(18.dp)
        DeckPlayerChrome.KODI -> RoundedCornerShape(8.dp)
        DeckPlayerChrome.MX_GESTURE -> RoundedCornerShape(25.dp)
        DeckPlayerChrome.VLC_COMPACT -> RoundedCornerShape(2.dp)
        DeckPlayerChrome.MATERIAL -> RoundedCornerShape(28.dp)
        DeckPlayerChrome.APPLE_FLOAT -> RoundedCornerShape(26.dp)
        DeckPlayerChrome.CYBER -> RoundedCornerShape(0.dp)
    }
    val playerOverlayOpacity = when (themeProfile.playerChrome) {
        DeckPlayerChrome.THEATER -> .50f
        DeckPlayerChrome.GLASS -> .22f
        DeckPlayerChrome.HUD, DeckPlayerChrome.CONSOLE -> .58f
        DeckPlayerChrome.ARCADE -> .46f
        DeckPlayerChrome.CLASSIC -> .35f
        DeckPlayerChrome.TV_CLASSIC -> .58f
        DeckPlayerChrome.YOUTUBE_MOBILE, DeckPlayerChrome.YOUTUBE_DESKTOP -> .43f
        DeckPlayerChrome.PLEX -> .50f
        DeckPlayerChrome.KODI -> .29f
        DeckPlayerChrome.MX_GESTURE -> .33f
        DeckPlayerChrome.VLC_COMPACT -> .60f
        DeckPlayerChrome.MATERIAL -> .40f
        DeckPlayerChrome.APPLE_FLOAT -> .18f
        DeckPlayerChrome.CYBER -> .61f
    }
    val playerButtonColor = when (themeProfile.playerChrome) {
        DeckPlayerChrome.THEATER, DeckPlayerChrome.PLEX,
        DeckPlayerChrome.YOUTUBE_MOBILE, DeckPlayerChrome.YOUTUBE_DESKTOP,
        DeckPlayerChrome.APPLE_FLOAT -> Color.White
        DeckPlayerChrome.HUD, DeckPlayerChrome.CYBER -> TubeDeckAccentMint
        DeckPlayerChrome.VLC_COMPACT -> Color(0xFFFF9800)
        DeckPlayerChrome.MATERIAL -> Color(0xFF6750A4)
        else -> TubeDeckAccentCyan
    }
    // Controls change hierarchy and placement with the experience,
    // not just accent colors. Playback state always stays on the same ExoPlayer.
    val cinematicPlayer = themeProfile.playerChrome in setOf(
        DeckPlayerChrome.PLEX, DeckPlayerChrome.KODI, DeckPlayerChrome.THEATER,
        DeckPlayerChrome.GLASS, DeckPlayerChrome.APPLE_FLOAT
    )
    val gesturePlayer = themeProfile.playerChrome == DeckPlayerChrome.MX_GESTURE
    val technicalPlayer = themeProfile.playerChrome in setOf(
        DeckPlayerChrome.VLC_COMPACT, DeckPlayerChrome.CONSOLE,
        DeckPlayerChrome.CYBER, DeckPlayerChrome.HUD
    )
    val largeTvControls = themeProfile.playerChrome == DeckPlayerChrome.TV_CLASSIC
    val compactPortrait = isPortrait && activeViewingMode != ViewingMode.CINEMA &&
        !isFullscreen && !inPictureInPicture && !isPipMode(activity)
    val centerControlDiameter = if (compactPortrait) 52.dp else 60.dp
    // The spinner and central Play/Pause row must share the exact same offset.
    // More importantly, BOTH are positioned inside safeDrawing insets. The old
    // spinner was centered on the raw screen while Play was centered inside
    // the system-bar-safe area, causing an obvious landscape/TV-box drift.
    val centerPlayYOffset = if (compactPortrait) 12.dp else 10.dp

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

        val effectiveHeight = if (autoSelected) {
            requestedHeight.coerceAtMost(resourceLimits.maxAutoVideoHeight)
        } else requestedHeight
        val option = selectResolvedOption(sourceMedia, effectiveHeight, carHeadUnit) ?: return
        val position = if (preservePosition) {
            exoPlayer.currentPosition
        } else {
            selectedResumeStart.longValue
        }
        PlaybackStartupTelemetry.markPrepare(media.mediaUri)
        isPreparing = true
        firstFrameRendered = false
        val preparedSource = runCatching {
            buildResolvedSource(dataSourceFactory, sourceMedia, option)
        }.getOrElse { sourceError ->
            isPreparing = false
            isBuffering = false
            resolveError = "Could not prepare video source: ${sourceError.message ?: sourceError.javaClass.simpleName}"
            return
        }
        runCatching {
            exoPlayer.setMediaSource(preparedSource, position)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }.onFailure { sourceError ->
            isPreparing = false
            isBuffering = false
            resolveError = "Could not start video playback: ${sourceError.message ?: sourceError.javaClass.simpleName}"
            return
        }
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

    fun selectCaptionLanguage(tag: String) {
        val requestedBase = tag.substringBefore('-').lowercase(Locale.US)
        if (tag == "auto") {
            captionLanguageTag = "auto"
            captionsEnabled = true
            controlsVisible = true
            return
        }

        val existing = resolved?.subtitles.orEmpty().firstOrNull {
            it.languageTag.substringBefore('-').equals(requestedBase, ignoreCase = true)
        }
        if (existing != null) {
            captionLanguageTag = existing.languageTag
            captionsEnabled = true
            controlsVisible = true
            return
        }

        if (requestedBase !in setOf("en", "ur") || captionTrackFetchInProgress) return
        captionTrackFetchInProgress = true
        pendingCaptionLanguage = requestedBase
        controlsVisible = true
        Toast.makeText(
            context,
            if (requestedBase == "ur") "Loading Urdu captions…" else "Loading English captions…",
            Toast.LENGTH_SHORT
        ).show()

        playbackScope.launch {
            try {
                val extra = YoutubeStreamResolver.preferredSubtitleTracks(
                    context = context,
                    url = media.mediaUri,
                    languages = setOf(requestedBase)
                )
                val track = extra.firstOrNull {
                    it.languageTag.substringBefore('-').equals(requestedBase, ignoreCase = true)
                }
                val current = resolved
                if (track == null || current == null) {
                    Toast.makeText(
                        context,
                        if (requestedBase == "ur") {
                            "Urdu captions are not exposed by YouTube for this video"
                        } else {
                            "English captions are not exposed by YouTube for this video"
                        },
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                val updated = current.copy(
                    subtitles = YoutubeStreamResolver.prioritizeSubtitlesForPlayer(
                        current.subtitles + extra,
                        preferredLanguage = requestedBase
                    )
                )
                resolved = updated
                hasCaptions = true
                captionLanguageTag = track.languageTag
                captionsEnabled = true

                val target = if (autoQuality) {
                    chooseAutoHeight(
                        context,
                        updated.options.map { it.height },
                        bandwidthMeter.bitrateEstimate
                    )
                } else {
                    selectedHeight.takeIf { it > 0 }
                        ?: updated.options.maxOfOrNull { it.height }
                        ?: 720
                }
                loadResolvedStream(
                    sourceMedia = updated,
                    requestedHeight = target,
                    autoSelected = autoQuality,
                    preservePosition = true
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Throwable) {
                Toast.makeText(
                    context,
                    "Could not load the requested caption language",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                captionTrackFetchInProgress = false
                pendingCaptionLanguage = null
            }
        }
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
    val latestOnPlaybackRequestedChanged by rememberUpdatedState(onPlaybackRequestedChanged)

    DisposableEffect(exoPlayer, mediaSession) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                latestOnPlaybackRequestedChanged(playWhenReady)
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                if (playing && !latestForegroundPlaybackAllowed) {
                    exoPlayer.pause()
                    return
                }
                isPlaying = playing
                if (!playing && !exoPlayer.playWhenReady && liveDubEnabled) liveDubEngine.pauseForPlayback()
                // Save at an actual pause as well as the regular 12-second tick;
                // do not clear an old position while the resume dialog is open.
                if (!playing && latestResumeEnabled && !latestResumePrompt &&
                    exoPlayer.playbackState == Player.STATE_READY
                ) {
                    val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    if (VideoResumePolicy.shouldSave(pos, exoPlayer.duration)) {
                        resumeStore.record(media.mediaUri, pos, exoPlayer.duration)
                    }
                }
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
                    if (state == Player.STATE_ENDED && latestResumeEnabled) {
                        resumeStore.record(media.mediaUri, 0L)
                    }
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
                                    episodes.drop(currentEpisodeIndex + 1).firstOrNull {
                                        PlaylistQueuePolicy.nextUnplayed(
                                            listOf(it.videoId), currentVideoId, playedVideoIds
                                        ) != null
                                    }
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

                                    else -> { if (latestSharedNextAction != null) latestSharedNextAction?.invoke() else latestOnPlaybackEnded?.invoke() }
                                }
                            }

                            PlaybackContinuationMode.AUTO_NEXT -> {
                                // Manual Next and end-of-stream use one selection policy.
                                // An empty related shelf triggers the same bounded Watch-Next lookup.
                                if (latestSharedNextAction != null) {
                                    latestSharedNextAction?.invoke()
                                } else {
                                    latestOnPlaybackEnded?.invoke()
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
                    PlaybackDiagnostics.record(context, "first_frame", "compat=$carHeadUnit")
                }
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                hasCaptions = youtubeVideo ||
                    tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSupported } ||
                    resolved?.subtitles?.isNotEmpty() == true
            }
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (!carHeadUnit) equalizerSnapshot = equalizerController.attach(audioSessionId)
                nightAudioActive = nightAudioController.apply(
                    audioSessionId, latestNightAudioEnabled && nightAudioSupported
                )
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
                PlaybackDiagnostics.record(context, "player_error", "${error.errorCodeName}: ${error.cause?.javaClass?.simpleName.orEmpty()}")
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
                            hasCaptions = fresh.subtitles.isNotEmpty() || youtubeVideo

                            val decoderSafeHeight = fresh.progressiveOptions
                                .filter { it.videoMimeType == "video/mp4" && it.height <= (if (carHeadUnit) 480 else 720) }
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
                    // Keep the Activity/player visible on a terminal Media3 error. Automatically
                    // replacing the native player with WebView looked like a sudden player/app exit
                    // and could trigger extra lifecycle transitions on OEM Android builds.
                    resolveError = "Media3 playback error: ${error.errorCodeName}"
                }
            }
        }
        val analytics = object : AnalyticsListener {
            override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                PlaybackDiagnostics.record(context, "video_decoder", decoderName)
            }
            override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                PlaybackDiagnostics.record(context, "audio_decoder", decoderName)
            }
        }
        PlaybackDiagnostics.record(context, "player_created", "compat=$carHeadUnit")
        exoPlayer.addAnalyticsListener(analytics)
        exoPlayer.addListener(listener)
        PlaybackHardwareButtonBridge.register(exoPlayer, { currentDispatchNext() }, { currentDispatchPrevious() })
        if (!carHeadUnit) equalizerSnapshot = equalizerController.attach(exoPlayer.audioSessionId)
        onDispose {
            if (latestResumeEnabled && !latestResumePrompt) {
                runCatching {
                    val completed = exoPlayer.playbackState == Player.STATE_ENDED
                    val position = if (completed) 0L else exoPlayer.currentPosition.coerceAtLeast(0L)
                    if (VideoResumePolicy.shouldSnapshot(
                            exoPlayer.currentMediaItem != null &&
                                exoPlayer.playbackState != Player.STATE_IDLE,
                            false,
                            position,
                            completed
                        )
                    ) {
                        resumeStore.record(media.mediaUri, position, exoPlayer.duration)
                    }
                }
            }
            PlaybackHardwareButtonBridge.unregister(exoPlayer)
            exoPlayer.removeListener(listener)
            exoPlayer.removeAnalyticsListener(analytics)
            nightAudioController.release()
            equalizerController.release()
            liveDubEngine.close()
            mediaSession.release()
            exoPlayer.release()
        }
    }

    LaunchedEffect(activeNightAudio, nightAudioSupported, exoPlayer) {
        nightAudioActive = nightAudioController.apply(
            exoPlayer.audioSessionId, activeNightAudio && nightAudioSupported
        )
    }
    LaunchedEffect(media.id, media.mediaUri, manualRecoveryAttempts) {
        isPreparing = true
        isBuffering = false
        val online = YoutubeUrls.videoId(media.mediaUri) != null
        if (online) {
            resolveError = null
            try {
                val result = YoutubeStreamResolver.resolve(context, media.mediaUri)
                resolved = result
                result.title.takeIf { it.isNotBlank() }?.let(onResolvedTitleChanged)
                hasCaptions = result.subtitles.isNotEmpty() || youtubeVideo
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
                    val safeTarget = chooseFastStartHeight(
                        result, requestedTarget.coerceAtMost(if (resourceLimits.constrained) resourceLimits.maxAutoVideoHeight else 720)
                    )
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
                // Do not switch hosts automatically. Keep the error card in the current player
                // and let the user choose "Open web player" explicitly.
                resolveError = "Could not resolve video stream: ${t.message ?: t.javaClass.simpleName}"
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
                selectedResumeStart.longValue
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
            hasCaptions = fresh.subtitles.isNotEmpty() || youtubeVideo
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
        if (!firstFrameRendered || !autoQuality || !youtubeVideo || inPictureInPicture || !resourceLimits.allowAdaptiveUpgrade) return@LaunchedEffect
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

    LaunchedEffect(firstFrameRendered, resolved?.relatedVideos, autoLoadUpNext, lowResourceMode) {
        if (!firstFrameRendered || !autoLoadUpNext || lowResourceMode) return@LaunchedEffect
        // Up Next prefetch is optional because even post-first-frame extraction can compete for
        // network/CPU on modest devices. When disabled in Settings no related item is prewarmed.
        resolved?.relatedVideos.orEmpty()
            .asSequence()
            .map { it.url }
            .distinct()
            .take(resourceLimits.relatedPrefetchCount)
            .forEach { YoutubeStreamResolver.prefetch(context, it) }
    }

    val activeDubSourceTag = remember(resolved, dubSourceCode) {
        chooseDubSourceTag(resolved, dubSourceCode)
    }
    val captionDubbingActive = liveDubEnabled && (
        dubMethod == "captions" || (dubMethod == "auto" && activeDubSourceTag != null)
    )
    val audioDubbingActive = liveDubEnabled && !captionDubbingActive
    val activeCaptionTag = remember(resolved, captionLanguageTag) {
        if (captionLanguageTag != "auto") {
            captionLanguageTag
        } else {
            val subtitles = resolved?.subtitles.orEmpty()
            val deviceLanguage = Locale.getDefault().language
            subtitles.firstOrNull {
                !it.autoGenerated &&
                    it.languageTag.substringBefore('-').equals(deviceLanguage, ignoreCase = true)
            }?.languageTag
                ?: subtitles.firstOrNull {
                    it.languageTag.substringBefore('-').equals(deviceLanguage, ignoreCase = true)
                }?.languageTag
                ?: subtitles.firstOrNull { !it.autoGenerated }?.languageTag
                ?: subtitles.firstOrNull()?.languageTag
        }
    }

    LaunchedEffect(
        liveDubEnabled, activeDubSourceTag, dubTargetCode, dubVoiceProfile, dubMethod,
        captionsEnabled, captionLanguageTag, activeCaptionTag
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
            .setSelectUndeterminedTextLanguage(captionsEnabled || captionDubbingActive)
        when {
            captionDubbingActive && activeDubSourceTag != null ->
                textBuilder.setPreferredTextLanguage(activeDubSourceTag)
            captionsEnabled && activeCaptionTag != null ->
                textBuilder.setPreferredTextLanguage(activeCaptionTag)
            else ->
                textBuilder.setPreferredTextLanguage(null)
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

    LaunchedEffect(controlsVisible, autoHideControls, tvControlsFocused, viewingMenuVisible) {
        // Do not hide TV controls while a remote or keyboard has focus inside them.
        if (controlsVisible && autoHideControls && !tvControlsFocused && !viewingMenuVisible) {
            delay(4200)
            if (!tvControlsFocused) controlsVisible = false
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
            if (holdFastForwardActive) {
                exoPlayer.setPlaybackSpeed(holdFastForwardOriginalSpeed)
                holdFastForwardActive = false
            }
            isFullscreen = false
            controlsVisible = false
            showSettings = false
            showQuickActions = false
            showEqualizer = false
            gestureOverlayVisible = false
        }
    }

    LaunchedEffect(activity, isFullscreen, activeViewingMode) {
        activity?.let { setFullscreen(it, isFullscreen || activeViewingMode == ViewingMode.CINEMA) }
    }
    LaunchedEffect(activeViewingMode) {
        if (activeViewingMode == ViewingMode.CINEMA) {
            showPlaybackModeCards = false
            controlsVisible = false
        }
    }

    // TV Back first dismisses a visible player overlay, as it does in SmartTube.
    // A subsequent Back is owned by the Activity and its PiP/exit state machine.
    BackHandler(enabled = showPlaybackModeCards && !inPictureInPicture) {
        showPlaybackModeCards = false
        cardsDismissedThisVideo = true
        controlsVisible = true
        tvPendingControlFocus = true
    }

    BackHandler(
        enabled = tvNavigation && controlsVisible && !inPictureInPicture &&
            !showSettings && !showQuickActions && !showEqualizer &&
            !showCaptionMenu && !showQualityMenu && !showComments && !viewingMenuVisible &&
            !showPlaybackModeCards
    ) {
        controlsVisible = false
        tvRestoreSurfaceFocus = true
    }

    LaunchedEffect(controlsVisible, tvRestoreSurfaceFocus) {
        if (!controlsVisible && tvRestoreSurfaceFocus) {
            withFrameNanos { }
            runCatching { tvSurfaceFocus.requestFocus() }
            tvRestoreSurfaceFocus = false
        }
    }

    LaunchedEffect(media.id, tvNavigation) {
        if (tvNavigation && !inPictureInPicture) tvSurfaceFocus.requestFocus()
    }

    LaunchedEffect(controlsVisible, tvPendingControlFocus, tvPendingTopFocus) {
        // Do not dispatch a D-pad focus request until the control overlay is attached.
        if (controlsVisible && (tvPendingControlFocus || tvPendingTopFocus)) {
            withFrameNanos { }
            runCatching {
                if (tvPendingTopFocus) tvTopBackFocus.requestFocus()
                else tvPlayPauseFocus.requestFocus()
            }
            tvPendingControlFocus = false
            tvPendingTopFocus = false
        }
    }

    val tvRemoteKeys = if (tvNavigation) Modifier
        .focusRequester(tvSurfaceFocus)
        .onFocusChanged { tvSurfaceFocused = it.isFocused }
        .onPreviewKeyEvent { key ->
            if (key.type != KeyEventType.KeyDown || inPictureInPicture) {
                false
            } else {
                val modalVisible = showSettings || showQuickActions || showEqualizer || viewingMenuVisible ||
                    showCaptionMenu || showQualityMenu || showPlaybackModeCards || showComments || showBookmarks
                val decision = if (!modalVisible) {
                    TvRemoteKeyLearningPolicy.remap(
                        key.nativeKeyEvent.keyCode, key.nativeKeyEvent.repeatCount,
                        remoteNextKeyCode, remotePreviousKeyCode
                    )
                } else null
                val actionDecision = decision ?: TvRemoteNavigationPolicy.decide(
                    keyCode = key.nativeKeyEvent.keyCode,
                    repeatCount = key.nativeKeyEvent.repeatCount,
                    surfaceFocused = tvSurfaceFocused,
                    controlsVisible = controlsVisible,
                    modalVisible = modalVisible
                )
                fun showCenterControls() {
                    controlsVisible = true
                    tvPendingControlFocus = true
                }
                when (actionDecision.action) {
                    TvPlayerKeyAction.IGNORE -> false
                    TvPlayerKeyAction.REVEAL_CENTER_CONTROLS -> {
                        showCenterControls()
                        true
                    }
                    TvPlayerKeyAction.REVEAL_TOP_CONTROLS -> {
                        controlsVisible = true
                        tvPendingTopFocus = true
                        true
                    }
                    TvPlayerKeyAction.HIDE_CONTROLS -> {
                        controlsVisible = false
                        tvRestoreSurfaceFocus = true
                        true
                    }
                    TvPlayerKeyAction.TOGGLE_CONTROLS -> {
                        controlsVisible = !controlsVisible
                        if (controlsVisible) tvPendingControlFocus = true
                        else tvRestoreSurfaceFocus = true
                        true
                    }
                    TvPlayerKeyAction.PLAY_PAUSE -> {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        true
                    }
                    TvPlayerKeyAction.PLAY -> {
                        exoPlayer.play()
                        true
                    }
                    TvPlayerKeyAction.PAUSE -> {
                        exoPlayer.pause()
                        true
                    }
                    TvPlayerKeyAction.STOP -> {
                        exoPlayer.pause()
                        onClose()
                        true
                    }
                    TvPlayerKeyAction.NEXT -> dispatchNextKey()
                    TvPlayerKeyAction.PREVIOUS -> dispatchPreviousKey()
                    TvPlayerKeyAction.SEEK_BACKWARD,
                    TvPlayerKeyAction.SEEK_FORWARD -> {
                        val sign = if (actionDecision.action == TvPlayerKeyAction.SEEK_FORWARD) 1L else -1L
                        val seekMs = sign * actionDecision.multiplier * seekIntervalSeconds * 1000L
                        val target = (exoPlayer.currentPosition + seekMs)
                            .coerceIn(0L, duration.coerceAtLeast(0L))
                        exoPlayer.seekTo(target)
                        if (duration > 0L) {
                            scrubFraction = (target.toFloat() / duration).coerceIn(0f, 1f)
                            remoteSeekPreviewActive = true
                            remoteSeekPreviewSequence += 1
                            controlsVisible = true
                        }
                        true
                    }
                    TvPlayerKeyAction.SEEK_PERCENT -> {
                        if (duration > 0L) {
                            val target = (duration * actionDecision.percent / 100L).coerceIn(0L, duration)
                            exoPlayer.seekTo(target)
                            scrubFraction = (target.toFloat() / duration).coerceIn(0f, 1f)
                            remoteSeekPreviewActive = true
                            remoteSeekPreviewSequence += 1
                            controlsVisible = true
                        }
                        true
                    }
                }
            }
        }
        .focusable() else Modifier

    Column(modifier = modifier.fillMaxSize().background(Color.Black).then(tvRemoteKeys)) {
        Box(
            modifier = if (compactPortrait) {
                Modifier.fillMaxWidth().aspectRatio(16f / 13f).background(Color.Black)
            } else {
                Modifier.fillMaxSize().background(Color.Black)
            }
        ) {
            // Keep the SAME TextureView attached across every viewing-mode
            // change. Recreating AndroidView caused OEM video/compositing failures.
            // Direct alpha on the TextureView is independent of Compose Z ordering.
            key(carHeadUnit) {
            AndroidView(
                factory = { ctx ->
                    val view = if (carHeadUnit) {
                        LayoutInflater.from(ctx).inflate(R.layout.mytube_car_player_view, null, false) as PlayerView
                    } else {
                        LayoutInflater.from(ctx).inflate(R.layout.mytube_viewing_player_view, null, false) as PlayerView
                    }
                    PlaybackDiagnostics.record(ctx, "video_surface", view.videoSurfaceView?.javaClass?.simpleName.orEmpty())
                    view.apply {
                        player = exoPlayer
                        useController = false
                        setKeepContentOnPlayerReset(true)
                        this.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(-1, -1)
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                    view.resizeMode = resizeMode
                    // Actual native video brightness. Unlike a Compose sibling overlay,
                    // a TextureView's alpha reliably affects the decoder picture.
                    // We leave subtitles and playback controls unaffected.
                    val dim = ViewingModePolicy.videoDim(
                        activeViewingMode, dimPreview, inPictureInPicture
                    )
                    view.videoSurfaceView?.alpha = (1f - dim).coerceIn(0.25f, 1f)
                    view.subtitleView?.visibility = if (captionsEnabled) View.VISIBLE else View.GONE
                    view.subtitleView?.setFixedTextSize(
                        android.util.TypedValue.COMPLEX_UNIT_SP,
                        18f * subtitleTextScale.coerceIn(.8f, 1.8f)
                    )
                    view.subtitleView?.setBottomPaddingFraction(
                        (0.08f + subtitleBottomInsetDp.coerceIn(0, 120) / 900f).coerceIn(0.08f, 0.25f)
                    )
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { view -> view.player = null }
            )
            }

            // Night additionally warms the picture. Brightness itself is applied
            // to the TextureView above, not twice through a fragile overlay.
            if (!inPictureInPicture && activeViewingMode == ViewingMode.NIGHT) {
                Box(Modifier.matchParentSize().background(Color(0xFFFFAD6A).copy(alpha = .08f)))
            }
            if (viewingModeAcknowledgement && !viewingMenuVisible && !inPictureInPicture) {
                Surface(
                    color = Color.Black.copy(alpha = .76f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(top = 14.dp)
                ) {
                    Text(
                        text = "Viewing mode: ${activeViewingMode.label}",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }

            if (gestureEnabled) {
                Box(
                    Modifier.fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .pointerInput(gestureEnabled, duration, inPictureInPicture) {
                            detectTapGestures(
                                onTap = { controlsVisible = !controlsVisible },
                                onPress = {
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        // Cancellation, release or PiP transition restores the prior speed.
                                        if (holdFastForwardActive) {
                                            exoPlayer.setPlaybackSpeed(holdFastForwardOriginalSpeed)
                                            holdFastForwardActive = false
                                        }
                                    }
                                },
                                onLongPress = { position ->
                                    if (PlaybackSurfaceGesturePolicy.longPress(position.x, size.width.toFloat()) ==
                                        PlaybackSurfaceGesture.HOLD_FAST_FORWARD
                                    ) {
                                        if (exoPlayer.isPlaying && !inPictureInPicture) {
                                            holdFastForwardOriginalSpeed = exoPlayer.playbackParameters.speed
                                            holdFastForwardSpeed = (holdFastForwardOriginalSpeed * 2f).coerceIn(2f, 5f)
                                            exoPlayer.setPlaybackSpeed(holdFastForwardSpeed)
                                            holdFastForwardActive = true
                                            gestureOverlayVisible = false
                                        }
                                    } else {
                                        showQuickActions = true
                                        controlsVisible = true
                                    }
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
                                        PlaybackSurfaceGesture.TOGGLE_PLAY_PAUSE -> {
                                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                            gestureIcon = if (exoPlayer.isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause
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
                                if (playbackCardsSwipeZone && dragging &&
                                    floatingCardsMode != "Off"
                                ) {
                                    showPlaybackModeCards = totalDy < -(size.height * .08f)
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

            if (holdFastForwardActive && !inPictureInPicture) {
                Surface(
                    color = Color.Black.copy(alpha = .78f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .padding(end = 18.dp)
                        .zIndex(60f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FastForward, null, tint = TubeDeckAccentCyan)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Fast forward ${playbackSpeedLabel(holdFastForwardSpeed)}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showPlaybackModeCards && !inPictureInPicture,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 10.dp,
                        end = 10.dp,
                        bottom = if (compactPortrait) 70.dp else 78.dp
                    )
                    .pointerInput(showPlaybackModeCards) {
                        var downwardDrag = 0f
                        detectVerticalDragGestures(
                            onDragStart = { downwardDrag = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                downwardDrag = (downwardDrag + dragAmount).coerceAtLeast(0f)
                                if (downwardDrag > 24f) change.consume()
                            },
                            onDragEnd = {
                                if (downwardDrag > 72f) {
                                    showPlaybackModeCards = false
                                    cardsDismissedThisVideo = true
                                    controlsVisible = true
                                }
                                downwardDrag = 0f
                            },
                            onDragCancel = { downwardDrag = 0f }
                        )
                    }
                    .zIndex(80f)
            ) {
                val currentVideoId = YoutubeUrls.videoId(media.mediaUri).orEmpty()
                PlaybackContinuationCards(
                    episodesLoading = continuationEpisodesLoading,
                    currentVideoId = currentVideoId,
                    relatedVideos = shelfRelated,
                    creatorLinks = creatorDescriptionLinks,
                    onCreatorLink = { link ->
                        if (link.kind == CreatorLinksPolicy.Kind.VIDEO &&
                            link.videoId != null
                        ) {
                            onPlayRelated?.invoke(
                                OfflineMedia(
                                    id = "creator-link-${link.videoId}-${System.nanoTime()}",
                                    title = "Creator-linked video",
                                    videoId = link.videoId,
                                    thumbnailUrl = "https://i.ytimg.com/vi/${link.videoId}/hqdefault.jpg",
                                    mediaUri = link.url
                                )
                            )
                        } else {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW, Uri.parse(link.url)
                                    )
                                )
                            }.onFailure {
                                Toast.makeText(context, "Could not open creator link", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    episodeShelf = continuationEpisodeShelf,
                    showRelevantShelf = showRelevantShelf,
                    showEpisodesShelf = showEpisodesShelf,
                    relatedLoading = fallbackRecommendationsLoading,
                    relatedError = fallbackRecommendationsError,
                    firstFocus = tvCardsFocus,
                    allowAnimatedPreviews = FocusAnimatedThumbs.eligible(
                        requested = animatedCardPreviews,
                        lowResourceMode = lowResourceMode,
                        pictureInPicture = inPictureInPicture,
                        blackout = com.drivedeck.tubedeck.data.repository.TubeDeckRepository.get(context)
                            .screenBlackoutEnabled.collectAsState().value,
                        sdk = android.os.Build.VERSION.SDK_INT,
                        metered = !FocusAnimatedThumbs.onUnmeteredNetwork(context)
                    ),
                    onClose = {
                        showPlaybackModeCards = false
                        cardsDismissedThisVideo = true
                        tvPendingControlFocus = true
                    },
                    onRetry = {
                        fallbackRecommendationsAttempted = false
                        fallbackRecommendationsError = false
                    },
                    onPlayMedia = { selected ->
                        showPlaybackModeCards = false
                        cardsDismissedThisVideo = true
                        onPlayRelated?.invoke(selected)
                    }
                )
            }

            if (continuationModeHint != null && controlsVisible && !showPlaybackModeCards) {
                continuationModeHint?.let { hint ->
                    Surface(
                        color = Color.Black.copy(alpha = .82f),
                        shape = RoundedCornerShape(10.dp),
                        tonalElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(
                                start = 18.dp,
                                bottom = if (compactPortrait) 116.dp else 110.dp
                            )
                            .zIndex(90f)
                    ) {
                        Text(
                            hint,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if ((isPreparing || !firstFrameRendered || isBuffering || playbackRecoveryActive) &&
                resolveError == null && !inPictureInPicture) {
                // Spinner and visible Play/Pause share the same safeDrawing
                // coordinate space, center offset and control diameter. A
                // fullscreen Box alone is not enough on asymmetric TV/landscape
                // navigation-bar insets: it would center on the raw window.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .zIndex(50f),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = .68f),
                        shape = playerButtonShape,
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .offset(y = centerPlayYOffset)
                            .size(centerControlDiameter)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = if (compactPortrait) 4.dp else 5.dp,
                                color = TubeDeckAccentCyan,
                                trackColor = Color.White.copy(alpha = .26f)
                            )
                        }
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
                                Icons.Default.PlayArrow -> "Playing"
                                Icons.Default.Pause -> "Paused"
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
                        .onFocusChanged { tvControlsFocused = it.hasFocus }
                        .background(Color.Black.copy(alpha = playerOverlayOpacity))
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    Row(
                        Modifier.fillMaxWidth().align(Alignment.TopCenter)
                            .then(if (themeProfile.playerChrome == DeckPlayerChrome.HUD ||
                                themeProfile.playerChrome == DeckPlayerChrome.CONSOLE ||
                                themeProfile.playerChrome == DeckPlayerChrome.CYBER ||
                                themeProfile.playerChrome == DeckPlayerChrome.VLC_COMPACT)
                                Modifier.background(Color.Black.copy(alpha = .72f))
                            else Modifier)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                liveDubEngine.stop()
                                exoPlayer.pause()
                                onClose()
                            },
                            modifier = Modifier.focusRequester(tvTopBackFocus).tvInputHighlight(CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Text(media.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (themeProfile.playerChrome == DeckPlayerChrome.HUD ||
                                themeProfile.playerChrome == DeckPlayerChrome.CONSOLE ||
                                themeProfile.playerChrome == DeckPlayerChrome.CYBER ||
                                themeProfile.playerChrome == DeckPlayerChrome.VLC_COMPACT) {
                                Text(
                                    "${if (autoQuality) "Auto • ${selectedHeight}p" else qualityLabel} • ${playbackSpeed}x • ${resizeModeLabel(resizeMode)}${if (liveDubEnabled) " • Dub ${LiveDubLanguages.label(dubTargetCode)}" else ""}",
                                    color = Color.White.copy(alpha = .72f),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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

                    if (!compactPortrait && (onDownloadVideo != null || onDownloadPlaylist != null) &&
                        !cinematicPlayer) {
                        Box(
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 54.dp, end = 8.dp)
                        ) {
                            DownloadActionStrip(onDownloadVideo, onDownloadPlaylist)
                        }
                    }

                    if (gesturePlayer && !compactPortrait) {
                        Surface(
                            color = Color.Black.copy(alpha = .78f),
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.align(Alignment.CenterEnd)
                                .padding(end = 8.dp, bottom = 65.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        exoPlayer.seekTo((currentPosition - 10_000L).coerceAtLeast(0L))
                                    },
                                    modifier = Modifier.tvInputHighlight(RoundedCornerShape(18.dp))
                                ) {
                                    Icon(Icons.Default.Replay10, "Back 10 seconds", tint = Color.White)
                                }
                                IconButton(
                                    onClick = { exoPlayer.seekTo(currentPosition + 10_000L) },
                                    modifier = Modifier.tvInputHighlight(RoundedCornerShape(18.dp))
                                ) {
                                    Icon(Icons.Default.Forward10, "Forward 10 seconds", tint = Color.White)
                                }
                                IconButton(
                                    onClick = { showSettings = true },
                                    modifier = Modifier.tvInputHighlight(RoundedCornerShape(18.dp))
                                ) {
                                    Icon(Icons.Default.Tune, "Playback tools", tint = Color.White)
                                }
                            }
                        }
                    }

                    if (firstFrameRendered) {
                        val currentVideoId = YoutubeUrls.videoId(media.mediaUri).orEmpty()
                        val suggestedNext = resolved?.relatedVideos
                            ?.firstOrNull { it.videoId.isNotBlank() && it.videoId != currentVideoId }

                        Row(
                            Modifier.align(Alignment.Center)
                                .offset(y = centerPlayYOffset),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(
                                if (compactPortrait) 12.dp else if (largeTvControls) 24.dp else 20.dp
                            )
                        ) {
                            IconButton(
                                onClick = { onPreviousItem?.invoke() },
                                enabled = onPreviousItem != null,
                                modifier = Modifier.size(if (compactPortrait) 44.dp else 50.dp).tvInputHighlight(playerButtonShape)
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
                                shape = playerButtonShape,
                                color = playerButtonColor,
                                modifier = Modifier.focusRequester(tvPlayPauseFocus)
                                    .size(centerControlDiameter).tvInputHighlight(playerButtonShape)
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

                            val nextEnabled = sharedNextAction != null
                            IconButton(
                                onClick = { dispatchNextKey() },
                                enabled = nextEnabled,
                                modifier = Modifier.size(if (compactPortrait) 44.dp else 50.dp).tvInputHighlight(CircleShape)
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
                            .then(
                                if (cinematicPlayer) Modifier.background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = .85f)
                                        )
                                    )
                                ) else if (technicalPlayer) Modifier
                                    .background(Color.Black.copy(alpha = .82f))
                                else Modifier
                            )
                            .padding(horizontal = if (cinematicPlayer) 17.dp else 10.dp,
                                vertical = if (cinematicPlayer) 12.dp else 1.dp)
                    ) {
                        if (cinematicPlayer) {
                            Text(
                                media.title,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = if (compactPortrait) 12.sp else 16.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        // Keep only frequent playback controls beside the seek bar. Playback speed,
                        // dubbing and Equalizer live under the top-right Player settings gear.
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(formatDuration(currentPosition), color = Color.White, fontSize = 9.sp)
                            Spacer(Modifier.weight(1f))
                            Text(formatDuration(duration), color = Color.White.copy(alpha = .75f), fontSize = 9.sp)
                        }
                        Box(Modifier.fillMaxWidth()) {
                            // Always visible in portrait; never hide Lights Out at the far
                            // right edge of a horizontally scrolling controls strip.
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { viewingMenuVisible = true; controlsVisible = true },
                                    modifier = Modifier.size(controllerHeight)
                                        .tvInputHighlight(RoundedCornerShape(8.dp))
                                ) {
                                    Icon(
                                        Icons.Default.NightsStay,
                                        "Lights Out / Cinema / Night viewing modes",
                                        tint = if (activeViewingMode != ViewingMode.NORMAL || activeNightAudio)
                                            TubeDeckAccentCyan else Color.White,
                                        modifier = Modifier.size(controllerIconSize)
                                    )
                                }
                                Row(
                                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(
                                        controllerGap, Alignment.CenterHorizontally
                                    )
                                ) {
                                IconButton(
                                    onClick = {
                                        val nextMode = when (playbackContinuationMode) {
                                            PlaybackContinuationMode.LOOP_CURRENT -> PlaybackContinuationMode.AUTO_NEXT
                                            PlaybackContinuationMode.AUTO_NEXT -> PlaybackContinuationMode.EPISODES
                                            PlaybackContinuationMode.EPISODES -> PlaybackContinuationMode.LOOP_CURRENT
                                        }
                                        onPlaybackContinuationModeChanged(nextMode)
                                        continuationModeHint = when (nextMode) {
                                            PlaybackContinuationMode.LOOP_CURRENT -> "Loop · repeat the current video"
                                            PlaybackContinuationMode.AUTO_NEXT -> "Auto play · play the next relevant video"
                                            PlaybackContinuationMode.EPISODES -> "Episode next · continue with the next detected episode"
                                        }
                                        controlsVisible = true
                                    },
                                    modifier = Modifier.size(controllerHeight)
                                ) {
                                    Icon(
                                        imageVector = when (playbackContinuationMode) {
                                            PlaybackContinuationMode.LOOP_CURRENT -> Icons.Default.Repeat
                                            PlaybackContinuationMode.AUTO_NEXT -> Icons.Default.SkipNext
                                            PlaybackContinuationMode.EPISODES -> Icons.Default.PlaylistPlay
                                        },
                                        contentDescription = "Playback continuation: ${playbackContinuationMode.label}. Tap to change mode.",
                                        tint = TubeDeckAccentCyan,
                                        modifier = Modifier.size(controllerIconSize)
                                    )
                                }

                                Box {
                                    TextButton(
                                        onClick = {
                                            showQualityMenu = true
                                            controlsVisible = true
                                        },
                                        modifier = Modifier.height(controllerHeight),
                                        contentPadding = PaddingValues(horizontal = (7f * controllerScale).dp, vertical = 0.dp)
                                    ) {
                                        Text(
                                            if (autoQuality) "Auto" else qualityLabel,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = controllerFontSize
                                        )
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            "Video quality",
                                            tint = Color.White,
                                            modifier = Modifier.size((17f * controllerScale).dp)
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
                                                        Icon(Icons.Default.Check, null, modifier = Modifier.size(controllerIconSize))
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
                                                                Icon(Icons.Default.Check, null, modifier = Modifier.size(controllerIconSize))
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

                                Box {
                                    IconButton(
                                        onClick = {
                                            showCaptionMenu = true
                                            controlsVisible = true
                                        },
                                        enabled = hasCaptions || youtubeVideo,
                                        modifier = Modifier.size(controllerHeight)
                                    ) {
                                        Icon(
                                            Icons.Default.ClosedCaption,
                                            if (hasCaptions) "Caption language" else "Captions unavailable",
                                            tint = when {
                                                !hasCaptions -> Color.White.copy(alpha = .28f)
                                                captionsEnabled -> TubeDeckAccentCyan
                                                else -> Color.White
                                            },
                                            modifier = Modifier.size(controllerIconSize)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showCaptionMenu,
                                        onDismissRequest = { showCaptionMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (!captionsEnabled) {
                                                        Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                                                        Spacer(Modifier.width(6.dp))
                                                    }
                                                    Text("Off")
                                                }
                                            },
                                            onClick = {
                                                showCaptionMenu = false
                                                captionsEnabled = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (captionsEnabled && captionLanguageTag == "auto") {
                                                        Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                                                        Spacer(Modifier.width(6.dp))
                                                    }
                                                    Text("Auto")
                                                }
                                            },
                                            onClick = {
                                                showCaptionMenu = false
                                                captionLanguageTag = "auto"
                                                captionsEnabled = true
                                            }
                                        )
                                        listOf("en" to "English", "ur" to "Urdu").forEach { (tag, label) ->
                                            val selectedTrack = resolved?.subtitles.orEmpty().firstOrNull {
                                                it.languageTag.substringBefore('-').equals(tag, ignoreCase = true)
                                            }
                                            DropdownMenuItem(
                                                text = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        if (
                                                            captionsEnabled &&
                                                            captionLanguageTag.substringBefore('-')
                                                                .equals(tag, ignoreCase = true)
                                                        ) {
                                                            Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                                                            Spacer(Modifier.width(6.dp))
                                                        }
                                                        Text(
                                                            when {
                                                                pendingCaptionLanguage == tag && captionTrackFetchInProgress ->
                                                                    label + " • Loading…"
                                                                selectedTrack?.autoGenerated == true ->
                                                                    label + " • Auto-translated"
                                                                else -> label
                                                            }
                                                        )
                                                    }
                                                },
                                                enabled = !captionTrackFetchInProgress || pendingCaptionLanguage == tag,
                                                onClick = {
                                                    showCaptionMenu = false
                                                    selectCaptionLanguage(tag)
                                                }
                                            )
                                        }
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        resizeMode = nextResizeMode(resizeMode)
                                        controlsVisible = true
                                    },
                                    modifier = Modifier.height(controllerHeight),
                                    contentPadding = PaddingValues(horizontal = (7f * controllerScale).dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        Icons.Default.AspectRatio,
                                        "Video size",
                                        tint = Color.White,
                                        modifier = Modifier.size((17f * controllerScale).dp)
                                    )
                                    Spacer(Modifier.width(controllerGap))
                                    Text(
                                        resizeModeLabel(resizeMode),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = controllerFontSize
                                    )
                                }

                                if (floatingCardsMode != "Off") {
                                    IconButton(
                                        onClick = {
                                            showPlaybackModeCards = true
                                            controlsVisible = true
                                        },
                                        modifier = Modifier.size(controllerHeight)
                                            .tvInputHighlight(RoundedCornerShape(8.dp))
                                    ) {
                                        Icon(
                                            Icons.Default.VideoLibrary,
                                            "More video cards",
                                            tint = TubeDeckAccentCyan,
                                            modifier = Modifier.size(controllerIconSize)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { showBookmarks = true; controlsVisible = true },
                                    modifier = Modifier.size(controllerHeight)
                                        .tvInputHighlight(RoundedCornerShape(8.dp))
                                ) {
                                    Icon(
                                        Icons.Default.BookmarkAdd,
                                        "Bookmark current video moment",
                                        tint = Color.White,
                                        modifier = Modifier.size(controllerIconSize)
                                    )
                                }


                                IconButton(
                                    onClick = { com.drivedeck.tubedeck.data.repository.TubeDeckRepository.get(context).enableScreenBlackout(); controlsVisible = false },
                                    modifier = Modifier.size(controllerHeight)
                                ) {
                                    Icon(
                                        Icons.Default.VisibilityOff,
                                        "Black screen, continue audio",
                                        tint = Color.White,
                                        modifier = Modifier.size(controllerIconSize)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        isFullscreen = !isFullscreen
                                        controlsVisible = true
                                    },
                                    modifier = Modifier.size(controllerHeight)
                                ) {
                                    Icon(
                                        if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        if (isFullscreen) "Exit fullscreen" else "Enter fullscreen",
                                        tint = Color.White,
                                        modifier = Modifier.size(controllerIconSize)
                                    )
                                }
                                }
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
                            chrome = themeProfile.playerChrome,
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
                            onScrubChange = {
                                remoteSeekPreviewActive = false
                                scrubFraction = it
                            },
                            onSeekFinished = { fraction ->
                                exoPlayer.seekTo((fraction * duration).toLong())
                                scrubFraction = null
                            }
                        )
                    }
                }
            }

            // The permanent bottom-right blackout eye was distracting on every
            // video and duplicated the fading eye in the lower control bar.
            // Keep the lower-bar eye (above) unchanged. During blackout the
            // opaque PlaybackBlackoutHost owns the screen and any tap restores it.

            if (showEqualizer && !inPictureInPicture) {
                PlaybackEqualizerOverlay(
                    snapshot = equalizerSnapshot,
                    textScale = equalizerTextScale,
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
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 8.dp, vertical = 32.dp)
                        .zIndex(70f)
                        .fillMaxWidth(if (compactPortrait) .95f else .50f)
                        .widthIn(max = 430.dp)
                )
            }

            if (resolveError != null) {
                val advice = PlaybackErrorAdvisor.analyze(resolveError.orEmpty())
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
                        Text(advice.message, color = Color.White.copy(alpha = .95f), fontSize = 13.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(advice.suggestion, color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(resolveError.orEmpty().take(180), color = Color.White.copy(alpha = .55f), fontSize = 10.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { manualRecoveryAttempts += 1 },
                                enabled = manualRecoveryAttempts < 3
                            ) { Text("Retry stream") }
                            TextButton(onClick = { showBookmarks = true }) { Text("Saved moments") }
                        }
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
                sideBySideEpisodeFeeds = sideBySideEpisodeFeeds,
                sharedEpisodeShelf = continuationEpisodeShelf,
                sharedEpisodesLoading = continuationEpisodesLoading,
                playbackReadyForCompanionLoads = firstFrameRendered,
                onPlayRelated = onPlayRelated,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }

    if (showResumePrompt && savedResume != null) {
        AlertDialog(
            onDismissRequest = {
                selectedResumeStart.longValue = 0L
                showResumePrompt = false
                resumeStore.record(media.mediaUri, 0L)
                if (exoPlayer.currentMediaItem != null) exoPlayer.seekTo(0L)
                exoPlayer.playWhenReady = true
            },
            title = { Text("Resume previous video?") },
            text = {
                Text("You were watching this video at " +
                    formatDuration(savedResume.positionMs) +
                    ". Continue from this point, or start over?")
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedResumeStart.longValue = savedResume.positionMs
                    showResumePrompt = false
                    if (exoPlayer.currentMediaItem != null) {
                        exoPlayer.seekTo(savedResume.positionMs)
                    }
                    exoPlayer.playWhenReady = true
                }) { Text("Resume") }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedResumeStart.longValue = 0L
                    showResumePrompt = false
                    resumeStore.record(media.mediaUri, 0L)
                    if (exoPlayer.currentMediaItem != null) exoPlayer.seekTo(0L)
                    exoPlayer.playWhenReady = true
                }) { Text("Start over") }
            }
        )
    }

    if (showBookmarks) {
        VideoBookmarksDialog(
            videoId = YoutubeUrls.videoId(media.mediaUri).orEmpty(),
            videoTitle = resolved?.title?.ifBlank { media.title } ?: media.title,
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
            onSeek = { ms -> exoPlayer.seekTo(ms.coerceIn(0L, duration.coerceAtLeast(0L))) },
            onDismiss = { showBookmarks = false }
        )
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

    if (viewingMenuVisible) {
        PlayerViewingModesDialog(
            viewingMode = activeViewingMode,
            dimPercent = dimPreview,
            nightAudioEnabled = activeNightAudio,
            nightAudioAvailable = nightAudioSupported,
            nightAudioActive = nightAudioActive,
            onChange = { mode, dim, night ->
                applyViewingMode(mode, dim, night)
            },
            onDismiss = { viewingMenuVisible = false }
        )
    }

    if (showSettings) {
        PlayerSettingsDialog(
            captionsEnabled = captionsEnabled,
            hasCaptions = hasCaptions,
            viewingMode = activeViewingMode,
            viewingDimPercent = dimPreview,
            nightAudioEnabled = activeNightAudio,
            nightAudioAvailable = nightAudioSupported,
            onViewingPreferencesChanged = ::applyViewingMode,
            gestureEnabled = gestureEnabled,
            autoHideControls = autoHideControls,
            fullscreen = isFullscreen,
            qualityOptions = resolved?.options?.map { it.height }?.distinct()?.sortedDescending().orEmpty(),
            currentQuality = qualityLabel,
            autoQuality = autoQuality,
            resizeMode = resizeMode,
            playbackSpeed = playbackSpeed,
            equalizerEnabled = equalizerSnapshot.enabled,
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
            onCaptionLanguage = ::selectCaptionLanguage,
            onDubSource = { dubSourceCode = it },
            onDubTarget = { dubTargetCode = it },
            onDubVoiceProfile = { dubVoiceProfile = it; liveDubEngine.setVoiceProfile(it) },
            onDubOriginalVolume = { dubOriginalVolume = it },
            onPlaybackSpeed = { speed ->
                playbackSpeed = speed
                exoPlayer.setPlaybackSpeed(speed)
                onPlaybackPreferenceChanged(
                    speed,
                    if (autoQuality) 0 else selectedHeight,
                    seekIntervalSeconds
                )
            },
            onOpenEqualizer = {
                showSettings = false
                equalizerSnapshot = equalizerController.prepareEqualizer()
                equalizerSnapshot = equalizerController.probeDts()
                showEqualizer = true
                controlsVisible = true
            },
            onCaptions = { enabled ->
                captionsEnabled = enabled
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
    textScale: Float,
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
    val safeTextScale = textScale.coerceIn(1.0f, 1.8f)
    val eqTitleFontSize = (13f * safeTextScale).sp
    val eqBodyFontSize = (9.5f * safeTextScale).sp
    val eqSmallFontSize = (8f * safeTextScale).sp

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
                .heightIn(max = 430.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Audio Equalizer",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = eqTitleFontSize,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("View", color = Color.White.copy(alpha = .66f), fontSize = eqBodyFontSize)
                FilterChip(
                    selected = snapshot.layoutId == "horizontal",
                    onClick = { onLayout("horizontal") },
                    label = { Text("↔ H", fontSize = eqBodyFontSize) }
                )
                FilterChip(
                    selected = snapshot.layoutId == "vertical",
                    onClick = { onLayout("vertical") },
                    label = { Text("↕ V", fontSize = eqBodyFontSize) }
                )
                Spacer(Modifier.width(4.dp))
                FilterChip(
                    selected = snapshot.styleId == "classic",
                    onClick = { onStyle("classic") },
                    label = { Text("Classic", fontSize = eqBodyFontSize) }
                )
                FilterChip(
                    selected = snapshot.styleId == "graphic",
                    onClick = { onStyle("graphic") },
                    label = { Text("Graphic", fontSize = eqBodyFontSize) }
                )
            }

            if (!snapshot.supported) {
                Text(
                    "Equalizer will become available when the playback audio session is ready.",
                    color = Color.White.copy(alpha = .72f),
                    fontSize = eqBodyFontSize
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
                            label = { Text(label, fontSize = eqBodyFontSize) }
                        )
                    }
                    if (snapshot.presetId == "custom") {
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = { Text("Custom", fontSize = eqBodyFontSize) }
                        )
                    }
                    if (snapshot.hasUserPreset) {
                        FilterChip(
                            selected = snapshot.presetId == "user",
                            onClick = onRecallUser,
                            label = { Text(snapshot.userPresetName, fontSize = eqBodyFontSize) }
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
                        label = { Text("Custom / User preset", fontSize = eqBodyFontSize) },
                        placeholder = { Text("e.g. My Speakers", fontSize = eqBodyFontSize) },
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(fontSize = eqBodyFontSize)
                    )
                    Button(
                        onClick = { onSaveUser(userPresetName) },
                        enabled = snapshot.bands.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Text("Save User", fontSize = eqBodyFontSize)
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
                                Text("Surround / Spatial", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = eqBodyFontSize)
                                Text(
                                    if (snapshot.surroundAvailable) "Session surround" else "Not supported on this audio route",
                                    color = Color.White.copy(alpha = .58f),
                                    fontSize = eqSmallFontSize
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
                                Text("Depth", color = Color.White.copy(alpha = .72f), fontSize = eqSmallFontSize, modifier = Modifier.width(46.dp))
                                Slider(
                                    value = snapshot.surroundStrength,
                                    onValueChange = onSurroundStrength,
                                    valueRange = 0f..1f,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${(snapshot.surroundStrength * 100).roundToInt()}%",
                                    color = Color.White.copy(alpha = .72f),
                                    fontSize = eqSmallFontSize,
                                    modifier = Modifier.width(34.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = .08f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("DTS", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = eqBodyFontSize)
                                Text(
                                    when {
                                        snapshot.dtsAvailable && !snapshot.dtsEffectName.isNullOrBlank() ->
                                            snapshot.dtsEffectName
                                        snapshot.dtsAvailable -> "Device DTS effect"
                                        else -> "No DTS effect exposed by this device"
                                    },
                                    color = if (snapshot.dtsAvailable) TubeDeckAccentMint else Color.White.copy(alpha = .52f),
                                    fontSize = eqSmallFontSize,
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
                                    fontSize = eqSmallFontSize,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    String.format(Locale.ROOT, "%+.1f dB", band.levelDb),
                                    color = Color.White.copy(alpha = .72f),
                                    fontSize = eqSmallFontSize
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
                                    fontSize = eqSmallFontSize
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
                                    fontSize = eqSmallFontSize,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    equalizerFrequencyLabel(band.centerFrequencyHz),
                                    color = Color.White.copy(alpha = .82f),
                                    fontSize = eqSmallFontSize
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
                                fontSize = eqBodyFontSize,
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
private fun PlaybackContinuationCards(
    episodesLoading: Boolean,
    currentVideoId: String,
    relatedVideos: List<YoutubeStreamResolver.RelatedVideo>,
    creatorLinks: List<CreatorLinksPolicy.Link> = emptyList(),
    onCreatorLink: (CreatorLinksPolicy.Link) -> Unit = {},
    episodeShelf: YoutubeStreamResolver.DiscoveredPlaylist?,
    showRelevantShelf: Boolean,
    showEpisodesShelf: Boolean,
    relatedLoading: Boolean = false,
    relatedError: Boolean = false,
    firstFocus: FocusRequester? = null,
    allowAnimatedPreviews: Boolean = false,
    onClose: () -> Unit = {},
    onRetry: () -> Unit = {},
    onPlayMedia: (OfflineMedia) -> Unit
) {
    val episodeCards = remember(episodeShelf, currentVideoId) {
        val episodes = episodeShelf?.episodes.orEmpty().distinctBy { it.videoId }
        val currentIndex = episodes.indexOfFirst { it.videoId == currentVideoId }
        if (currentIndex >= 0) {
            (episodes.drop(currentIndex + 1) + episodes.take(currentIndex))
                .filterNot { it.videoId == currentVideoId }
                .take(12)
        } else {
            episodes.filterNot { it.videoId == currentVideoId }.take(12)
        }
    }
    val relatedCards = remember(relatedVideos, currentVideoId) {
        relatedVideos
            .filterNot { it.videoId == currentVideoId }
            .distinctBy { it.videoId }
            .take(12)
    }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val compactCardWidth = ((screenWidthDp - 50f) / 6f).coerceIn(88f, 132f).dp

    Surface(
        color = Color.Black.copy(alpha = .92f),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp,
        shadowElevation = 10.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("More videos", color = TubeDeckTextPrimary,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.then(
                        if (firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier
                    ).tvInputHighlight(RoundedCornerShape(8.dp))
                ) { Text("Close", color = TubeDeckAccentCyan) }
            }
            if (!showRelevantShelf && !showEpisodesShelf) {
                Text(
                    "Relevant and Episodes rows are disabled in Settings",
                    color = TubeDeckTextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            if (showRelevantShelf) {
                Text(
                    "Relevant",
                    color = TubeDeckTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
                if (relatedCards.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        items(
                            items = relatedCards,
                            key = { "gesture-related-${it.videoId}" }
                        ) { related ->
                            RelatedVideoCard(
                                video = related,
                                cardWidth = compactCardWidth,
                                allowAnimatedPreview = allowAnimatedPreviews
                            ) {
                                onPlayMedia(
                                    OfflineMedia(
                                        id = "gesture-related-${related.videoId}-${System.nanoTime()}",
                                        title = related.title,
                                        videoId = related.videoId,
                                        thumbnailUrl = related.thumbnailUrl,
                                        mediaUri = related.url
                                    )
                                )
                            }
                        }
                    }
                } else if (relatedLoading) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(7.dp))
                        Text("Finding related videos…", color = TubeDeckTextSecondary)
                    }
                } else if (relatedError) {
                    TextButton(onClick = onRetry) {
                        Text("Recommendations unavailable · Retry", color = TubeDeckAccentCyan)
                    }
                } else {
                    Text(
                        "No related cards on this video",
                        color = TubeDeckTextSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            if (showRelevantShelf && showEpisodesShelf) {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = TubeDeckBorder.copy(alpha = .48f))
                Spacer(Modifier.height(2.dp))
            }

            if (creatorLinks.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = TubeDeckBorder.copy(alpha = .5f))
                Text(
                    "Links from this video's creator",
                    color = TubeDeckTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    items(creatorLinks, key = { "creator-${it.url}" }) { link ->
                        TextButton(
                            onClick = { onCreatorLink(link) },
                            modifier = Modifier.tvInputHighlight(RoundedCornerShape(8.dp))
                        ) {
                            Icon(
                                when (link.kind) {
                                    CreatorLinksPolicy.Kind.VIDEO -> Icons.Default.PlayCircle
                                    CreatorLinksPolicy.Kind.PLAYLIST -> Icons.Default.PlaylistPlay
                                    CreatorLinksPolicy.Kind.CHANNEL -> Icons.Default.Person
                                }, null, tint = TubeDeckAccentCyan,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(link.label, color = TubeDeckTextPrimary, fontSize = 10.sp)
                        }
                    }
                }
            }

            if (showEpisodesShelf) {
                Text(
                    "Episodes",
                    color = TubeDeckTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
                when {
                    episodesLoading -> {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(7.dp))
                            Text("Finding episode cards…", color = TubeDeckTextSecondary, fontSize = 10.sp)
                        }
                    }

                    episodeCards.isNotEmpty() -> {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            items(
                                items = episodeCards,
                                key = { "gesture-episode-${it.videoId}" }
                            ) { episode ->
                                EpisodeVideoCard(
                                    episode = episode,
                                    cardWidth = compactCardWidth
                                ) {
                                    onPlayMedia(
                                        OfflineMedia(
                                            id = "gesture-episode-${episode.videoId}-${System.nanoTime()}",
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

                    else -> {
                        Text(
                            "No additional episode cards found",
                            color = TubeDeckTextSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
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
    sideBySideEpisodeFeeds: Boolean,
    sharedEpisodeShelf: YoutubeStreamResolver.DiscoveredPlaylist?,
    sharedEpisodesLoading: Boolean,
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

    val currentVideoId = YoutubeUrls.videoId(media.mediaUri).orEmpty()
    val episodeItems = sharedEpisodeShelf?.episodes.orEmpty()
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
                        Text(if (showComments) "Hide comments" else "Comments", fontSize = 9.5.sp)
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
                        Text(if (showDescription) "Hide description" else "Description", fontSize = 9.5.sp)
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
                                Text(metadata, color = TubeDeckTextSecondary, fontSize = 9.5.sp)
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
                    } else if (relatedVideos.isEmpty() && episodeItems.isEmpty() && !sharedEpisodesLoading) {
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
                    } else if (sideBySideEpisodeFeeds && autoLoadEpisodes && autoLoadUpNext) {
                        // Two independent, vertically scrollable portrait feeds with equal card widths.
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                                val feedCardWidth = maxWidth
                                Column(Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "More episodes",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            Icons.Default.PlaylistPlay,
                                            contentDescription = null,
                                            tint = TubeDeckAccentMint,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (sharedEpisodesLoading) {
                                            item(key = "dual-episodes-loading") {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                                                    Spacer(Modifier.width(5.dp))
                                                    Text("Finding episodes…", color = TubeDeckTextSecondary, fontSize = 10.sp)
                                                }
                                            }
                                        }
                                        items(episodeItems, key = { "dual-episode-${it.videoId}" }) { episode ->
                                            EpisodeVideoCard(episode = episode, cardWidth = feedCardWidth) {
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
                                        if (episodeItems.isEmpty() && !sharedEpisodesLoading) {
                                            item(key = "dual-episodes-empty") {
                                                Text("No additional episodes found", color = TubeDeckTextSecondary, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            VerticalDivider(
                                modifier = Modifier.fillMaxHeight(),
                                color = TubeDeckBorder.copy(alpha = .72f)
                            )
                            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                                val feedCardWidth = maxWidth
                                Column(Modifier.fillMaxSize()) {
                                    Text(
                                        "Up next",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                    )
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(relatedVideos, key = { "dual-related-${it.videoId}" }) { related ->
                                            RelatedVideoCard(video = related, cardWidth = feedCardWidth) {
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
                                        if (relatedVideos.isEmpty()) {
                                            item(key = "dual-related-empty") {
                                                Text("No recommendations available yet", color = TubeDeckTextSecondary, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (autoLoadEpisodes && sharedEpisodesLoading) {
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
    cardWidth: Dp = 132.dp,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(cardWidth)
            .tvInputHighlight(RoundedCornerShape(8.dp))
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
            fontSize = 9.5.sp,
            lineHeight = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
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
private fun RelatedVideoCard(
    video: YoutubeStreamResolver.RelatedVideo,
    cardWidth: Dp,
    allowAnimatedPreview: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    var previewReady by remember { mutableStateOf(false) }
    LaunchedEffect(focused, allowAnimatedPreview) {
        previewReady = false
        if (focused && allowAnimatedPreview) {
            delay(1_100)
            previewReady = true
        }
    }
    val animatedUrl = remember(video.videoId) { FocusAnimatedThumbs.url(video.videoId) }
    Column(
        modifier = Modifier
            .width(cardWidth)
            .onFocusChanged { focused = it.isFocused }
            .tvInputHighlight(RoundedCornerShape(8.dp))
            .clickable(enabled = true, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            val imageModifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
            if (previewReady && animatedUrl != null) {
                SubcomposeAsyncImage(
                    model = animatedUrl,
                    imageLoader = FocusAnimatedThumbs.imageLoader(context),
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = imageModifier,
                    content = {
                        if (painter.state is AsyncImagePainter.State.Success) {
                            SubcomposeAsyncImageContent()
                        } else {
                            AsyncImage(
                                model = video.thumbnailUrl,
                                contentDescription = video.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                )
            } else {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = imageModifier
                )
            }
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
        Text(
            video.title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.5.sp,
            lineHeight = 11.sp,
            softWrap = true
        )
        Text(
            video.channelTitle,
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

/**
 * A dedicated focusable dialog prevents the old DropdownMenu dismissal callback
 * from writing the stale mode back over a newly selected one.
 * Reused for ALL thirteen player chrome profiles; independent of theme.
 */
@Composable
private fun PlayerViewingModesDialog(
    viewingMode: ViewingMode,
    dimPercent: Int,
    nightAudioEnabled: Boolean,
    nightAudioAvailable: Boolean,
    nightAudioActive: Boolean,
    onChange: (ViewingMode, Int, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Viewing modes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ViewingMode.values().forEach { mode ->
                    FilterChip(
                        selected = viewingMode == mode,
                        onClick = { onChange(mode, dimPercent, nightAudioEnabled) },
                        label = { Text(mode.label) },
                        leadingIcon = {
                            if (viewingMode == mode) Icon(Icons.Default.Check, null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (viewingMode == ViewingMode.LIGHTS_OUT || viewingMode == ViewingMode.NIGHT) {
                    Text("Video dimming · ${dimPercent.coerceIn(10, 85)}%")
                    Slider(
                        value = dimPercent.coerceIn(10, 85).toFloat(),
                        onValueChange = { onChange(viewingMode, it.toInt(), nightAudioEnabled) },
                        valueRange = 10f..85f
                    )
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Night Audio", Modifier.weight(1f))
                    Switch(
                        checked = nightAudioEnabled,
                        enabled = nightAudioAvailable,
                        onCheckedChange = { onChange(viewingMode, dimPercent, it) }
                    )
                }
                if (!nightAudioAvailable) {
                    Text("Night Audio is not supported on this output", fontSize = 11.sp)
                } else if (nightAudioEnabled && !nightAudioActive) {
                    Text("Audio effect not available in this playback session", fontSize = 11.sp)
                }
                Text(
                    "Full Blackout remains a separate control. Mode changes take effect immediately.",
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun PlayerSettingsDialog(
    captionsEnabled: Boolean,
    hasCaptions: Boolean,
    viewingMode: ViewingMode,
    viewingDimPercent: Int,
    nightAudioEnabled: Boolean,
    nightAudioAvailable: Boolean,
    onViewingPreferencesChanged: (ViewingMode, Int, Boolean) -> Unit,
    gestureEnabled: Boolean,
    autoHideControls: Boolean,
    fullscreen: Boolean,
    qualityOptions: List<Int>,
    currentQuality: String,
    autoQuality: Boolean,
    resizeMode: Int,
    playbackSpeed: Float,
    equalizerEnabled: Boolean,
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
    onPlaybackSpeed: (Float) -> Unit,
    onOpenEqualizer: () -> Unit,
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
    var playbackSpeedMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Player settings") },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Viewing mode", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ViewingMode.values().forEach { mode ->
                        FilterChip(
                            selected = viewingMode == mode,
                            onClick = { onViewingPreferencesChanged(mode, viewingDimPercent, nightAudioEnabled) },
                            label = { Text(mode.label) }
                        )
                    }
                }
                if (viewingMode == ViewingMode.LIGHTS_OUT || viewingMode == ViewingMode.NIGHT) {
                    Text("Video dimming · $viewingDimPercent%")
                    Slider(
                        value = viewingDimPercent.toFloat(),
                        onValueChange = {
                            onViewingPreferencesChanged(viewingMode, it.toInt(), nightAudioEnabled)
                        },
                        valueRange = 10f..85f
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Night Audio", Modifier.weight(1f))
                    Switch(
                        checked = nightAudioEnabled,
                        onCheckedChange = {
                            onViewingPreferencesChanged(viewingMode, viewingDimPercent, it)
                        },
                        enabled = nightAudioAvailable
                    )
                }
                if (!nightAudioAvailable) Text("Night Audio unavailable on this device", fontSize = 11.sp)
                HorizontalDivider()
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
                                when (captionLanguageTag.substringBefore('-').lowercase(Locale.US)) {
                                    "auto" -> "Auto"
                                    "en" -> "English"
                                    "ur" -> "Urdu"
                                    else -> availableSubtitles.firstOrNull {
                                        it.languageTag == captionLanguageTag
                                    }?.displayName ?: captionLanguageTag
                                }
                            )
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = captionMenuExpanded, onDismissRequest = { captionMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Auto") },
                                onClick = { captionMenuExpanded = false; onCaptionLanguage("auto") }
                            )
                            listOf("en" to "English", "ur" to "Urdu").forEach { (tag, label) ->
                                val track = availableSubtitles.firstOrNull {
                                    it.languageTag.substringBefore('-').equals(tag, ignoreCase = true)
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            label + if (track?.autoGenerated == true) " • Auto-translated" else ""
                                        )
                                    },
                                    onClick = { captionMenuExpanded = false; onCaptionLanguage(tag) }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()
                Text("Playback speed", fontWeight = FontWeight.Bold)
                Box {
                    OutlinedButton(
                        onClick = { playbackSpeedMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (kotlin.math.abs(playbackSpeed - 1.0f) < 0.01f) {
                                "1.0x · Normal"
                            } else {
                                playbackSpeedLabel(playbackSpeed)
                            }
                        )
                        Spacer(Modifier.weight(1f))
                        Icon(
                            if (playbackSpeedMenuExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            null
                        )
                    }
                    DropdownMenu(
                        expanded = playbackSpeedMenuExpanded,
                        onDismissRequest = { playbackSpeedMenuExpanded = false }
                    ) {
                        PLAYBACK_SPEED_OPTIONS.forEach { speed ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (kotlin.math.abs(playbackSpeed - speed) < 0.01f) {
                                            Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(
                                            if (kotlin.math.abs(speed - 1.0f) < 0.01f) {
                                                "1.0x · Normal"
                                            } else {
                                                playbackSpeedLabel(speed)
                                            }
                                        )
                                    }
                                },
                                onClick = {
                                    playbackSpeedMenuExpanded = false
                                    onPlaybackSpeed(speed)
                                }
                            )
                        }
                    }
                }

                Text("Audio enhancements", fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onOpenEqualizer, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Equalizer, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (equalizerEnabled) "Equalizer · On" else "Equalizer · Off")
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null)
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

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // The preview follows the touched/hovered thumb, clamped at screen edges.
        // This is a single overlay used by all player chrome/theme profiles.
        val maxLeft = (maxWidth - previewWidth).coerceAtLeast(0.dp)
        val left = (maxWidth * normalized - previewWidth / 2).coerceIn(0.dp, maxLeft)
        Column(modifier = Modifier.offset(x = left).width(previewWidth)) {
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
                        val frame = SeekPreviewPolicy.frame(
                            positionMs = positionMs,
                            frameDurationMs = storyboard.durationPerFrameMs,
                            totalFrames = storyboard.totalCount,
                            columns = storyboard.framesPerPageX,
                            rows = storyboard.framesPerPageY,
                            pageCount = storyboard.urls.size
                        )
                        if (frame != null) {
                            val sheetWidth = previewWidth * storyboard.framesPerPageX.toFloat()
                            val sheetHeight = previewHeight * storyboard.framesPerPageY.toFloat()
                            AsyncImage(
                                model = storyboard.urls[frame.page],
                                contentDescription = "Video frame at " + formatDuration(positionMs),
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier
                                    .requiredSize(sheetWidth, sheetHeight)
                                    .offset(
                                        x = (-previewWidth.value * frame.column).dp,
                                        y = (-previewHeight.value * frame.row).dp
                                    )
                            )
                        }
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
}

@Composable
private fun CompactSeekBar(
    chrome: DeckPlayerChrome,
    progress: Float,
    chapterFractions: List<Float> = emptyList(),
    sponsorRanges: List<Pair<Float, Float>> = emptyList(),
    onScrubChange: (Float?) -> Unit,
    onSeekFinished: (Float) -> Unit
) {
    val normalized = progress.coerceIn(0f, 1f)
    val seekAccent = when (chrome) {
        DeckPlayerChrome.YOUTUBE_MOBILE, DeckPlayerChrome.YOUTUBE_DESKTOP -> Color(0xFFFF0033)
        DeckPlayerChrome.VLC_COMPACT -> Color(0xFFFF9800)
        DeckPlayerChrome.PLEX -> Color(0xFFE9AC37)
        DeckPlayerChrome.MATERIAL -> Color(0xFF6750A4)
        DeckPlayerChrome.APPLE_FLOAT -> Color.White
        DeckPlayerChrome.MX_GESTURE -> Color(0xFF0088FF)
        DeckPlayerChrome.CYBER -> TubeDeckAccentMint
        else -> TubeDeckAccentCyan
    }
    val seekStroke = when (chrome) {
        DeckPlayerChrome.PLEX, DeckPlayerChrome.KODI, DeckPlayerChrome.MX_GESTURE -> 5.dp
        DeckPlayerChrome.VLC_COMPACT, DeckPlayerChrome.CONSOLE -> 2.dp
        else -> 3.dp
    }
    Canvas(
        modifier = Modifier.fillMaxWidth()
            .height(26.dp)
            .pointerInput("seek-preview-hover") {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed } && size.width > 0) {
                            when (event.type) {
                                PointerEventType.Enter, PointerEventType.Move -> {
                                    val point = event.changes.firstOrNull()
                                    if (point != null) {
                                        onScrubChange(
                                            (point.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        )
                                    }
                                }
                                PointerEventType.Exit -> onScrubChange(null)
                                else -> Unit
                            }
                        }
                    }
                }
            }
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
            color = seekAccent,
            start = Offset(0f, y),
            end = Offset(activeX, y),
            strokeWidth = seekStroke.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = seekAccent,
            radius = (if (chrome == DeckPlayerChrome.MX_GESTURE) 6.5.dp else 4.5.dp).toPx(),
            center = Offset(activeX, y)
        )
    }
}

@Composable
private fun FadeVisibility(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    val motion = LocalDeckThemeProfile.current.motionStyle
    val duration = LocalDeckExperience.current.motionDurationMs
    val entrance = if (motion == DeckMotionStyle.PULSE) {
        fadeIn(animationSpec = tween(durationMillis = duration)) +
            slideInVertically(
                initialOffsetY = { height -> height / 9 },
                animationSpec = tween(durationMillis = duration)
            )
    } else fadeIn(animationSpec = tween(durationMillis = duration))
    AnimatedVisibility(
        visible = visible,
        enter = entrance,
        exit = fadeOut(animationSpec = tween(durationMillis = duration))
    ) {
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
private fun selectResolvedOption(
    resolved: YoutubeStreamResolver.ResolvedMedia,
    requestedHeight: Int,
    compatibilityMode: Boolean
): YoutubeStreamResolver.VideoOption? {
    val candidates = if (compatibilityMode) {
        resolved.progressiveOptions.filter { it.videoMimeType == "video/mp4" && it.height <= 480 }
            .ifEmpty { resolved.options.filter { it.videoMimeType == "video/mp4" } }
            .ifEmpty { resolved.options }
    } else resolved.options
    val height = PlaybackQualityPolicy.chooseHeight(candidates.map { it.height }, requestedHeight, compatibilityMode)
    return candidates.firstOrNull { it.height == height }
}

private fun buildResolvedSource(
    factory: DefaultDataSource.Factory,
    resolved: YoutubeStreamResolver.ResolvedMedia,
    option: YoutubeStreamResolver.VideoOption
): MediaSource {
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

private val PLAYBACK_SPEED_OPTIONS = listOf(
    1.0f,
    0.25f, 0.5f, 0.75f,
    1.25f, 1.5f, 1.75f, 2.0f,
    2.5f, 3.0f, 3.5f, 4.0f, 4.5f, 5.0f
)

private fun playbackSpeedLabel(speed: Float): String =
    if (kotlin.math.abs(speed - kotlin.math.round(speed)) < 0.01f) {
        String.format(Locale.US, "%.1fx", speed)
    } else {
        String.format(Locale.US, "%.2fx", speed).trimEnd('0').replace(".x", "x")
    }

private fun setFullscreen(activity: Activity, fullscreen: Boolean) {
    val window = activity.window
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.systemBarsBehavior =
        androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

    // Only an explicit fullscreen player view may hide Android system bars.
    // Blackout is a separate opaque panel; linking it to immersive mode summoned
    // Android's first-use confirmation window above the blackout mask.
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


