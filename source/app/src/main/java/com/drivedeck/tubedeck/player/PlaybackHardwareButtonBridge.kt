package com.drivedeck.tubedeck.player

import android.view.KeyEvent

/**
 * OEM Android TV and car head units sometimes route a hardware media key to the
 * foreground Activity instead of to Media3's MediaSession. Register the current
 * video player's identical queue actions as a fallback; do not intercept Back,
 * volume or ordinary text input.
 */
object PlaybackHardwareButtonBridge {
    @Volatile private var owner: Any? = null
    @Volatile private var skipNext: (() -> Boolean)? = null
    @Volatile private var skipPrevious: (() -> Boolean)? = null

    @Synchronized
    fun register(ownerToken: Any, onNext: () -> Boolean, onPrevious: () -> Boolean) {
        owner = ownerToken
        skipNext = onNext
        skipPrevious = onPrevious
    }

    @Synchronized
    fun unregister(ownerToken: Any) {
        if (owner === ownerToken) {
            owner = null
            skipNext = null
            skipPrevious = null
        }
    }

    fun handle(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> skipNext?.invoke() == true
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> skipPrevious?.invoke() == true
            else -> false
        }
    }
}
