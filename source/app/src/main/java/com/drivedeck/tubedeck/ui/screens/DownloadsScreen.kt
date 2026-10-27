package com.drivedeck.tubedeck.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.drivedeck.tubedeck.data.repository.DownloadQueuePolicy
import com.drivedeck.tubedeck.ui.components.tvInputHighlight
import com.drivedeck.tubedeck.ui.theme.*
import java.text.DateFormat
import java.util.Date

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
    onPauseTasks: (Set<String>) -> Unit,
    onResumeOrRetryTasks: (Set<String>) -> Unit,
    onDeleteTasks: (Set<String>) -> Unit,
    onPauseAllTasks: () -> Unit,
    onResumeOrRetryAllTasks: () -> Unit,
    onDeleteAllTasks: () -> Unit,
    onClearFinished: () -> Unit,
    onClearHistory: () -> Unit,
    clipboardCount: Int,
    fileOperationBusy: Boolean,
    onCopyFiles: (Set<String>, Boolean) -> Unit,
    onPasteFiles: () -> Unit,
    onMoveFiles: (Set<String>) -> Unit,
    onDeleteFiles: (Set<String>) -> Unit,
    onPlayMedia: (OfflineMedia) -> Unit,
    onCustomDownload: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    var customUrl by rememberSaveable { mutableStateOf("") }
    var filterType by rememberSaveable { mutableStateOf("All") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedTaskIds by remember { mutableStateOf(setOf<String>()) }
    var confirmDeleteSelected by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }
    var selectedFileIds by remember { mutableStateOf(setOf<String>()) }
    var confirmDeleteFiles by remember { mutableStateOf(setOf<String>()) }
    var vaultListView by rememberSaveable { mutableStateOf(true) }
    var queueListView by rememberSaveable { mutableStateOf(true) }
    var finishedActionsOpen by rememberSaveable { mutableStateOf(false) }
    var showNewDownload by rememberSaveable { mutableStateOf(false) }
    val pendingQueue = remember(downloadQueue) { DownloadQueuePolicy.pendingTasks(downloadQueue) }
    LaunchedEffect(offlineMediaList) {
        selectedFileIds = selectedFileIds.intersect(offlineMediaList.mapTo(mutableSetOf()) { it.id })
    }

    LaunchedEffect(pendingQueue) {
        val validIds = pendingQueue.mapTo(mutableSetOf()) { it.id }
        selectedTaskIds = selectedTaskIds.intersect(validIds)
    }

    val tabs = listOf(
        "Finished/Completed (${offlineMediaList.size})",
        "Pending/Queue (${pendingQueue.size})",
        "Activity/History (${downloadHistory.size})"
    )

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showNewDownload = !showNewDownload }) {
                            Icon(
                                if (showNewDownload) Icons.Default.ExpandLess else Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(if (showNewDownload) "Hide" else "New download", fontSize = 11.sp)
                        }
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TubeDeckTextSecondary)
                        }
                    }
                }

                if (showNewDownload) {
                    Spacer(modifier = Modifier.height(8.dp))
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
                            modifier = Modifier.weight(1f).height(48.dp),
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
                                    showNewDownload = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TubeDeckAccentCyan,
                                contentColor = TubeDeckBackground
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Download", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = TubeDeckSurface,
                    contentColor = TubeDeckAccentCyan,
                    edgePadding = 4.dp,
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    title,
                                    fontSize = 10.5.sp,
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("All", "Videos", "Audio").forEach { filter ->
                                    val isSelected = filterType == filter
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .tvInputHighlight(RoundedCornerShape(8.dp))
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
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                TextButton(
                                    onClick = { vaultListView = false },
                                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        Icons.Default.GridView,
                                        contentDescription = "Tile view",
                                        modifier = Modifier.size(17.dp),
                                        tint = if (!vaultListView) TubeDeckAccentCyan else TubeDeckTextSecondary
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        "Tiles",
                                        fontSize = 10.sp,
                                        color = if (!vaultListView) TubeDeckAccentCyan else TubeDeckTextSecondary
                                    )
                                }
                                TextButton(
                                    onClick = { vaultListView = true },
                                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ViewList,
                                        contentDescription = "List view",
                                        modifier = Modifier.size(17.dp),
                                        tint = if (vaultListView) TubeDeckAccentCyan else TubeDeckTextSecondary
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        "List",
                                        fontSize = 10.sp,
                                        color = if (vaultListView) TubeDeckAccentCyan else TubeDeckTextSecondary
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
                        Text(
                            if (selectedFileIds.isEmpty()) "${filteredList.size} finished files" else "${selectedFileIds.size} selected",
                            fontSize = 10.sp,
                            color = if (selectedFileIds.isEmpty()) TubeDeckTextSecondary else TubeDeckAccentCyan
                        )
                        Spacer(Modifier.height(5.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    selectedFileIds = if (selectedFileIds.containsAll(filteredList.map { it.id })) {
                                        emptySet()
                                    } else {
                                        filteredList.mapTo(mutableSetOf()) { it.id }
                                    }
                                },
                                enabled = !fileOperationBusy && filteredList.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.SelectAll, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (selectedFileIds.containsAll(filteredList.map { it.id }) && filteredList.isNotEmpty()) "None" else "Select", fontSize = 10.sp)
                            }
                            OutlinedButton(
                                onClick = onPasteFiles,
                                enabled = !fileOperationBusy && clipboardCount > 0,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Paste ($clipboardCount)", fontSize = 10.sp, maxLines = 1)
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { finishedActionsOpen = true },
                                    enabled = !fileOperationBusy && selectedFileIds.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Manage", fontSize = 10.sp)
                                }
                                DropdownMenu(
                                    expanded = finishedActionsOpen,
                                    onDismissRequest = { finishedActionsOpen = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Copy") },
                                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                        onClick = { finishedActionsOpen = false; onCopyFiles(selectedFileIds, false) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Cut") },
                                        leadingIcon = { Icon(Icons.Default.ContentCut, null) },
                                        onClick = { finishedActionsOpen = false; onCopyFiles(selectedFileIds, true) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Move") },
                                        leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
                                        onClick = { finishedActionsOpen = false; onMoveFiles(selectedFileIds) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = TubeDeckError) },
                                        leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = TubeDeckError) },
                                        onClick = { finishedActionsOpen = false; confirmDeleteFiles = selectedFileIds }
                                    )
                                }
                            }
                        }
                        if (fileOperationBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (filteredList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No media found", color = TubeDeckTextSecondary)
                            }
                        } else if (vaultListView) {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(filteredList, key = { it.id }) { media ->
                                    OfflineMediaListRow(
                                        media = media,
                                        selected = media.id in selectedFileIds,
                                        enabled = !fileOperationBusy,
                                        onSelectedChange = {
                                            selectedFileIds = if (it) selectedFileIds + media.id else selectedFileIds - media.id
                                        },
                                        onPlay = { onPlayMedia(media) },
                                        onCopy = { onCopyFiles(setOf(media.id), false) },
                                        onCut = { onCopyFiles(setOf(media.id), true) },
                                        onMove = { onMoveFiles(setOf(media.id)) },
                                        onDelete = { confirmDeleteFiles = setOf(media.id) }
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 160.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filteredList, key = { it.id }) { media ->
                                    OfflineMediaCard(
                                        media = media, selected = media.id in selectedFileIds, enabled = !fileOperationBusy,
                                        onSelectedChange = { selectedFileIds = if (it) selectedFileIds + media.id else selectedFileIds - media.id },
                                        onPlay = { onPlayMedia(media) },
                                        onCopy = { onCopyFiles(setOf(media.id), false) },
                                        onCut = { onCopyFiles(setOf(media.id), true) },
                                        onMove = { onMoveFiles(setOf(media.id)) },
                                        onDelete = { confirmDeleteFiles = setOf(media.id) }
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        val allSelected = pendingQueue.isNotEmpty() &&
                            selectedTaskIds.size == pendingQueue.size
                        val selectedCount = selectedTaskIds.size

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "Pending downloads",
                                    fontSize = 13.sp,
                                    color = TubeDeckTextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (selectedCount > 0) "$selectedCount selected"
                                    else "${pendingQueue.size} tasks",
                                    fontSize = 10.sp,
                                    color = if (selectedCount > 0) TubeDeckAccentCyan else TubeDeckTextSecondary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { queueListView = false },
                                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        Icons.Default.GridView,
                                        contentDescription = "Queue tile view",
                                        modifier = Modifier.size(16.dp),
                                        tint = if (!queueListView) TubeDeckAccentCyan else TubeDeckTextSecondary
                                    )
                                }
                                TextButton(
                                    onClick = { queueListView = true },
                                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ViewList,
                                        contentDescription = "Queue list view",
                                        modifier = Modifier.size(16.dp),
                                        tint = if (queueListView) TubeDeckAccentCyan else TubeDeckTextSecondary
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    selectedTaskIds = if (allSelected) {
                                        emptySet()
                                    } else {
                                        pendingQueue.mapTo(mutableSetOf()) { it.id }
                                    }
                                },
                                enabled = pendingQueue.isNotEmpty(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.SelectAll, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (allSelected) "Clear selection" else "Select all", fontSize = 10.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (selectedCount > 0) "$selectedCount selected" else "Batch actions",
                                fontSize = 10.sp,
                                color = if (selectedCount > 0) TubeDeckAccentCyan else TubeDeckTextSecondary
                            )
                        }

                        Spacer(Modifier.height(5.dp))

                        QueueBatchControls(
                            actions = listOf(
                                QueueBatchAction(
                                    label = "Pause all",
                                    icon = Icons.Default.Pause,
                                    enabled = pendingQueue.any {
                                        it.status == TaskStatus.DOWNLOADING ||
                                            it.status == TaskStatus.QUEUED
                                    },
                                    onClick = onPauseAllTasks
                                ),
                                QueueBatchAction(
                                    label = "Resume / retry all",
                                    icon = Icons.Default.Refresh,
                                    enabled = pendingQueue.any {
                                        it.status == TaskStatus.PAUSED ||
                                            it.status == TaskStatus.FAILED
                                    },
                                    onClick = onResumeOrRetryAllTasks
                                ),
                                QueueBatchAction(
                                    label = "Delete all",
                                    icon = Icons.Default.Delete,
                                    enabled = pendingQueue.isNotEmpty(),
                                    destructive = true,
                                    onClick = { confirmDeleteAll = true }
                                )
                            )
                        )

                        Spacer(Modifier.height(5.dp))

                        QueueBatchControls(
                            actions = listOf(
                                QueueBatchAction(
                                    label = "Pause selected",
                                    icon = Icons.Default.Pause,
                                    enabled = selectedTaskIds.isNotEmpty() &&
                                        pendingQueue.any {
                                            it.id in selectedTaskIds &&
                                                (it.status == TaskStatus.DOWNLOADING ||
                                                    it.status == TaskStatus.QUEUED)
                                        },
                                    onClick = { onPauseTasks(selectedTaskIds) }
                                ),
                                QueueBatchAction(
                                    label = "Resume / retry selected",
                                    icon = Icons.Default.Refresh,
                                    enabled = selectedTaskIds.isNotEmpty() &&
                                        pendingQueue.any {
                                            it.id in selectedTaskIds &&
                                                (it.status == TaskStatus.PAUSED ||
                                                    it.status == TaskStatus.FAILED)
                                        },
                                    onClick = { onResumeOrRetryTasks(selectedTaskIds) }
                                ),
                                QueueBatchAction(
                                    label = "Delete selected",
                                    icon = Icons.Default.DeleteOutline,
                                    enabled = selectedTaskIds.isNotEmpty(),
                                    destructive = true,
                                    onClick = { confirmDeleteSelected = true }
                                )
                            )
                        )

                        Spacer(Modifier.height(8.dp))

                        if (pendingQueue.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No pending downloads", color = TubeDeckTextSecondary)
                            }
                        } else if (queueListView) {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(pendingQueue, key = { it.id }) { task ->
                                    QueueTaskCard(
                                        task = task,
                                        selected = task.id in selectedTaskIds,
                                        onSelectedChange = { selected ->
                                            selectedTaskIds = if (selected) {
                                                selectedTaskIds + task.id
                                            } else {
                                                selectedTaskIds - task.id
                                            }
                                        },
                                        onPause = { onPauseTask(task.id) },
                                        onResume = { onResumeTask(task.id) },
                                        onCancel = { onCancelTask(task.id) },
                                        onRetry = { onRetryTask(task.id) }
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 220.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(pendingQueue, key = { it.id }) { task ->
                                    QueueTaskCard(
                                        task = task,
                                        selected = task.id in selectedTaskIds,
                                        onSelectedChange = { selected ->
                                            selectedTaskIds = if (selected) {
                                                selectedTaskIds + task.id
                                            } else {
                                                selectedTaskIds - task.id
                                            }
                                        },
                                        onPause = { onPauseTask(task.id) },
                                        onResume = { onResumeTask(task.id) },
                                        onCancel = { onCancelTask(task.id) },
                                        onRetry = { onRetryTask(task.id) }
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Download activity", fontSize = 13.sp, color = TubeDeckTextSecondary, fontWeight = FontWeight.SemiBold)
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

    if (confirmDeleteSelected) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            title = { Text("Delete selected queue items?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTasks(selectedTaskIds)
                        selectedTaskIds = emptySet()
                        confirmDeleteSelected = false
                    }
                ) { Text("Delete", color = TubeDeckError) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSelected = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmDeleteFiles.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { confirmDeleteFiles = emptySet() },
            title = { Text("Delete ${confirmDeleteFiles.size} files?") },
            confirmButton = { TextButton(onClick = {
                onDeleteFiles(confirmDeleteFiles)
                confirmDeleteFiles = emptySet()
            }) { Text("Delete", color = TubeDeckError) } },
            dismissButton = { TextButton(onClick = { confirmDeleteFiles = emptySet() }) { Text("Cancel") } }
        )
    }
    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete the entire queue?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAllTasks()
                        selectedTaskIds = emptySet()
                        confirmDeleteAll = false
                    }
                ) { Text("Delete all", color = TubeDeckError) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") }
            }
        )
    }
}
@Composable
fun OfflineMediaCard(media: OfflineMedia, selected: Boolean, enabled: Boolean, onSelectedChange: (Boolean) -> Unit,
                     onPlay: () -> Unit, onCopy: () -> Unit, onCut: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, TubeDeckBorder, RoundedCornerShape(12.dp)).tvInputHighlight().clickable { onPlay() },
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
            Text(media.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TubeDeckTextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(media.qualityLabel, fontSize = 10.sp, color = TubeDeckTextSecondary)
                Checkbox(checked = selected, onCheckedChange = onSelectedChange, enabled = enabled, modifier = Modifier.size(32.dp))
                Box {
                    IconButton(onClick = { menuOpen = true }, enabled = enabled, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, "File actions", tint = TubeDeckTextSecondary) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Copy") }, onClick = { menuOpen = false; onCopy() })
                        DropdownMenuItem(text = { Text("Cut") }, onClick = { menuOpen = false; onCut() })
                        DropdownMenuItem(text = { Text("Move") }, onClick = { menuOpen = false; onMove() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete() })
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineMediaListRow(
    media: OfflineMedia,
    selected: Boolean,
    enabled: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onPlay: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, if (selected) TubeDeckAccentCyan else TubeDeckBorder, RoundedCornerShape(10.dp))
            .tvInputHighlight(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onPlay),
        color = if (selected) TubeDeckAccentCyan.copy(alpha = 0.07f) else TubeDeckSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = onSelectedChange,
                enabled = enabled,
                modifier = Modifier.size(28.dp)
            )
            AsyncImage(
                model = media.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(92.dp, 54.dp).clip(RoundedCornerShape(6.dp))
            )
            Column(Modifier.weight(1f)) {
                Text(
                    media.title,
                    color = TubeDeckTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    listOf(media.qualityLabel, media.format)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    color = TubeDeckTextSecondary,
                    fontSize = 10.sp
                )
            }
            IconButton(onClick = onPlay, enabled = enabled, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = TubeDeckAccentMint)
            }
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = enabled, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "File actions", tint = TubeDeckTextSecondary)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Copy to…") }, onClick = { menuOpen = false; onCopy() })
                    DropdownMenuItem(text = { Text("Cut / move to…") }, onClick = { menuOpen = false; onCut() })
                    DropdownMenuItem(text = { Text("Move to…") }, onClick = { menuOpen = false; onMove() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }
    }
}

@Composable
fun QueueTaskCard(
    task: DownloadTask,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (selected) TubeDeckAccentCyan else TubeDeckBorder,
                RoundedCornerShape(12.dp)
            )
            .tvInputHighlight(RoundedCornerShape(12.dp))
            .clickable { onSelectedChange(!selected) },
        color = if (selected) TubeDeckAccentCyan.copy(alpha = 0.08f) else TubeDeckSurfaceVariant
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = onSelectedChange,
                    modifier = Modifier.size(28.dp),
                    colors = CheckboxDefaults.colors(checkedColor = TubeDeckAccentCyan)
                )
                AsyncImage(model = task.thumbnailUrl, contentDescription = null, modifier = Modifier.size(60.dp, 36.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TubeDeckTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${task.quality} · ${task.speed}", fontSize = 10.sp, color = TubeDeckAccentCyan)
                }
                when (task.status) {
                    TaskStatus.DOWNLOADING, TaskStatus.QUEUED ->
                        IconButton(onClick = onPause, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Pause, "Pause", tint = TubeDeckTextPrimary)
                        }
                    TaskStatus.PAUSED ->
                        IconButton(onClick = onResume, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.PlayArrow, "Resume", tint = TubeDeckTextPrimary)
                        }
                    TaskStatus.FAILED ->
                        IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, "Retry", tint = TubeDeckTextPrimary)
                        }
                    TaskStatus.COMPLETED, TaskStatus.CANCELLED, TaskStatus.RETRIED -> Unit
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


private data class QueueBatchAction(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val enabled: Boolean,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

@Composable
private fun QueueBatchControls(actions: List<QueueBatchAction>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        actions.forEach { action ->
            OutlinedButton(
                onClick = action.onClick,
                enabled = action.enabled,
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (action.destructive) TubeDeckError else TubeDeckAccentCyan
                )
            ) {
                Icon(action.icon, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(action.label, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}


@Composable
fun HistoryItemCard(item: DownloadTask) {
    val statusLabel = when (item.status) {
        TaskStatus.COMPLETED -> "Completed"
        TaskStatus.FAILED -> "Failed"
        TaskStatus.CANCELLED -> "Cancelled"
        TaskStatus.RETRIED -> "Retried"
        TaskStatus.PAUSED -> "Paused"
        TaskStatus.DOWNLOADING -> "Downloading"
        TaskStatus.QUEUED -> "Queued"
    }
    val statusColor = when (item.status) {
        TaskStatus.COMPLETED -> TubeDeckSuccess
        TaskStatus.FAILED, TaskStatus.CANCELLED -> TubeDeckError
        TaskStatus.RETRIED -> TubeDeckAccentCyan
        else -> TubeDeckTextSecondary
    }
    val eventText = remember(item.eventAt) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.eventAt))
    }

    Surface(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, TubeDeckBorder, RoundedCornerShape(10.dp)),
        color = TubeDeckSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(60.dp, 36.dp).clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    fontSize = 12.sp,
                    color = TubeDeckTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(eventText)
                        if (item.retryCount > 0) append(" · retries ").append(item.retryCount)
                    },
                    fontSize = 9.5.sp,
                    color = TubeDeckTextSecondary,
                    maxLines = 1
                )
                item.errorMessage?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        fontSize = 9.5.sp,
                        color = TubeDeckTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(statusLabel, fontSize = 10.sp, color = statusColor, fontWeight = FontWeight.Bold)
        }
    }
}
