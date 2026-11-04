package com.drivedeck.tubedeck.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import coil.ImageLoader
import coil.decode.ImageDecoderDecoder

/**
 * Animated WebP thumbnails require no second ExoPlayer or video decoder.
 * The shared ImageLoader is allocated lazily only on the first eligible focus.
 */
internal object FocusAnimatedThumbs {
    @Volatile private var loader: ImageLoader? = null
    private val lock = Any()
    private val youtubeId = Regex("[A-Za-z0-9_-]{11}")

    fun url(videoId: String): String? = if (youtubeId.matches(videoId)) {
        "https://i.ytimg.com/an_webp/$videoId/mqdefault_6s.webp"
    } else null

    fun eligible(
        requested: Boolean,
        lowResourceMode: Boolean,
        pictureInPicture: Boolean,
        blackout: Boolean,
        sdk: Int,
        metered: Boolean
    ): Boolean = requested && !lowResourceMode && !pictureInPicture && !blackout &&
        !metered && sdk >= 28

    fun onUnmeteredNetwork(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        cm != null && !cm.isActiveNetworkMetered
    }.getOrDefault(false)

    fun imageLoader(context: Context): ImageLoader = loader ?: synchronized(lock) {
        loader ?: ImageLoader.Builder(context.applicationContext)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                }
            }
            .crossfade(false)
            .build().also { loader = it }
    }
}
