package com.drivedeck.tubedeck.ui.screens

/**
 * Width-first layout rules. Do not assume that landscape, pixel count, or the
 * presence of a remote proves a particular usable content width.
 */
internal object HomeResponsiveLayoutPolicy {
    fun gridColumns(availableWidthDp: Int, tvNavigation: Boolean): Int {
        val usable = availableWidthDp.coerceAtLeast(240)
        val minimumCardDp = if (tvNavigation) 235 else 190
        return (usable / minimumCardDp).coerceIn(1, if (tvNavigation) 5 else 4)
    }

    fun initialVideos(setting: Int): Int = setting.coerceIn(10, 50)
}
