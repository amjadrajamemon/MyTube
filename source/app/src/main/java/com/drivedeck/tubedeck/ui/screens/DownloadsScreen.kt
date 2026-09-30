package com.drivedeck.tubedeck.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.drivedeck.tubedeck.data.model.*
import com.drivedeck.tubedeck.ui.theme.*

@Composable
fun DownloadsScreen(
    downloadQueue: List<DownloadTask>,
    offlineMediaList: List<OfflineMedia>,
    downloadHistory: List<DownloadTask>,
    initialTab: Int = 0,
    onPauseTask: (String) -> Unit,
    onResumeTask: (String) -> Unit,
    onCancelTask: (String) -> Unit,
    onRetryTask: (String) -> Unit,
    onClearFinished: () -> Unit,
    onClearHistory: () -> Unit,
    onDeleteOfflineMedia: (String) -> Unit,
    onPlayMedia: (OfflineMedia) -> Unit,
    onCustomDownload: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var customUrl by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    val tabs = listOf("Downloads (${offlineMediaList.size})", "Queue (${downloadQueue.size})", "History (${downloadHistory.size})")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TubeDeckBackground)
            .statusBarsPadding() // FIX: Status Bar overlap
            .navigationBarsPadding() // FIX: Bottom Nav overlap
    ) {
        // Drawer Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = TubeDeckSurface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.DownloadForOffline, contentDescription = null, tint = TubeDeckAccentCyan)
                        Text(
                            "Downloads Center",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TubeDeckTextPrimary
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TubeDeckTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom Download Box
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        placeholder = { Text("Paste YouTube URL", fontSize = 12.sp, color = TubeDeckTextSecondary) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TubeDeckTextPrimary,
                            unfocusedTextColor = TubeDeckTextPrimary,
                            focusedBorderColor = TubeDeckAccentCyan,
                            unfocusedBorderColor = TubeDeckBorder,
                            focusedContainerColor = TubeDeckSurfaceVariant,
                            unfocusedContainerColor = TubeDeckSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = {
                            if (customUrl.isNotBlank()) {
                                onCustomDownload(customUrl)
                                customUrl = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TubeDeckAccentCyan, contentColor = TubeDeckBackground),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Download", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = TubeDeckSurface,
                    contentColor = TubeDeckAccentCyan,
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == index) TubeDeckAccentCyan else TubeDeckTextSecondary
                                )
                            }
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            when (selectedTab) {
                0 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("All", "Videos", "Audio").forEach { filter ->
                                val isSelected = filterType == filter
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { filterType = filter }
                                        .border(1.dp, if (isSelected) TubeDeckAccentMint else TubeDeckBorder, RoundedCornerShape(8.dp)),
                                    color = if (isSelected) TubeDeckAccentMint.copy(alpha = 0.2f) else TubeDeckSurfaceVariant
                                ) {
                                    Text(
                                        filter,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        fontSize = 11.sp,
                                        color = if (isSelected) TubeDeckAccentMint else TubeDeckTextSecondary
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        val filteredList = offlineMediaList.filter { item ->
                            val matchesType = when (filterType) {
                                "Videos" -> !item.isAudioOnly
                                "Audio" -> item.isAudioOnly
                                else -> true
                            }
                            matchesType
                        }
                        if (filteredList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No media found", color = TubeDeckTextSecondary)
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 160.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filteredList) { media ->
                                    OfflineMediaCard(media = media, onPlay = { onPlayMedia(media) }, onDelete = { onDeleteOfflineMedia(media.id) })
                                }
                            }
                        }
                    }
                }
                1 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Active Tasks", fontSize = 13.sp, color = TubeDeckTextSecondary, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = onClearFinished) {
                                Text("Clear finished", fontSize = 12.sp, color = TubeDeckAccentCyan)
                            }
                        }
                        if (downloadQueue.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Queue is empty", color = TubeDeckTextSecondary)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(downloadQueue) { task ->
                                    QueueTaskCard(task = task, onPause = { onPauseTask(task.id) }, onResume = { onResumeTask(task.id) }, onCancel = { onCancelTask(task.id) }, onRetry = { onRetryTask(task.id) })
                                }
                            }
                        }
                    }
                }
                2 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Download History", fontSize = 13.sp, color = TubeDeckTextSecondary, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = onClearHistory) {
                                Text("Clear history", fontSize = 12.sp, color = TubeDeckError)
                            }
                        }
                        if (downloadHistory.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No history", color = TubeDeckTextSecondary)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(downloadHistory) { item -> HistoryItemCard(item = item) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OfflineMediaCard(media: OfflineMedia, onPlay: () -> Unit, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, TubeDeckBorder, RoundedCornerShape(12.dp)).clickable { onPlay() },
        color = TubeDeckSurfaceVariant
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(8.dp))) {
                AsyncImage(model = media.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(modifier = Modifier.size(32.dp).align(Alignment.Center).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = TubeDeckAccentMint)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(media.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(media.qualityLabel, fontSize = 10.sp, color = TubeDeckTextSecondary)
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.DeleteOutline, null, tint = TubeDeckTextSecondary, modifier = Modifier.size(16.dp)) }
            }
        }
    }
}

@Composable
fun QueueTaskCard(task: DownloadTask, onPause: () -> Unit, onResume: () -> Unit, onCancel: () -> Unit, onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, TubeDeckBorder, RoundedCornerShape(12.dp)), color = TubeDeckSurfaceVariant) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AsyncImage(model = task.thumbnailUrl, contentDescription = null, modifier = Modifier.size(60.dp, 36.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${task.quality} · ${task.speed}", fontSize = 10.sp, color = TubeDeckAccentCyan)
                }
                when (task.status) {
                    TaskStatus.DOWNLOADING, TaskStatus.QUEUED ->
                        IconButton(onClick = onPause, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Pause, "Pause", tint = Color.White)
                        }
                    TaskStatus.PAUSED ->
                        IconButton(onClick = onResume, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.PlayArrow, "Resume", tint = Color.White)
                        }
                    TaskStatus.FAILED ->
                        IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, "Retry", tint = Color.White)
                        }
                    TaskStatus.COMPLETED -> Unit
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, null, tint = TubeDeckTextSecondary) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${task.status.name.lowercase().replaceFirstChar { it.uppercase() }} · ${task.downloadedBytes / 1_048_576} MB" +
                    if (task.totalBytes > 0) " / ${task.totalBytes / 1_048_576} MB" else "",
                fontSize = 10.sp, color = TubeDeckTextSecondary
            )
            task.errorMessage?.let { Text(it, fontSize = 11.sp, color = TubeDeckError) }
            if (task.totalBytes > 0) {
                LinearProgressIndicator(progress = { task.progress }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = TubeDeckAccentCyan, trackColor = TubeDeckBackground)
            } else if (task.status == TaskStatus.DOWNLOADING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
            }
        }
    }
}

@Composable
fun HistoryItemCard(item: DownloadTask) {
    Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, TubeDeckBorder, RoundedCornerShape(10.dp)), color = TubeDeckSurfaceVariant) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AsyncImage(model = item.thumbnailUrl, contentDescription = null, modifier = Modifier.size(60.dp, 36.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
            Text(item.title, fontSize = 12.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("Done", fontSize = 10.sp, color = TubeDeckSuccess, fontWeight = FontWeight.Bold)
        }
    }
}
