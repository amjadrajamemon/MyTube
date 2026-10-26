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
            // Capture foreground/window ownership at the actual failure, not after
            // the emulator has shut down. Report it even when screenshot checks fail.
            val details = runCatching { failureDiagnostics() }.getOrElse { it.toString() }
            android.util.Log.e("MyTubeBlackout", "Blackout check failed:\\n$details", error)
            finish(Activity.RESULT_CANCELED, Bundle().apply {
                putString("shortMsg", error.stackTraceToString() + "\\nWindow diagnostics:\\n" + details)
            })
        }
    }

    private fun checkpoint(stage: String) {
        android.util.Log.i("MyTubeBlackout", "CHECKPOINT phase=$phase stage=$stage")
        sendStatus(0, Bundle().apply { putString("stream", "\nBLACKOUT_CHECKPOINT: $phase — $stage\n") })
    }

    private fun verifyTransitions(fixture: File) {
        check(!repository.screenBlackoutEnabled.value) { "Expected a clear display before the eye button test" }
        checkpoint("launch native player")
        val activity = launchNative(fixture, "Blackout first video")
        await("native player ready") { onMain { player(activity)?.duration?.let { it > 1_000L } == true } }
        val eye = awaitNode("Black screen, continue audio")
        checkpoint("activate blackout eye control")
        tap(eye.centerX(), eye.centerY())
        awaitMask()
        assertBlackPixels()
        checkpoint("opaque blackout screenshot confirmed")
        // Let Android's double-tap recognition window expire after pressing the
        // blackout eye. Otherwise the first touch on the new mask can be paired
        // with the eye-button tap and misclassified as a user double-tap.
        SystemClock.sleep(700L)
        val center = screenCenter()
        tap(center.first, center.second)
        SystemClock.sleep(600L)
        check(repository.screenBlackoutEnabled.value) {
            "Single tap disabled blackout unexpectedly; investigate overlay double-tap gesture leakage"
        }
        awaitMask()
        check(onMain { player(activity)?.playWhenReady } == true) { "Single tap affected playback" }
        checkpoint("single tap consumed; test long press")
        press(center.first, center.second, 900L)
        awaitMask()

        checkpoint("long press consumed; launch manually selected video")
        // The same callback used when a user selects another video must retain the mask.
        val replacement = OfflineMedia("blackout-manual", "Blackout manually selected video", "", "", Uri.fromFile(fixture).toString())
        onMain { invoke(activity, "launchRelatedPlayer", arrayOf(OfflineMedia::class.java), arrayOf(replacement)) }
        await("manually selected video") { onMain { currentMedia(activity).id == "blackout-manual" } }
        awaitMask()
        assertBlackPixels()

        checkpoint("manual video replacement retained blackout")
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
        checkpoint("waiting for actual Media3 automatic-next transition")
        await("automatic next video", 20_000L) { onMain { currentMedia(activity).videoId == "jNQXAC9IVRw" } }
        awaitMask()
        assertBlackPixels()
        check(repository.screenBlackoutEnabled.value)
        checkpoint("native Activity finishing before Web host")
        onMain { activity.finish() }
        await("native Activity closing", 10_000L) { activity.isFinishing || activity.isDestroyed }
        SystemClock.sleep(250L)

        checkpoint("automatic-next kept blackout; launch Web host")
        // Switching presentation hosts cannot clear a preference owned by the app window.
        onMain { repository.updateSettings(repository.settings.value.copy(playerMode = PlayerMode.WEB)) }
        val web = startActivitySync(Intent(targetContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("https://www.youtube.com/watch?v=aqz-KE-bpKQ")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        awaitMask()
        assertBlackPixels()
        checkpoint("Web host retained blackout")
        onMain {
            repository.updateSettings(repository.settings.value.copy(playerMode = PlayerMode.NATIVE))
            web.finish()
        }
        await("Web Activity closing", 10_000L) { web.isFinishing || web.isDestroyed }
        SystemClock.sleep(250L)
        check(repository.screenBlackoutEnabled.value) { "Activity replacement cleared blackout" }
        // Leave the preference set; the next instrumentation invocation starts a fresh process.
        sendStatus(0, Bundle().apply { putString("stream", "PASS: eye button; single/long tap ignored; manual selection; actual automatic next; Web host; activity replacement; opaque black pixels.\n") })
    }

    private fun verifyProcessRestart(fixture: File) {
        check(repository.screenBlackoutEnabled.value) { "Blackout was lost after process restart" }
        checkpoint("verify blackout restored after force-stop")
        val activity = launchNative(fixture, "Blackout restored after process restart")
        awaitMask()
        assertBlackPixels()
        await("restored player ready") { onMain { player(activity)?.duration?.let { it > 1_000L } == true } }
        onMain { player(activity)?.seekTo(0L); player(activity)?.play() }
        val center = screenCenter()
        tap(center.first, center.second)
        SystemClock.sleep(600L)
        awaitMask()
        checkpoint("single tap after restart consumed; check double tap")
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

    private fun awaitMask() {
        // Immersive bar transitions occasionally make the Compose accessibility
        // window temporarily unavailable on the API 34 emulator. Validate the
        // actual screen pixels as well as the persistent blackout state instead.
        await("blackout preference") { repository.screenBlackoutEnabled.value }
        // StateFlow updates before the Android compositor presents the separate
        // blackout window. Wait for actual opaque pixels rather than falsely failing
        // on a stale SurfaceView frame during the dialog-window transition.
        val deadline = SystemClock.uptimeMillis() + 10_000L
        while (SystemClock.uptimeMillis() < deadline) {
            if (runCatching { assertBlackPixels() }.isSuccess) return
            SystemClock.sleep(150L)
        }
        // Preserve the original strong assertion and precise failing ARGB report.
        assertBlackPixels()
    }

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
        try {
            for (x in listOf(bitmap.width / 4, bitmap.width / 2, 3 * bitmap.width / 4)) {
                for (y in listOf(bitmap.height / 4, bitmap.height / 2, 3 * bitmap.height / 4)) {
                    val pixel = bitmap.getPixel(x, y)
                    check(Color.red(pixel) < 8 && Color.green(pixel) < 8 && Color.blue(pixel) < 8) {
                        "Non-black frame at $x,$y: ARGB=0x${Integer.toHexString(pixel)}; " +
                            "blackoutEnabled=${repository.screenBlackoutEnabled.value}; " +
                            "maskAccessibilityVisible=${node(maskDescription) != null}"
                    }
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun failureDiagnostics(): String {
        val visiblePackage = uiAutomation.rootInActiveWindow?.packageName ?: "<none>"
        val commands = listOf(
            "dumpsys activity activities",
            "dumpsys window windows"
        )
        val pattern = Regex(
            "ResumedActivity|topResumedActivity|mResumedActivity|mCurrentFocus|" +
                "mFocusedApp|NativePlayerActivity|MainActivity|Playback Blackout|mHasSurface|mAttrs="
        )
        val result = commands.joinToString("\\n") { command ->
            val lines = uiAutomation.executeShellCommand(command).use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().use { reader ->
                    reader.lineSequence().filter { pattern.containsMatchIn(it) }
                        .take(40).joinToString("\\n")
                }
            }
            "$command:\\n$lines"
        }
        return "activeAccessibilityPackage=$visiblePackage; blackoutEnabled=" +
            "${repository.screenBlackoutEnabled.value}\\n$result"
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
