package com.drivedeck.tubedeck.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.drawRect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * A lightweight background treatment shared across every app destination.
 * Never allocates an image, player, timer or offscreen rendering layer.
 */
fun Modifier.deckScreenBackground(): Modifier = composed {
    val profile = LocalDeckThemeProfile.current
    val tokens = LocalDeckExperience.current
    val base = TubeDeckBackground
    val accent = TubeDeckAccentCyan
    val strength = when (tokens.backdrop) {
        DeckBackdropStyle.INK_BLACK -> 0f
        DeckBackdropStyle.DESKTOP -> .025f
        DeckBackdropStyle.MOBILE, DeckBackdropStyle.DESKTOP_VIDEO -> .035f
        DeckBackdropStyle.TV_RAILS -> .055f
        DeckBackdropStyle.BROADCAST_GRID -> .08f
        DeckBackdropStyle.CINEMA -> .085f
        DeckBackdropStyle.EDITORIAL -> .035f
        DeckBackdropStyle.ARCADE, DeckBackdropStyle.DIGITAL_HUD -> .105f
        DeckBackdropStyle.FROSTED_GLASS, DeckBackdropStyle.TV_SPOTLIGHT -> .11f
        DeckBackdropStyle.MATERIAL_MIST -> .09f
    }
    this.drawBehind {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(lerp(base, accent, strength), base, base)
            )
        )
    }
}

/**
 * Atmospheric wash using only the existing thumbnail URL. Decode is capped at
 * 360px; images are disabled for low-resource devices by experience tokens.
 * Low alpha preserves icon/text contrast, and the image never handles touch.
 */
@Composable
fun DeckArtworkHalo(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier
) {
    val tokens = LocalDeckExperience.current
    if (!tokens.artworkAtmosphere || thumbnailUrl.isNullOrBlank()) return
    val context = LocalContext.current
    Box(modifier.height(215.dp)) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .size(360, 210)
                .crossfade(false)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
                .blur(26.dp)
                .alpha(if (tokens.backdrop == DeckBackdropStyle.CINEMA) .15f else .10f)
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, TubeDeckBackground.copy(alpha = .70f), TubeDeckBackground)
                )
            )
        )
    }
}
