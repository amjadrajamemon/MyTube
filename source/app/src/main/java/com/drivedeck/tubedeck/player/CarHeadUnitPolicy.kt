package com.drivedeck.tubedeck.player

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * Embedded XY AUTO firmware typically reports tablet rather than automotive UI mode.
 * Limit this work-around to automotive feature flags and identifiable head-unit builds.
 */
internal object CarHeadUnitPolicy {
    private val firmwareMarkers = listOf(
        "xyauto", "xy auto", "xy-auto", "yt9216", "yt9217", "yt9218",
        "yt9260", "yt9270", "yt5760", "8227l", "ac8259"
    )

    fun isLegacyFirmwareTag(value: String): Boolean {
        val id = value.lowercase(Locale.US)
        return firmwareMarkers.any { marker -> marker in id }
    }

    fun isCarHeadUnit(context: Context): Boolean {
        val uiType = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        if (uiType == Configuration.UI_MODE_TYPE_CAR) return true
        if (runCatching {
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
            }.getOrDefault(false)
        ) return true
        return sequenceOf(
            Build.MANUFACTURER, Build.BRAND, Build.DEVICE, Build.PRODUCT,
            Build.MODEL, Build.DISPLAY, Build.FINGERPRINT
        ).filterNotNull().any(::isLegacyFirmwareTag)
    }
}
