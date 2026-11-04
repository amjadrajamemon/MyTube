package com.drivedeck.tubedeck.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi

internal object PlaybackPipActions {
    const val ACTION_EXPAND_PLAYER = "com.drivedeck.tubedeck.action.PIP_EXPAND_PLAYER"
    const val ACTION_KEEP_PIP = "com.drivedeck.tubedeck.action.PIP_KEEP"
    const val ACTION_AUDIO_ONLY = "com.drivedeck.tubedeck.action.PIP_AUDIO_ONLY"
    const val ACTION_CLOSE = "com.drivedeck.tubedeck.action.PIP_CLOSE"

    @RequiresApi(Build.VERSION_CODES.O)
    fun buildParams(
        context: Context,
        activityClass: Class<*>,
        aspectRatio: Rational = Rational(16, 9),
        autoEnterEnabled: Boolean = false
    ): PictureInPictureParams {
        @Suppress("UNUSED_VARIABLE")
        val retainedForNamedCallCompatibility = activityClass

        val audioAction = RemoteAction(
            Icon.createWithResource(context, android.R.drawable.ic_lock_silent_mode_off),
            "Audio",
            "Continue in audio-only mode",
            actionIntent(context, ACTION_AUDIO_ONLY, 7101)
        )
        val closeAction = RemoteAction(
            Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
            "Close",
            "Stop playback completely",
            actionIntent(context, ACTION_CLOSE, 7102)
        )

        // Some Android TV firmware never shows its system X. Keep an explicit
        // Close command first in the system PiP menu on all supported API levels.
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
            .setActions(listOf(closeAction, audioAction))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnterEnabled)
            builder.setSeamlessResizeEnabled(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ also invokes this same Close command for system dismissal.
            builder.setCloseAction(closeAction)
        }
        return builder.build()
    }

    private fun actionIntent(
        context: Context,
        action: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, PlaybackPipActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

}
