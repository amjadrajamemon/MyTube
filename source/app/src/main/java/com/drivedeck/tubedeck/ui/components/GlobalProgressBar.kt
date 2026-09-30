package com.drivedeck.tubedeck.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.drivedeck.tubedeck.ui.theme.TubeDeckAccentAmber
import com.drivedeck.tubedeck.ui.theme.TubeDeckAccentCyan
import com.drivedeck.tubedeck.ui.theme.TubeDeckAccentMint

@Composable
fun GlobalProgressBar(
    isVisible: Boolean,
    progress: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val gradient = Brush.horizontalGradient(
            colors = listOf(
                TubeDeckAccentCyan,
                TubeDeckAccentMint,
                TubeDeckAccentAmber,
                TubeDeckAccentCyan
            )
        )
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(3.5.dp)
                .background(TubeDeckAccentCyan.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progress.coerceIn(0.05f, 1f))
                    .height(3.5.dp)
                    .background(gradient)
            )
        }
    }
}
