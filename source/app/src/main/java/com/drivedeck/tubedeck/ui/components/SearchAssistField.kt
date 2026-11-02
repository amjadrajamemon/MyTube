package com.drivedeck.tubedeck.ui.components

import android.view.KeyEvent as AndroidKeyEvent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.drivedeck.tubedeck.data.repository.SearchAssistPolicy
import com.drivedeck.tubedeck.data.repository.YoutubeSearchSuggestions
import com.drivedeck.tubedeck.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

@Composable
fun SearchAssistField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    recentSearches: List<String>,
    onRemoveRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    placeholder: String = "Search YouTube",
    showRecentChips: Boolean = false,
    autoFocus: Boolean = false,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    var hasSearchFocus by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val firstSuggestionFocusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val tvMode = remember(context) { isTvLikeNavigationDevice(context) }
    var remote by remember { mutableStateOf(emptyList<String>()) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clean = SearchAssistPolicy.clean(query)

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            searchFocusRequester.requestFocus()
            // The TV's IME is available for remotes; a physical keyboard can type directly.
        }
    }

    LaunchedEffect(clean, hasSearchFocus) {
        remote = emptyList()
        if (hasSearchFocus && clean.isNotBlank() && !SearchAssistPolicy.isUrl(clean)) {
            delay(300L)
            try {
                val fetched = YoutubeSearchSuggestions.fetch(clean)
                ensureActive()
                remote = fetched
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // Local history remains usable offline or when suggestions are unavailable.
            }
        }
    }
    fun submit(term: String) {
        if (term.isBlank()) return
        onQueryChange(term)
        keyboard?.hide()
        focusManager.clearFocus()
        onSearch(term)
    }
    val choices = SearchAssistPolicy.merge(clean, recentSearches, remote)
    Column(modifier.onFocusChanged { hasSearchFocus = it.hasFocus }) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth()
                .focusRequester(searchFocusRequester)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && focused && choices.isNotEmpty() &&
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN
                    ) {
                        firstSuggestionFocusRequester.requestFocus()
                        true
                    } else false
                },
            placeholder = { Text(placeholder, fontSize = if (tvMode) 19.sp else 15.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Row {
                    if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, "Clear search")
                    }
                    IconButton(onClick = { submit(query) }, enabled = query.isNotBlank()) {
                        Icon(Icons.Default.Search, "Search")
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
        if (hasSearchFocus && choices.isNotEmpty()) {
            Surface(color = TubeDeckSurfaceVariant, tonalElevation = 4.dp) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (clean.isBlank()) "Recent searches" else "Search suggestions",
                            color = TubeDeckTextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (recentSearches.isNotEmpty()) TextButton(onClick = onClearRecentSearches) { Text("Clear history") }
                    }
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 156.dp)) {
                        itemsIndexed(choices, key = { _, term -> term }) { index, term ->
                            val recent = recentSearches.any { it.equals(term, true) }
                            Row((if (index == 0) Modifier.focusRequester(firstSuggestionFocusRequester) else Modifier)
                                .fillMaxWidth().tvInputHighlight(RoundedCornerShape(8.dp))
                                .clickable { submit(term) }.padding(start = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (recent) Icons.Default.History else Icons.Default.Search, null,
                                    tint = TubeDeckTextSecondary, modifier = Modifier.size(20.dp))
                                Text(term, color = TubeDeckTextPrimary, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = if (tvMode) 18.dp else 12.dp))
                                if (recent) IconButton(onClick = { onRemoveRecentSearch(term) }) {
                                    Icon(Icons.Default.Close, "Remove recent search: $term")
                                }
                            }
                        }
                    }
                }
            }
        } else if (!hasSearchFocus && showRecentChips && recentSearches.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent searches", color = TubeDeckTextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onClearRecentSearches) { Text("Clear history") }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recentSearches.take(8), key = { it }) { term ->
                    InputChip(selected = false, onClick = { submit(term) }, label = {
                        Text(term, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
                    }, trailingIcon = {
                        Icon(Icons.Default.Close, "Remove recent search: $term",
                            Modifier.size(20.dp).clickable { onRemoveRecentSearch(term) })
                    })
                }
            }
        }
    }
}
