package com.drivedeck.tubedeck.ui.components

/**
 * Compact headers on short landscape windows regardless of whether an
 * aftermarket car tablet reports itself as Automotive, Tablet or TV.
 * Never collapse a tall portrait phone just because it has a physical remote.
 */
internal object SearchCompactPolicy {
    fun useSingleRowHeader(
        widthDp: Int,
        heightDp: Int,
        isLandscape: Boolean,
        carHeadUnit: Boolean
    ): Boolean = (isLandscape && widthDp >= 540 && heightDp <= 700) ||
        (carHeadUnit && widthDp >= 540 && widthDp > heightDp)

    fun overlayMaxHeightDp(heightDp: Int): Int =
        (heightDp.coerceAtLeast(240) / 3).coerceIn(82, 172)

    fun suggestionLimit(compact: Boolean): Int = if (compact) 4 else 8
}
