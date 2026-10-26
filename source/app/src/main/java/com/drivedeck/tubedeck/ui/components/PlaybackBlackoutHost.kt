package com.drivedeck.tubedeck.ui.components

import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivedeck.tubedeck.data.repository.TubeDeckRepository

/**
 * Blackout follows the application preference, never an ExoPlayer instance or a video ID.
 *
 * A Compose sibling can be under a SurfaceView; a Compose Dialog can take input focus
 * from the player and be lost during Activity transitions on some OEM firmware.
 * An Activity-token application PANEL sits above SurfaceView without taking focus,
 * consumes ALL touch events and keeps the underlying player/audio session intact.
 * It does not use SYSTEM_ALERT_WINDOW or a system-wide overlay permission.
 */
@Composable
fun PlaybackBlackoutHost(repository: TubeDeckRepository, content: @Composable () -> Unit) {
    val blank by repository.screenBlackoutEnabled.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    // Compose owns the player as usual. Never re-create the player when blackout toggles.
    Box(Modifier.fillMaxSize()) {
        content()
    }

    DisposableEffect(activity, blank) {
        if (!blank || activity == null || activity.isFinishing || activity.isDestroyed) {
            onDispose { }
        } else {
            val hostWindow = activity.window
            val hostView = hostWindow.decorView
            val manager = activity.getSystemService(WindowManager::class.java)
            // A second independently drawn layer keeps the Activity fully black
            // while the separate application-panel surface is being recreated.
            // OEM launchers may briefly expose a grey app background when the
            // player/Web host switches orientation even with the panel attached.
            val previousForeground = hostView.foreground
            val protectiveForeground = ColorDrawable(android.graphics.Color.BLACK)
            hostView.foreground = protectiveForeground
            val detector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean = true
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    repository.restoreScreenAfterDoubleTap()
                    return true
                }
                override fun onLongPress(e: MotionEvent) {
                    // The blank screen ignores holds and does not seek/pause the video.
                }
            })
            val mask = FrameLayout(activity).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                contentDescription = "Screen blank. Double-tap to restore video"
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                setOnTouchListener { _, event ->
                    detector.onTouchEvent(event)
                    true // Never leak taps, long presses or swipes to the video.
                }
            }
            var installed = false
            var disposed = false
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.OPAQUE
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                title = "MyTube Playback Blackout"
            }
            // Deferring to the attached decor token prevents BadTokenException after
            // Activity recreation; the mask is removed using the same manager.
            val attach = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    if (disposed || installed) return
                    val token = v.windowToken ?: return
                    params.token = token
                    runCatching {
                        manager.addView(mask, params)
                        installed = true
                    }.onFailure {
                        Log.e("MyTubeBlackout", "Cannot attach opaque blackout panel", it)
                    }
                }
                override fun onViewDetachedFromWindow(v: View) {
                    if (installed) {
                        runCatching { manager.removeViewImmediate(mask) }
                        installed = false
                    }
                }
            }
            hostView.addOnAttachStateChangeListener(attach)
            if (hostView.isAttachedToWindow) {
                attach.onViewAttachedToWindow(hostView)
            }
            // This overlay must not request immersive mode. Android displays its own
            // first-use ImmersiveModeConfirmation window ABOVE application panels.
            // The host player alone owns fullscreen/system-bar policy; requesting
            // it again on blackout made the confirmation steal touch focus.
            onDispose {
                disposed = true
                // Only restore what we installed; never erase another owner’s UI.
                if (hostView.foreground === protectiveForeground) {
                    hostView.foreground = previousForeground
                }
                hostView.removeOnAttachStateChangeListener(attach)
                if (installed) {
                    runCatching { manager.removeViewImmediate(mask) }
                    installed = false
                }
            }
        }
    }
}
