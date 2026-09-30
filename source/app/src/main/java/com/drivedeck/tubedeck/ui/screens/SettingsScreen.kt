package com.drivedeck.tubedeck.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivedeck.tubedeck.data.model.DownloadMode
import com.drivedeck.tubedeck.data.model.PlayerMode
import com.drivedeck.tubedeck.data.model.TubeDeckSettings
import com.drivedeck.tubedeck.ui.theme.*
import androidx.documentfile.provider.DocumentFile

@Composable
fun SettingsScreen(
    currentSettings: TubeDeckSettings,
    blockedAdsCount: Int,
    onSaveSettings: (TubeDeckSettings) -> Unit,
    onResetAdBlockStats: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var downloadDir by remember { mutableStateOf(currentSettings.downloadDir) }
    var downloadTreeUri by remember { mutableStateOf(currentSettings.downloadTreeUri) }
    var folderError by remember { mutableStateOf<String?>(null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                require(DocumentFile.fromTreeUri(context, uri)?.canWrite() == true) { "This folder is not writable" }
                downloadTreeUri = uri.toString()
                folderError = null
            }.onFailure { folderError = it.message ?: "Could not access selected folder" }
        }
    }
    val chosenFolderName = remember(downloadTreeUri) {
        downloadTreeUri?.let { value ->
            runCatching { DocumentFile.fromTreeUri(context, Uri.parse(value))?.name }.getOrNull()
        }
    }
    
    var instantMode by remember { mutableStateOf(currentSettings.instantMode) }
    var instantQuality by remember { mutableStateOf(currentSettings.instantQuality) }
    
    var customMode by remember { mutableStateOf(currentSettings.customMode) }
    var customQuality by remember { mutableStateOf(currentSettings.customQuality) }
    
    var playlistMode by remember { mutableStateOf(currentSettings.playlistMode) }
    var playlistQuality by remember { mutableStateOf(currentSettings.playlistQuality) }
    var parallelText by remember { mutableStateOf(currentSettings.maxConcurrent.coerceIn(1, 32).toString()) }
    val parallelCount = parallelText.toIntOrNull()
    
    var playerMode by remember { mutableStateOf(currentSettings.playerMode) }
    
    var adBlockEnabled by remember { mutableStateOf(currentSettings.adBlockEnabled) }
    var blockVideoAds by remember { mutableStateOf(currentSettings.blockVideoAds) }
    var blockBannerAds by remember { mutableStateOf(currentSettings.blockBannerAds) }
    var blockTrackers by remember { mutableStateOf(currentSettings.blockTrackers) }
    var sponsorBlockEnabled by remember { mutableStateOf(currentSettings.sponsorBlockEnabled) }
    var blockShortsEnabled by remember { mutableStateOf(currentSettings.blockShortsEnabled) }
    var pipEnabled by remember { mutableStateOf(currentSettings.pipEnabled) }

    val qualities = listOf("Best", "4320p", "2160p", "1440p", "1080p", "720p", "480p", "360p", "240p", "144p")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TubeDeckBackground)
            .statusBarsPadding()
    ) {
        // Top Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = TubeDeckSurface,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = TubeDeckAccentCyan)
                    Text("Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Player Mode
            SettingsCard(title = "Default Media Player") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerMode.values().forEach { mode ->
                        val isSelected = playerMode == mode
                        Surface(
                            modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp))
                                .clickable { playerMode = mode }
                                .border(1.2.dp, if (isSelected) TubeDeckAccentCyan else TubeDeckBorder, RoundedCornerShape(10.dp)),
                            color = if (isSelected) TubeDeckAccentCyan.copy(alpha = 0.15f) else Color.Transparent
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(mode.label, fontSize = 12.sp, color = if (isSelected) TubeDeckAccentCyan else Color.Gray, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }

            SettingsCard(title = "Download folder") {
                Text("Save files in a folder you choose, or use the app storage folder below.", color = Color.LightGray, fontSize = 12.sp)
                Text("Selected: ${chosenFolderName ?: if (downloadTreeUri != null) "Selected folder" else "App storage / $downloadDir"}", color = Color.White, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { folderPicker.launch(null) }) { Text("Choose folder") }
                    if (downloadTreeUri != null) {
                        TextButton(onClick = { downloadTreeUri = null; folderError = null }) { Text("Use app storage") }
                    }
                }
                folderError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                Text("App storage folder name", color = Color.LightGray, fontSize = 12.sp)
                OutlinedTextField(
                    value = downloadDir,
                    onValueChange = { downloadDir = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = TubeDeckAccentCyan,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                if (!downloadDir.matches(Regex("[a-zA-Z0-9 _-]{1,40}"))) {
                    Text("Use 1–40 letters, numbers, spaces, _ or -.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Parallel downloads", Modifier.weight(1f), color = Color.White)
                    OutlinedTextField(
                        value = parallelText,
                        onValueChange = { if (it.length <= 2 && it.all { ch -> ch.isDigit() }) parallelText = it },
                        modifier = Modifier.width(76.dp), singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Text("Choose 1–32. More simultaneous transfers use more bandwidth and memory.", color = Color.LightGray, fontSize = 11.sp)
                if (parallelCount == null || parallelCount !in 1..32) Text("Enter a number from 1 to 32.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            // Instant Download Settings
            SettingsCard(title = "Instant Download Defaults") {
                ModeSelector(selectedMode = instantMode, onSelect = { instantMode = it })
                Spacer(modifier = Modifier.height(8.dp))
                QualityGrid(selectedQuality = instantQuality, onSelect = { instantQuality = it }, qualities = qualities)
            }

            // Custom Download Settings
            SettingsCard(title = "Custom Download Defaults") {
                ModeSelector(selectedMode = customMode, onSelect = { customMode = it })
                Spacer(modifier = Modifier.height(8.dp))
                QualityGrid(selectedQuality = customQuality, onSelect = { customQuality = it }, qualities = qualities)
            }

            // Playlist Download Settings
            SettingsCard(title = "Playlist / Mix Download Defaults") {
                ModeSelector(selectedMode = playlistMode, onSelect = { playlistMode = it })
                Spacer(modifier = Modifier.height(8.dp))
                QualityGrid(selectedQuality = playlistQuality, onSelect = { playlistQuality = it }, qualities = qualities)
            }

            // Blocking Toggle
            SettingsCard(title = "Protection & Blocking") {
                ToggleRow("Request filtering", adBlockEnabled) { adBlockEnabled = it }
                ToggleRow("Try to skip video ads", blockVideoAds) { blockVideoAds = it }
                ToggleRow("SponsorBlock (Skip segments)", sponsorBlockEnabled) { sponsorBlockEnabled = it }
                ToggleRow("Block Banner/Side Ads", blockBannerAds) { blockBannerAds = it }
                ToggleRow("Block known tracking requests", blockTrackers) { blockTrackers = it }
                ToggleRow("Hide Shorts shelves", blockShortsEnabled) { blockShortsEnabled = it }
                ToggleRow("Background PiP Mode", pipEnabled) { pipEnabled = it }
            }

            Button(
                onClick = {
                    onSaveSettings(
                        currentSettings.copy(
                            downloadDir = downloadDir,
                            downloadTreeUri = downloadTreeUri,
                            maxConcurrent = parallelCount ?: currentSettings.maxConcurrent,
                            instantMode = instantMode,
                            instantQuality = instantQuality,
                            customMode = customMode,
                            customQuality = customQuality,
                            playlistMode = playlistMode,
                            playlistQuality = playlistQuality,
                            playerMode = playerMode,
                            adBlockEnabled = adBlockEnabled,
                            blockVideoAds = blockVideoAds,
                            blockBannerAds = blockBannerAds,
                            blockTrackers = blockTrackers,
                            blockShortsEnabled = blockShortsEnabled,
                            sponsorBlockEnabled = sponsorBlockEnabled,
                            pipEnabled = pipEnabled
                        )
                    )
                    onClose()
                },
                enabled = downloadDir.matches(Regex("[a-zA-Z0-9 _-]{1,40}")) && parallelCount != null && parallelCount in 1..32 && folderError == null,
                colors = ButtonDefaults.buttonColors(containerColor = TubeDeckAccentCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text("Save and Apply Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            OutlinedButton(
                onClick = {
                    onSaveSettings(currentSettings.copy(fabInstantX = 40f, fabInstantY = 120f, fabCustomX = 40f, fabCustomY = 190f))
                    onClose()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Reset floating button positions") }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ModeSelector(selectedMode: DownloadMode, onSelect: (DownloadMode) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DownloadMode.values().forEach { mode ->
            val isSelected = selectedMode == mode
            Surface(
                modifier = Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(mode) }
                    .border(1.dp, if (isSelected) TubeDeckAccentCyan else TubeDeckBorder, RoundedCornerShape(8.dp)),
                color = if (isSelected) TubeDeckAccentCyan.copy(alpha = 0.1f) else Color.Transparent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(mode.label, fontSize = 11.sp, color = if (isSelected) TubeDeckAccentCyan else Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun QualityGrid(selectedQuality: String, onSelect: (String) -> Unit, qualities: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        qualities.chunked(3).forEach { chunk ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chunk.forEach { q ->
                    val isSelected = selectedQuality.startsWith(q)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(q) }
                            .border(1.dp, if (isSelected) TubeDeckAccentCyan else Color.DarkGray, RoundedCornerShape(8.dp)),
                        color = if (isSelected) TubeDeckAccentCyan.copy(alpha = 0.15f) else Color.Transparent
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(q, fontSize = 11.sp, color = if (isSelected) TubeDeckAccentCyan else Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedTrackColor = TubeDeckAccentCyan))
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, TubeDeckBorder, RoundedCornerShape(12.dp)), color = TubeDeckSurfaceVariant) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TubeDeckAccentCyan)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
