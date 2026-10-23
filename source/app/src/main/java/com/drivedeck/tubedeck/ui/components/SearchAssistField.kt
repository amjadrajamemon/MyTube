package com.drivedeck.tubedeck.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    var remote by remember { mutableStateOf(emptyList<String>()) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clean = SearchAssistPolicy.clean(query)

    LaunchedEffect(clean, focused) {
        remote = emptyList()
        if (focused && clean.isNotBlank() && !SearchAssistPolicy.isUrl(clean)) {
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
    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            placeholder = { Text(placeholder, fontSize = 15.sp) },
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
        if (focused && choices.isNotEmpty()) {
            Surface(color = TubeDeckSurfaceVariant, tonalElevation = 4.dp) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (clean.isBlank()) "Recent searches" else "Search suggestions",
                            color = TubeDeckTextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (recentSearches.isNotEmpty()) TextButton(onClick = onClearRecentSearches) { Text("Clear history") }
                    }
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 224.dp)) {
                        items(choices, key = { it }) { term ->
                            val recent = recentSearches.any { it.equals(term, true) }
                            Row(Modifier.fillMaxWidth().clickable { submit(term) }.padding(start = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (recent) Icons.Default.History else Icons.Default.Search, null,
                                    tint = TubeDeckTextSecondary, modifier = Modifier.size(20.dp))
                                Text(term, color = TubeDeckTextPrimary, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 12.dp))
                                if (recent) IconButton(onClick = { onRemoveRecentSearch(term) }) {
                                    Icon(Icons.Default.Close, "Remove recent search: $term")
                                }
                            }
                        }
                    }
                }
            }
        } else if (!focused && showRecentChips && recentSearches.isNotEmpty()) {
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
