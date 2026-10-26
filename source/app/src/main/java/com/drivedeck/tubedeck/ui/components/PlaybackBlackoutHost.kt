package com.drivedeck.tubedeck.ui.components

import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivedeck.tubedeck.data.repository.TubeDeckRepository

/**
 * Blackout is attached to the Activity, not to a particular video or player instance.
 * Use a separate, opaque Android dialog window to stay above Media3 SurfaceViews and
 * composited video surfaces, including when native playback is replaced or re-created.
 * A normal Compose sibling overlay can appear underneath some OEM video surfaces.
 */
@Composable
fun PlaybackBlackoutHost(repository: TubeDeckRepository, content: @Composable () -> Unit) {
    val blank by repository.screenBlackoutEnabled.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    DisposableEffect(activity, blank) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val barsWereVisible = window?.decorView?.let { ViewCompat.getRootWindowInsets(it) }
            ?.let {
                it.isVisible(WindowInsetsCompat.Type.statusBars()) ||
                    it.isVisible(WindowInsetsCompat.Type.navigationBars())
            } ?: true
        val previousBehavior = controller?.systemBarsBehavior
        if (blank) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (blank) {
                if (barsWereVisible) controller?.show(WindowInsetsCompat.Type.systemBars())
                if (previousBehavior != null) controller?.systemBarsBehavior = previousBehavior
            }
        }
    }

    // Do not tear down or pause the underlying player: the audio remains audible.
    Box(Modifier.fillMaxSize()) {
        content()
    }

    if (blank) {
        Dialog(
            onDismissRequest = { /* Only a deliberate double tap may clear blackout. */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            val view = LocalView.current
            DisposableEffect(view) {
                val dialogWindow = (view.parent as? DialogWindowProvider)?.window
                dialogWindow?.apply {
                    // A separate, fully opaque window covers SurfaceView compositor layers.
                    setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
                    clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT
                    )
                    WindowCompat.getInsetsController(this, decorView).apply {
                        systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        hide(WindowInsetsCompat.Type.systemBars())
                    }
                }
                onDispose { /* Compose disposes the dialog window automatically. */ }
            }
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black)
                    .semantics {
                        contentDescription = "Screen blank. Double-tap to restore video"
                    }
                    .pointerInput(repository) {
                        detectTapGestures(
                            onTap = { /* Ignore one tap; keep audio playing. */ },
                            onLongPress = { /* Ignore hold. */ },
                            onDoubleTap = { repository.restoreScreenAfterDoubleTap() }
                        )
                    }
            )
        }
    }
}
