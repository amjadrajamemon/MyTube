package com.drivedeck.tubedeck.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.drivedeck.tubedeck.player.VideoBookmarkStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

private fun displayTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return "%d:%02d:%02d".format(java.util.Locale.US, seconds / 3600, (seconds / 60) % 60, seconds % 60)
}

private fun createQr(text: String): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 224, 224)
    Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888).also { image ->
        for (y in 0 until 224) for (x in 0 until 224) {
            image.setPixel(x, y, if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
}.getOrNull()

private fun shareText(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Send video at this moment")) }
        .onFailure { Toast.makeText(context, "No sharing app is available", Toast.LENGTH_SHORT).show() }
}

/** On-demand UI only: never starts a second decoder, network client, or background timer. */
@Composable
internal fun VideoBookmarksDialog(
    videoId: String,
    videoTitle: String,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { VideoBookmarkStore(context) }
    var bookmarks by remember(videoId) { mutableStateOf(store.forVideo(videoId)) }
    var note by remember(videoId) { mutableStateOf("") }
    var qrUrl by remember(videoId) { mutableStateOf<String?>(null) }
    val qrBitmap = remember(qrUrl) { qrUrl?.let(::createQr) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saved video moments") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 450.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(videoTitle, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                Text("Current position: ${displayTime(currentPositionMs)}")
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(160) },
                    label = { Text("Optional note") }, maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        store.add(videoId, videoTitle, currentPositionMs, note)
                        bookmarks = store.forVideo(videoId)
                        note = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Bookmark this moment") }
                val currentLink = VideoBookmarkStore.shareUrl(videoId, currentPositionMs)
                if (currentLink != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { shareText(context, currentLink) }) { Text("Share link") }
                        TextButton(onClick = { qrUrl = if (qrUrl == currentLink) null else currentLink }) { Text("Show QR") }
                    }
                }
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Scan QR to open the video at the selected timestamp",
                        modifier = Modifier.align(Alignment.CenterHorizontally).size(224.dp)
                    )
                }
                HorizontalDivider()
                if (bookmarks.isEmpty()) {
                    Text("No moments saved for this video.", style = MaterialTheme.typography.bodySmall)
                }
                bookmarks.forEach { bookmark ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${displayTime(bookmark.positionMs)}  ${bookmark.note}", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            TextButton(onClick = { onSeek(bookmark.positionMs); onDismiss() }) {
                                Text("Jump")
                            }
                            VideoBookmarkStore.shareUrl(videoId, bookmark.positionMs)?.let { url ->
                                TextButton(onClick = { shareText(context, url) }) { Text("Send") }
                            }
                            TextButton(onClick = {
                                store.delete(videoId, bookmark.createdAtMs)
                                bookmarks = store.forVideo(videoId)
                            }) { Text("Delete") }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
