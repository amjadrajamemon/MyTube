package com.drivedeck.tubedeck.player

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.drivedeck.tubedeck.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/** Small, on-device journal. No media URLs, account details, or network upload. */
internal object PlaybackDiagnostics {
    private const val PREFS = "mytube_playback_diagnostics"
    private val installed = AtomicBoolean(false)

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val app = context.applicationContext
        record(app, "process_start", "api=${Build.VERSION.SDK_INT}")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, "java_crash", error.stackTraceToString().take(5000), critical = true) }
            previous?.uncaughtException(thread, error)
        }
    }

    @Synchronized
    fun record(context: Context, event: String, detail: String = "", critical: Boolean = false) {
        val safeDetail = detail.replace(Regex("https?://\\S+"), "[URL]")
        val line = "${SystemClock.elapsedRealtime()} pid=${Process.myPid()} $event $safeDetail"
        Log.i("MyTubePlayback", line)
        runCatching {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val journal = (prefs.getString("journal", "").orEmpty() + line + "\n").takeLast(18000)
            val editor = prefs.edit().putString("journal", journal)
            if (critical) editor.commit() else editor.apply()
        }
    }

    fun report(context: Context): String = buildString {
        appendLine("MyTube playback report ${BuildConfig.VERSION_NAME}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}; board=${Build.BOARD}; hardware=${Build.HARDWARE}")
        appendLine("Android ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT}; ABI ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Build: ${Build.DISPLAY}")
        val prefs = context.getSharedPreferences("tubedeck_prefs", Context.MODE_PRIVATE)
        appendLine("XY AUTO compatibility: ${prefs.getBoolean("xyAutoCompatibilityMode", CarHeadUnitPolicy.isCarHeadUnit(context))}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                context.getSystemService(ActivityManager::class.java)
                    .getHistoricalProcessExitReasons(context.packageName, 0, 3)
                    .forEach { appendLine("Process exit: reason=${it.reason}, status=${it.status}, ${it.description.orEmpty()}") }
            }
        }
        appendLine(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("journal", "No playback recorded yet."))
    }
}
