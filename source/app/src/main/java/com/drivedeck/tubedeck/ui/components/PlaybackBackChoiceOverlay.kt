package com.drivedeck.tubedeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivedeck.tubedeck.ui.theme.TubeDeckAccentCyan
import com.drivedeck.tubedeck.ui.theme.TubeDeckError

@Composable
fun PlaybackBackChoiceOverlay(
    onCloseVideo: () -> Unit,
    onAudioOnly: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.88f),
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.background(Color.Transparent),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCloseVideo,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TubeDeckError)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close video")
                Spacer(Modifier.width(4.dp))
                Text("Close", fontSize = 11.sp)
            }

            OutlinedButton(
                onClick = onAudioOnly,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TubeDeckAccentCyan)
            ) {
                Icon(Icons.Default.Headphones, contentDescription = "Audio only")
                Spacer(Modifier.width(4.dp))
                Text("Audio only", fontSize = 11.sp)
            }
        }
    }
}
