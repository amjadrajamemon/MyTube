package com.drivedeck.tubedeck.ui.components

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.InputDevice
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.drivedeck.tubedeck.ui.theme.TubeDeckAccentCyan

/**
 * Android TV, Google TV, and generic no-touch set-top boxes do not always advertise
 * the same package features. Keep touch/car screens unchanged while allowing
 * keyboard/D-pad navigation on inexpensive non-Leanback TV boxes.
 */
fun isTvLikeNavigationDevice(context: Context): Boolean {
    val pm = context.packageManager
    val mode = (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)
        ?.currentModeType ?: (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK)
    val largeDisplay = context.resources.configuration.smallestScreenWidthDp >= 600
    // Many inexpensive AOSP TV boxes advertise a touchscreen despite having
    // only an air mouse, mini keyboard, or D-pad attached. Detect those real,
    // external controllers rather than relying exclusively on Leanback flags.
    val physicalRemote = if (largeDisplay) {
        InputDevice.getDeviceIds().any { id ->
            val device = InputDevice.getDevice(id)
            device != null && !device.isVirtual && (
                device.sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE ||
                device.sources and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD ||
                device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
            )
        }
    } else false
    return mode == Configuration.UI_MODE_TYPE_TELEVISION ||
        pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        (largeDisplay && !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) ||
        physicalRemote
}

/**
 * Shares visible D-pad/keyboard focus and pointer hover feedback between Compose
 * video cards, navigation buttons, and player actions. The caller's clickable()
 * remains responsible for the actual click (mouse, Enter, or D-pad center).
 */
fun Modifier.tvInputHighlight(shape: Shape = RoundedCornerShape(12.dp)): Modifier = composed {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    val color = when {
        focused -> TubeDeckAccentCyan
        hovered -> TubeDeckAccentCyan.copy(alpha = 0.55f)
        else -> Color.Transparent
    }
    this
        .onFocusChanged { focused = it.isFocused }
        .hoverable(hoverSource)
        .border(if (focused) 3.dp else 2.dp, color, shape)
}
