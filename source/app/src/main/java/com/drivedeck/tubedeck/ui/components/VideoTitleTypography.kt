package com.drivedeck.tubedeck.ui.components

import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.drivedeck.tubedeck.ui.theme.LocalVideoTitleScale
import com.drivedeck.tubedeck.ui.theme.TubeDeckTextPrimary
import kotlin.math.roundToInt

/**
 * Standardize Home, TV and search result typography independently of highly
 * stylized interface fonts. Width is measured in dp rather than hardware pixels.
 */
internal data class VideoTitleMetrics(
    val titleSp: Int,
    val lineHeightSp: Int,
    val metadataSp: Int,
    val maxTitleLines: Int,
    val shelfCardHeightDp: Int,
    val listCardHeightDp: Int
)

internal object VideoTitleTypographyPolicy {
    fun metrics(
        screenWidthDp: Int,
        screenHeightDp: Int,
        landscape: Boolean,
        userScale: Float
    ): VideoTitleMetrics {
        val shortLandscape = landscape && screenWidthDp >= 540 && screenHeightDp <= 700
        val baseSize = when {
            shortLandscape -> 16
            screenWidthDp >= 800 -> 16
            else -> 15
        }
        val size = (baseSize * userScale.coerceIn(1f, 1.5f)).roundToInt()
            .coerceIn(15, 24)
        val line = (size * 1.32f).roundToInt().coerceAtLeast(size + 4)
        val metadata = (12.5f * userScale.coerceIn(1f, 1.5f)).roundToInt()
            .coerceIn(12, 19)
        return VideoTitleMetrics(
            titleSp = size,
            lineHeightSp = line,
            metadataSp = metadata,
            maxTitleLines = if (shortLandscape) 4 else 3,
            // Prevent a larger user-selected title from being clipped by fixed-height shelves.
            shelfCardHeightDp = (292 + (size - 15) * 10).coerceIn(292, 390),
            listCardHeightDp = (180 + (size - 15) * 7).coerceIn(180, 255)
        )
    }
}

@Composable
internal fun rememberVideoTitleMetrics(): VideoTitleMetrics {
    val config = LocalConfiguration.current
    val scale = LocalVideoTitleScale.current
    return VideoTitleTypographyPolicy.metrics(
        screenWidthDp = config.screenWidthDp,
        screenHeightDp = config.screenHeightDp,
        landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE,
        userScale = scale
    )
}

/** Clear, medium-weight sans-serif content titles, even in serif/HUD UI themes. */
@Composable
fun ReadableVideoTitle(
    title: String,
    modifier: Modifier = Modifier,
    maxLines: Int? = null,
    weight: FontWeight = FontWeight.SemiBold
) {
    val metrics = rememberVideoTitleMetrics()
    Text(
        text = title,
        color = TubeDeckTextPrimary,
        fontSize = metrics.titleSp.sp,
        lineHeight = metrics.lineHeightSp.sp,
        fontFamily = FontFamily.SansSerif,
        fontWeight = weight,
        softWrap = true,
        maxLines = maxLines ?: metrics.maxTitleLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/** Higher-contrast secondary text for titles' channel labels. */
@Composable
fun ReadableVideoMetadata(
    label: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 2
) {
    val metrics = rememberVideoTitleMetrics()
    Text(
        text = label,
        color = TubeDeckTextPrimary.copy(alpha = .85f),
        fontSize = metrics.metadataSp.sp,
        lineHeight = (metrics.metadataSp + 4).sp,
        fontFamily = FontFamily.SansSerif,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        softWrap = true,
        modifier = modifier
    )
}
