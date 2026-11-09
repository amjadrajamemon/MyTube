package com.drivedeck.tubedeck.player

/** One deliberate Back action during ordinary TV video playback.
 * In-player menus/control chrome continue to consume their own first Back.
 */
internal enum class TvBackButtonAction {
    MINI_PIP,
    CLOSE_IMMEDIATELY,
    SHOW_ACTION_MENU
}

internal object TvBackButtonPolicy {
    const val MINI_PIP = "Mini-PiP"
    const val CLOSE_IMMEDIATELY = "Close immediately"
    const val SHOW_ACTION_MENU = "Action menu"

    fun normalize(raw: String?): String = when (raw) {
        MINI_PIP, CLOSE_IMMEDIATELY, SHOW_ACTION_MENU -> raw
        else -> SHOW_ACTION_MENU
    }

    /** Never silently drop playback if PiP is disabled. */
    fun decide(raw: String?, pipEnabled: Boolean): TvBackButtonAction =
        when (normalize(raw)) {
            MINI_PIP -> if (pipEnabled) TvBackButtonAction.MINI_PIP
                else TvBackButtonAction.SHOW_ACTION_MENU
            CLOSE_IMMEDIATELY -> TvBackButtonAction.CLOSE_IMMEDIATELY
            else -> TvBackButtonAction.SHOW_ACTION_MENU
        }
}
