package com.drivedeck.tubedeck.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.drivedeck.tubedeck.auth.GoogleAccountProfile
import com.drivedeck.tubedeck.data.model.HomeVideo
import com.drivedeck.tubedeck.data.model.PlaylistCandidate
import com.drivedeck.tubedeck.player.YoutubeSearchPolicy
import com.drivedeck.tubedeck.ui.components.GoogleAccountButton
import com.drivedeck.tubedeck.ui.components.SearchAssistField
import com.drivedeck.tubedeck.ui.components.isTvLikeNavigationDevice
import com.drivedeck.tubedeck.ui.components.tvInputHighlight
import com.drivedeck.tubedeck.ui.theme.*
import java.util.Locale

@Composable
fun DriveDeckScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onExecuteSearch: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    googleAccount: GoogleAccountProfile? = null,
    onGoogleAccountClick: () -> Unit = {},
    recentSearches: List<String> = emptyList(),
    onRemoveRecentSearch: (String) -> Unit = {},
    onClearRecentSearches: () -> Unit = {},
    watchHistory: List<HomeVideo> = emptyList(),
    suggestedVideos: List<HomeVideo> = emptyList(),
    trendingVideos: List<HomeVideo> = emptyList(),
    liveNowVideos: List<HomeVideo> = emptyList(),
    liveNowStatus: String = "",
    liveSearchResults: List<HomeVideo> = emptyList(),
    liveSearchQuery: String = "",
    liveSearchStatus: String = "",
    videoSearchResults: List<HomeVideo> = emptyList(),
    videoSearchQuery: String = "",
    videoSearchStatus: String = "",
    playlistMode: Boolean = false,
    playlistResults: List<PlaylistCandidate> = emptyList(),
    playlistSearchQuery: String = "",
    playlistSearchStatus: String = "",
    onOpenPlaylist: (PlaylistCandidate) -> Unit = {},
    suggestionsEnabled: Boolean = true,
    historyEnabled: Boolean = true,
    trendingEnabled: Boolean = true,
    countryCode: String = "US",
    trendingStatus: String = "",
    viewStateKey: String = "home",
    onRefreshFeed: () -> Unit = {},
    onClearLiveSearch: () -> Unit = {},
    onClearVideoSearch: () -> Unit = {},
    onOpenWebSearch: (String) -> Unit = {},
    onOpenVideo: (HomeVideo) -> Unit = {},
    onClearHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var listView by rememberSaveable(viewStateKey) { mutableStateOf(true) }
    val context = LocalContext.current
    val tvNavigation = remember(context) { isTvLikeNavigationDevice(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TubeDeckBackground)
            .statusBarsPadding()
    ) {
        DriveDeckTopHeader(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onSearch = onExecuteSearch,
            recentSearches = recentSearches,
            onRemoveRecentSearch = onRemoveRecentSearch,
            onClearRecentSearches = onClearRecentSearches,
            onOpenDownloads = onOpenDownloads,
            googleAccount = googleAccount,
            onGoogleAccountClick = onGoogleAccountClick,
            searchPlaceholder = if (playlistMode) "Search playlists and Mixes" else "Search YouTube",
            autoFocus = tvNavigation
        )

        ContentViewModeBar(
            listView = listView,
            onListView = { listView = true },
            onCardView = { listView = false }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (playlistMode) {
                item {
                    PlaylistShelf(
                        title = if (playlistSearchQuery.isBlank()) "Playlists & Mixes" else "Results · $playlistSearchQuery",
                        playlists = playlistResults,
                        onOpenPlaylist = onOpenPlaylist,
                        listView = listView,
                        loading = playlistSearchStatus == "Loading",
                        emptyLabel = playlistSearchStatus.takeIf { it.isNotBlank() && it != "Loading" }
                    )
                }
            } else if (liveSearchQuery.isNotBlank()) {
                item {
                    HomeShelf(
                        "Live results · $liveSearchQuery",
                        liveSearchResults,
                        onOpenVideo,
                        listView = listView,
                        loading = liveSearchStatus == "Loading",
                        emptyLabel = liveSearchStatus.takeIf { it.isNotBlank() && it != "Loading" },
                        action = "Clear",
                        onAction = onClearLiveSearch
                    )
                }
            } else if (tvNavigation && videoSearchQuery.isNotBlank()) {
                item(key = "tv-native-search-results") {
                    HomeShelf(
                        "TV results · $videoSearchQuery",
                        videoSearchResults,
                        onOpenVideo,
                        listView = listView,
                        loading = videoSearchStatus == "Loading",
                        emptyLabel = videoSearchStatus.takeIf { it.isNotBlank() && it != "Loading" },
                        action = "Clear",
                        onAction = onClearVideoSearch
                    )
                }
                item(key = "tv-web-search-fallback") {
                    TextButton(
                        onClick = { onOpenWebSearch(videoSearchQuery) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                            .tvInputHighlight(RoundedCornerShape(10.dp))
                    ) { Text("Open web search instead") }
                }
            }
            if (!playlistMode) {
                // Keep all three Home categories visible in one row, even when a feed is disabled.
                // Individual Settings toggles still control whether each column loads video cards.
                item(key = "home-three-feeds") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        HomeFeedColumn(
                            title = "History",
                            videos = if (historyEnabled) watchHistory.take(12) else emptyList(),
                            onOpenVideo = onOpenVideo,
                            listView = listView,
                            enabled = historyEnabled,
                            action = if (historyEnabled && watchHistory.isNotEmpty()) "Clear" else null,
                            onAction = onClearHistory,
                            modifier = Modifier.weight(1f)
                        )
                        HomeFeedColumn(
                            title = "Relevant",
                            videos = if (suggestionsEnabled) suggestedVideos.take(12) else emptyList(),
                            onOpenVideo = onOpenVideo,
                            listView = listView,
                            enabled = suggestionsEnabled,
                            action = if (suggestionsEnabled) "Refresh" else null,
                            onAction = onRefreshFeed,
                            modifier = Modifier.weight(1f)
                        )
                        HomeFeedColumn(
                            title = "Trending",
                            subtitle = countryCode,
                            videos = if (trendingEnabled) trendingVideos.take(12) else emptyList(),
                            onOpenVideo = onOpenVideo,
                            listView = listView,
                            enabled = trendingEnabled,
                            loading = trendingStatus == "Loading",
                            action = if (trendingEnabled) "Refresh" else null,
                            onAction = onRefreshFeed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentViewModeBar(
    listView: Boolean,
    onListView: () -> Unit,
    onCardView: () -> Unit
) {
    Surface(color = TubeDeckSurface, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "View",
                color = TubeDeckTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            FilledTonalIconButton(
                onClick = onListView,
                modifier = Modifier.size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (listView) TubeDeckAccentCyan.copy(alpha = .20f) else TubeDeckSurfaceVariant,
                    contentColor = if (listView) TubeDeckAccentCyan else TubeDeckTextSecondary
                )
            ) {
                Icon(Icons.Default.ViewList, "List view", modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(7.dp))
            FilledTonalIconButton(
                onClick = onCardView,
                modifier = Modifier.size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (!listView) TubeDeckAccentCyan.copy(alpha = .20f) else TubeDeckSurfaceVariant,
                    contentColor = if (!listView) TubeDeckAccentCyan else TubeDeckTextSecondary
                )
            ) {
                Icon(Icons.Default.GridView, "Card view", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun PlaylistShelf(
    title: String,
    playlists: List<PlaylistCandidate>,
    onOpenPlaylist: (PlaylistCandidate) -> Unit,
    listView: Boolean,
    loading: Boolean = false,
    emptyLabel: String? = null
) {
    Surface(
        color = TubeDeckSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Column(Modifier.padding(bottom = 12.dp)) {
            SectionHeader(title)
            when {
                playlists.isNotEmpty() && listView -> Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    playlists.forEach { playlist ->
                        PlaylistListRow(playlist) { onOpenPlaylist(playlist) }
                    }
                }
                playlists.isNotEmpty() -> Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    playlists.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            row.forEach { playlist ->
                                PlaylistTileCard(
                                    playlist = playlist,
                                    onClick = { onOpenPlaylist(playlist) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                else -> Box(
                    Modifier.fillMaxWidth().height(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text(emptyLabel ?: "Search for a playlist or Mix", color = TubeDeckTextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistListRow(playlist: PlaylistCandidate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .tvInputHighlight(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        verticalAlignment = Alignment.Top
    ) {
        PlaylistThumbnail(playlist, Modifier.width(138.dp).aspectRatio(16f / 9f))
        Column(Modifier.weight(1f).padding(start = 10.dp, top = 1.dp, end = 3.dp)) {
            Text(
                playlist.title,
                color = TubeDeckTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                playlist.channel,
                color = TubeDeckTextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val count = playlist.itemCount.takeIf { it > 0L }?.let { String.format(Locale.US, "%,d videos", it) }
            Text(
                listOfNotNull(playlist.kind.takeIf { it.isNotBlank() }, count).joinToString(" • "),
                color = TubeDeckAccentCyan,
                fontSize = 10.5.sp
            )
        }
    }
}

@Composable
private fun PlaylistTileCard(
    playlist: PlaylistCandidate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.tvInputHighlight().clickable(onClick = onClick)) {
        PlaylistThumbnail(playlist, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Spacer(Modifier.height(7.dp))
        Text(
            playlist.title,
            color = TubeDeckTextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            playlist.channel,
            color = TubeDeckTextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (playlist.itemCount > 0L) {
            Text(
                String.format(Locale.US, "%,d videos", playlist.itemCount),
                color = TubeDeckAccentCyan,
                fontSize = 10.5.sp
            )
        }
    }
}

@Composable
private fun PlaylistThumbnail(playlist: PlaylistCandidate, modifier: Modifier) {
    Box(modifier) {
        AsyncImage(
            model = playlist.thumbnailUrl,
            contentDescription = playlist.title,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
        Surface(
            color = Color.Black.copy(alpha = .78f),
            shape = RoundedCornerShape(5.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlaylistPlay, null, tint = Color.White, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text(playlist.kind.ifBlank { "Playlist" }, color = Color.White, fontSize = 9.5.sp)
            }
        }
    }
}

/**
 * Home has three equally sized columns on all supported screen widths.
 * Cards stack vertically within a column rather than creating three separate full-width shelves.
 */
@Composable
private fun HomeFeedColumn(
    title: String,
    videos: List<HomeVideo>,
    onOpenVideo: (HomeVideo) -> Unit,
    listView: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    loading: Boolean = false,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    Surface(
        color = TubeDeckSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (listView) 8.dp else 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 34.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = TubeDeckTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            color = TubeDeckTextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
                if (action != null) {
                    IconButton(
                        onClick = onAction,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (action == "Clear") Icons.Default.DeleteSweep else Icons.Default.Refresh,
                            contentDescription = "$action $title",
                            tint = TubeDeckAccentCyan,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
            if (!enabled) {
                Text("Off in Settings", color = TubeDeckTextSecondary, fontSize = 10.sp)
            } else if (videos.isEmpty()) {
                if (loading) {
                    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                } else {
                    Text("No videos yet", color = TubeDeckTextSecondary, fontSize = 10.sp)
                }
            } else {
                videos.forEach { video ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .tvInputHighlight(RoundedCornerShape(9.dp))
                            .clickable { onOpenVideo(video) }
                    ) {
                        VideoThumbnail(video, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            video.title,
                            color = TubeDeckTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = if (isTvLikeNavigationDevice(LocalContext.current)) 14.sp else if (listView) 10.5.sp else 11.sp,
                            lineHeight = if (isTvLikeNavigationDevice(LocalContext.current)) 17.sp else 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!listView) {
                            Text(
                                video.channelTitle,
                                color = TubeDeckTextSecondary,
                                fontSize = 9.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeShelf(
    title: String,
    videos: List<HomeVideo>,
    onOpenVideo: (HomeVideo) -> Unit,
    listView: Boolean,
    enabled: Boolean = true,
    loading: Boolean = false,
    emptyLabel: String? = null,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    Surface(
        color = TubeDeckSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Column(Modifier.padding(bottom = 12.dp)) {
            SectionHeader(title, action, onAction)
            if (videos.isNotEmpty()) {
                if (listView) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        videos.forEach { video ->
                            VideoListRow(video, onClick = { onOpenVideo(video) })
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        videos.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                row.forEach { video ->
                                    VideoTileCard(
                                        video,
                                        onClick = { onOpenVideo(video) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                    if (loading && enabled) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            emptyLabel ?: if (enabled) "—" else "Off",
                            color = TubeDeckTextSecondary,
                            fontSize = 11.sp
                        )
                    }
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
        Text(
            title,
            color = TubeDeckTextPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun VideoListRow(video: HomeVideo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .tvInputHighlight(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        verticalAlignment = Alignment.Top
    ) {
        VideoThumbnail(
            video = video,
            modifier = Modifier.width(138.dp).aspectRatio(16f / 9f)
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 10.dp, top = 1.dp, end = 3.dp)
        ) {
            Text(
                video.title,
                color = TubeDeckTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                video.channelTitle,
                color = TubeDeckTextSecondary,
                fontSize = 11.sp
            )
            VideoViews(video.viewCount)
        }
    }
}

@Composable
private fun VideoTileCard(
    video: HomeVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.tvInputHighlight().clickable(onClick = onClick)) {
        VideoThumbnail(
            video = video,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        )
        Spacer(Modifier.height(7.dp))
        Text(
            video.title,
            color = TubeDeckTextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(3.dp))
        Text(
            video.channelTitle,
            color = TubeDeckTextSecondary,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        VideoViews(video.viewCount)
    }
}

@Composable
private fun VideoThumbnail(video: HomeVideo, modifier: Modifier) {
    Box(modifier) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
        if (video.durationText.isNotBlank()) {
            Surface(
                color = if (video.durationText == "LIVE") Color.Red else Color.Black.copy(alpha = .82f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)
            ) {
                Text(
                    video.durationText,
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun VideoViews(viewCount: Long) {
    if (viewCount >= 0L) {
        Text(
            String.format(Locale.US, "%,d views", viewCount),
            color = TubeDeckTextSecondary,
            fontSize = 10.5.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveDeckTopHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    recentSearches: List<String>,
    onRemoveRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onOpenDownloads: () -> Unit,
    googleAccount: GoogleAccountProfile? = null,
    onGoogleAccountClick: () -> Unit = {},
    searchPlaceholder: String = "Search YouTube",
    autoFocus: Boolean = false,
    modifier: Modifier = Modifier
) {
    // First composition should focus the Home feed rather than opening the IME.
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        if (!autoFocus) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = TubeDeckSurface,
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayCircleFilled, null, tint = Color.Red, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    "MyTube",
                    color = TubeDeckTextPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                GoogleAccountButton(account = googleAccount, onClick = onGoogleAccountClick)
                IconButton(onClick = onOpenDownloads, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Downloads", tint = TubeDeckTextPrimary)
                }
            }

            SearchAssistField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onSearch = onSearch,
                recentSearches = recentSearches,
                onRemoveRecentSearch = onRemoveRecentSearch,
                onClearRecentSearches = onClearRecentSearches,
                placeholder = searchPlaceholder,
                showRecentChips = true,
                autoFocus = autoFocus,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

