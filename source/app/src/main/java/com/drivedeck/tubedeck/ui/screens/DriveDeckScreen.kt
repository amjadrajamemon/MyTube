@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.drivedeck.tubedeck.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    onLoadMoreHomeFeed: (String) -> Unit = {},
    moreFeedsLoading: Set<String> = emptySet(),
    moreFeedsExhausted: Set<String> = emptySet(),
    onClearLiveSearch: () -> Unit = {},
    onClearVideoSearch: () -> Unit = {},
    onOpenWebSearch: (String) -> Unit = {},
    onOpenVideo: (HomeVideo) -> Unit = {},
    onClearHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val deckTheme = LocalDeckThemeProfile.current
    var listView by rememberSaveable(viewStateKey, deckTheme.name) {
        mutableStateOf(deckTheme.geometry == DeckCardGeometry.COMPACT ||
            deckTheme.geometry == DeckCardGeometry.TERMINAL)
    }
    // Navigation is gesture-driven: phone users swipe between Home feed panels,
    // while theme-specific stacked shelves use horizontal video rails.
    // No per-feed orientation toggle is necessary.
    val context = LocalContext.current
    val tvNavigation = remember(context) { isTvLikeNavigationDevice(context) }
    val firstVideoFocus = remember { FocusRequester() }
    val searchResultFocus = remember { FocusRequester() }
    var selectedSearchVideoId by rememberSaveable(viewStateKey) { mutableStateOf("") }
    val activeSearchVideos = when {
        liveSearchQuery.isNotBlank() -> liveSearchResults
        tvNavigation && videoSearchQuery.isNotBlank() -> videoSearchResults
        else -> emptyList()
    }
    val hasTvSearchResults = activeSearchVideos.isNotEmpty()
    val searchResultTargetId = selectedSearchVideoId.takeIf { id ->
        activeSearchVideos.any { it.videoId == id }
    } ?: activeSearchVideos.firstOrNull()?.videoId
    LaunchedEffect(tvNavigation, playlistMode, liveSearchQuery, videoSearchQuery, hasTvSearchResults) {
        if (tvNavigation && !playlistMode && hasTvSearchResults) {
            repeat(4) {
                delay(80)
                if (runCatching { searchResultFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            }
        }
    }
    // Remember actual selection, not merely that focus was initialized. The
    // SaveableStateProvider in MainActivity restores this when returning from video.
    var selectedHomeFeed by rememberSaveable(viewStateKey) { mutableStateOf("") }
    var selectedHomeVideoId by rememberSaveable(viewStateKey) { mutableStateOf("") }
    val feeds = listOf(
        "History" to if (historyEnabled) watchHistory else emptyList(),
        "Relevant" to if (suggestionsEnabled) suggestedVideos else emptyList(),
        "Trending" to if (trendingEnabled) trendingVideos else emptyList()
    )
    val selectedIsAvailable = feeds.any { (feed, videos) ->
        feed == selectedHomeFeed && videos.any { it.videoId == selectedHomeVideoId }
    }
    val chosenFeedAndVideo = if (selectedIsAvailable) {
        selectedHomeFeed to selectedHomeVideoId
    } else {
        feeds.firstOrNull { it.second.isNotEmpty() }?.let { it.first to it.second.first().videoId }
    }
    val focusFeed = chosenFeedAndVideo?.first
    val focusVideoId = chosenFeedAndVideo?.second

    LaunchedEffect(tvNavigation, playlistMode, focusFeed != null, hasTvSearchResults) {
        if (tvNavigation && !playlistMode && !hasTvSearchResults && focusFeed != null) {
            // First frame may precede feed data or the LazyColumn item mount.
            repeat(4) {
                delay(80)
                if (runCatching { firstVideoFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            }
        }
    }

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
            autoFocus = false
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
                        onAction = onClearLiveSearch,
                        firstResultFocus = if (tvNavigation) searchResultFocus else null,
                        focusVideoId = searchResultTargetId,
                        onVideoFocused = { selectedSearchVideoId = it.videoId }
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
                        onAction = onClearVideoSearch,
                        firstResultFocus = searchResultFocus,
                        focusVideoId = searchResultTargetId,
                        onVideoFocused = { selectedSearchVideoId = it.videoId }
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
                // Adapt to *measured* width, not pixel resolution or device label.
                // Phones must never squeeze three panels into 100-200dp columns.
                item(key = "home-three-feeds") {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        val layout = HomeLayoutPolicy.decide(
                            availableWidthDp = maxWidth.value.toInt(),
                            threeColumnTheme = deckTheme.homePresentation == DeckHomePresentation.THREE_COLUMN
                        )

                        @Composable
                        fun Feed(index: Int, feedModifier: Modifier, horizontalCards: Boolean) {
                            when (index) {
                                0 -> HomeFeedColumn(
                                    title = "History",
                                    horizontal = horizontalCards,
                                    loadMoreEnabled = "History" !in moreFeedsExhausted,
                                    moreLoading = "History" in moreFeedsLoading,
                                    onLoadMore = { onLoadMoreHomeFeed("History") },
                                    videos = if (historyEnabled) watchHistory else emptyList(),
                                    onOpenVideo = onOpenVideo,
                                    listView = listView,
                                    enabled = historyEnabled,
                                    action = if (historyEnabled && watchHistory.isNotEmpty()) "Clear" else null,
                                    onAction = onClearHistory,
                                    firstItemFocus = if (focusFeed == "History" && tvNavigation) firstVideoFocus else null,
                                    focusVideoId = if (focusFeed == "History") focusVideoId else null,
                                    onVideoFocused = { video ->
                                        selectedHomeFeed = "History"
                                        selectedHomeVideoId = video.videoId
                                    },
                                    modifier = feedModifier
                                )
                                1 -> HomeFeedColumn(
                                    title = "Relevant",
                                    horizontal = horizontalCards,
                                    moreLoading = "Relevant" in moreFeedsLoading,
                                    loadMoreEnabled = "Relevant" !in moreFeedsExhausted,
                                    onLoadMore = { onLoadMoreHomeFeed("Relevant") },
                                    videos = if (suggestionsEnabled) suggestedVideos else emptyList(),
                                    onOpenVideo = onOpenVideo,
                                    listView = listView,
                                    enabled = suggestionsEnabled,
                                    action = if (suggestionsEnabled) "Refresh" else null,
                                    onAction = onRefreshFeed,
                                    firstItemFocus = if (focusFeed == "Relevant" && tvNavigation) firstVideoFocus else null,
                                    focusVideoId = if (focusFeed == "Relevant") focusVideoId else null,
                                    onVideoFocused = { video ->
                                        selectedHomeFeed = "Relevant"
                                        selectedHomeVideoId = video.videoId
                                    },
                                    modifier = feedModifier
                                )
                                else -> HomeFeedColumn(
                                    title = "Trending",
                                    horizontal = horizontalCards,
                                    moreLoading = "Trending" in moreFeedsLoading,
                                    loadMoreEnabled = "Trending" !in moreFeedsExhausted,
                                    onLoadMore = { onLoadMoreHomeFeed("Trending") },
                                    subtitle = countryCode,
                                    videos = if (trendingEnabled) trendingVideos else emptyList(),
                                    onOpenVideo = onOpenVideo,
                                    listView = listView,
                                    enabled = trendingEnabled,
                                    loading = trendingStatus == "Loading",
                                    action = if (trendingEnabled) "Refresh" else null,
                                    onAction = onRefreshFeed,
                                    firstItemFocus = if (focusFeed == "Trending" && tvNavigation) firstVideoFocus else null,
                                    focusVideoId = if (focusFeed == "Trending") focusVideoId else null,
                                    onVideoFocused = { video ->
                                        selectedHomeFeed = "Trending"
                                        selectedHomeVideoId = video.videoId
                                    },
                                    modifier = feedModifier
                                )
                            }
                        }

                        when (deckTheme.homePresentation) {
                            DeckHomePresentation.THREE_COLUMN -> when (layout) {
                                HomeLayoutPolicy.Layout.WIDE_COLUMNS -> Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    repeat(3) { index ->
                                        Feed(index, Modifier.weight(1f), horizontalCards = false)
                                    }
                                }
                                else -> {
                                    // On phones, these are real swipeable feed PANELS.
                                    // Each panel is wide enough for a readable list row.
                                    val panelWidth = (maxWidth - 30.dp).coerceAtMost(620.dp)
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(3) { index ->
                                            Feed(index, Modifier.width(panelWidth), horizontalCards = false)
                                        }
                                    }
                                }
                            }

                            DeckHomePresentation.IPTV_DASHBOARD -> Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ThemeDashboardBanner("CHANNEL DASHBOARD", "MYTUBE / SOURCE COLLECTION")
                                if (maxWidth >= 840.dp) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        repeat(3) { index ->
                                            Feed(index, Modifier.weight(1f), horizontalCards = false)
                                        }
                                    }
                                } else repeat(3) { index ->
                                    Feed(index, Modifier.fillMaxWidth(), horizontalCards = true)
                                }
                            }

                            DeckHomePresentation.STACKED_SHELVES -> Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                ThemeDashboardBanner("Discover", "Your library and personalized recommendations")
                                repeat(3) { index ->
                                    Feed(index, Modifier.fillMaxWidth(), horizontalCards = true)
                                }
                            }

                            DeckHomePresentation.DESKTOP_TABLE -> Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ThemeDashboardBanner("MYTUBE://WORKSPACE", "LIBRARY  /  DISCOVERY  /  TRENDING")
                                repeat(3) { index ->
                                    Feed(index, Modifier.fillMaxWidth(), horizontalCards = false)
                                }
                            }

                            DeckHomePresentation.HUD_CONSOLE -> Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ThemeDashboardBanner("MEDIA COMMAND CENTER", "FEED STATUS / LIVE MONITOR")
                                if (maxWidth >= 760.dp) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Feed(0, Modifier.weight(1f), horizontalCards = false)
                                        Feed(1, Modifier.weight(1f), horizontalCards = false)
                                    }
                                } else {
                                    Feed(0, Modifier.fillMaxWidth(), horizontalCards = true)
                                    Feed(1, Modifier.fillMaxWidth(), horizontalCards = true)
                                }
                                Feed(2, Modifier.fillMaxWidth(), horizontalCards = true)
                            }

                            DeckHomePresentation.CINEMA_STRIPS -> Column(
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                suggestedVideos.firstOrNull()?.let { video ->
                                    HomeSpotlight(video, "NOW SHOWING", onOpenVideo)
                                }
                                repeat(3) { index ->
                                    Feed(index, Modifier.fillMaxWidth(), horizontalCards = true)
                                }
                            }

                            DeckHomePresentation.GLASS_CAROUSEL -> Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ThemeDashboardBanner("Explore MyTube", "Swipe to discover what matters to you")
                                suggestedVideos.firstOrNull()?.let { video ->
                                    HomeSpotlight(video, "FEATURED FOR YOU", onOpenVideo)
                                }
                                repeat(3) { index ->
                                    Feed(index, Modifier.fillMaxWidth(), horizontalCards = true)
                                }
                            }

                            DeckHomePresentation.AMOLED_INDEX -> Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ThemeDashboardBanner("Library", "PURE BLACK  /  NO DISTRACTIONS")
                                repeat(3) { index ->
                                    Feed(index, Modifier.fillMaxWidth(), horizontalCards = false)
                                }
                            }

                            DeckHomePresentation.ARCADE_GALLERY -> Column(
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                ThemeDashboardBanner("SELECT YOUR STAGE", "PLAYER ONE  /  VIDEO ARCADE")
                                repeat(3) { index ->
                                    Feed(index, Modifier.fillMaxWidth(), horizontalCards = false)
                                }
                            }

                            DeckHomePresentation.MINIMAL_EDITORIAL -> Column(
                                verticalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                ThemeDashboardBanner("Watch something wonderful.", "A quieter place to discover")
                                suggestedVideos.firstOrNull()?.let { video ->
                                    HomeSpotlight(video, "EDITOR'S PICK", onOpenVideo)
                                }
                                Feed(1, Modifier.fillMaxWidth(), horizontalCards = true)
                                Feed(0, Modifier.fillMaxWidth(), horizontalCards = false)
                                Feed(2, Modifier.fillMaxWidth(), horizontalCards = true)
                            }
                        }
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
            modifier = Modifier.fillMaxSize()
                .clip(RoundedCornerShape(LocalDeckThemeProfile.current.cornerRadiusDp.dp)),
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
 * Three Home feeds support swipeable panels on narrow THREE_COLUMN themes,
 * readable TV columns on wide canvases, and horizontal video rails in shelf
 * themes. The layouts are deliberately different:
 * List = compact thumbnail to the left, full wrapped title on the right;
 * Tile = large thumbnail above the full wrapped title.
 *
 * Lazy composition keeps only the visible cards resident. More items are
 * requested on demand at the trailing edge rather than retaining infinite
 * thumbnails or decoding a video preview in the Home page.
 */
@Composable
private fun HomeFeedColumn(
    title: String,
    videos: List<HomeVideo>,
    onOpenVideo: (HomeVideo) -> Unit,
    listView: Boolean,
    enabled: Boolean,
    horizontal: Boolean,
    loadMoreEnabled: Boolean,
    moreLoading: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    loading: Boolean = false,
    action: String? = null,
    onAction: () -> Unit = {},
    firstItemFocus: FocusRequester? = null,
    focusVideoId: String? = null,
    onVideoFocused: (HomeVideo) -> Unit = {}
) {
    val state = rememberLazyListState()
    val profile = LocalDeckThemeProfile.current
    val cardShape = RoundedCornerShape(profile.cornerRadiusDp.dp)
    val imageRatio = when(profile.geometry) {
        DeckCardGeometry.CINEMA -> 2.25f
        DeckCardGeometry.TERMINAL -> 1.6f
        else -> 16f / 9f
    }
    val focusIndex = videos.indexOfFirst { it.videoId == focusVideoId }
    LaunchedEffect(horizontal, focusIndex) {
        if (focusIndex >= 0) state.scrollToItem(focusIndex)
    }
    val lastIndex by remember(state) {
        derivedStateOf { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    }
    // Prevent repeated requests when the list is still at its previous end.
    var lastRequestedSize by remember(title) { mutableIntStateOf(-1) }
    LaunchedEffect(lastIndex, videos.size, horizontal, moreLoading, enabled, loadMoreEnabled) {
        if (enabled && loadMoreEnabled && !moreLoading && videos.isNotEmpty() &&
            lastIndex >= videos.lastIndex - 2 && lastRequestedSize != videos.size
        ) {
            lastRequestedSize = videos.size
            onLoadMore()
        }
    }
    Surface(color = TubeDeckSurface, shape = cardShape, modifier = modifier) {
        Column(
            modifier = Modifier.padding(
                horizontal = (profile.contentGapDp / 2).dp,
                vertical = profile.contentGapDp.dp
            ),
            verticalArrangement = Arrangement.spacedBy((profile.contentGapDp / 2).dp)
        ) {
            Column(Modifier.fillMaxWidth().heightIn(min = 45.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title, color = TubeDeckTextPrimary, fontSize = 16.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1,
                        softWrap = false, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (action != null) {
                        IconButton(onClick = onAction, modifier = Modifier.size(36.dp)) {
                            Icon(
                                if (action == "Clear") Icons.Default.DeleteSweep else Icons.Default.Refresh,
                                "$action $title", tint = TubeDeckAccentCyan, modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, color = TubeDeckTextSecondary, fontSize = 10.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!enabled) {
                Text("Off in Settings", color = TubeDeckTextSecondary, fontSize = 10.sp)
            } else if (videos.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                    if (loading || moreLoading) CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp)
                    else Text("No videos yet", color = TubeDeckTextSecondary, fontSize = 10.sp)
                }
            } else {
                @Composable
                fun HomeCard(video: HomeVideo, width: Modifier) {
                    val focused = video.videoId == focusVideoId
                    val itemModifier = width
                        .then(if (focused && firstItemFocus != null) Modifier.focusRequester(firstItemFocus) else Modifier)
                        .onFocusChanged { if (it.isFocused) onVideoFocused(video) }
                        .clip(cardShape)
                        .tvInputHighlight(cardShape)
                        .clickable {
                            onVideoFocused(video)
                            onOpenVideo(video)
                        }
                        .padding(3.dp)
                    if (listView) {
                        Row(itemModifier, verticalAlignment = Alignment.Top) {
                            VideoThumbnail(video,
                                Modifier.width(if (horizontal) 112.dp else if (profile.geometry == DeckCardGeometry.COMPACT) 62.dp else 77.dp)
                                    .aspectRatio(imageRatio))
                            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                                Text(video.title, color = TubeDeckTextPrimary, fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp, lineHeight = 16.sp, maxLines = 3,
                                     overflow = TextOverflow.Ellipsis)
                                Text(video.channelTitle, color = TubeDeckTextSecondary, fontSize = 10.sp,
                                     maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    } else {
                        Column(itemModifier) {
                            VideoThumbnail(video, Modifier.fillMaxWidth().aspectRatio(imageRatio))
                            Spacer(Modifier.height(5.dp))
                            Text(video.title, color = TubeDeckTextPrimary, fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp, lineHeight = 16.sp, maxLines = 3,
                                     overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(3.dp))
                            Text(video.channelTitle, color = TubeDeckTextSecondary, fontSize = 10.sp,
                                 maxLines = 2, overflow = TextOverflow.Ellipsis)
                            VideoViews(video.viewCount)
                        }
                    }
                }
                if (horizontal) {
                    LazyRow(
                        state = state,
                        modifier = Modifier.fillMaxWidth().height(
                            if (listView) 160.dp else if (profile.geometry == DeckCardGeometry.CINEMA) 302.dp else 278.dp
                        ),
                        contentPadding = PaddingValues(end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(videos, key = { index, item -> "${item.videoId}-$index" }) { _, video ->
                            HomeCard(video, Modifier.width(
                                if (listView) 305.dp else if (profile.geometry == DeckCardGeometry.CINEMA) 263.dp else 230.dp
                            ))
                        }
                    }
                } else {
                    LazyColumn(
                        state = state,
                        modifier = Modifier.fillMaxWidth().height(
                            if (profile.navigationPlacement == DeckNavigationPlacement.SIDE) 660.dp
                            else if (listView) 520.dp else 640.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(if (listView) 6.dp else 14.dp)
                    ) {
                        itemsIndexed(videos, key = { index, item -> "${item.videoId}-$index" }) { _, video ->
                            HomeCard(video, Modifier.fillMaxWidth())
                        }
                    }
                }
                if (moreLoading) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else if (loadMoreEnabled) {
                    TextButton(onClick = {
                        lastRequestedSize = videos.size
                        onLoadMore()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Load more $title", fontSize = 10.sp)
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
    onAction: () -> Unit = {},
    firstResultFocus: FocusRequester? = null,
    focusVideoId: String? = null,
    onVideoFocused: (HomeVideo) -> Unit = {}
) {
    val focusScrollScope = rememberCoroutineScope()
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
                            val bring = remember(video.videoId) { BringIntoViewRequester() }
                            VideoListRow(
                                video,
                                onClick = {
                                    onVideoFocused(video)
                                    onOpenVideo(video)
                                },
                                modifier = Modifier
                                    .then(if (firstResultFocus != null && video.videoId == focusVideoId) Modifier.focusRequester(firstResultFocus) else Modifier)
                                    .bringIntoViewRequester(bring)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            onVideoFocused(video)
                                            focusScrollScope.launch { runCatching { bring.bringIntoView() } }
                                        }
                                    }
                            )
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
                                    val bring = remember(video.videoId) { BringIntoViewRequester() }
                                    VideoTileCard(
                                        video,
                                        onClick = {
                                            onVideoFocused(video)
                                            onOpenVideo(video)
                                        },
                                        modifier = Modifier.weight(1f)
                                            .then(if (firstResultFocus != null && video.videoId == focusVideoId) Modifier.focusRequester(firstResultFocus) else Modifier)
                                            .bringIntoViewRequester(bring)
                                            .onFocusChanged {
                                                if (it.isFocused) {
                                                    onVideoFocused(video)
                                                    focusScrollScope.launch { runCatching { bring.bringIntoView() } }
                                                }
                                            }
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
private fun VideoListRow(
    video: HomeVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
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
            modifier = Modifier.fillMaxSize()
                .clip(RoundedCornerShape(LocalDeckThemeProfile.current.cornerRadiusDp.dp)),
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
    val profile = LocalDeckThemeProfile.current
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
        tonalElevation = if (profile.navVisual == DeckNavVisual.ICONS) 0.dp else 8.dp
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val desktopCommand = profile.navVisual == DeckNavVisual.COMMAND && maxWidth >= 700.dp
            val zen = profile.navVisual == DeckNavVisual.MINIMAL
            val horizontalPadding = if (profile.navVisual == DeckNavVisual.COMPACT) 6.dp else 12.dp
            val search: @Composable (Modifier) -> Unit = { searchModifier ->
                SearchAssistField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onSearch = onSearch,
                    recentSearches = recentSearches,
                    onRemoveRecentSearch = onRemoveRecentSearch,
                    onClearRecentSearches = onClearRecentSearches,
                    placeholder = searchPlaceholder,
                    showRecentChips = false,
                    autoFocus = autoFocus,
                    modifier = searchModifier
                )
            }
            if (desktopCommand) {
                // A real desktop command strip: search and actions share the toolbar row.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Terminal, "Command workspace",
                        tint = TubeDeckAccentCyan, modifier = Modifier.size(27.dp))
                    Text("MyTube", color = TubeDeckTextPrimary,
                        fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    search(Modifier.weight(1f))
                    GoogleAccountButton(account = googleAccount, onClick = onGoogleAccountClick)
                    IconButton(onClick = onOpenDownloads, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.FileDownload, "Downloads", tint = TubeDeckAccentCyan)
                    }
                }
            } else if (zen) {
                // Content-first header: search is dominant; secondary actions stay compact.
                Column(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 5.dp)) {
                    search(Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("MyTube", color = TubeDeckTextSecondary,
                            fontSize = 12.sp, modifier = Modifier.weight(1f))
                        GoogleAccountButton(account = googleAccount, onClick = onGoogleAccountClick)
                        IconButton(onClick = onOpenDownloads, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.FileDownload, "Downloads", tint = TubeDeckTextSecondary)
                        }
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = horizontalPadding,
                        vertical = if (profile.navVisual == DeckNavVisual.COMPACT) 4.dp else 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val headerIcon = when (profile.playerChrome) {
                            DeckPlayerChrome.HUD -> Icons.Default.Dashboard
                            DeckPlayerChrome.ARCADE -> Icons.Default.SportsEsports
                            DeckPlayerChrome.THEATER -> Icons.Default.Movie
                            DeckPlayerChrome.GLASS -> Icons.Default.PlayCircleFilled
                            DeckPlayerChrome.CONSOLE -> Icons.Default.Terminal
                            DeckPlayerChrome.CLASSIC -> Icons.Default.PlayCircleFilled
                        }
                        Icon(headerIcon, null, tint = TubeDeckAccentCyan,
                            modifier = Modifier.size(if (profile.navVisual == DeckNavVisual.COMPACT) 24.dp else 30.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("MyTube", color = TubeDeckTextPrimary,
                            fontSize = if (profile.navVisual == DeckNavVisual.COMPACT) 17.sp else 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.weight(1f))
                        GoogleAccountButton(account = googleAccount, onClick = onGoogleAccountClick)
                        IconButton(onClick = onOpenDownloads, modifier = Modifier.size(42.dp)) {
                            Icon(Icons.Default.FileDownload, "Downloads", tint = TubeDeckTextPrimary)
                        }
                    }
                    search(Modifier.fillMaxWidth())
                }
            }
        }
    }
}
