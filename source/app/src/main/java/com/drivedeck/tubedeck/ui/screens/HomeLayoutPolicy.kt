package com.drivedeck.tubedeck.ui.screens

/**
 * Home feed layout policy shared by all themes and device types.
 *
 * A phone may report an unusually large physical resolution while having a
 * narrow Compose dp viewport. We use the *actual available dp width*, never
 * screen pixels or a "TV-like" input-device guess, to choose a safe layout.
 */
object HomeLayoutPolicy {
    enum class Layout { WIDE_COLUMNS, SWIPE_PANELS, HORIZONTAL_SHELVES }

    fun decide(availableWidthDp: Int, threeColumnTheme: Boolean): Layout = when {
        !threeColumnTheme -> Layout.HORIZONTAL_SHELVES
        availableWidthDp >= 900 -> Layout.WIDE_COLUMNS
        else -> Layout.SWIPE_PANELS
    }
}
