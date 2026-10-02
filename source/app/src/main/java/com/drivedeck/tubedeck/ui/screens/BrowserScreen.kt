package com.drivedeck.tubedeck.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import com.drivedeck.tubedeck.data.model.TubeDeckSettings
import com.drivedeck.tubedeck.data.model.PlayerMode
import com.drivedeck.tubedeck.ui.theme.*
import com.drivedeck.tubedeck.player.YoutubeUrls
import java.io.ByteArrayInputStream
import org.json.JSONArray
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
private fun DraggableDownloadFab(
    x: Float,
    y: Float,
    maxX: Float,
    maxY: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit,
    onPositionChanged: (Float, Float) -> Unit
) {
    var position by remember(x, y, maxX, maxY) {
        mutableStateOf(androidx.compose.ui.geometry.Offset(x.coerceIn(0f, maxX), y.coerceIn(0f, maxY)))
    }

    FloatingActionButton(
        onClick = onClick,
        containerColor = TubeDeckSurfaceVariant.copy(alpha = 0.92f),
        contentColor = tint,
        shape = CircleShape,
        modifier = Modifier
            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
            .size(48.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        position = androidx.compose.ui.geometry.Offset(
                            (position.x + dragAmount.x).coerceIn(0f, maxX),
                            (position.y + dragAmount.y).coerceIn(0f, maxY)
                        )
                    },
                    onDragEnd = { onPositionChanged(position.x, position.y) }
                )
            }
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    currentUrl: String,
    onUrlChanged: (String) -> Unit,
    onNativeVideo: (String) -> Unit,
    onCanGoBackChanged: (Boolean) -> Unit,
    onCanGoForwardChanged: (Boolean) -> Unit,
    onQuickDownload: (String) -> Unit,
    onSilentDownload: (String) -> Unit,
    settings: TubeDeckSettings,
    onUpdateFabPosition: (instantX: Float, instantY: Float, customX: Float, customY: Float) -> Unit,
    onAdBlocked: (Int) -> Unit,
    webViewRef: (WebView?) -> Unit,
    modifier: Modifier = Modifier,
    onFullscreenChanged: (Boolean) -> Unit = {}
) {
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val activity = context as? Activity
    val currentOnUrlChanged by rememberUpdatedState(onUrlChanged)
    val currentOnNativeVideo by rememberUpdatedState(onNativeVideo)
    val currentOnAdBlocked by rememberUpdatedState(onAdBlocked)
    val currentSettings by rememberUpdatedState(settings)
    val latestUrl by rememberUpdatedState(currentUrl)
    var lastObservedVideoId by remember { mutableStateOf<String?>(null) }
    var lastNativeRequestAt by remember { mutableLongStateOf(0L) }

    fun observeUrl(view: WebView?, url: String?, explicitNavigation: Boolean = false) {
        if (url?.startsWith("https://") != true) return
        val videoId = YoutubeUrls.videoId(url)
        if (currentSettings.playerMode == PlayerMode.NATIVE && videoId != null) {
            val now = SystemClock.elapsedRealtime()

            // Main-frame clicks are explicit user choices and may intentionally reopen the same
            // video. Passive WebView callbacks/polling for the same consumed watch URL must never
            // relaunch the player; that was the source of the search/video/back loop.
            val shouldLaunch =
                explicitNavigation ||
                    lastObservedVideoId != videoId ||
                    now - lastNativeRequestAt > 10_000L

            if (shouldLaunch) {
                lastObservedVideoId = videoId
                lastNativeRequestAt = now
                currentOnNativeVideo(url)
            }

            if (!explicitNavigation) {
                // YouTube SPA navigation may already have pushed /watch into WebView history.
                // Go back to the preserved results page instead of loadUrl(), which would create
                // another history entry and cause Back to bounce into /watch again.
                if (view?.canGoBack() == true) {
                    view.goBack()
                } else {
                    val stableUrl = latestUrl
                        .takeIf { YoutubeUrls.videoId(it) == null && it.startsWith("https://") }
                        ?: "https://www.youtube.com/"
                    if (view?.url != stableUrl) view?.loadUrl(stableUrl)
                }
            }
            return
        }

        currentOnUrlChanged(url)
    }

    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var isCustomViewShowing by remember { mutableStateOf(false) }

    // Clean up full screen view on disposal
    DisposableEffect(customView) {
        val decorView = activity?.window?.decorView as? FrameLayout
        if (customView != null && decorView != null) {
            decorView.addView(customView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            isCustomViewShowing = true
            onFullscreenChanged(true)
        }
        onDispose {
            if (customView != null && decorView != null) {
                decorView.removeView(customView)
                isCustomViewShowing = false
                onFullscreenChanged(false)
            }
        }
    }

    BackHandler(enabled = isCustomViewShowing) {
        customViewCallback?.onCustomViewHidden()
    }

    val tubeDeckInjectedScript = remember(
        settings.adBlockEnabled, settings.blockVideoAds, settings.blockBannerAds, settings.blockShortsEnabled
    ) {
        """
        (function() {
            if (window.__tdInterval) clearInterval(window.__tdInterval);
            const oldStyle = document.getElementById('td-browser-style');
            if (oldStyle) oldStyle.remove();
            
            // AdBlock & Shorts Removal CSS
            const style = document.createElement('style');
            style.id = 'td-browser-style';
            style.textContent = `
                ${if (settings.blockShortsEnabled) """
                ytm-reel-shelf-renderer, ytd-reel-shelf-renderer, ytm-shorts-lockup-view-model, ytd-shorts,
                a[href*="/shorts/"], [aria-label*="Shorts"], .pivot-shorts, [tab-identifier="FEshorts"] {
                    display: none !important;
                }
                """ else ""}
                ${if (settings.adBlockEnabled && settings.blockBannerAds) ".video-ads, .ytp-ad-module, .ad-container, #player-ads { display: none !important; }" else ""}
            `;
            document.documentElement.appendChild(style);

            function runAdBlockEngine() {
                ${if (settings.adBlockEnabled && settings.blockVideoAds) """
                    const video = document.querySelector('video');
                    const player = document.querySelector('#movie_player');
                    if (player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'))) {
                        if (video) {
                            video.muted = true;
                            video.playbackRate = 16.0;
                            if (video.duration > 0.1) video.currentTime = video.duration - 0.01;
                        }
                    }
                    const skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-skip-ad-button');
                    if (skipBtn) skipBtn.click();
                """ else ""}
            }
            window.__tdInterval = setInterval(runAdBlockEngine, 750);
        })();
        """.trimIndent()
    }
    val currentScript by rememberUpdatedState(tubeDeckInjectedScript)

    // Persistent WebView instance
    val persistentWebView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            this.settings.javaScriptEnabled = true
            this.settings.domStorageEnabled = true
            this.settings.allowFileAccess = false
            this.settings.allowContentAccess = false
            this.settings.mediaPlaybackRequiresUserGesture = false
            this.settings.databaseEnabled = true
            this.settings.useWideViewPort = true
            this.settings.loadWithOverviewMode = true
            this.settings.javaScriptCanOpenWindowsAutomatically = false
            this.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                this.settings.safeBrowsingEnabled = true
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress >= 90) isLoading = false
                }
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    customView = view
                    customViewCallback = callback
                }
                override fun onHideCustomView() {
                    customView = null
                    customViewCallback = null
                }
            }
            
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: ""
                    if (!url.startsWith("https://")) return true
                    if (request?.isForMainFrame == true && currentSettings.playerMode == PlayerMode.NATIVE && YoutubeUrls.videoId(url) != null) {
                        observeUrl(view, url, explicitNavigation = true)
                        return true
                    }
                    return false
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    observeUrl(view, url)
                    onCanGoBackChanged(view?.canGoBack() == true)
                    onCanGoForwardChanged(view?.canGoForward() == true)
                }
                
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (url != null && !url.startsWith("https://") && url != "about:blank") {
                        view?.stopLoading()
                        return
                    }
                    if (url != null && currentSettings.playerMode == PlayerMode.NATIVE && YoutubeUrls.videoId(url) != null) {
                        view?.stopLoading()
                        observeUrl(view, url)
                        return
                    }
                    isLoading = true
                    observeUrl(view, url)
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    isLoading = false
                    observeUrl(view, url)
                    if (url != null && YoutubeUrls.isYoutubePage(url)) view?.evaluateJavascript(currentScript, null)
                    onCanGoBackChanged(view?.canGoBack() == true)
                    onCanGoForwardChanged(view?.canGoForward() == true)
                }

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val liveSettings = currentSettings
                    if (!liveSettings.adBlockEnabled) return super.shouldInterceptRequest(view, request)
                    val reqUrl = request?.url?.toString() ?: ""
                    val adKeywords = listOf("doubleclick", "googleads", "pagead", "adserver")
                    val trackers = listOf("google-analytics.com", "googletagmanager.com")
                    if ((liveSettings.blockBannerAds && adKeywords.any { reqUrl.contains(it, ignoreCase = true) }) ||
                        (liveSettings.blockTrackers && trackers.any { reqUrl.contains(it, ignoreCase = true) })) {
                        view?.post { currentOnAdBlocked(1) }
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }
        }
    }

    DisposableEffect(persistentWebView) {
        webViewRef(persistentWebView)
        onDispose {
            webViewRef(null)
        }
    }

    LaunchedEffect(tubeDeckInjectedScript) {
        if (persistentWebView.url?.let { YoutubeUrls.isYoutubePage(it) } == true) {
            persistentWebView.evaluateJavascript(tubeDeckInjectedScript, null)
        }
    }

    LaunchedEffect(persistentWebView, activity) {
        while (true) {
            delay(1500)
            if (activity?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) {
                continue
            }
            persistentWebView.evaluateJavascript("location.href") { result ->
                val url = runCatching { JSONArray("[$result]").optString(0) }.getOrNull()
                if (url?.startsWith("https://") == true && url != latestUrl) observeUrl(persistentWebView, url)
            }
        }
    }

    LaunchedEffect(currentUrl) {
        if (persistentWebView.url != currentUrl && currentUrl.startsWith("https://")) {
            persistentWebView.loadUrl(currentUrl)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(TubeDeckBackground)) {
        AndroidView(
            factory = { persistentWebView },
            modifier = Modifier.fillMaxSize().navigationBarsPadding().testTag("tubedeck_webview"),
            onRelease = { view ->
                view.webViewClient = WebViewClient()
                view.webChromeClient = WebChromeClient()
                view.stopLoading()
                view.destroy()
            }
        )

        if (isLoading && !isCustomViewShowing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).height(3.dp),
                color = TubeDeckAccentCyan, trackColor = TubeDeckBackground
            )
        }

        if (!isCustomViewShowing) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val maxX = with(density) { (maxWidth - 48.dp).toPx() }.coerceAtLeast(0f)
                val maxY = with(density) { (maxHeight - 48.dp).toPx() }.coerceAtLeast(0f)
                // Instant Download Button (Amber)
                // Both shortcuts are freely draggable. Positions are persisted by MainActivity.
                DraggableDownloadFab(
                    x = settings.fabInstantX,
                    y = settings.fabInstantY,
                    maxX = maxX,
                    maxY = maxY,
                    icon = Icons.Default.FlashOn,
                    tint = TubeDeckAccentAmber,
                    contentDescription = "Instant Download",
                    onClick = { onSilentDownload(persistentWebView.url ?: currentUrl) },
                    onPositionChanged = { x, y ->
                        onUpdateFabPosition(x, y, settings.fabCustomX, settings.fabCustomY)
                    }
                )

                DraggableDownloadFab(
                    x = settings.fabCustomX,
                    y = settings.fabCustomY,
                    maxX = maxX,
                    maxY = maxY,
                    icon = Icons.Default.Download,
                    tint = TubeDeckAccentCyan,
                    contentDescription = "Custom Download",
                    onClick = { onQuickDownload(persistentWebView.url ?: currentUrl) },
                    onPositionChanged = { x, y ->
                        onUpdateFabPosition(settings.fabInstantX, settings.fabInstantY, x, y)
                    }
                )
            }
        }
    }
}
