package com.drivedeck.tubedeck.ui.theme

/**
 * Adapt the whole design system from real device hints and measured dp width.
 * Do not use the raw pixel resolution to guess TV/phone behavior. Users can
 * disable automatic adaptation to retain their explicit theme selection.
 */
object DeckDeviceModePolicy {
    fun theme(
        selected: String,
        adaptive: Boolean,
        carDashboard: Boolean,
        carDetected: Boolean,
        tvDetected: Boolean,
        viewportWidthDp: Int
    ): String = when {
        (carDashboard || adaptive) && carDetected -> "CommandDrive Auto"
        adaptive && tvDetected -> "StreamPlus Luxe TV"
        adaptive && viewportWidthDp >= 780 -> "NeoGlass Luxe"
        adaptive -> "Midnight Editorial Pro"
        else -> selected
    }
}
