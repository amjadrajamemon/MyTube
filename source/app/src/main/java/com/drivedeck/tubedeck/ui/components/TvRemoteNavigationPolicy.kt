package com.drivedeck.tubedeck.ui.components

import android.view.KeyEvent

/**
 * Single policy for TV remotes, USB keyboards and air-mouse D-pads.
 *
 * Pointer clicks and text entry remain owned by Android/Compose. Do not eat a
 * directional key while a player control, popup, or text field has focus.
 */
internal enum class TvPlayerKeyAction {
    IGNORE,
    REVEAL_CENTER_CONTROLS,
    REVEAL_TOP_CONTROLS,
    HIDE_CONTROLS,
    TOGGLE_CONTROLS,
    PLAY_PAUSE,
    PLAY,
    PAUSE,
    STOP,
    NEXT,
    PREVIOUS,
    SEEK_BACKWARD,
    SEEK_FORWARD,
    SEEK_PERCENT
}

internal data class TvPlayerKeyDecision(
    val action: TvPlayerKeyAction,
    val multiplier: Int = 1,
    val percent: Int = 0
)

internal object TvRemoteNavigationPolicy {
    fun decide(
        keyCode: Int,
        repeatCount: Int,
        surfaceFocused: Boolean,
        controlsVisible: Boolean,
        modalVisible: Boolean
    ): TvPlayerKeyDecision {
        val ignore = TvPlayerKeyDecision(TvPlayerKeyAction.IGNORE)
        if (modalVisible) return ignore

        // A held media key is still one click, while repeated arrow/seeking
        // keys accelerate in predictable steps.
        val seekMultiplier = (1 + repeatCount.coerceAtLeast(0) / 4).coerceAtMost(5)
        fun seek(action: TvPlayerKeyAction) =
            TvPlayerKeyDecision(action, multiplier = seekMultiplier)

        return when (keyCode) {
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_BUTTON_START -> if (repeatCount == 0) {
                TvPlayerKeyDecision(TvPlayerKeyAction.TOGGLE_CONTROLS)
            } else ignore

            KeyEvent.KEYCODE_ESCAPE -> if (repeatCount == 0) {
                TvPlayerKeyDecision(if (controlsVisible) TvPlayerKeyAction.HIDE_CONTROLS else TvPlayerKeyAction.STOP)
            } else ignore

            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_PAGE_UP -> if (repeatCount == 0) {
                TvPlayerKeyDecision(TvPlayerKeyAction.NEXT)
            } else ignore

            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN -> if (repeatCount == 0) {
                TvPlayerKeyDecision(TvPlayerKeyAction.PREVIOUS)
            } else ignore

            KeyEvent.KEYCODE_MEDIA_STOP -> if (repeatCount == 0) {
                TvPlayerKeyDecision(TvPlayerKeyAction.STOP)
            } else ignore

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_K -> if (repeatCount == 0) {
                TvPlayerKeyDecision(TvPlayerKeyAction.PLAY_PAUSE)
            } else ignore

            KeyEvent.KEYCODE_MEDIA_PLAY -> if (repeatCount == 0) {
                TvPlayerKeyDecision(TvPlayerKeyAction.PLAY)
            } else ignore

            KeyEvent.KEYCODE_MEDIA_PAUSE -> if (repeatCount == 0) {
                TvPlayerKeyDecision(TvPlayerKeyAction.PAUSE)
            } else ignore

            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_L -> seek(TvPlayerKeyAction.SEEK_FORWARD)

            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_J -> seek(TvPlayerKeyAction.SEEK_BACKWARD)

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A -> if (surfaceFocused && repeatCount == 0) {
                TvPlayerKeyDecision(TvPlayerKeyAction.REVEAL_CENTER_CONTROLS)
            } else ignore

            KeyEvent.KEYCODE_DPAD_UP -> if (surfaceFocused && repeatCount == 0) {
                TvPlayerKeyDecision(TvPlayerKeyAction.REVEAL_TOP_CONTROLS)
            } else ignore

            KeyEvent.KEYCODE_DPAD_DOWN -> if (surfaceFocused && repeatCount == 0) {
                TvPlayerKeyDecision(TvPlayerKeyAction.REVEAL_CENTER_CONTROLS)
            } else ignore

            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_LEFT -> if (!surfaceFocused) {
                ignore // Move focus between visible controls; never seek behind them.
            } else if (controlsVisible) {
                TvPlayerKeyDecision(TvPlayerKeyAction.REVEAL_CENTER_CONTROLS)
            } else seek(if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                TvPlayerKeyAction.SEEK_FORWARD
            } else TvPlayerKeyAction.SEEK_BACKWARD)

            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> if (surfaceFocused && !controlsVisible &&
                repeatCount == 0
            ) {
                TvPlayerKeyDecision(
                    TvPlayerKeyAction.SEEK_PERCENT,
                    percent = (keyCode - KeyEvent.KEYCODE_0) * 10
                )
            } else ignore

            else -> ignore
        }
    }
}
