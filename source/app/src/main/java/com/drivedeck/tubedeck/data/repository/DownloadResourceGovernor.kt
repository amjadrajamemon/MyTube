package com.drivedeck.tubedeck.data.repository

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.PowerManager

/**
 * Invisible admission control for heavyweight download jobs.
 *
 * The queue itself is intentionally unlimited. This object only decides how many extractor /
 * transfer / mux jobs should be active at once on the current device. Already-running jobs are
 * never cancelled when conditions worsen; new work simply waits in QUEUED state until a slot is
 * naturally available.
 */
internal object DownloadResourceGovernor {
    fun targetConcurrency(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val severeThermal = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE

        return computeTarget(
            memoryClassMb = activityManager.memoryClass,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            lowMemory = memoryInfo.lowMemory,
            powerSave = powerManager.isPowerSaveMode,
            severeThermal = severeThermal,
            meteredNetwork = connectivityManager.isActiveNetworkMetered
        )
    }

    internal fun computeTarget(
        memoryClassMb: Int,
        cpuCores: Int,
        lowMemory: Boolean,
        powerSave: Boolean,
        severeThermal: Boolean,
        meteredNetwork: Boolean
    ): Int {
        // yt-dlp extraction + FFmpeg muxing are heavyweight mobile workloads. Keep the visible
        // queue unlimited, but never turn a phone into a 16-process transcode host.
        var target = when {
            memoryClassMb <= 256 -> 1
            memoryClassMb <= 512 -> 2
            memoryClassMb <= 1_024 -> 3
            else -> 4
        }

        target = minOf(target, cpuCores.coerceIn(1, 4))
        if (meteredNetwork) target = minOf(target, 2)
        if (powerSave) target = minOf(target, 1)
        if (lowMemory || severeThermal) target = 1
        return target.coerceIn(1, 4)
    }
}
