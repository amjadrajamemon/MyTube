package com.drivedeck.tubedeck.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.drivedeck.tubedeck.data.model.HomeVideo
import com.drivedeck.tubedeck.auth.GoogleAccountProfile
import com.drivedeck.tubedeck.ui.components.GoogleAccountButton
import com.drivedeck.tubedeck.ui.theme.*

@Composable
fun DriveDeckScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onExecuteSearch: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    googleAccount: GoogleAccountProfile? = null,
    onGoogleAccountClick: () -> Unit = {},
    watchHistory: List<HomeVideo> = emptyList(),
    suggestedVideos: List<HomeVideo> = emptyList(),
    onOpenVideo: (HomeVideo) -> Unit = {},
    onClearHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val topics = remember {
        listOf("All", "Music", "Gaming", "News", "Live", "Technology", "Sports", "Movies", "Learning")
    }
    var selectedTopic by remember { mutableStateOf("All") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TubeDeckBackground)
            .statusBarsPadding()
    ) {
        DriveDeckTopHeader(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onSearch = { onExecuteSearch(searchQuery) },
            onOpenDownloads = onOpenDownloads,
            googleAccount = googleAccount,
            onGoogleAccountClick = onGoogleAccountClick
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(topics) { topic ->
                        FilterChip(
                            selected = selectedTopic == topic,
                            onClick = {
                                selectedTopic = topic
                                if (topic != "All") onExecuteSearch(topic)
                            },
                            label = { Text(topic) }
                        )
                    }
                }
            }

            if (watchHistory.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "History",
                        action = "Clear",
                        onAction = onClearHistory
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(watchHistory.take(12), key = { "history-${it.videoId}" }) { video ->
                            CompactVideoCard(video = video, onClick = { onOpenVideo(video) })
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = if (suggestedVideos.isEmpty()) "Recommended" else "Recommended for you"
                )
            }

            if (suggestedVideos.isEmpty()) {
                item {
                    Surface(
                        color = TubeDeckSurfaceVariant,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.SmartDisplay, null, tint = TubeDeckAccentCyan, modifier = Modifier.size(38.dp))
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("Your feed will appear here", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(suggestedVideos, key = { "suggested-${it.videoId}" }) { video ->
                    StandardVideoCard(video = video, onClick = { onOpenVideo(video) })
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun CompactVideoCard(video: HomeVideo, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(210.dp).clickable(onClick = onClick)
    ) {
        Box {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            if (video.durationText.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = .82f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)
                ) {
                    Text(video.durationText, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(video.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
        Text(video.channelTitle, color = TubeDeckTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
    }
}

@Composable
private fun StandardVideoCard(video: HomeVideo, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(bottom = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                contentScale = ContentScale.Crop
            )
            if (video.durationText.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = .82f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(7.dp)
                ) {
                    Text(video.durationText, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                }
            }
        }
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Surface(shape = CircleShape, color = TubeDeckSurfaceVariant, modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = TubeDeckTextSecondary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                Spacer(Modifier.height(3.dp))
                Text(video.channelTitle, color = TubeDeckTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
            }
            Icon(Icons.Default.MoreVert, null, tint = TubeDeckTextSecondary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveDeckTopHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenDownloads: () -> Unit,
    googleAccount: GoogleAccountProfile? = null,
    onGoogleAccountClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = TubeDeckSurface,
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayCircleFilled, null, tint = Color.Red, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(7.dp))
                Text("MyTube", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                GoogleAccountButton(account = googleAccount, onClick = onGoogleAccountClick)
                IconButton(onClick = onOpenDownloads, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Downloads", tint = Color.White)
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                placeholder = { Text("Search YouTube", color = Color.Gray, fontSize = 15.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TubeDeckTextSecondary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, null, tint = Color.LightGray)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = TubeDeckAccentCyan,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedContainerColor = TubeDeckSurfaceVariant,
                    unfocusedContainerColor = TubeDeckSurfaceVariant,
                    cursorColor = TubeDeckAccentCyan
                ),
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}
