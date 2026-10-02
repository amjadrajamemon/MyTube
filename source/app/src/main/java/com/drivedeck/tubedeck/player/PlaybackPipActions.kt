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
    const val ACTION_CLOSE = "com.drivedeck.tubedeck.action.PIP_CLOSE"
    const val ACTION_AUDIO_ONLY = "com.drivedeck.tubedeck.action.PIP_AUDIO_ONLY"
    const val ACTION_SHOW_EXIT_CHOOSER = "com.drivedeck.tubedeck.action.PIP_EXIT_CHOOSER"

    @RequiresApi(Build.VERSION_CODES.O)
    fun buildParams(
        context: Context,
        activityClass: Class<*>,
        aspectRatio: Rational = Rational(16, 9)
    ): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setActions(
                listOf(
                    RemoteAction(
                        Icon.createWithResource(context, android.R.drawable.ic_menu_more),
                        "Options",
                        "Open playback exit options",
                        actionIntent(context, activityClass, ACTION_SHOW_EXIT_CHOOSER, 7100)
                    ),
                    RemoteAction(
                        Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                        "Close",
                        "Close video",
                        actionIntent(context, activityClass, ACTION_CLOSE, 7101)
                    ),
                    RemoteAction(
                        Icon.createWithResource(context, android.R.drawable.ic_media_play),
                        "Audio only",
                        "Continue audio only",
                        actionIntent(context, activityClass, ACTION_AUDIO_ONLY, 7102)
                    )
                )
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(false)
            builder.setSeamlessResizeEnabled(true)
        }

        return builder.build()
    }

    private fun actionIntent(
        context: Context,
        activityClass: Class<*>,
        action: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, activityClass).apply {
            this.action = action
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
