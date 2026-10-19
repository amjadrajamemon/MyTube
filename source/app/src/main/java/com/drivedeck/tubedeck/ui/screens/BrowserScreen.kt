package com.drivedeck.tubedeck.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.os.Build
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.drivedeck.tubedeck.data.model.TubeDeckSettings
import com.drivedeck.tubedeck.data.model.PlayerMode
import com.drivedeck.tubedeck.ui.theme.*
import com.drivedeck.tubedeck.player.YoutubeUrls
import java.io.ByteArrayInputStream
import org.json.JSONArray
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun BrowserScreen(
    currentUrl: String,
    onUrlChanged: (String) -> Unit,
    onNativeVideo: (String) -> Unit,
    onNativePlaylist: (String) -> Unit = {},
    onCanGoBackChanged: (Boolean) -> Unit,
    onCanGoForwardChanged: (Boolean) -> Unit,
    settings: TubeDeckSettings,
    onAdBlocked: (Int) -> Unit,
    webViewRef: (WebView?) -> Unit,
    onPlaybackStateChanged: (isPlaying: Boolean, positionMs: Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    navigationGeneration: Int = 0,
    historyResetGeneration: Int = 0,
    desktopTheaterEnabled: Boolean = false,
    onFullscreenChanged: (Boolean) -> Unit = {}
) {
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity
    val currentOnUrlChanged by rememberUpdatedState(onUrlChanged)
    val currentOnNativeVideo by rememberUpdatedState(onNativeVideo)
    val currentOnNativePlaylist by rememberUpdatedState(onNativePlaylist)
    val currentOnAdBlocked by rememberUpdatedState(onAdBlocked)
    val currentOnPlaybackStateChanged by rememberUpdatedState(onPlaybackStateChanged)
    val currentSettings by rememberUpdatedState(settings)
    val currentIsActive by rememberUpdatedState(isActive)
    val latestUrl by rememberUpdatedState(currentUrl)
    val nativeNavigation = remember { NativeVideoNavigationPolicy() }
    val pendingHistoryReset = remember { mutableIntStateOf(0) }
    val appliedHistoryReset = remember { mutableIntStateOf(0) }
    val appliedNavigationGeneration = remember { mutableIntStateOf(-1) }
    var rendererGeneration by remember { mutableIntStateOf(0) }

    fun observeUrl(view: WebView?, url: String?, explicitNavigation: Boolean = false) {
        if (url?.startsWith("https://") != true) return
        val videoId = YoutubeUrls.videoId(url)
        if (currentSettings.playerMode == PlayerMode.NATIVE && videoId != null) {
            val foreground = currentIsActive && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!nativeNavigation.shouldOpen(videoId, YoutubeUrls.videoId(view?.url.orEmpty()), explicitNavigation, foreground)) return
            currentOnNativeVideo(url)

            if (!explicitNavigation && YoutubeUrls.videoId(view?.url.orEmpty()) == videoId) {
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
                    const adActive = !!(player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting')));
                    if (adActive) {
                        if (video) {
                            if (!window.__tdAdActive) {
                                window.__tdPreviousMuted = !!video.muted;
                                window.__tdPreviousRate = Number(video.playbackRate || 1.0);
                            }
                            window.__tdAdActive = true;
                            video.muted = true;
                            video.playbackRate = 16.0;
                            if (video.duration > 0.1) video.currentTime = video.duration - 0.01;
                        }
                        const skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-skip-ad-button');
                        if (skipBtn) skipBtn.click();
                    } else if (window.__tdAdActive) {
                        if (video) {
                            video.muted = !!window.__tdPreviousMuted;
                            video.playbackRate = Number(window.__tdPreviousRate || 1.0);
                        }
                        window.__tdAdActive = false;
                        delete window.__tdPreviousMuted;
                        delete window.__tdPreviousRate;
                    }
                """ else ""}
            }
            ${if (settings.adBlockEnabled && settings.blockVideoAds) "window.__tdInterval = setInterval(runAdBlockEngine, 750);" else "window.__tdInterval = null;"}
        })();
        """.trimIndent()
    }
    val currentScript by rememberUpdatedState(tubeDeckInjectedScript)
    // Prefer YouTube's own player-size control to preserve SPA layout state.
    val desktopViewScript = remember(settings.desktopYoutubeUiEnabled, desktopTheaterEnabled) {
        if (!settings.desktopYoutubeUiEnabled) "" else """
            (function(){
                var watch = document.querySelector('ytd-watch-flexy');
                if (!watch) return 'not-watch';
                var theater = ${desktopTheaterEnabled};
                if (watch.hasAttribute('theater') !== theater) {
                    var toggle = document.querySelector('button.ytp-size-button');
                    if (toggle) toggle.click();
                    else if (theater) watch.setAttribute('theater', '');
                    else watch.removeAttribute('theater');
                }
                return theater ? 'theater' : 'standard';
            })();
        """.trimIndent()
    }
    val currentDesktopViewScript by rememberUpdatedState(desktopViewScript)

    // Persistent WebView instance. A Chromium renderer crash makes its WebView permanently
    // unusable; rendererGeneration creates a fresh instance while retaining MyTube's logical URL.
    val desktopSiteEnabled = settings.desktopYoutubeUiEnabled
    val persistentWebView = remember(rendererGeneration, desktopSiteEnabled) {
        val generationAtCreation = rendererGeneration
        WebView(context).apply {
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) nativeNavigation.onUserGesture()
                false
            }
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode in setOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER)) nativeNavigation.onUserGesture()
                false
            }
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
            if (desktopSiteEnabled) {
                this.settings.userAgentString =
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                this.settings.setSupportZoom(true)
                this.settings.builtInZoomControls = true
                this.settings.displayZoomControls = false
            }
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
                    if (request?.isForMainFrame == true && currentSettings.playerMode == PlayerMode.NATIVE) {
                        if (YoutubeUrls.videoId(url) != null) {
                            if (request.hasGesture()) nativeNavigation.onUserGesture()
                            observeUrl(view, url, explicitNavigation = true)
                            return true
                        }
                        if (YoutubeUrls.playlistId(url) != null) {
                            // Playlist-only results are opened natively instead of loading a
                            // playlist page that can bounce through YouTube SPA redirects.
                            currentOnNativePlaylist(url)
                            return true
                        }
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
                    if (
                        pendingHistoryReset.intValue > appliedHistoryReset.intValue &&
                        url == latestUrl
                    ) {
                        // A search started from Home/Mixes is a fresh browser navigation session.
                        // Make that results page the WebView root only after it commits, so Back
                        // returns to MyTube Home rather than walking through stale older searches.
                        view?.clearHistory()
                        appliedHistoryReset.intValue = pendingHistoryReset.intValue
                        pendingHistoryReset.intValue = 0
                    }
                    if (url != null && YoutubeUrls.isYoutubePage(url)) {
                        view?.evaluateJavascript(currentScript, null)
                        if (currentDesktopViewScript.isNotEmpty()) view?.evaluateJavascript(currentDesktopViewScript, null)
                    }
                    onCanGoBackChanged(view?.canGoBack() == true)
                    onCanGoForwardChanged(view?.canGoForward() == true)
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    isLoading = true
                    currentOnPlaybackStateChanged(false, 0L)
                    view?.post {
                        if (rendererGeneration == generationAtCreation) {
                            runCatching {
                                (view.parent as? ViewGroup)?.removeView(view)
                                view.destroy()
                            }
                            appliedNavigationGeneration.intValue = -1
                            rendererGeneration += 1
                        }
                    }
                    return true
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

    // Keep this WebView/session alive while other MyTube screens are shown. Pausing the view
    // preserves DOM, scroll position, SPA history and search results without letting hidden media
    // continue playing underneath another screen.
    LaunchedEffect(isActive) {
        if (isActive) {
            persistentWebView.onResume()
        } else {
            persistentWebView.evaluateJavascript(
                "(function(){var v=document.querySelector('video');if(v){v.pause();}return true;})()",
                null
            )
            persistentWebView.onPause()
            currentOnPlaybackStateChanged(false, 0L)
        }
    }

    LaunchedEffect(tubeDeckInjectedScript) {
        if (persistentWebView.url?.let { YoutubeUrls.isYoutubePage(it) } == true) {
            persistentWebView.evaluateJavascript(tubeDeckInjectedScript, null)
        }
    }
    LaunchedEffect(persistentWebView, desktopViewScript) {
        if (desktopViewScript.isNotEmpty() &&
            persistentWebView.url?.let { YoutubeUrls.isYoutubePage(it) } == true
        ) {
            persistentWebView.evaluateJavascript(desktopViewScript, null)
        }
    }

    LaunchedEffect(persistentWebView, lifecycleOwner) {
        while (true) {
            delay(2500)
            if (
                !currentIsActive ||
                !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            ) {
                continue
            }
            persistentWebView.evaluateJavascript("location.href") { result ->
                val url = runCatching { JSONArray("[$result]").optString(0) }.getOrNull()
                if (url?.startsWith("https://") == true && url != latestUrl) observeUrl(persistentWebView, url)
            }
            if (YoutubeUrls.videoId(persistentWebView.url.orEmpty()) != null) {
                if (currentDesktopViewScript.isNotEmpty()) persistentWebView.evaluateJavascript(currentDesktopViewScript, null)
                persistentWebView.evaluateJavascript(
                    """(function(){
                        var v=document.querySelector('video');
                        if(!v) return '0|0';
                        var playing=(!v.paused && !v.ended && v.readyState>1) ? '1' : '0';
                        return playing+'|'+Math.max(0,Math.floor((v.currentTime||0)*1000));
                    })();""".trimIndent()
                ) { result ->
                    val decoded = runCatching { JSONArray("[$result]").optString(0) }.getOrNull().orEmpty()
                    val parts = decoded.split('|', limit = 2)
                    val playing = parts.firstOrNull() == "1"
                    val positionMs = parts.getOrNull(1)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                    currentOnPlaybackStateChanged(playing, positionMs)
                }
            } else {
                currentOnPlaybackStateChanged(false, 0L)
            }
        }
    }

    LaunchedEffect(historyResetGeneration) {
        if (historyResetGeneration > appliedHistoryReset.intValue) {
            pendingHistoryReset.intValue = historyResetGeneration
            if (persistentWebView.url == currentUrl && currentUrl.startsWith("https://")) {
                persistentWebView.clearHistory()
                appliedHistoryReset.intValue = historyResetGeneration
                pendingHistoryReset.intValue = 0
                onCanGoBackChanged(false)
                onCanGoForwardChanged(false)
            }
        }
    }

    LaunchedEffect(navigationGeneration, persistentWebView) {
        // Only an explicit MyTube navigation command may call loadUrl(). URL observations from
        // WebView/YouTube SPA history update currentUrl in MainActivity but never feed back into
        // another network load.
        if (
            BrowserNavigationPolicy.shouldLoad(
                currentWebUrl = persistentWebView.url,
                targetUrl = currentUrl,
                navigationGeneration = navigationGeneration,
                appliedNavigationGeneration = appliedNavigationGeneration.intValue
            )
        ) {
            persistentWebView.loadUrl(currentUrl)
        }
        appliedNavigationGeneration.intValue = navigationGeneration
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


    }
}

