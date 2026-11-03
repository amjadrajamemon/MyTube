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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivedeck.tubedeck.ui.theme.*
import com.drivedeck.tubedeck.auth.GoogleAccountProfile

@OptIn(ExperimentalMaterial3Api::class)
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
    val focusManager = LocalFocusManager.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (includeStatusBarInset) Modifier.statusBarsPadding() else Modifier),
        color = TubeDeckSurface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                // ALL ICONS/TITLES REMOVED FROM TOP LEFT as requested. 
                // Only Nav controls on the right.

                Row(verticalAlignment = Alignment.CenterVertically) {
                    GoogleAccountButton(
                        account = googleAccount,
                        onClick = onGoogleAccountClick,
                        showLabelWhenSignedOut = true
                    )
                    IconButton(onClick = onBack, enabled = canGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = if (canGoBack) TubeDeckTextPrimary else Color.DarkGray)
                    }
                    IconButton(onClick = onForward, enabled = canGoForward) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward", tint = if (canGoForward) TubeDeckTextPrimary else Color.DarkGray)
                    }
                    IconButton(onClick = onReload) {
                        Icon(Icons.Default.Refresh, "Reload", tint = TubeDeckTextPrimary)
                    }
                }
            }

            // High Visibility Search Bar - Unified Design
            SearchAssistField(
                query = queryText,
                onQueryChange = { queryText = it },
                onSearch = onNavigate,
                recentSearches = recentSearches,
                onRemoveRecentSearch = onRemoveRecentSearch,
                onClearRecentSearches = onClearRecentSearches,
                placeholder = "Search or enter HTTPS URL...",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )
        }
    }
}

