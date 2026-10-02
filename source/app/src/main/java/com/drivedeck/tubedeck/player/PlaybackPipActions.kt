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

    @RequiresApi(Build.VERSION_CODES.O)
    fun buildParams(
        context: Context,
        activityClass: Class<*>,
        aspectRatio: Rational = Rational(16, 9)
    ): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
            .setActions(
                listOf(
                    RemoteAction(
                        Icon.createWithResource(context, android.R.drawable.ic_menu_more),
                        "Options",
                        "Return to playback options",
                        actionIntent(context, activityClass, ACTION_EXPAND_PLAYER, 7100)
                    )
                )
            )

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
