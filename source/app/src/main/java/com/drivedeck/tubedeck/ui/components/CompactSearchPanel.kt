package com.drivedeck.tubedeck.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.drivedeck.tubedeck.data.repository.SearchAssistPolicy
import com.drivedeck.tubedeck.data.repository.YoutubeSearchSuggestions
import com.drivedeck.tubedeck.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Search is deliberately NOT part of the Home content column. Only an explicit
 * tap/remote action creates this dialog (and its focusable text field). A phone
 * keyboard can still consume the Android window, but it cannot be opened by
 * restoring focus to a permanently mounted Home search field.
 */
internal object CompactSearchOverlayPolicy {
    fun suggestionCount(isLandscape: Boolean, imeVisible: Boolean): Int = when {
        isLandscape && imeVisible -> 0
        imeVisible -> 2
        isLandscape -> 3
        else -> 4
    }

    fun panelMaxWidthDp(screenWidthDp: Int): Int =
        (screenWidthDp - 24).coerceIn(240, 700)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    recentSearches: List<String>,
    onRemoveRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onDismiss: () -> Unit,
    placeholder: String,
    startWithKeyboard: Boolean = false,
    historyFirst: Boolean = false
) {
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val searchRequester = remember { FocusRequester() }
    val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val limit = CompactSearchOverlayPolicy.suggestionCount(landscape, imeVisible)
    val clean = SearchAssistPolicy.clean(query)
    var remoteSuggestions by remember { mutableStateOf(emptyList<String>()) }
    var editedSinceOpen by remember { mutableStateOf(false) }

    LaunchedEffect(clean, historyFirst, editedSinceOpen) {
        remoteSuggestions = emptyList()
        // Do not prefetch online suggestions when opening history alone.
        if (!(historyFirst && !editedSinceOpen) &&
            clean.isNotBlank() && !SearchAssistPolicy.isUrl(clean)) {
            delay(300)
            try {
                remoteSuggestions = YoutubeSearchSuggestions.fetch(clean)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // Local search history still works without online suggestions.
            }
        }
    }
    // The History action always opens real history, even if the last search
    // query was remembered from a prior session.
    val showingHistory = (historyFirst && !editedSinceOpen) || clean.isBlank()
    val options = if (showingHistory) recentSearches.take(6)
        else SearchAssistPolicy.merge(clean, recentSearches, remoteSuggestions)
    val visibleOptions = options.take(limit)
    fun submit(term: String) {
        val chosen = SearchAssistPolicy.clean(term)
        if (chosen.isBlank()) return
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        onSubmit(chosen)
        onDismiss()
    }
    Dialog(
        onDismissRequest = {
            keyboard?.hide()
            focusManager.clearFocus(force = true)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = CompactSearchOverlayPolicy.panelMaxWidthDp(config.screenWidthDp).dp)
                .fillMaxWidth()
                // An OEM landscape IME must not let this overlay occupy the Home viewport.
                .heightIn(max = (config.screenHeightDp * (if (landscape) 0.56f else 0.72f)).dp)
                .padding(horizontal = 12.dp),
            color = TubeDeckSurface,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 14.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Focus only after this dialog's own TextField is attached.
                // The delay also helps OEM head-unit IMEs initialize correctly.
                LaunchedEffect(startWithKeyboard) {
                    if (startWithKeyboard) {
                        delay(70L)
                        searchRequester.requestFocus()
                        keyboard?.show()
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Search", color = TubeDeckTextPrimary, fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            keyboard?.hide()
                            focusManager.clearFocus(force = true)
                            onDismiss()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close search")
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        editedSinceOpen = true
                        onQueryChange(it)
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(searchRequester),
                    placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (query.isNotBlank()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Default.Close, "Clear input")
                                }
                            }
                            IconButton(
                                onClick = { submit(query) },
                                enabled = query.isNotBlank()
                            ) {
                                Icon(Icons.Default.Search, "Run search")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit(query) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TubeDeckTextPrimary,
                        unfocusedTextColor = TubeDeckTextPrimary,
                        focusedContainerColor = TubeDeckSurfaceVariant,
                        unfocusedContainerColor = TubeDeckSurfaceVariant,
                        focusedBorderColor = TubeDeckAccentCyan,
                        cursorColor = TubeDeckAccentCyan
                    )
                )
                // One-line, comma-separated recent searches. Scrolling is
                // horizontal, never an expanding list above the Home feed.
                if (showingHistory && recentSearches.isNotEmpty() &&
                    !(landscape && imeVisible)) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 42.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.History, contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = TubeDeckTextSecondary
                        )
                        Text(
                            "Recent:", color = TubeDeckTextSecondary,
                            fontSize = 12.sp, maxLines = 1
                        )
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            itemsIndexed(
                                recentSearches.distinct().take(12),
                                key = { _, term -> term }
                            ) { index, term ->
                                if (index > 0) {
                                    Text(
                                        ", ", color = TubeDeckTextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                                Text(
                                    text = term,
                                    fontSize = 13.sp,
                                    color = TubeDeckTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .widthIn(max = 172.dp)
                                        .tvInputHighlight(RoundedCornerShape(5.dp))
                                        .semantics {
                                            contentDescription =
                                                "Search $term. Long press to remove from history"
                                        }
                                        .combinedClickable(
                                            onClick = { submit(term) },
                                            onLongClick = { onRemoveRecentSearch(term) }
                                        )
                                        .padding(horizontal = 2.dp, vertical = 8.dp)
                                )
                            }
                        }
                        TextButton(
                            onClick = onClearRecentSearches,
                            contentPadding = PaddingValues(horizontal = 5.dp)
                        ) { Text("Clear", fontSize = 12.sp) }
                    }
                } else if (!showingHistory && visibleOptions.isNotEmpty()) {
                    // Typed suggestions remain bounded and scrollable. They are
                    // hidden while the landscape keyboard consumes the viewport.
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(max = if (landscape) 126.dp else 144.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        items(visibleOptions, key = { it }) { term ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .tvInputHighlight(RoundedCornerShape(8.dp))
                                    .clickable { submit(term) }
                                    .focusable()
                                    .heightIn(min = 40.dp)
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Search, null,
                                    modifier = Modifier.size(17.dp),
                                    tint = TubeDeckTextSecondary
                                )
                                Text(
                                    term,
                                    color = TubeDeckTextPrimary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
