package com.drivedeck.tubedeck.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivedeck.tubedeck.ui.theme.TubeDeckAccentCyan
import com.drivedeck.tubedeck.ui.theme.TubeDeckAccentMint
import com.drivedeck.tubedeck.ui.theme.TubeDeckError
import kotlinx.coroutines.delay

@Composable
fun PlaybackBackChoiceOverlay(
    onCloseVideo: () -> Unit,
    onAudioOnly: () -> Unit,
    onPictureInPicture: () -> Unit,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(true) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "playback_choice_alpha"
    )

    LaunchedEffect(Unit) {
        delay(4_500L)
        visible = false
        delay(300L)
        onTimeout()
    }

    Surface(
        modifier = modifier
            .alpha(alpha)
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 10.dp, end = 10.dp, bottom = 12.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.92f),
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.background(Color.Transparent).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedButton(
                onClick = onCloseVideo,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TubeDeckError)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close")
                Spacer(Modifier.width(3.dp))
                Text("Close", fontSize = 10.5.sp)
            }
            OutlinedButton(
                onClick = onAudioOnly,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TubeDeckAccentCyan)
            ) {
                Icon(Icons.Default.Headphones, contentDescription = "Audio")
                Spacer(Modifier.width(3.dp))
                Text("Audio", fontSize = 10.5.sp)
            }
            OutlinedButton(
                onClick = onPictureInPicture,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TubeDeckAccentMint)
            ) {
                Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP")
                Spacer(Modifier.width(3.dp))
                Text("PiP", fontSize = 10.5.sp)
            }
        }
    }
}
