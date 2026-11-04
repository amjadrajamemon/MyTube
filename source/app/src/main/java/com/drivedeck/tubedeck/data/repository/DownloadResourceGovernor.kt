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
        // Some inexpensive Android TV ROMs omit services or throw when querying
        // broken vendor power/thermal implementations. Falling back to one
        // worker is safer than crashing the entire download scheduler.
        val activityManager = runCatching {
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        }.getOrNull()
        val powerManager = runCatching {
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        }.getOrNull()
        val connectivityManager = runCatching {
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        }.getOrNull()
        val memoryInfo = runCatching {
            activityManager?.let { manager ->
                ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
            }
        }.getOrNull()
        val memoryClassMb = runCatching { activityManager?.memoryClass }.getOrNull() ?: 160
        val powerSave = runCatching { powerManager?.isPowerSaveMode }.getOrNull() ?: true
        val severeThermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                (powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_SEVERE) >=
                    PowerManager.THERMAL_STATUS_SEVERE
            }.getOrDefault(true)
        } else false
        val metered = runCatching {
            connectivityManager?.isActiveNetworkMetered
        }.getOrNull() ?: true

        return computeTarget(
            memoryClassMb = memoryClassMb,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            lowMemory = memoryInfo?.lowMemory ?: true,
            powerSave = powerSave,
            severeThermal = severeThermal,
            meteredNetwork = metered
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
