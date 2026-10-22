package com.drivedeck.tubedeck.player

import android.app.ActivityManager
import android.content.Context

/**
 * Conservative runtime caps for low-memory and low-CPU Android devices.
 *
 * Limits are selected once per player creation; no persistent setting or loss
 * of explicit user-selected video qualities is required.
 */
internal data class PlaybackResourceLimits(
    val constrained: Boolean,
    val maxAutoVideoWidth: Int,
    val maxAutoVideoHeight: Int,
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val targetBufferBytes: Int,
    val allowAdaptiveUpgrade: Boolean,
    val relatedPrefetchCount: Int
)

internal object PlaybackResourcePolicy {
    private const val MIB = 1024 * 1024

    fun detect(context: Context, carCompatibility: Boolean): PlaybackResourceLimits {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        // The heap class is a safer proxy for app memory pressure than total physical RAM.
        val heapMb = manager?.memoryClass ?: 256
        val lowRam = manager?.isLowRamDevice ?: false
        return choose(lowRam, heapMb, Runtime.getRuntime().availableProcessors(), carCompatibility)
    }

    fun choose(
        lowRam: Boolean,
        memoryClassMb: Int,
        cpuCores: Int,
        carCompatibility: Boolean
    ): PlaybackResourceLimits {
        val tinyHeap = lowRam || memoryClassMb <= 160
        val modest = tinyHeap || (memoryClassMb <= 256 && cpuCores <= 4)
        return when {
            tinyHeap -> PlaybackResourceLimits(
                constrained = true, maxAutoVideoWidth = 854, maxAutoVideoHeight = 480,
                minBufferMs = 6_000, maxBufferMs = 20_000,
                targetBufferBytes = 8 * MIB, allowAdaptiveUpgrade = false,
                relatedPrefetchCount = 0
            )
            carCompatibility -> PlaybackResourceLimits(
                constrained = true, maxAutoVideoWidth = 854, maxAutoVideoHeight = 480,
                minBufferMs = 8_000, maxBufferMs = 28_000,
                targetBufferBytes = 12 * MIB, allowAdaptiveUpgrade = false,
                relatedPrefetchCount = 1
            )
            modest -> PlaybackResourceLimits(
                constrained = true, maxAutoVideoWidth = 1280, maxAutoVideoHeight = 720,
                minBufferMs = 10_000, maxBufferMs = 35_000,
                targetBufferBytes = 16 * MIB, allowAdaptiveUpgrade = false,
                relatedPrefetchCount = 1
            )
            else -> PlaybackResourceLimits(
                constrained = false, maxAutoVideoWidth = Int.MAX_VALUE,
                maxAutoVideoHeight = Int.MAX_VALUE, minBufferMs = 18_000,
                maxBufferMs = 90_000, targetBufferBytes = -1,
                allowAdaptiveUpgrade = true, relatedPrefetchCount = 2
            )
        }
    }
}
