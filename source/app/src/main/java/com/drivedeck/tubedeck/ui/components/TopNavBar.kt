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
    modifier: Modifier = Modifier
) {
    var queryText by remember(currentUrl) { mutableStateOf(currentUrl) }
    val focusManager = LocalFocusManager.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),
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
                    GoogleAccountButton(account = googleAccount, onClick = onGoogleAccountClick)
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
            OutlinedTextField(
                value = queryText,
                onValueChange = { queryText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(56.dp),
                placeholder = { Text("Search or enter HTTPS URL...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TubeDeckAccentCyan) },
                trailingIcon = {
                    if (queryText.isNotEmpty()) {
                        IconButton(onClick = { queryText = "" }) {
                            Icon(Icons.Default.Close, null, tint = Color.Gray)
                        }
                    }
                },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = TubeDeckTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    focusManager.clearFocus()
                    onNavigate(queryText)
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TubeDeckTextPrimary,
                    unfocusedTextColor = TubeDeckTextPrimary,
                    focusedBorderColor = TubeDeckAccentCyan,
                    unfocusedBorderColor = TubeDeckBorder,
                    focusedContainerColor = Color.Black,
                    unfocusedContainerColor = Color.Black,
                    cursorColor = TubeDeckAccentCyan
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}
