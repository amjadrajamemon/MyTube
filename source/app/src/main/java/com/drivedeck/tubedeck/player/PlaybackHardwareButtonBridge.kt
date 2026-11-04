package com.drivedeck.tubedeck.player

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent

/**
 * A common transport-key entry point for Activity dispatch AND MediaSession.
 * Both entry points share debouncing and press/release tracking, so an OEM
 * delivering the same physical key on both routes cannot skip two videos.
 *
 * Do not intercept Back/volume or default seek-forward; only Next/Previous
 * families (and user-trained key codes) are handled.
 */
object PlaybackHardwareButtonBridge {
    @Volatile private var owner: Any? = null
    @Volatile private var skipNext: (() -> Boolean)? = null
    @Volatile private var skipPrevious: (() -> Boolean)? = null
    private var customNextKeyCode = 0
    private var customPreviousKeyCode = 0
    private var diagnosticContext: Context? = null
    private val pressedKeys = mutableSetOf<Int>()
    private var lastNextDispatchTime = -10_000L
    private var lastPreviousDispatchTime = -10_000L

    @Synchronized
    fun register(
        ownerToken: Any,
        onNext: () -> Boolean,
        onPrevious: () -> Boolean,
        nextKeyCode: Int = 0,
        previousKeyCode: Int = 0,
        context: Context? = null
    ) {
        owner = ownerToken
        skipNext = onNext
        skipPrevious = onPrevious
        customNextKeyCode = nextKeyCode
        customPreviousKeyCode = previousKeyCode
        diagnosticContext = context?.applicationContext
        // Retain recent down/up/dispatch history across an immediate video
        // replacement; otherwise one late UP could skip the next video again.
    }

    @Synchronized
    fun unregister(ownerToken: Any) {
        if (owner === ownerToken) {
            owner = null
            skipNext = null
            skipPrevious = null
            customNextKeyCode = 0
            customPreviousKeyCode = 0
            diagnosticContext = null
        }
    }

    @Synchronized
    fun handle(event: KeyEvent): Boolean {
        val transport = CarMediaTransportKeyPolicy.classify(
            event.keyCode, customNextKeyCode, customPreviousKeyCode
        ) ?: return false
        val callback = when (transport) {
            CarTransportAction.NEXT -> skipNext
            CarTransportAction.PREVIOUS -> skipPrevious
        } ?: return false
        val pressedBefore = event.keyCode in pressedKeys
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                // Some OEMs emit only repeated DOWN packets: the first one
                // seen is still a legitimate physical press.
                if (event.repeatCount != 0 && pressedBefore) return true
                pressedKeys.add(event.keyCode)
            }
            KeyEvent.ACTION_UP -> {
                pressedKeys.remove(event.keyCode)
                if (pressedBefore) return true
            }
            else -> return false
        }
        if (!CarMediaTransportKeyPolicy.shouldTrigger(
                event.action, event.repeatCount, pressedBefore
            )
        ) return true

        val now = SystemClock.elapsedRealtime()
        val previousTime = if (transport == CarTransportAction.NEXT) {
            lastNextDispatchTime
        } else {
            lastPreviousDispatchTime
        }
        // UP-only devices and two independent Android dispatch routes must
        // not accidentally skip twice in the same physical click.
        if (now - previousTime < CarMediaTransportKeyPolicy.DUPLICATE_WINDOW_MS) {
            diagnosticContext?.let {
                PlaybackDiagnostics.record(it, "car_media_key_duplicate",
                    "key=${event.keyCode} action=${event.action}")
            }
            return true
        }
        val delivered = callback.invoke()
        if (delivered) {
            if (transport == CarTransportAction.NEXT) {
                lastNextDispatchTime = now
            } else {
                lastPreviousDispatchTime = now
            }
        }
        diagnosticContext?.let {
            PlaybackDiagnostics.record(it, "car_media_key",
                "transport=${transport.name} key=${event.keyCode} " +
                    "action=${event.action} repeat=${event.repeatCount} delivered=$delivered")
        }
        return delivered
    }
}
