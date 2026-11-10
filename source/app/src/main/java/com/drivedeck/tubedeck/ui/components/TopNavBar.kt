package com.drivedeck.tubedeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.content.res.Configuration
import com.drivedeck.tubedeck.player.CarHeadUnitPolicy
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivedeck.tubedeck.ui.theme.*
import com.drivedeck.tubedeck.auth.GoogleAccountProfile

/**
 * Browser navigation never mounts an editable search box until explicitly
 * requested. The fixed row leaves the web page readable in both orientations.
 */
@Composable
fun TopNavBar(
    currentUrl: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    activeDownloadCount: Int,
    blockedAdsCount: Int,
    isAdBlockActive: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onReload: () -> Unit,
    onNavigate: (String) -> Unit,
    onOpenShield: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenOffline: () -> Unit,
    googleAccount: GoogleAccountProfile? = null,
    onGoogleAccountClick: () -> Unit = {},
    recentSearches: List<String> = emptyList(),
    onRemoveRecentSearch: (String) -> Unit = {},
    onClearRecentSearches: () -> Unit = {},
    modifier: Modifier = Modifier,
    includeStatusBarInset: Boolean = true
) {
    var queryText by remember(currentUrl) { mutableStateOf(currentUrl) }
    var searchDialogMode by remember { mutableStateOf<String?>(null) }
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val focusManager = LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(configuration.orientation) {
        searchDialogMode = null
        keyboard?.hide()
        focusManager.clearFocus(force = true)
    }
    Surface(
        modifier = modifier.fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    if (includeStatusBarInset) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Horizontal
                )
            ),
        color = TubeDeckSurface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 5.dp, vertical = 3.dp)
                .heightIn(min = 46.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            IconButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.size(38.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                    tint = if (canGoBack) TubeDeckTextPrimary else Color.DarkGray)
            }
            IconButton(onClick = onForward, enabled = canGoForward, modifier = Modifier.size(38.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward",
                    tint = if (canGoForward) TubeDeckTextPrimary else Color.DarkGray)
            }
            val shape = RoundedCornerShape(13.dp)
            Surface(
                modifier = Modifier.weight(1f).height(42.dp)
                    .tvInputHighlight(shape)
                    .clickable { searchDialogMode = "search" },
                color = TubeDeckSurfaceVariant, shape = shape
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(Icons.Default.Search, null, tint = TubeDeckAccentCyan,
                        modifier = Modifier.size(18.dp))
                    Text(
                        currentUrl.ifBlank { "Search or enter URL" },
                        fontSize = 13.sp, color = TubeDeckTextSecondary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            if (recentSearches.isNotEmpty() && !landscape) {
                IconButton(onClick = { searchDialogMode = "history" }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.History, "Recent searches", tint = TubeDeckTextPrimary)
                }
            }
            IconButton(onClick = onReload, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Refresh, "Reload", tint = TubeDeckTextPrimary)
            }
            if (!landscape) {
                GoogleAccountButton(
                    account = googleAccount,
                    onClick = onGoogleAccountClick,
                    showLabelWhenSignedOut = false
                )
            }
        }
    }
    if (searchDialogMode != null) {
        CompactSearchPanel(
            query = queryText,
            onQueryChange = { queryText = it },
            onSubmit = onNavigate,
            recentSearches = recentSearches,
            onRemoveRecentSearch = onRemoveRecentSearch,
            onClearRecentSearches = onClearRecentSearches,
            onDismiss = { searchDialogMode = null },
            placeholder = "Search or enter HTTPS URL",
            startWithKeyboard = searchDialogMode == "search",
            historyFirst = searchDialogMode == "history"
        )
    }
}
