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
import com.drivedeck.tubedeck.player.YtDlpUpdateManager
import com.drivedeck.tubedeck.ui.theme.*
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.launch

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
    val updateScope = rememberCoroutineScope()
    val ytDlpState by YtDlpUpdateManager.state.collectAsState()
    LaunchedEffect(Unit) {
        YtDlpUpdateManager.scheduleWeekly(context)
        YtDlpUpdateManager.refreshState(context)
    }
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
    
    var playerMode by remember { mutableStateOf(currentSettings.playerMode) }
    
    var adBlockEnabled by remember { mutableStateOf(currentSettings.adBlockEnabled) }
    var blockVideoAds by remember { mutableStateOf(currentSettings.blockVideoAds) }
    var blockBannerAds by remember { mutableStateOf(currentSettings.blockBannerAds) }
    var blockTrackers by remember { mutableStateOf(currentSettings.blockTrackers) }
    var sponsorBlockEnabled by remember { mutableStateOf(currentSettings.sponsorBlockEnabled) }
    var blockShortsEnabled by remember { mutableStateOf(currentSettings.blockShortsEnabled) }
    var pipEnabled by remember { mutableStateOf(currentSettings.pipEnabled) }
    var autoLoadEpisodes by remember { mutableStateOf(currentSettings.autoLoadEpisodes) }
    var autoLoadUpNext by remember { mutableStateOf(currentSettings.autoLoadUpNext) }

    var appearancePreset by remember { mutableStateOf(currentSettings.appearancePreset) }
    var fontScale by remember { mutableFloatStateOf(currentSettings.fontScale.coerceIn(0.80f, 1.35f)) }
    var themeAccentArgb by remember { mutableLongStateOf(currentSettings.themeAccentArgb) }
    var themeBackgroundArgb by remember { mutableLongStateOf(currentSettings.themeBackgroundArgb) }
    var themeTextArgb by remember { mutableLongStateOf(currentSettings.themeTextArgb) }

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
                    Text("Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TubeDeckTextPrimary)
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
            SettingsCard(title = "Appearance & Customization") {
                Text(
                    "Preset: " + appearancePreset,
                    color = TubeDeckTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))

                Text("Top 10 built-in themes", color = TubeDeckTextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                appearancePresets.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { preset ->
                            val selected = appearancePreset == preset.name
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(70.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        appearancePreset = preset.name
                                        fontScale = preset.fontScale
                                        themeAccentArgb = preset.accentArgb
                                        themeBackgroundArgb = preset.backgroundArgb
                                        themeTextArgb = preset.textArgb
                                    }
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) Color(preset.accentArgb.toInt()) else TubeDeckBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    ),
                                color = Color(preset.backgroundArgb.toInt())
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        preset.name,
                                        color = Color(preset.textArgb.toInt()),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Box(
                                            Modifier.size(13.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(preset.accentArgb.toInt()))
                                        )
                                        Text(
                                            preset.subtitle,
                                            color = Color(preset.textArgb.toInt()).copy(alpha = 0.72f),
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = TubeDeckBorder
                )

                Text(
                    "Font size " + (fontScale * 100).toInt() + "%",
                    color = TubeDeckTextPrimary,
                    fontSize = 12.sp
                )
                Slider(
                    value = fontScale,
                    onValueChange = {
                        fontScale = it.coerceIn(0.80f, 1.35f)
                        appearancePreset = "Custom"
                    },
                    valueRange = 0.80f..1.35f
                )
                Text(
                    "Adjusts text throughout MyTube. Range is limited to keep controls usable.",
                    color = TubeDeckTextSecondary,
                    fontSize = 10.sp
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    "Custom color adjusters",
                    color = TubeDeckTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))

                RgbColorAdjuster(
                    label = "Accent / controls",
                    argb = themeAccentArgb,
                    onChange = {
                        themeAccentArgb = it
                        appearancePreset = "Custom"
                    }
                )
                RgbColorAdjuster(
                    label = "Overall theme / background",
                    argb = themeBackgroundArgb,
                    onChange = {
                        themeBackgroundArgb = it
                        appearancePreset = "Custom"
                    }
                )
                RgbColorAdjuster(
                    label = "Primary text",
                    argb = themeTextArgb,
                    onChange = {
                        themeTextArgb = it
                        appearancePreset = "Custom"
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            appearancePreset = "Glass Midnight"
                            fontScale = 1.0f
                            themeAccentArgb = 0xFF00D2FFL
                            themeBackgroundArgb = 0xFF080B11L
                            themeTextArgb = 0xFFFFFFFFL
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset appearance")
                    }
                    Surface(
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(themeBackgroundArgb.toInt()),
                        border = androidx.compose.foundation.BorderStroke(
                            2.dp,
                            Color(themeAccentArgb.toInt())
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "Preview",
                                color = Color(themeTextArgb.toInt()),
                                fontSize = (12f * fontScale).sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

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

            SettingsCard(title = "Download Engine") {
                Text(
                    "Version: " + (ytDlpState.version ?: "Bundled / not initialized"),
                    color = Color.White,
                    fontSize = 12.sp
                )
                Text(
                    "Last update check: " + ytDlpState.lastCheckedLabel,
                    color = TubeDeckTextSecondary,
                    fontSize = 11.sp
                )
                Text(
                    ytDlpState.message,
                    color = if (ytDlpState.message.contains("failed", ignoreCase = true)) MaterialTheme.colorScheme.error else TubeDeckTextSecondary,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        updateScope.launch {
                            YtDlpUpdateManager.updateNow(context)
                        }
                    },
                    enabled = !ytDlpState.updating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TubeDeckAccentCyan,
                        contentColor = Color.Black
                    )
                ) {
                    if (ytDlpState.updating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.Black
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Updating…")
                    } else {
                        Icon(Icons.Default.SystemUpdate, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Check/update Download Engine")
                    }
                }
            }

            SettingsCard(title = "Download folder") {
                Text(
                    "Selected: ${chosenFolderName ?: if (downloadTreeUri != null) "Selected folder" else "No folder selected"}",
                    color = Color.White,
                    fontSize = 12.sp
                )
                OutlinedButton(onClick = { folderPicker.launch(null) }) {
                    Text(if (downloadTreeUri == null) "Choose folder" else "Change folder")
                }
                folderError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
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

            SettingsCard(title = "Playback performance") {
                Text(
                    "Keep these off for the fastest video start and lowest background extraction. Enable only the shelves you want loaded automatically.",
                    color = TubeDeckTextSecondary,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(6.dp))
                ToggleRow("Auto-load Episodes", autoLoadEpisodes) { autoLoadEpisodes = it }
                ToggleRow("Auto-load Up Next", autoLoadUpNext) { autoLoadUpNext = it }
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
                            pipEnabled = pipEnabled,
                            autoLoadEpisodes = autoLoadEpisodes,
                            autoLoadUpNext = autoLoadUpNext,
                            appearancePreset = appearancePreset,
                            fontScale = fontScale.coerceIn(0.80f, 1.35f),
                            themeAccentArgb = themeAccentArgb,
                            themeBackgroundArgb = themeBackgroundArgb,
                            themeTextArgb = themeTextArgb
                        )
                    )
                    onClose()
                },
                enabled = folderError == null,
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


private data class AppearancePreset(
    val name: String,
    val subtitle: String,
    val accentArgb: Long,
    val backgroundArgb: Long,
    val textArgb: Long = 0xFFFFFFFFL,
    val fontScale: Float = 1.0f
)

private val appearancePresets = listOf(
    AppearancePreset("Glass Midnight", "Glass • balanced", 0xFF00D2FFL, 0xFF080B11L),
    AppearancePreset("Glass Slate", "Glass • soft blue", 0xFF7DD3FCL, 0xFF10151DL),
    AppearancePreset("Neon Cyan", "Neon • electric", 0xFF00F5FFL, 0xFF02070AL),
    AppearancePreset("Neon Purple", "Neon • violet", 0xFFD946EFL, 0xFF0A0614L),
    AppearancePreset("Neon Lime", "Neon • vivid green", 0xFF39FF14L, 0xFF050806L),
    AppearancePreset("Ocean Deep", "Ocean • deep blue", 0xFF00B4D8L, 0xFF03111DL),
    AppearancePreset("Ocean Teal", "Ocean • teal glass", 0xFF2DD4BFL, 0xFF041716L),
    AppearancePreset("HD Graphite", "HD • neutral", 0xFF60A5FAL, 0xFF0B0D10L),
    AppearancePreset("HD Crimson", "HD • cinematic", 0xFFFF4D6DL, 0xFF12070AL),
    AppearancePreset("AMOLED Black", "Pure black • OLED", 0xFF00E5FFL, 0xFF000000L)
)

@Composable
private fun RgbColorAdjuster(
    label: String,
    argb: Long,
    onChange: (Long) -> Unit
) {
    val red = ((argb shr 16) and 0xFF).toInt()
    val green = ((argb shr 8) and 0xFF).toInt()
    val blue = (argb and 0xFF).toInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = TubeDeckTextSecondary, fontSize = 10.5.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier.size(18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(argb.toInt()))
                        .border(1.dp, TubeDeckBorder, RoundedCornerShape(5.dp))
                )
                Text(
                    "#%06X".format(argb and 0xFFFFFFL),
                    color = TubeDeckTextSecondary,
                    fontSize = 9.sp
                )
            }
        }
        ColorChannelSlider("R", red) { onChange(withRgb(argb, red = it)) }
        ColorChannelSlider("G", green) { onChange(withRgb(argb, green = it)) }
        ColorChannelSlider("B", blue) { onChange(withRgb(argb, blue = it)) }
    }
}

@Composable
private fun ColorChannelSlider(
    label: String,
    value: Int,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.width(18.dp), color = TubeDeckTextSecondary, fontSize = 9.sp)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f)
        )
        Text(
            value.toString(),
            modifier = Modifier.width(32.dp),
            color = TubeDeckTextSecondary,
            fontSize = 9.sp,
            textAlign = TextAlign.End
        )
    }
}

private fun withRgb(
    source: Long,
    red: Int = ((source shr 16) and 0xFF).toInt(),
    green: Int = ((source shr 8) and 0xFF).toInt(),
    blue: Int = (source and 0xFF).toInt()
): Long =
    0xFF000000L or
        ((red.coerceIn(0, 255).toLong()) shl 16) or
        ((green.coerceIn(0, 255).toLong()) shl 8) or
        blue.coerceIn(0, 255).toLong()

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
        Text(label, color = TubeDeckTextPrimary, fontSize = 14.sp)
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
