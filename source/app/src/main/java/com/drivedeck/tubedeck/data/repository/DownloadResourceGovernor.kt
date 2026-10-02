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
        var target = when {
            memoryClassMb <= 256 -> 3
            memoryClassMb <= 512 -> 5
            memoryClassMb <= 1_024 -> 8
            memoryClassMb <= 2_048 -> 12
            else -> 16
        }

        target = minOf(target, (cpuCores.coerceAtLeast(1) * 2).coerceIn(2, 16))
        if (meteredNetwork) target = minOf(target, 6)
        if (powerSave) target = minOf(target, 3)
        if (lowMemory || severeThermal) target = minOf(target, 2)
        return target.coerceIn(2, 16)
    }
}
