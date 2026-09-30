package com.drivedeck.tubedeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.drivedeck.tubedeck.data.model.DownloadMode
import com.drivedeck.tubedeck.data.model.DownloadChoices
import com.drivedeck.tubedeck.data.model.PlaylistItem
import com.drivedeck.tubedeck.data.model.TubeDeckSettings
import com.drivedeck.tubedeck.ui.theme.*
import com.drivedeck.tubedeck.player.YoutubeUrls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadModal(
    initialUrl: String,
    initialPlaylistMode: Boolean = false,
    onDismiss: () -> Unit,
    onStartDownload: (url: String, title: String, mode: DownloadMode, quality: String) -> Unit,
    onDownloadPlaylist: (items: List<PlaylistItem>, mode: DownloadMode, quality: String) -> Unit,
    onFetchPlaylistItems: suspend (String) -> List<PlaylistItem>,
    onFetchDownloadChoices: suspend (String) -> DownloadChoices,
    settings: TubeDeckSettings
) {
    var urlText by remember { mutableStateOf(initialUrl) }
    val hasVideo = YoutubeUrls.videoId(urlText) != null
    val hasPlaylist = YoutubeUrls.playlist(urlText)
    var playlistRequested by remember(initialUrl) { mutableStateOf(initialPlaylistMode || (hasPlaylist && !hasVideo)) }
    val isPlaylist = playlistRequested || (hasPlaylist && !hasVideo)
    
    var selectedMode by remember { mutableStateOf(if (isPlaylist) settings.playlistMode else settings.customMode) }
    var selectedQuality by remember { mutableStateOf(if (isPlaylist) settings.playlistQuality else settings.customQuality) }
    var playlistItems by remember { mutableStateOf(emptyList<PlaylistItem>()) }
    var videoQualities by remember { mutableStateOf(emptyList<String>()) }
    var audioAvailable by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var isFetching by remember { mutableStateOf(false) }
    
    LaunchedEffect(urlText, isPlaylist) {
        isFetching = true
        fetchError = null
        playlistItems = emptyList()
        videoQualities = emptyList()
        audioAvailable = false
        try {
            delay(350)
            if (isPlaylist) {
                require(hasPlaylist) { "Paste a YouTube playlist link to choose its videos." }
                playlistItems = onFetchPlaylistItems(urlText)
                if (playlistItems.isEmpty()) fetchError = "No available videos were found in this playlist."
            } else {
                require(hasVideo) { "Paste a valid YouTube video link." }
                val choices = onFetchDownloadChoices(urlText)
                videoQualities = choices.videoQualities
                audioAvailable = choices.audioAvailable
                if (selectedMode == DownloadMode.VIDEO && videoQualities.isNotEmpty() && selectedQuality !in videoQualities) {
                    selectedQuality = "Best"
                }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            fetchError = error.message ?: "Could not read media information."
        } finally {
            isFetching = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().wrapContentHeight().clip(RoundedCornerShape(24.dp)).border(2.dp, TubeDeckAccentCyan, RoundedCornerShape(24.dp)),
            color = TubeDeckSurface, contentColor = Color.White
        ) {
            Column(modifier = Modifier.padding(20.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(if (isPlaylist) Icons.Default.VideoLibrary else Icons.Default.Download, null, tint = TubeDeckAccentCyan)
                        Text(if (isPlaylist) "Playlist Downloader" else "Media Downloader", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = Color.Gray) }
                }

                Spacer(modifier = Modifier.height(12.dp))
                // When the downloader is opened from an on-video action the source URL is
                // already known. Keep it in state/backend and do not waste player space by
                // printing a long video/playlist URL above the download controls.
                if (initialUrl.isBlank()) {
                    OutlinedTextField(
                        value = urlText, onValueChange = { urlText = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(if (isPlaylist) "Playlist URL" else "Video URL") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (hasVideo || hasPlaylist) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !isPlaylist, enabled = hasVideo, onClick = { playlistRequested = false }, label = { Text("This video") })
                        FilterChip(selected = isPlaylist, onClick = { playlistRequested = true }, label = { Text("Whole playlist") })
                    }
                }

                // Mode Selector for current download option
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DownloadMode.values().forEach { mode ->
                        val isModeSelected = selectedMode == mode
                        Surface(
                            modifier = Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp))
                                .clickable { 
                                    selectedMode = mode
                                    if (mode == DownloadMode.AUDIO) {
                                        selectedQuality = "Best"
                                    } else {
                                        selectedQuality = if (isPlaylist) settings.playlistQuality
                                            else settings.customQuality.takeIf { it in videoQualities } ?: "Best"
                                    }
                                }
                                .border(1.2.dp, if (isModeSelected) TubeDeckAccentCyan else TubeDeckBorder, RoundedCornerShape(10.dp)),
                            color = if (isModeSelected) TubeDeckAccentCyan.copy(alpha = 0.15f) else Color.Transparent
                        ) {
                            Box(contentAlignment = Alignment.Center) { Text(mode.label, fontSize = 12.sp, color = if (isModeSelected) TubeDeckAccentCyan else Color.Gray) }
                        }
                    }
                }

                // Quality Grid
                val qualities = if (selectedMode == DownloadMode.AUDIO) listOf("Best")
                else if (isPlaylist) listOf("Best", "4320p", "2160p", "1440p", "1080p", "720p", "480p", "360p", "240p", "144p")
                else listOf("Best") + videoQualities

                Text(
                    if (isPlaylist) "Quality ceiling for each item; the actual available format may be lower."
                    else "Available video streams; separate video and audio tracks are combined when compatible.",
                    fontSize = 11.sp, color = TubeDeckTextSecondary
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    qualities.chunked(3).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { q ->
                                val isSelected = selectedQuality.startsWith(q) || q.startsWith(selectedQuality)
                                Surface(
                                    modifier = Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp))
                                        .clickable { selectedQuality = q }
                                        .border(1.2.dp, if (isSelected) TubeDeckAccentCyan else TubeDeckBorder, RoundedCornerShape(10.dp)),
                                    color = if (isSelected) TubeDeckAccentCyan.copy(alpha = 0.15f) else Color.Transparent
                                ) {
                                    Box(contentAlignment = Alignment.Center) { Text(q, fontSize = 11.sp, color = if (isSelected) TubeDeckAccentCyan else Color.Gray) }
                                }
                            }
                        }
                    }
                }

                if (isPlaylist) {
                    Spacer(modifier = Modifier.height(20.dp))
                    if (isFetching) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = TubeDeckAccentCyan)
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Select Tracks (${playlistItems.size})", fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("All", color = TubeDeckAccentCyan, modifier = Modifier.clickable {
                                    playlistItems = playlistItems.map { it.copy(isSelected = true) }
                                }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("None", color = Color.Gray, modifier = Modifier.clickable {
                                    playlistItems = playlistItems.map { it.copy(isSelected = false) }
                                }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(playlistItems) { item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically, 
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(TubeDeckSurfaceVariant).clickable { 
                                        playlistItems = playlistItems.map { if (it.videoId == item.videoId) it.copy(isSelected = !it.isSelected) else it }
                                    }.padding(6.dp)
                                ) {
                                    Checkbox(checked = item.isSelected, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = TubeDeckAccentCyan))
                                    
                                    Surface(
                                        modifier = Modifier.size(54.dp, 36.dp).clip(RoundedCornerShape(4.dp)).border(1.dp, TubeDeckBorder, RoundedCornerShape(4.dp)),
                                        color = Color.Black
                                    ) {
                                        AsyncImage(
                                            model = item.thumbnailUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    
                                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                                        Text(item.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White, fontWeight = FontWeight.Bold)
                                        if (item.duration.isNotEmpty()) {
                                            Text(item.duration, fontSize = 11.sp, color = TubeDeckTextSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!isPlaylist && isFetching) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                fetchError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                if (!isPlaylist && !isFetching && selectedMode == DownloadMode.VIDEO && videoQualities.isEmpty() && fetchError == null) {
                    Text("No compatible video stream is available for this video.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                if (!isPlaylist && !isFetching && selectedMode == DownloadMode.AUDIO && !audioAvailable && fetchError == null) {
                    Text("No audio stream is available for this video.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (isPlaylist) onDownloadPlaylist(playlistItems.filter { it.isSelected }, selectedMode, selectedQuality)
                        else onStartDownload(urlText, "YouTube Media", selectedMode, selectedQuality)
                        onDismiss()
                    },
                    enabled = !isFetching && fetchError == null &&
                        (if (isPlaylist) playlistItems.any { it.isSelected }
                         else if (selectedMode == DownloadMode.VIDEO) videoQualities.isNotEmpty() else audioAvailable),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TubeDeckAccentCyan, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Start Download", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
