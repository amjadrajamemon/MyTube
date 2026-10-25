@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.drivedeck.tubedeck

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.InputDevice
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.drivedeck.tubedeck.data.model.OfflineMedia
import com.drivedeck.tubedeck.data.model.PlaybackContinuationMode
import com.drivedeck.tubedeck.data.model.PlayerMode
import com.drivedeck.tubedeck.data.model.PlaylistItem
import com.drivedeck.tubedeck.data.repository.TubeDeckRepository
import java.io.File

/** Real target-APK tests, excluded from production. No test entry points are added to the app. */
class BlackoutInstrumentation : Instrumentation() {
    private var phase = "transitions"
    private val maskDescription = "Screen blank. Double-tap to restore video"
    private val repository get() = TubeDeckRepository.get(targetContext)

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        phase = arguments?.getString("phase") ?: phase
        start()
    }

    override fun onStart() {
        try {
            val fixture = File(targetContext.filesDir, "blackout-fixture.mp4")
            context.assets.open("blackout-fixture.mp4").use { input -> fixture.outputStream().use { output -> input.copyTo(output) } }
            if (phase == "restart") verifyProcessRestart(fixture) else verifyTransitions(fixture)
            val report = Bundle().apply { putString("stream", "\nBLACKOUT_TESTS_PASSED: $phase\n") }
            finish(Activity.RESULT_OK, report)
        } catch (error: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("shortMsg", error.stackTraceToString()) })
        }
    }

    private fun verifyTransitions(fixture: File) {
        check(!repository.screenBlackoutEnabled.value) { "Expected a clear display before the eye button test" }
        val activity = launchNative(fixture, "Blackout first video")
        await("native player ready") { onMain { player(activity)?.duration?.let { it > 1_000L } == true } }
        val eye = awaitNode("Black screen, continue audio")
        tap(eye.centerX(), eye.centerY())
        awaitMask()
        assertBlackPixels()
        val center = screenCenter()
        tap(center.first, center.second)
        SystemClock.sleep(600L)
        awaitMask()
        check(onMain { player(activity)?.playWhenReady } == true) { "Single tap affected playback" }
        press(center.first, center.second, 900L)
        awaitMask()

        // The same callback used when a user selects another video must retain the mask.
        val replacement = OfflineMedia("blackout-manual", "Blackout manually selected video", "", "", Uri.fromFile(fixture).toString())
        onMain { invoke(activity, "launchRelatedPlayer", arrayOf(OfflineMedia::class.java), arrayOf(replacement)) }
        await("manually selected video") { onMain { currentMedia(activity).id == "blackout-manual" } }
        awaitMask()
        assertBlackPixels()

        // Configure a real playlist continuation, then let Media3 emit STATE_ENDED.
        onMain {
            repository.updateSettings(repository.settings.value.copy(playbackContinuationMode = PlaybackContinuationMode.AUTO_NEXT))
            invoke(activity, "setActivePlaylistId", arrayOf(String::class.java), arrayOf("PL_BLACKOUT_FIXTURE"))
            invoke(activity, "setActivePlaylistItems", arrayOf(List::class.java), arrayOf(listOf(
                PlaylistItem("aqz-KE-bpKQ", "First playlist video"),
                PlaylistItem("jNQXAC9IVRw", "Automatically selected next video")
            )))
            invoke(activity, "setActivePlaylistIndex", arrayOf(Int::class.javaPrimitiveType!!), arrayOf(0))
        }
        SystemClock.sleep(500L)
        await("replacement player ready") { onMain { player(activity)?.duration?.let { it > 1_000L } == true } }
        onMain {
            val p = checkNotNull(player(activity))
            p.seekTo((p.duration - 250L).coerceAtLeast(0L))
            p.play()
        }
        await("automatic next video", 20_000L) { onMain { currentMedia(activity).videoId == "jNQXAC9IVRw" } }
        awaitMask()
        assertBlackPixels()
        check(repository.screenBlackoutEnabled.value)
        onMain { activity.finish() }
        waitForIdleSync()

        // Switching presentation hosts cannot clear a preference owned by the app window.
        onMain { repository.updateSettings(repository.settings.value.copy(playerMode = PlayerMode.WEB)) }
        val web = startActivitySync(Intent(targetContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("https://www.youtube.com/watch?v=aqz-KE-bpKQ")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        awaitMask()
        assertBlackPixels()
        onMain {
            repository.updateSettings(repository.settings.value.copy(playerMode = PlayerMode.NATIVE))
            web.finish()
        }
        waitForIdleSync()
        check(repository.screenBlackoutEnabled.value) { "Activity replacement cleared blackout" }
        // Leave the preference set; the next instrumentation invocation starts a fresh process.
        sendStatus(0, Bundle().apply { putString("stream", "PASS: eye button; single/long tap ignored; manual selection; actual automatic next; Web host; activity replacement; opaque black pixels.\n") })
    }

    private fun verifyProcessRestart(fixture: File) {
        check(repository.screenBlackoutEnabled.value) { "Blackout was lost after process restart" }
        val activity = launchNative(fixture, "Blackout restored after process restart")
        awaitMask()
        assertBlackPixels()
        await("restored player ready") { onMain { player(activity)?.duration?.let { it > 1_000L } == true } }
        onMain { player(activity)?.seekTo(0L); player(activity)?.play() }
        val center = screenCenter()
        tap(center.first, center.second)
        SystemClock.sleep(600L)
        awaitMask()
        // Send two physical taps inside Android's double-tap window. The mask consumes both.
        tap(center.first, center.second)
        SystemClock.sleep(80L)
        tap(center.first, center.second)
        await("manual double-tap restore") { !repository.screenBlackoutEnabled.value && node(maskDescription) == null }
        check(onMain { player(activity)?.playWhenReady } == true) { "Unlock double-tap leaked into play/pause" }
        onMain { activity.finish() }
        sendStatus(0, Bundle().apply { putString("stream", "PASS: process restart retains blackout; single tap ignored; double-tap restores without pausing playback.\n") })
    }

    private fun launchNative(fixture: File, title: String): NativePlayerActivity {
        val session = onMain {
            repository.updateSettings(repository.settings.value.copy(autoRotatePlayback = false))
            val registry = Class.forName("com.drivedeck.tubedeck.player.PlaybackSessionRegistry")
            registry.getMethod("open").invoke(registry.getField("INSTANCE").get(null)) as String
        }
        val activity = startActivitySync(Intent(targetContext, NativePlayerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(NativePlayerActivity.EXTRA_SESSION_ID, session)
            putExtra(NativePlayerActivity.EXTRA_MEDIA_ID, title)
            putExtra(NativePlayerActivity.EXTRA_TITLE, title)
            putExtra(NativePlayerActivity.EXTRA_MEDIA_URI, Uri.fromFile(fixture).toString())
        }) as NativePlayerActivity
        onMain { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        return activity
    }

    private fun currentMedia(activity: Activity) = invoke(activity, "getCurrentMedia", emptyArray(), emptyArray()) as OfflineMedia

    private fun invoke(activity: Activity, name: String, types: Array<Class<*>>, values: Array<Any?>): Any? =
        activity.javaClass.getDeclaredMethod(name, *types).apply { isAccessible = true }.invoke(activity, *values)

    private fun player(activity: Activity): Player? {
        fun find(view: View): Player? {
            if (view is PlayerView) return view.player
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        return find(activity.window.decorView)
    }

    private fun node(description: String): AccessibilityNodeInfo? {
        fun find(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (n.contentDescription?.toString() == description) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { find(it) }?.let { return it }
            return null
        }
        return uiAutomation.rootInActiveWindow?.let(::find)
    }

    private fun awaitNode(description: String): Rect {
        var result: Rect? = null
        await(description) { node(description)?.let { n -> result = Rect().also(n::getBoundsInScreen) }; result != null }
        return checkNotNull(result)
    }

    private fun awaitMask() { await("blackout overlay") { repository.screenBlackoutEnabled.value && node(maskDescription) != null } }

    private fun screenCenter(): Pair<Int, Int> {
        // The overlay has already passed awaitMask() and the opaque-black screenshot
        // assertion. System-bar transitions on Android 14 can briefly clear the active
        // accessibility window; derive touch coordinates from the actual display rather
        // than requiring a second accessibility lookup while that window is changing.
        val screenshot = checkNotNull(uiAutomation.takeScreenshot())
        return try {
            screenshot.width / 2 to screenshot.height / 2
        } finally {
            screenshot.recycle()
        }
    }

    private fun assertBlackPixels() {
        SystemClock.sleep(150L)
        val raw = checkNotNull(uiAutomation.takeScreenshot())
        val bitmap = if (raw.config == Bitmap.Config.HARDWARE) {
            checkNotNull(raw.copy(Bitmap.Config.ARGB_8888, false)).also { raw.recycle() }
        } else raw
        for (x in listOf(bitmap.width / 4, bitmap.width / 2, 3 * bitmap.width / 4)) {
            for (y in listOf(bitmap.height / 4, bitmap.height / 2, 3 * bitmap.height / 4)) {
                val pixel = bitmap.getPixel(x, y)
                check(Color.red(pixel) < 8 && Color.green(pixel) < 8 && Color.blue(pixel) < 8) { "Non-black frame at $x,$y" }
            }
        }
        bitmap.recycle()
    }

    private fun tap(x: Int, y: Int) = press(x, y, 35L)
    private fun press(x: Int, y: Int, holdMs: Long) {
        val down = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        check(uiAutomation.injectInputEvent(event, true)); event.recycle()
        SystemClock.sleep(holdMs)
        val up = MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 0)
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        check(uiAutomation.injectInputEvent(up, true)); up.recycle()
    }

    private fun await(label: String, timeout: Long = 12_000L, test: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < deadline) {
            if (test()) return
            SystemClock.sleep(80L)
        }
        error("Timed out waiting for $label")
    }

    private fun <T> onMain(block: () -> T): T {
        var value: T? = null
        var failure: Throwable? = null
        runOnMainSync { try { value = block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST") return value as T
    }
}
