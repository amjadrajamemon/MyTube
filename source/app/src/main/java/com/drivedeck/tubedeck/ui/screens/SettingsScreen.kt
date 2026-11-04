package com.drivedeck.tubedeck.ui.screens

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.drivedeck.tubedeck.auth.GoogleAccountProfile
import com.drivedeck.tubedeck.auth.GoogleAccountSession
import com.drivedeck.tubedeck.auth.YoutubeAuthorizationState
import com.drivedeck.tubedeck.data.model.DownloadMode
import com.drivedeck.tubedeck.data.model.ViewingMode
import com.drivedeck.tubedeck.data.model.PlayerMode
import com.drivedeck.tubedeck.data.model.TubeDeckSettings
import com.drivedeck.tubedeck.player.YtDlpUpdateManager
import com.drivedeck.tubedeck.player.PlaybackDiagnostics
import com.drivedeck.tubedeck.ui.components.tvInputHighlight
import com.drivedeck.tubedeck.ui.components.RemoteInputTrainingDialog
import com.drivedeck.tubedeck.data.repository.TubeDeckRepository
import com.drivedeck.tubedeck.ui.theme.*
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    currentSettings: TubeDeckSettings,
    blockedAdsCount: Int,
    googleAccount: GoogleAccountProfile? = null,
    onGoogleAccountClick: () -> Unit = {},
    onPreviewSettings: (TubeDeckSettings) -> Unit,
    onSaveSettings: (TubeDeckSettings) -> Unit,
    onViewingPreferencesChanged: (ViewingMode, Int, Boolean) -> Unit = { _, _, _ -> },
    onResetAdBlockStats: () -> Unit,
    onClearWatchHistory: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val updateScope = rememberCoroutineScope()
    val ytDlpState by YtDlpUpdateManager.state.collectAsState()
    val googleAccountSession = remember { GoogleAccountSession.get(context) }
    val youtubeAuthStatus by googleAccountSession.status.collectAsState()
    LaunchedEffect(Unit) {
        YtDlpUpdateManager.scheduleWeekly(context)
        YtDlpUpdateManager.refreshState(context)
        YtDlpUpdateManager.refreshLatestVersion(context)
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
    
    var homeSuggestionsEnabled by remember { mutableStateOf(currentSettings.homeSuggestionsEnabled) }
    var homeHistoryEnabled by remember { mutableStateOf(currentSettings.homeHistoryEnabled) }
    var homeTrendingEnabled by remember { mutableStateOf(currentSettings.homeTrendingEnabled) }
    var autoRotatePlayback by remember { mutableStateOf(currentSettings.autoRotatePlayback) }
    var playerMode by remember { mutableStateOf(currentSettings.playerMode) }
    var xyAutoCompatibilityMode by remember { mutableStateOf(currentSettings.xyAutoCompatibilityMode) }
    var forceTvNavigation by remember { mutableStateOf(currentSettings.forceTvNavigation) }
    var preferredPlaybackQualityHeight by remember { mutableIntStateOf(currentSettings.preferredPlaybackQualityHeight) }
    var preferredPlaybackSpeed by remember { mutableFloatStateOf(currentSettings.preferredPlaybackSpeed.coerceIn(0.25f, 5.0f)) }
    var seekIntervalSeconds by remember { mutableIntStateOf(currentSettings.seekIntervalSeconds.coerceIn(5, 60)) }
    var viewingMode by remember { mutableStateOf(currentSettings.viewingMode) }
    var viewingDimPercent by remember { mutableIntStateOf(currentSettings.viewingDimPercent.coerceIn(10, 85)) }
    var nightAudioEnabled by remember { mutableStateOf(currentSettings.nightAudioEnabled) }
    
    var adBlockEnabled by remember { mutableStateOf(currentSettings.adBlockEnabled) }
    var blockVideoAds by remember { mutableStateOf(currentSettings.blockVideoAds) }
    var blockBannerAds by remember { mutableStateOf(currentSettings.blockBannerAds) }
    var blockTrackers by remember { mutableStateOf(currentSettings.blockTrackers) }
    var sponsorBlockEnabled by remember { mutableStateOf(currentSettings.sponsorBlockEnabled) }
    var blockShortsEnabled by remember { mutableStateOf(currentSettings.blockShortsEnabled) }
    var pipEnabled by remember { mutableStateOf(currentSettings.pipEnabled) }
    var autoLoadEpisodes by remember { mutableStateOf(currentSettings.autoLoadEpisodes) }
    var autoLoadUpNext by remember { mutableStateOf(currentSettings.autoLoadUpNext) }
    var sideBySideEpisodeFeeds by remember { mutableStateOf(currentSettings.sideBySideEpisodeFeeds) }
    var desktopYoutubeUiEnabled by remember { mutableStateOf(currentSettings.desktopYoutubeUiEnabled) }
    var playerControllerScale by remember {
        mutableFloatStateOf(currentSettings.playerControllerScale.coerceIn(0.85f, 1.50f))
    }
    var equalizerTextScale by remember {
        mutableFloatStateOf(currentSettings.equalizerTextScale.coerceIn(1.0f, 1.8f))
    }
    var showRelevantShelf by remember { mutableStateOf(currentSettings.showRelevantShelf) }
    var showEpisodesShelf by remember { mutableStateOf(currentSettings.showEpisodesShelf) }
    var floatingCardsMode by remember { mutableStateOf(currentSettings.floatingCardsMode) }
    var animatedCardPreviews by remember { mutableStateOf(currentSettings.animatedCardPreviews) }
    var hideWatchedRecommendations by remember { mutableStateOf(currentSettings.hideWatchedRecommendations) }
    var lowResourceMode by remember { mutableStateOf(currentSettings.lowResourceMode) }
    var resumeAfterInterruptions by remember { mutableStateOf(currentSettings.resumeAfterInterruptions) }
    var subtitleTextScale by remember { mutableFloatStateOf(currentSettings.subtitleTextScale.coerceIn(.8f, 1.8f)) }
    var subtitleBottomInsetDp by remember { mutableIntStateOf(currentSettings.subtitleBottomInsetDp.coerceIn(0, 120)) }
    var remoteNextKeyCode by remember { mutableIntStateOf(currentSettings.remoteNextKeyCode) }
    var remotePreviousKeyCode by remember { mutableIntStateOf(currentSettings.remotePreviousKeyCode) }
    var showRemoteTraining by remember { mutableStateOf(false) }
    val repository = remember(context) { TubeDeckRepository.get(context) }
    val privateSession by repository.privateSession.collectAsState()

    var appearancePreset by remember { mutableStateOf(DeckThemeCatalog.get(currentSettings.appearancePreset).name) }
    var fontScale by remember { mutableFloatStateOf(currentSettings.fontScale.coerceIn(0.80f, 1.35f)) }
    var themeAccentArgb by remember { mutableLongStateOf(currentSettings.themeAccentArgb) }
    var themeBackgroundArgb by remember { mutableLongStateOf(currentSettings.themeBackgroundArgb) }
    var themeTextArgb by remember { mutableLongStateOf(currentSettings.themeTextArgb) }
    var previewApplied by remember { mutableStateOf(false) }
    var showClearWatchHistoryConfirm by remember { mutableStateOf(false) }

    fun appearanceOnlySettings(): TubeDeckSettings =
        currentSettings.copy(
            appearancePreset = appearancePreset,
            fontScale = fontScale.coerceIn(0.80f, 1.35f),
            themeAccentArgb = themeAccentArgb,
            themeBackgroundArgb = themeBackgroundArgb,
            themeTextArgb = themeTextArgb
        )

    // Every UI-editable preference is merged into the latest repository snapshot.
    // No manual Save operation is required. The first composition is NOT a change.
    val autoSaveDraft = currentSettings.copy(
                            downloadDir = downloadDir,
                            downloadTreeUri = downloadTreeUri,
                            instantMode = instantMode,
                            instantQuality = instantQuality,
                            customMode = customMode,
                            customQuality = customQuality,
                            playlistMode = playlistMode,
                            playlistQuality = playlistQuality,
                            playerMode = playerMode,
                            xyAutoCompatibilityMode = xyAutoCompatibilityMode,
                            forceTvNavigation = forceTvNavigation,
                            preferredPlaybackQualityHeight = preferredPlaybackQualityHeight,
                            preferredPlaybackSpeed = preferredPlaybackSpeed,
                            seekIntervalSeconds = seekIntervalSeconds,
                            viewingMode = viewingMode,
                            viewingDimPercent = viewingDimPercent.coerceIn(10, 85),
                            nightAudioEnabled = nightAudioEnabled,
                            autoRotatePlayback = autoRotatePlayback,
                            homeSuggestionsEnabled = homeSuggestionsEnabled,
                            homeHistoryEnabled = homeHistoryEnabled,
                            homeTrendingEnabled = homeTrendingEnabled,
                            adBlockEnabled = adBlockEnabled,
                            blockVideoAds = blockVideoAds,
                            blockBannerAds = blockBannerAds,
                            blockTrackers = blockTrackers,
                            blockShortsEnabled = blockShortsEnabled,
                            sponsorBlockEnabled = sponsorBlockEnabled,
                            pipEnabled = pipEnabled,
                            autoLoadEpisodes = autoLoadEpisodes,
                            autoLoadUpNext = autoLoadUpNext,
                            sideBySideEpisodeFeeds = sideBySideEpisodeFeeds,
                            desktopYoutubeUiEnabled = desktopYoutubeUiEnabled,
                            playerControllerScale = playerControllerScale.coerceIn(0.85f, 1.50f),
                            equalizerTextScale = equalizerTextScale.coerceIn(1.0f, 1.8f),
                            showRelevantShelf = showRelevantShelf,
                            showEpisodesShelf = showEpisodesShelf,
                            floatingCardsMode = floatingCardsMode,
                            animatedCardPreviews = animatedCardPreviews,
                            hideWatchedRecommendations = hideWatchedRecommendations,
                            lowResourceMode = lowResourceMode,
                            resumeAfterInterruptions = resumeAfterInterruptions,
                            subtitleTextScale = subtitleTextScale,
                            subtitleBottomInsetDp = subtitleBottomInsetDp,
                            remoteNextKeyCode = remoteNextKeyCode,
                            remotePreviousKeyCode = remotePreviousKeyCode,
                            appearancePreset = appearancePreset,
                            fontScale = fontScale.coerceIn(0.80f, 1.35f),
                            themeAccentArgb = themeAccentArgb,
                            themeBackgroundArgb = themeBackgroundArgb,
                            themeTextArgb = themeTextArgb
                        )
    var lastAutoSaveDraft by remember { mutableStateOf(autoSaveDraft) }
    LaunchedEffect(autoSaveDraft, folderError) {
        if (folderError == null && autoSaveDraft != lastAutoSaveDraft) {
            lastAutoSaveDraft = autoSaveDraft
            onSaveSettings(autoSaveDraft)
        }
    }

    val qualities = listOf("Best", "4320p", "2160p", "1440p", "1080p", "720p", "480p", "360p", "240p", "144p")

    if (showClearWatchHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearWatchHistoryConfirm = false },
            icon = {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Clear watch history?") },
            text = {
                Text(
                    "This clears MyTube's local watch history and resets the Relevant feed. " +
                        "Downloads, saved files, searches and your Google/YouTube account stay intact."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearWatchHistoryConfirm = false
                        onClearWatchHistory()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Clear & refresh")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearWatchHistoryConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRemoteTraining) {
        RemoteInputTrainingDialog(
            nextCode = remoteNextKeyCode,
            previousCode = remotePreviousKeyCode,
            onNextCode = { remoteNextKeyCode = it },
            onPreviousCode = { remotePreviousKeyCode = it },
            onDismiss = { showRemoteTraining = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TubeDeckBackground)
            .deckScreenBackground()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
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
                    appearancePreset,
                    color = TubeDeckTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
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
                                    .height(166.dp)
                                    .clip(RoundedCornerShape(preset.cornerRadiusDp.dp))
                                    .tvInputHighlight(RoundedCornerShape(10.dp))
                                    .clickable {
                                        appearancePreset = preset.name
                                        fontScale = preset.fontScale
                                        themeAccentArgb = preset.accentArgb
                                        themeBackgroundArgb = preset.backgroundArgb
                                        themeTextArgb = preset.textArgb
                                        previewApplied = false
                                    }
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) Color(preset.accentArgb.toInt()) else TubeDeckBorder,
                                        shape = RoundedCornerShape(preset.cornerRadiusDp.dp)
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
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = preset.fontFamily
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    ThemeLayoutPreview(preset)
                                    Text(
                                        preset.subtitle.ifBlank { preset.homePresentation.name.replace('_', ' ') },
                                        color = Color(preset.textArgb.toInt()).copy(alpha = .78f),
                                        fontFamily = preset.fontFamily,
                                        fontSize = 9.sp,
                                        lineHeight = 11.sp,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "PLAYER  ·  " + preset.playerChrome.name.replace('_', ' '),
                                        color = Color(preset.accentArgb.toInt()),
                                        fontSize = 8.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )

                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "Text size · ${(fontScale * 100).toInt()}%",
                    color = TubeDeckTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = fontScale,
                    onValueChange = {
                        fontScale = it.coerceIn(0.80f, 1.35f)
                        // Keep the selected layout/font preset when the user fine-tunes text size.
                        previewApplied = false
                    },
                    valueRange = 0.80f..1.35f,
                    steps = 10
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Smaller", color = TubeDeckTextSecondary, fontSize = 10.sp)
                    Text("Normal", color = TubeDeckTextSecondary, fontSize = 10.sp)
                    Text("Larger", color = TubeDeckTextSecondary, fontSize = 10.sp)
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            appearancePreset = "Kodi Arctic"
                            fontScale = 1.0f
                            themeAccentArgb = 0xFF00D2FFL
                            themeBackgroundArgb = 0xFF080B11L
                            themeTextArgb = 0xFFFFFFFFL
                            previewApplied = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset appearance")
                    }
                    Text(
                        "Theme preview and changes apply automatically.",
                        color = TubeDeckTextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            SettingsCard(title = "App interface") {
                ToggleRow("Private session · don't save new watches or searches", privateSession) {
                    repository.setPrivateSession(it)
                }
                Text(
                    "Private session lasts until the application process closes. Existing history isn't deleted, and downloads and account activity are not anonymized.",
                    color = TubeDeckTextSecondary, fontSize = 11.sp
                )
                HorizontalDivider()
                ToggleRow("Force TV remote mode (fallback)", forceTvNavigation) {
                    forceTvNavigation = it
                }
                TextButton(onClick = { showRemoteTraining = true }) {
                    Icon(Icons.Default.Keyboard, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Learn TV remote buttons and keyboard keys")
                }
                ToggleRow("YouTube desktop interface", desktopYoutubeUiEnabled) { desktopYoutubeUiEnabled = it }
                Text(
                    "Use YouTube's desktop Home, search and watch pages with Standard and Theater views. Downloads and MyTube settings remain accessible. YouTube may restrict Google sign-in inside WebView.",
                    color = TubeDeckTextSecondary,
                    fontSize = 11.sp
                )
            }

            // Player Mode
            SettingsCard(title = "Default Media Player") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerMode.values().forEach { mode ->
                        val isSelected = playerMode == mode
                        Surface(
                            modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp))
                                .tvInputHighlight(RoundedCornerShape(10.dp))
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

            SettingsCard(title = "Car player compatibility") {
                ToggleRow("Force XY AUTO compatibility (fallback)", xyAutoCompatibilityMode) { xyAutoCompatibilityMode = it }
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("MyTube playback report", PlaybackDiagnostics.report(context)))
                    Toast.makeText(context, "Playback report copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy playback report") }
            }

            SettingsCard(title = "Google / YouTube") {
                val statusLabel = when (youtubeAuthStatus.state) {
                    YoutubeAuthorizationState.SIGNED_OUT -> "Signed out"
                    YoutubeAuthorizationState.CONNECTING -> "Connecting…"
                    YoutubeAuthorizationState.CONNECTED -> when {
                        youtubeAuthStatus.youtubeAuthorized ->
                            googleAccount?.displayName?.ifBlank { "Google + YouTube connected" }
                                ?: "Google + YouTube connected"
                        else ->
                            googleAccount?.displayName?.ifBlank { "Google signed in" }
                                ?: "Google signed in"
                    }
                    YoutubeAuthorizationState.CONFIGURATION_REQUIRED -> "Setup required"
                    YoutubeAuthorizationState.ERROR -> "Error"
                }
                Text(
                    statusLabel,
                    color = if (youtubeAuthStatus.state == YoutubeAuthorizationState.CONNECTED) {
                        TubeDeckAccentMint
                    } else if (
                        youtubeAuthStatus.state == YoutubeAuthorizationState.CONFIGURATION_REQUIRED ||
                        youtubeAuthStatus.state == YoutubeAuthorizationState.ERROR
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        TubeDeckTextPrimary
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                )
                youtubeAuthStatus.message.takeIf { it.isNotBlank() }?.let { message ->
                    Spacer(Modifier.height(3.dp))
                    Text(
                        message,
                        color = if (youtubeAuthStatus.state == YoutubeAuthorizationState.CONNECTED) {
                            TubeDeckTextPrimary.copy(alpha = .72f)
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        fontSize = 10.5.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onGoogleAccountClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TubeDeckAccentCyan,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(when {
                        youtubeAuthStatus.state == YoutubeAuthorizationState.CONNECTING -> "Cancel sign-in"
                        youtubeAuthStatus.state == YoutubeAuthorizationState.ERROR ||
                            youtubeAuthStatus.state == YoutubeAuthorizationState.CONFIGURATION_REQUIRED -> "Retry sign-in"
                        googleAccount == null -> "Sign in with Google"
                        !youtubeAuthStatus.youtubeAuthorized -> "Connect YouTube"
                        else -> "Google account"
                    })
                }
                if (googleAccountSession.isDeviceCodeConfigured() && !youtubeAuthStatus.youtubeAuthorized) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { googleAccountSession.startDeviceCodeAuthorization() },
                        enabled = youtubeAuthStatus.state != YoutubeAuthorizationState.CONNECTING,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sign in with code")
                    }
                }
            }

            SettingsCard(title = "Download Engine") {
                val installedVersion = ytDlpState.version ?: "—"
                val latestVersion = ytDlpState.latestVersion ?: "Checking…"
                Text(
                    "Installed: $installedVersion   Latest: $latestVersion",
                    color = TubeDeckTextSecondary,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(6.dp))
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
                        Text("Update")
                    }
                }
                if (ytDlpState.lastCheckedAt > 0L) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Checked: ${ytDlpState.lastCheckedLabel}",
                        color = TubeDeckTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            SettingsCard(title = "Download folder") {
                Text(
                    "Selected: ${chosenFolderName ?: if (downloadTreeUri != null) "Selected folder" else "MyTube app storage (default)"}",
                    color = Color.White,
                    fontSize = 12.sp
                )
                if (downloadTreeUri == null) {
                    Text(
                        "Downloads work without selecting a folder. Files can be moved or copied from MyTube's Downloads screen; uninstalling may remove files in app storage.",
                        color = TubeDeckTextSecondary,
                        fontSize = 11.sp
                    )
                }
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

            SettingsCard(title = "Viewing & Night Audio") {
                Text("Viewing mode", color = TubeDeckTextPrimary, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    ViewingMode.values().forEach { mode ->
                        val selected = viewingMode == mode
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .tvInputHighlight(RoundedCornerShape(10.dp))
                                .clickable {
                                    viewingMode = mode
                                    onViewingPreferencesChanged(mode, viewingDimPercent, nightAudioEnabled)
                                }
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) TubeDeckAccentCyan else TubeDeckBorder,
                                    RoundedCornerShape(10.dp)
                                ).padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (selected) Icons.Default.RadioButtonChecked
                                else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (selected) TubeDeckAccentCyan else TubeDeckTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                mode.label,
                                color = if (selected) TubeDeckAccentCyan else TubeDeckTextPrimary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                if (viewingMode == ViewingMode.LIGHTS_OUT || viewingMode == ViewingMode.NIGHT) {
                    Text("Video dimming · $viewingDimPercent%", color = TubeDeckTextPrimary)
                    Slider(
                        value = viewingDimPercent.toFloat(),
                        onValueChange = {
                            viewingDimPercent = it.toInt().coerceIn(10, 85)
                            onViewingPreferencesChanged(viewingMode, viewingDimPercent, nightAudioEnabled)
                        },
                        valueRange = 10f..85f
                    )
                }
                Text(
                    "Selections apply immediately. Cinema focuses the player, Lights Out dims video and Night adds a warm tint. Full Blackout stays separate.",
                    color = TubeDeckTextSecondary, fontSize = 11.sp
                )
                HorizontalDivider()
                ToggleRow("Night Audio · soften peaks and lift quiet speech", nightAudioEnabled) {
                    nightAudioEnabled = it
                    onViewingPreferencesChanged(viewingMode, viewingDimPercent, it)
                }
                Text(
                    "Native player only. Uses session-scoped Android compression on supported devices; never overrides system volume. May be unavailable on car units or some TV firmware.",
                    color = TubeDeckTextSecondary, fontSize = 11.sp
                )
            }

            SettingsCard(title = "Playback") {
                ToggleRow("Auto-rotate video playback", autoRotatePlayback) { autoRotatePlayback = it }
                HorizontalDivider()
                ToggleRow("Auto-load Episodes", autoLoadEpisodes) { autoLoadEpisodes = it }
                ToggleRow("Auto-load Up Next", autoLoadUpNext) { autoLoadUpNext = it }
                ToggleRow("Portrait feeds · Side by side", sideBySideEpisodeFeeds) { sideBySideEpisodeFeeds = it }
                HorizontalDivider()

                ToggleRow("Swipe shelf · Relevant row", showRelevantShelf) { showRelevantShelf = it }
                ToggleRow("Swipe shelf · Episodes row", showEpisodesShelf) { showEpisodesShelf = it }
                ToggleRow("Hide played videos from recommendation cards", hideWatchedRecommendations) {
                    hideWatchedRecommendations = it
                }
                Text("Floating recommendations", color = TubeDeckTextPrimary, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("Off", "Manual", "Last 15 seconds").forEach { mode ->
                        FilterChip(
                            selected = floatingCardsMode == mode,
                            onClick = { floatingCardsMode = mode },
                            label = { Text(mode, fontSize = 10.sp) }
                        )
                    }
                }
                Text(
                    "Manual keeps the screen uncluttered. The 15-second mode opens suggestions only near the end; dismissing them keeps them hidden for that video.",
                    color = TubeDeckTextSecondary, fontSize = 11.sp
                )
                ToggleRow(
                    "Animated thumbnail previews when a related card stays focused",
                    animatedCardPreviews
                ) { animatedCardPreviews = it }
                Text(
                    "Optional: tries YouTube's short animated WebP thumbnail after 1.1 seconds of focus. Uses no second video player; falls back to the static image. Disabled in Low Resource Mode or on older Android.",
                    color = TubeDeckTextSecondary, fontSize = 11.sp
                )
                ToggleRow("Ask to resume saved position after a restart or interruption", resumeAfterInterruptions) {
                    resumeAfterInterruptions = it
                }
                ToggleRow("Low Resource Mode · limit buffers and card prefetch", lowResourceMode) {
                    lowResourceMode = it
                }
                Text(
                    "Useful on low-RAM Android TV boxes and car units; manually selected quality remains available.",
                    color = TubeDeckTextSecondary, fontSize = 11.sp
                )
                HorizontalDivider()
                Text("Subtitle size · ${(subtitleTextScale * 100).toInt()}%", color = TubeDeckTextPrimary)
                Slider(
                    value = subtitleTextScale, onValueChange = { subtitleTextScale = it },
                    valueRange = .8f..1.8f, steps = 9
                )
                Text("Subtitle bottom clearance · ${subtitleBottomInsetDp} dp", color = TubeDeckTextPrimary)
                Slider(
                    value = subtitleBottomInsetDp.toFloat(),
                    onValueChange = { subtitleBottomInsetDp = it.toInt() },
                    valueRange = 0f..120f, steps = 11
                )
                HorizontalDivider()

                Text(
                    "Controller size · ${(playerControllerScale * 100).toInt()}%",
                    color = TubeDeckTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = playerControllerScale,
                    onValueChange = { playerControllerScale = it.coerceIn(0.85f, 1.50f) },
                    valueRange = 0.85f..1.50f,
                    steps = 12
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Compact", color = TubeDeckTextSecondary, fontSize = 10.sp)
                    Text("Default", color = TubeDeckTextSecondary, fontSize = 10.sp)
                    Text("Large", color = TubeDeckTextSecondary, fontSize = 10.sp)
                }
                HorizontalDivider()

                Text(
                    "Equalizer text size · ${(equalizerTextScale * 100).toInt()}%",
                    color = TubeDeckTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = equalizerTextScale,
                    onValueChange = { equalizerTextScale = it.coerceIn(1.0f, 1.8f) },
                    valueRange = 1.0f..1.8f,
                    steps = 7
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Normal", color = TubeDeckTextSecondary, fontSize = 10.sp)
                    Text("Large", color = TubeDeckTextSecondary, fontSize = 10.sp)
                    Text("Extra large", color = TubeDeckTextSecondary, fontSize = 10.sp)
                }
                HorizontalDivider()

                Text("Remembered quality", color = TubeDeckTextPrimary, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0, 360, 480, 720, 1080, 1440, 2160).forEach { height ->
                        FilterChip(
                            selected = preferredPlaybackQualityHeight == height,
                            onClick = { preferredPlaybackQualityHeight = height },
                            label = { Text(if (height == 0) "Auto" else "${height}p") }
                        )
                    }
                }

                Text("Remembered playback speed", color = TubeDeckTextPrimary, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 3.5f, 4f, 4.5f, 5f).forEach { speed ->
                        FilterChip(
                            selected = kotlin.math.abs(preferredPlaybackSpeed - speed) < 0.01f,
                            onClick = { preferredPlaybackSpeed = speed },
                            label = { Text("${speed}x") }
                        )
                    }
                }

                Text("Seek interval", color = TubeDeckTextPrimary, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5, 10, 15, 30, 60).forEach { seconds ->
                        FilterChip(
                            selected = seekIntervalSeconds == seconds,
                            onClick = { seekIntervalSeconds = seconds },
                            label = { Text("${seconds}s") }
                        )
                    }
                }
            }

            SettingsCard(title = "Home layout") {
                ToggleRow("Relevant", homeSuggestionsEnabled) { homeSuggestionsEnabled = it }
                ToggleRow("History", homeHistoryEnabled) { homeHistoryEnabled = it }
                ToggleRow("Trending", homeTrendingEnabled) { homeTrendingEnabled = it }
                HorizontalDivider()
                Text(
                    "Reset the local viewing profile used by MyTube to shape Relevant videos.",
                    color = TubeDeckTextSecondary,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showClearWatchHistoryConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Clear watch history & reset Relevant")
                }
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

            Text(
                "Settings are saved and applied automatically when changed. Close this screen at any time.",
                color = TubeDeckTextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}


/**
 * Actual diagram of a theme's Home architecture, not a colored title card
 * or marketing copy. Keeps the settings selector compact and scannable.
 */
@Composable
private fun ThemeLayoutPreview(profile: DeckThemeProfile) {
    val ink = Color(profile.textArgb.toInt())
    val accent = Color(profile.accentArgb.toInt())
    val panel = ink.copy(alpha = 0.12f)
    @Composable
    fun Tile(width: androidx.compose.ui.Modifier, height: androidx.compose.ui.unit.Dp) {
        Box(width.height(height).clip(RoundedCornerShape(3.dp)).background(panel))
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (profile.navigationPlacement == DeckNavigationPlacement.TOP) {
            Box(Modifier.fillMaxWidth().height(6.dp).background(accent.copy(alpha = .8f)))
        }
        Row(Modifier.fillMaxWidth().height(57.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (profile.navigationPlacement == DeckNavigationPlacement.SIDE) {
                Column(Modifier.width(12.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(4) {
                        Box(Modifier.fillMaxWidth().height(8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (it == 0) accent else panel))
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (profile.homePresentation) {
                    DeckHomePresentation.THREE_COLUMN -> Row(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        repeat(3) { Box(Modifier.weight(1f).height(50.dp).background(panel)) }
                    }
                    DeckHomePresentation.DESKTOP_TABLE, DeckHomePresentation.AMOLED_INDEX,
                    DeckHomePresentation.YOUTUBE_STANDARD -> repeat(3) {
                        Box(Modifier.fillMaxWidth().height(if (it == 0) 20.dp else 12.dp)
                            .background(if (it == 0) accent.copy(alpha = .35f) else panel))
                    }
                    DeckHomePresentation.YOUTUBE_DESKTOP, DeckHomePresentation.ARCADE_GALLERY -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(3) { Tile(Modifier.weight(1f), 24.dp) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(3) { Tile(Modifier.weight(1f), 21.dp) }
                        }
                    }
                    DeckHomePresentation.CINEMA_STRIPS, DeckHomePresentation.GLASS_CAROUSEL,
                    DeckHomePresentation.MINIMAL_EDITORIAL -> {
                        Box(Modifier.fillMaxWidth().height(29.dp)
                            .background(accent.copy(alpha = .25f)))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(3) { Tile(Modifier.weight(1f), 21.dp) }
                        }
                    }
                    DeckHomePresentation.HUD_CONSOLE, DeckHomePresentation.IPTV_DASHBOARD -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Tile(Modifier.weight(2f), 48.dp)
                            Tile(Modifier.weight(1f), 48.dp)
                        }
                    }
                    else -> repeat(2) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(3) { Tile(Modifier.weight(1f), 24.dp) }
                        }
                    }
                }
            }
        }
        if (profile.navigationPlacement == DeckNavigationPlacement.BOTTOM) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(5) {
                    Box(Modifier.weight(1f).height(5.dp)
                        .background(if (it == 0) accent else panel))
                }
            }
        }
    }
}

private val appearancePresets = DeckThemeCatalog.presets

@Composable
private fun ModeSelector(selectedMode: DownloadMode, onSelect: (DownloadMode) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DownloadMode.values().forEach { mode ->
            val isSelected = selectedMode == mode
            Surface(
                modifier = Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(8.dp))
                    .tvInputHighlight(RoundedCornerShape(8.dp))
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
                            .tvInputHighlight(RoundedCornerShape(8.dp))
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


