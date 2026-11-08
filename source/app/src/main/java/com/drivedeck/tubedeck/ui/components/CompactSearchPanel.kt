package com.drivedeck.tubedeck.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
    startWithKeyboard: Boolean = false
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

    LaunchedEffect(clean) {
        remoteSuggestions = emptyList()
        if (clean.isNotBlank() && !SearchAssistPolicy.isUrl(clean)) {
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
    val options = if (clean.isBlank()) recentSearches.take(6)
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
    LaunchedEffect(startWithKeyboard) {
        // This effect can run only after an explicit search-icon action creates
        // the dialog, never at application launch.
        if (startWithKeyboard) {
            searchRequester.requestFocus()
            keyboard?.show()
        }
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
                    onValueChange = onQueryChange,
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
                // Nothing is permanently inserted between Home and its feed.
                // Collapse the history entirely while an IME consumes a short
                // landscape viewport. Retain a small scrollable list otherwise.
                if (visibleOptions.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (clean.isBlank()) "Recent searches" else "Suggestions",
                            color = TubeDeckTextSecondary, fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (clean.isBlank() && recentSearches.isNotEmpty()) {
                            TextButton(onClick = onClearRecentSearches) {
                                Text("Clear", fontSize = 12.sp)
                            }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(
                            max = if (landscape) 138.dp else 195.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        itemsIndexed(visibleOptions, key = { _, term -> term }) { _, term ->
                            val isRecent = recentSearches.any { it.equals(term, true) }
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .tvInputHighlight(RoundedCornerShape(10.dp))
                                    .clickable { submit(term) }
                                    .focusable()
                                    .heightIn(min = 42.dp)
                                    .padding(start = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isRecent) Icons.Default.History else Icons.Default.Search,
                                    null,
                                    modifier = Modifier.size(18.dp),
                                    tint = TubeDeckTextSecondary
                                )
                                Text(
                                    term,
                                    color = TubeDeckTextPrimary,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                                )
                                if (isRecent) {
                                    IconButton(
                                        onClick = { onRemoveRecentSearch(term) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Close, "Remove $term")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
