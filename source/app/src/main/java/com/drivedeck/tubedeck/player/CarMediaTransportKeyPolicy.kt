package com.drivedeck.tubedeck.player

import android.view.KeyEvent

/**
 * Transport keys are inconsistent across Android car stereos/steering-wheel adapters.
 * Classify keys before they reach Media3's single-item seekToNext (a no-op for us).
 *
 * Do not treat FAST_FORWARD as Next by default: that key legitimately seeks.
 * User-trained key mappings can explicitly override that behavior.
 */
internal enum class CarTransportAction { NEXT, PREVIOUS }

internal object CarMediaTransportKeyPolicy {
    fun classify(
        keyCode: Int,
        customNext: Int = 0,
        customPrevious: Int = 0
    ): CarTransportAction? {
        // Never intercept Android system controls, Back or volume by accident.
        if (keyCode in intArrayOf(
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME,
                KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER
            )
        ) return null
        if (customNext > 0 && keyCode == customNext) return CarTransportAction.NEXT
        if (customPrevious > 0 && keyCode == customPrevious) return CarTransportAction.PREVIOUS
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_PAGE_UP -> CarTransportAction.NEXT
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN -> CarTransportAction.PREVIOUS
            else -> null
        }
    }

    /**
     * Android normally emits DOWN -> UP; several car firmware builds send UP only.
     * Dispatch on the first DOWN, or on an orphan UP. Repeats / paired UPs are
     * consumed without triggering a second track skip.
     */
    fun shouldTrigger(action: Int, repeatCount: Int, sawDown: Boolean): Boolean =
        (action == KeyEvent.ACTION_DOWN && (repeatCount == 0 || !sawDown)) ||
            (action == KeyEvent.ACTION_UP && !sawDown)

    const val DUPLICATE_WINDOW_MS = 145L
}
