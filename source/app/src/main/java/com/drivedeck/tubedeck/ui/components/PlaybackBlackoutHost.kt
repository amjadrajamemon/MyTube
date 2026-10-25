package com.drivedeck.tubedeck.ui.components

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivedeck.tubedeck.data.repository.TubeDeckRepository

/** The mask belongs to the app window, never to a video ID or a player instance. */
@Composable
fun PlaybackBlackoutHost(repository: TubeDeckRepository, content: @Composable () -> Unit) {
    val blank by repository.screenBlackoutEnabled.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity, blank) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val barsWereVisible = window?.decorView?.let { ViewCompat.getRootWindowInsets(it) }
            ?.let { it.isVisible(WindowInsetsCompat.Type.statusBars()) || it.isVisible(WindowInsetsCompat.Type.navigationBars()) }
            ?: true
        val previousBehavior = controller?.systemBarsBehavior
        if (blank) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (blank) {
                if (barsWereVisible) controller?.show(WindowInsetsCompat.Type.systemBars())
                if (previousBehavior != null) controller?.systemBarsBehavior = previousBehavior
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        content()
        if (blank) {
            Box(
                Modifier.fillMaxSize().zIndex(Float.MAX_VALUE).background(Color.Black)
                    .semantics { contentDescription = "Screen blank. Double-tap to restore video" }
                    .pointerInput(repository) {
                        detectTapGestures(
                            onTap = { /* A single tap must not reveal or control the player. */ },
                            onLongPress = { /* Keep the mask and consume the gesture. */ },
                            onDoubleTap = { repository.restoreScreenAfterDoubleTap() }
                        )
                    }
            )
        }
    }
}
