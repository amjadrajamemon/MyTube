@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.drivedeck.tubedeck.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.content.res.Configuration
import com.drivedeck.tubedeck.player.CarHeadUnitPolicy
import com.drivedeck.tubedeck.ui.components.SearchCompactPolicy
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.drivedeck.tubedeck.ui.components.ReadableVideoTitle
import com.drivedeck.tubedeck.ui.components.ReadableVideoMetadata
import com.drivedeck.tubedeck.ui.components.rememberVideoTitleMetrics
import com.drivedeck.tubedeck.ui.components.SearchAssistField
import com.drivedeck.tubedeck.ui.components.rememberAdaptiveTvNavigation
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
    homeFeedOrder: String = "History,Relevant,Trending",
    homeDisplayMode: String = "Theme",
    homeBrowseMode: String = "List",
    onHomeBrowseModeChange: (String) -> Unit = {},
    homeInitialVideoCount: Int = 20,
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
    // The persisted setting, not theme-dependent rememberSaveable, owns List/Tile mode.
    // It survives process death, playback navigation, theme switches and orientation.
    val listView = HomeResponsiveLayoutPolicy.browseMode(homeBrowseMode) == "List"
    val tvNavigation = rememberAdaptiveTvNavigation()
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
    val feedOrder = HomeFeedArrangement.visible(
        homeFeedOrder, historyEnabled, suggestionsEnabled, trendingEnabled
    )
    val initialCount = HomeResponsiveLayoutPolicy.initialVideos(homeInitialVideoCount)
    val visibleCounts = remember(viewStateKey, initialCount) { mutableStateMapOf<String, Int>() }
    val lastMoreRequestedAt = remember(viewStateKey) { mutableStateMapOf<String, Int>() }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val gridColumns = HomeResponsiveLayoutPolicy.gridColumns(screenWidthDp - 24, tvNavigation)
    val homeSearchActive = liveSearchQuery.isNotBlank() ||
        (tvNavigation && videoSearchQuery.isNotBlank())
    val unifiedHomeView = !homeSearchActive
    val feeds = feedOrder.map { name ->
        name to when (name) {
            "History" -> if (historyEnabled) watchHistory else emptyList()
            "Relevant" -> if (suggestionsEnabled) suggestedVideos else emptyList()
            else -> if (trendingEnabled) trendingVideos else emptyList()
        }
    }
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

    val homeScroll = rememberLazyListState()
    val selectedArtwork = feeds.firstOrNull { it.first == focusFeed }?.second
        ?.firstOrNull { it.videoId == focusVideoId }?.thumbnailUrl
    Box(modifier = modifier.fillMaxSize().background(TubeDeckBackground).deckScreenBackground()) {
        DeckArtworkHalo(
            thumbnailUrl = selectedArtwork,
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
        )
        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
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
            onListView = { onHomeBrowseModeChange("List") },
            onCardView = { onHomeBrowseModeChange("Tile") }
        )

        LazyColumn(
            state = homeScroll,
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
            if (!playlistMode && unifiedHomeView) {
                if (feeds.isEmpty()) {
                    item(key = "home-all-feeds-off") {
                        Text(
                            "All Home sections are hidden. Enable History, Relevant or Trending in Settings.",
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            color = TubeDeckTextSecondary
                        )
                    }
                }
                if (listView) {
                    // Three independent vertical category lists placed next to each other.
                    // Small phones show a wide, horizontally swipeable panel, avoiding tiny text.
                    item(key = "home-side-by-side-vertical-lists") {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            val visiblePanels = HomeResponsiveLayoutPolicy.listPanelCount(maxWidth.value.toInt(), feeds.size)
                            val panelWidth = ((maxWidth - 24.dp) / visiblePanels)
                                .coerceAtLeast(240.dp)
                            val panelHeight = (
                                LocalConfiguration.current.screenHeightDp -
                                    if (tvNavigation) 165 else 176
                                ).coerceAtLeast(310).dp
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().height(panelHeight),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                items(feeds, key = { it.first }) { (feedName, videos) ->
                                    val visibleCount = visibleCounts[feedName] ?: initialCount
                                    HomeVerticalFeedPanel(
                                        feedName = feedName,
                                        videos = videos,
                                        visibleCount = visibleCount,
                                        batchSize = initialCount,
                                        moreLoading = feedName in moreFeedsLoading ||
                                            (feedName == "Trending" && trendingStatus == "Loading"),
                                        canLoadMore = feedName !in moreFeedsExhausted,
                                        trendingStatus = trendingStatus,
                                        tvNavigation = tvNavigation,
                                        focusVideoId = if (focusFeed == feedName) focusVideoId else null,
                                        firstItemFocus = if (tvNavigation && focusFeed == feedName)
                                            firstVideoFocus else null,
                                        onIncreaseCount = { visibleCounts[feedName] = it },
                                        onLoadMore = { onLoadMoreHomeFeed(feedName) },
                                        onRefresh = if (feedName == "History") onClearHistory
                                            else onRefreshFeed,
                                        onVideoFocused = { video ->
                                            selectedHomeFeed = feedName
                                            selectedHomeVideoId = video.videoId
                                        },
                                        onOpenVideo = onOpenVideo,
                                        modifier = Modifier.width(panelWidth).fillMaxHeight()
                                    )
                                }
                            }
                        }
                    }
                } else {
                    feeds.forEach { (feedName, videos) ->
                        item(key = "responsive-header-$feedName") {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (feedName == "Relevant") "Recommended" else feedName,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TubeDeckTextPrimary
                                )
                                if (feedName != "History") {
                                    IconButton(onClick = onRefreshFeed) {
                                        Icon(Icons.Default.Refresh, "Refresh $feedName")
                                    }
                                }
                            }
                        }
                        val visibleCount = visibleCounts[feedName] ?: initialCount
                        val shown = videos.take(visibleCount)
                        if (listView) {
                            itemsIndexed(
                                shown,
                                key = { index, video -> "home-list-$feedName-$index-${video.videoId}" }
                            ) { _, video ->
                                AdaptiveHomeVideoCard(
                                    video = video,
                                    listView = true,
                                    tvNavigation = tvNavigation,
                                    firstItemFocus = if (tvNavigation && focusFeed == feedName &&
                                        focusVideoId == video.videoId) firstVideoFocus else null,
                                    onVideoFocused = {
                                        selectedHomeFeed = feedName
                                        selectedHomeVideoId = it.videoId
                                    },
                                    onOpenVideo = onOpenVideo,
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            itemsIndexed(
                                shown.chunked(gridColumns),
                                key = { index, _ -> "home-tiles-$feedName-$index" }
                            ) { _, row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    row.forEach { video ->
                                        AdaptiveHomeVideoCard(
                                            video = video,
                                            listView = false,
                                            tvNavigation = tvNavigation,
                                            firstItemFocus = if (tvNavigation && focusFeed == feedName &&
                                                focusVideoId == video.videoId) firstVideoFocus else null,
                                            onVideoFocused = {
                                                selectedHomeFeed = feedName
                                                selectedHomeVideoId = it.videoId
                                            },
                                            onOpenVideo = onOpenVideo,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(gridColumns - row.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        item(key = "home-end-$feedName") {
                            val loading = feedName in moreFeedsLoading ||
                                (feedName == "Trending" && trendingStatus == "Loading")
                            val exhausted = feedName in moreFeedsExhausted
                            LaunchedEffect(feedName, videos.size, visibleCount, loading, exhausted) {
                                if (videos.size > visibleCount) {
                                    visibleCounts[feedName] = visibleCount + initialCount
                                } else if (videos.isNotEmpty() && !loading && !exhausted &&
                                    lastMoreRequestedAt[feedName] != videos.size) {
                                    lastMoreRequestedAt[feedName] = videos.size
                                    onLoadMoreHomeFeed(feedName)
                                }
                            }
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    loading -> CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp), strokeWidth = 2.dp
                                    )
                                    videos.isEmpty() -> Text(
                                        if (feedName == "Trending" && trendingStatus == "Retry")
                                            "Trending unavailable — tap Refresh to retry"
                                        else "No videos available yet",
                                        color = TubeDeckTextSecondary, fontSize = 12.sp
                                    )
                                    exhausted -> Text(
                                        "End of $feedName", color = TubeDeckTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                    }
            } else if (!playlistMode && !homeSearchActive) {
                // Adapt to *measured* width, not pixel resolution or device label.
                // Phones must never squeeze three panels into 100-200dp columns.
                item(key = "home-three-feeds") {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        val availableWidth = maxWidth
                        val layout = HomeLayoutPolicy.decide(
                            availableWidthDp = maxWidth.value.toInt(),
                            threeColumnTheme = deckTheme.homePresentation == DeckHomePresentation.THREE_COLUMN
                        )

                        @Composable
                        fun Feed(index: Int, feedModifier: Modifier, horizontalCards: Boolean) {
                            when (feedOrder.getOrNull(index) ?: return) {
                                "History" -> HomeFeedColumn(
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
                                "Relevant" -> HomeFeedColumn(
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
                            DeckHomePresentation.NEO_GLASS_LOUNGE -> Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = TubeDeckSurfaceVariant.copy(alpha = .82f),
                                    tonalElevation = 5.dp,
                                    modifier = Modifier.fillMaxWidth().border(
                                        1.dp, TubeDeckAccentCyan.copy(alpha = .40f), RoundedCornerShape(24.dp)
                                    )
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text("NEOGLASS   /   YOUR SCREEN, REIMAGINED",
                                            color = TubeDeckAccentCyan, fontSize = 11.sp)
                                        suggestedVideos.firstOrNull()?.let { HomeSpotlight(it, "Tonight's highlight", onOpenVideo) }
                                    }
                                }
                                if (availableWidth >= 800.dp) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Feed(0, Modifier.weight(1f), horizontalCards = false)
                                        Feed(1, Modifier.weight(1f), horizontalCards = false)
                                    }
                                } else {
                                    Feed(0, Modifier.fillMaxWidth(), horizontalCards = true)
                                    Feed(1, Modifier.fillMaxWidth(), horizontalCards = true)
                                }
                                Feed(2, Modifier.fillMaxWidth(), horizontalCards = true)
                            }

                            DeckHomePresentation.CINEMA_DECK_STAGE -> Column(
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                Text("CINEMA DECK  /  FEATURE PRESENTATION",
                                    color = TubeDeckAccentCyan, fontSize = 11.sp)
                                (suggestedVideos.firstOrNull().takeIf { suggestionsEnabled }
                                    ?: trendingVideos.firstOrNull().takeIf { trendingEnabled })?.let {
                                    HomeSpotlight(it, "NOW SHOWING", onOpenVideo)
                                }
                                ThemeDiscoveryPills(onExecuteSearch, listOf("Trailers", "Movies", "Music", "Documentaries"))
                                Feed(1, Modifier.fillMaxWidth(), horizontalCards = true)
                                Feed(2, Modifier.fillMaxWidth(), horizontalCards = true)
                                Feed(0, Modifier.fillMaxWidth(), horizontalCards = false)
                            }

                            DeckHomePresentation.COMMAND_DRIVE -> Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    color = TubeDeckSurfaceVariant,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(18.dp)) {
                                        Text("COMMAND DRIVE  /  AUDIO-FIRST",
                                            color = TubeDeckAccentCyan,
                                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text("For parked browsing only. Keep video hidden while driving.",
                                            color = TubeDeckTextSecondary, fontSize = 12.sp)
                                        Spacer(Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Button(onClick = onOpenDownloads) { Text("Offline library") }
                                            OutlinedButton(onClick = { onExecuteSearch("music") }) {
                                                Text("Music")
                                            }
                                        }
                                    }
                                }
                                if (availableWidth >= 820.dp) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Feed(0, Modifier.weight(1f), horizontalCards = false)
                                        Column(Modifier.weight(1.2f)) {
                                            Feed(1, Modifier.fillMaxWidth(), horizontalCards = true)
                                            Feed(2, Modifier.fillMaxWidth(), horizontalCards = true)
                                        }
                                    }
                                } else {
                                    repeat(3) { Feed(it, Modifier.fillMaxWidth(), horizontalCards = true) }
                                }
                            }

                            DeckHomePresentation.STREAMPLUS_LOUNGE -> Column(
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                Text("STREAMPLUS   •   YOUR LIVING ROOM",
                                    color = TubeDeckAccentCyan, fontWeight = FontWeight.Bold)
                                if (availableWidth >= 840.dp) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Column(Modifier.weight(1.65f)) {
                                            (suggestedVideos.firstOrNull().takeIf { suggestionsEnabled }
                                    ?: trendingVideos.firstOrNull().takeIf { trendingEnabled })?.let {
                                                HomeSpotlight(it, "FEATURED TONIGHT", onOpenVideo)
                                            }
                                        }
                                        Feed(0, Modifier.weight(1f), horizontalCards = false)
                                    }
                                } else {
                                    suggestedVideos.firstOrNull()?.let {
                                        HomeSpotlight(it, "FEATURED", onOpenVideo)
                                    }
                                    Feed(0, Modifier.fillMaxWidth(), horizontalCards = true)
                                }
                                Feed(1, Modifier.fillMaxWidth(), horizontalCards = true)
                                Feed(2, Modifier.fillMaxWidth(), horizontalCards = true)
                            }

                            DeckHomePresentation.MIDNIGHT_EDITORIAL -> Column(
                                verticalArrangement = Arrangement.spacedBy(26.dp)
                            ) {
                                Text("THE MIDNIGHT EDITION",
                                    color = TubeDeckTextPrimary,
                                    fontWeight = FontWeight.Light, fontSize = 25.sp)
                                HorizontalDivider(color = TubeDeckAccentCyan.copy(alpha = .65f))
                                (suggestedVideos.firstOrNull().takeIf { suggestionsEnabled }
                                    ?: trendingVideos.firstOrNull().takeIf { trendingEnabled })?.let {
                                    HomeSpotlight(it, "THE FEATURE", onOpenVideo)
                                }
                                if (availableWidth >= 850.dp) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                                        Feed(1, Modifier.weight(1.45f), horizontalCards = true)
                                        Feed(0, Modifier.weight(1f), horizontalCards = false)
                                    }
                                } else {
                                    Feed(1, Modifier.fillMaxWidth(), horizontalCards = true)
                                    Feed(0, Modifier.fillMaxWidth(), horizontalCards = false)
                                }
                                Feed(2, Modifier.fillMaxWidth(), horizontalCards = true)
                            }

                            DeckHomePresentation.CYBER_COMMAND -> Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(color = TubeDeckAccentCyan.copy(alpha = .13f),
                                    shape = RoundedCornerShape(3.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("TUBEDECK  //  COMMAND CONSOLE", color = TubeDeckAccentCyan)
                                        Text("SIGNAL: READY", color = TubeDeckTextPrimary)
                                    }
                                }
                                if (availableWidth >= 880.dp) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Feed(1, Modifier.weight(1.5f), horizontalCards = false)
                                        Column(Modifier.weight(1f)) {
                                            Feed(2, Modifier.fillMaxWidth(), horizontalCards = true)
                                            Feed(0, Modifier.fillMaxWidth(), horizontalCards = true)
                                        }
                                    }
                                } else {
                                    repeat(3) { Feed(it, Modifier.fillMaxWidth(), horizontalCards = false) }
                                }
                            }

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
                                if (availableWidth >= 840.dp) {
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
                                repeat(3) { index ->
                                    Feed(index, Modifier.fillMaxWidth(), horizontalCards = true)
                                }
                            }

                            DeckHomePresentation.DESKTOP_TABLE -> Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                repeat(3) { index ->
                                    Feed(index, Modifier.fillMaxWidth(), horizontalCards = false)
                                }
                            }

                            DeckHomePresentation.HUD_CONSOLE -> Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (availableWidth >= 760.dp) {
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
                                    HomeSpotlight(video, "Featured", onOpenVideo)
                                }
                                repeat(3) { index ->
                                    Feed(index, Modifier.fillMaxWidth(), horizontalCards = true)
                                }
                            }

                            DeckHomePresentation.GLASS_CAROUSEL -> Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                suggestedVideos.firstOrNull()?.let { video ->
                                    HomeSpotlight(video, "Featured", onOpenVideo)
                                }
                                repeat(3) { index ->
                                    Feed(index, Modifier.fillMaxWidth(), horizontalCards = true)
                                }
                            }

                            DeckHomePresentation.AMOLED_INDEX -> Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                repeat(3) { index ->
                                    Feed(index, Modifier.fillMaxWidth(), horizontalCards = false)
                                }
                            }

                            DeckHomePresentation.ARCADE_GALLERY -> Column(
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                repeat(3) { index ->
                                    Feed(index, Modifier.fillMaxWidth(), horizontalCards = false)
                                }
                            }

                            DeckHomePresentation.MINIMAL_EDITORIAL -> Column(
                                verticalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                suggestedVideos.firstOrNull()?.let { video ->
                                    HomeSpotlight(video, "EDITOR'S PICK", onOpenVideo)
                                }
                                Feed(1, Modifier.fillMaxWidth(), horizontalCards = true)
                                Feed(0, Modifier.fillMaxWidth(), horizontalCards = false)
                                Feed(2, Modifier.fillMaxWidth(), horizontalCards = true)
                            }
                            DeckHomePresentation.YOUTUBE_STANDARD -> Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (suggestionsEnabled) YouTubeFeedGrid(
                                    videos = if (suggestionsEnabled) suggestedVideos else emptyList(),
                                    onOpenVideo = onOpenVideo,
                                    desktop = false,
                                    listView = listView,
                                    firstItemFocus = if (focusFeed == "Relevant" && tvNavigation) firstVideoFocus else null,
                                    focusVideoId = if (focusFeed == "Relevant") focusVideoId else null,
                                    onVideoFocused = {
                                        selectedHomeFeed = "Relevant"
                                        selectedHomeVideoId = it.videoId
                                    },
                                    onMore = { onLoadMoreHomeFeed("Relevant") },
                                    canLoadMore = "Relevant" !in moreFeedsExhausted,
                                    moreLoading = "Relevant" in moreFeedsLoading
                                )
                                if (historyEnabled) SmartTubeBrowseRail(
                                    "History", if (historyEnabled) watchHistory else emptyList(),
                                    onOpenVideo, listView,
                                    onMore = { onLoadMoreHomeFeed("History") },
                                    canLoadMore = "History" !in moreFeedsExhausted,
                                    moreLoading = "History" in moreFeedsLoading,
                                    onRefresh = onClearHistory, actionLabel = "Clear history"
                                )
                                if (trendingEnabled) SmartTubeBrowseRail(
                                    "Trending", if (trendingEnabled) trendingVideos else emptyList(),
                                    onOpenVideo, listView,
                                    onMore = { onLoadMoreHomeFeed("Trending") },
                                    canLoadMore = "Trending" !in moreFeedsExhausted,
                                    moreLoading = "Trending" in moreFeedsLoading,
                                    onRefresh = onRefreshFeed
                                )
                            }
                            DeckHomePresentation.YOUTUBE_DESKTOP -> Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                if (suggestionsEnabled) YouTubeFeedGrid(
                                    videos = if (suggestionsEnabled) suggestedVideos else emptyList(),
                                    onOpenVideo = onOpenVideo,
                                    desktop = true,
                                    listView = listView,
                                    firstItemFocus = if (focusFeed == "Relevant" && tvNavigation) firstVideoFocus else null,
                                    focusVideoId = if (focusFeed == "Relevant") focusVideoId else null,
                                    onVideoFocused = {
                                        selectedHomeFeed = "Relevant"
                                        selectedHomeVideoId = it.videoId
                                    },
                                    onMore = { onLoadMoreHomeFeed("Relevant") },
                                    canLoadMore = "Relevant" !in moreFeedsExhausted,
                                    moreLoading = "Relevant" in moreFeedsLoading
                                )
                                if (historyEnabled) SmartTubeBrowseRail(
                                    "Your history", if (historyEnabled) watchHistory else emptyList(),
                                    onOpenVideo, listView,
                                    onMore = { onLoadMoreHomeFeed("History") },
                                    canLoadMore = "History" !in moreFeedsExhausted,
                                    moreLoading = "History" in moreFeedsLoading,
                                    onRefresh = onClearHistory, actionLabel = "Clear history"
                                )
                                if (trendingEnabled) SmartTubeBrowseRail(
                                    "Trending", if (trendingEnabled) trendingVideos else emptyList(),
                                    onOpenVideo, listView,
                                    onMore = { onLoadMoreHomeFeed("Trending") },
                                    canLoadMore = "Trending" !in moreFeedsExhausted,
                                    moreLoading = "Trending" in moreFeedsLoading,
                                    onRefresh = onRefreshFeed
                                )
                            }

                            DeckHomePresentation.SMARTTUBE_TV -> Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                // Independent TV-first browse experience with compact dark
                                // horizontal rails, clear section headers and D-pad focus.
                                SmartTubeBrowseRail(
                                    "Recommended",
                                    if (suggestionsEnabled) suggestedVideos else emptyList(),
                                    onOpenVideo,
                                    listView,
                                    firstItemFocus = if (focusFeed == "Relevant" && tvNavigation) firstVideoFocus else null,
                                    focusVideoId = if (focusFeed == "Relevant") focusVideoId else null,
                                    onVideoFocused = {
                                        selectedHomeFeed = "Relevant"
                                        selectedHomeVideoId = it.videoId
                                    },
                                    onMore = { onLoadMoreHomeFeed("Relevant") },
                                    canLoadMore = "Relevant" !in moreFeedsExhausted,
                                    moreLoading = "Relevant" in moreFeedsLoading,
                                    onRefresh = onRefreshFeed
                                )
                                if (historyEnabled) SmartTubeBrowseRail(
                                    "History",
                                    if (historyEnabled) watchHistory else emptyList(),
                                    onOpenVideo,
                                    listView,
                                    firstItemFocus = if (focusFeed == "History" && tvNavigation) firstVideoFocus else null,
                                    focusVideoId = if (focusFeed == "History") focusVideoId else null,
                                    onVideoFocused = {
                                        selectedHomeFeed = "History"
                                        selectedHomeVideoId = it.videoId
                                    },
                                    onMore = { onLoadMoreHomeFeed("History") },
                                    canLoadMore = "History" !in moreFeedsExhausted,
                                    moreLoading = "History" in moreFeedsLoading,
                                    onRefresh = onClearHistory,
                                    actionLabel = "Clear history"
                                )
                                if (trendingEnabled) SmartTubeBrowseRail(
                                    "Trending · ${countryCode.uppercase(Locale.ROOT)}",
                                    if (trendingEnabled) trendingVideos else emptyList(),
                                    onOpenVideo,
                                    listView,
                                    firstItemFocus = if (focusFeed == "Trending" && tvNavigation) firstVideoFocus else null,
                                    focusVideoId = if (focusFeed == "Trending") focusVideoId else null,
                                    onVideoFocused = {
                                        selectedHomeFeed = "Trending"
                                        selectedHomeVideoId = it.videoId
                                    },
                                    onMore = { onLoadMoreHomeFeed("Trending") },
                                    canLoadMore = "Trending" !in moreFeedsExhausted,
                                    moreLoading = "Trending" in moreFeedsLoading,
                                    onRefresh = onRefreshFeed
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

/** Responsive, keyboard/TV-focusable quick discovery lane for premium themes. */
@Composable
private fun ThemeDiscoveryPills(
    onSearch: (String) -> Unit,
    terms: List<String> = listOf("Music", "Technology", "Film", "Gaming", "Travel", "Science")
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        contentPadding = PaddingValues(horizontal = 6.dp)
    ) {
        items(terms.size) { index ->
            val term = terms[index]
            FilterChip(
                selected = false,
                onClick = { onSearch(term) },
                label = { Text(term, maxLines = 1) },
                modifier = Modifier.tvInputHighlight(RoundedCornerShape(16.dp))
            )
        }
    }
}

/** Full-screen, measured-width video discovery grid for YouTube-style modes. */
@Composable
private fun YouTubeFeedGrid(
    videos: List<HomeVideo>,
    onOpenVideo: (HomeVideo) -> Unit,
    desktop: Boolean,
    listView: Boolean,
    firstItemFocus: FocusRequester?,
    focusVideoId: String?,
    onVideoFocused: (HomeVideo) -> Unit,
    onMore: () -> Unit,
    canLoadMore: Boolean,
    moreLoading: Boolean
) {
    val state = rememberLazyGridState()
    val titleMetrics = rememberVideoTitleMetrics()
    val focusIndex = videos.indexOfFirst { it.videoId == focusVideoId }
    LaunchedEffect(focusIndex) {
        if (focusIndex >= 0) state.scrollToItem(focusIndex)
    }
    val lastVisible by remember(state) {
        derivedStateOf { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    }
    var lastRequestedSize by remember(desktop) { mutableIntStateOf(-1) }
    LaunchedEffect(lastVisible, videos.size, moreLoading, canLoadMore) {
        if (canLoadMore && !moreLoading && videos.isNotEmpty() &&
            lastVisible >= videos.size - 3 && lastRequestedSize != videos.size) {
            lastRequestedSize = videos.size
            onMore()
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = when {
                listView -> 1
                !desktop && maxWidth < 600.dp -> 1
                maxWidth >= 1250.dp -> 4
                maxWidth >= 950.dp -> 3
                maxWidth >= 720.dp -> 2
                else -> 2
            }
            val height = if (desktop) 790.dp else 870.dp
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxWidth().height(height),
                state = state,
                contentPadding = PaddingValues(horizontal = if (desktop) 12.dp else 6.dp),
                verticalArrangement = Arrangement.spacedBy(if (desktop) 18.dp else 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                gridItemsIndexed(videos, key = { index, video -> "${video.videoId}-$index" }) { _, video ->
                    val focused = video.videoId == focusVideoId
                    val card = Modifier.fillMaxWidth()
                        .then(if (focused && firstItemFocus != null)
                            Modifier.focusRequester(firstItemFocus) else Modifier)
                        .tvInputHighlight(RoundedCornerShape(10.dp))
                        .clickable { onVideoFocused(video); onOpenVideo(video) }
                        .padding(bottom = 8.dp)
                    if (listView) {
                        Row(card, verticalAlignment = Alignment.Top) {
                            VideoThumbnail(video, Modifier.width(132.dp).aspectRatio(16f / 9f))
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                ReadableVideoTitle(video.title, maxLines = 4)
                                Spacer(Modifier.height(5.dp))
                                ReadableVideoMetadata(video.channelTitle, maxLines = 2)
                            }
                        }
                    } else {
                        Column(card) {
                            VideoThumbnail(video, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                            Spacer(Modifier.height(7.dp))
                            ReadableVideoTitle(video.title)
                            Spacer(Modifier.height(5.dp))
                            ReadableVideoMetadata(video.channelTitle, maxLines = 1)
                        }
                    }
                }
            }
        }
        if (canLoadMore) {
            if (moreLoading) {
                Box(Modifier.fillMaxWidth().height(38.dp),
                    contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            } else {
                TextButton(
                    onClick = { lastRequestedSize = videos.size; onMore() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("Show more") }
            }
        }
    }
}

/** Independent SmartTube-style horizontal video shelf for touch and TV D-pad. */
@Composable
private fun SmartTubeBrowseRail(
    title: String, videos: List<HomeVideo>,
    onOpenVideo: (HomeVideo) -> Unit, listView: Boolean,
    firstItemFocus: FocusRequester? = null,
    focusVideoId: String? = null,
    onVideoFocused: (HomeVideo) -> Unit = {},
    onMore: () -> Unit, canLoadMore: Boolean, moreLoading: Boolean,
    onRefresh: () -> Unit = {}, actionLabel: String = "Refresh"
) {
    val state = rememberLazyListState()
    val titleMetrics = rememberVideoTitleMetrics()
    val focusIndex = videos.indexOfFirst { it.videoId == focusVideoId }
    LaunchedEffect(focusIndex) { if (focusIndex >= 0) state.scrollToItem(focusIndex) }
    val lastVisible by remember(state) {
        derivedStateOf { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    }
    var lastRequestedSize by remember(title) { mutableIntStateOf(-1) }
    LaunchedEffect(lastVisible, videos.size, canLoadMore, moreLoading) {
        if (canLoadMore && !moreLoading && videos.isNotEmpty() &&
            lastVisible >= videos.size - 2 && lastRequestedSize != videos.size) {
            lastRequestedSize = videos.size
            onMore()
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, modifier = Modifier.weight(1f),
                color = TubeDeckTextPrimary, fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onRefresh, modifier = Modifier.size(34.dp)) {
                Icon(if (actionLabel == "Clear history") Icons.Default.DeleteOutline
                    else Icons.Default.Refresh, contentDescription = "$actionLabel · $title",
                    tint = TubeDeckTextSecondary, modifier = Modifier.size(18.dp))
            }
        }
        if (videos.isEmpty()) {
            Text("No videos in this section", color = TubeDeckTextSecondary,
                fontSize = 12.sp, modifier = Modifier.padding(start = 14.dp, bottom = 10.dp))
        } else {
            LazyRow(
                state = state,
                modifier = Modifier.fillMaxWidth().height(
                    if (listView) titleMetrics.listCardHeightDp.dp else titleMetrics.shelfCardHeightDp.dp
                ),
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(videos, key = { index, video -> "${video.videoId}-$index" }) { _, video ->
                    val focused = video.videoId == focusVideoId
                    val card = Modifier
                        .width(if (listView) 345.dp else 260.dp)
                        .then(if (focused && firstItemFocus != null)
                            Modifier.focusRequester(firstItemFocus) else Modifier)
                        .onFocusChanged { if (it.isFocused) onVideoFocused(video) }
                        .tvInputHighlight(RoundedCornerShape(3.dp))
                        .clickable { onVideoFocused(video); onOpenVideo(video) }
                    if (listView) {
                        Row(card, verticalAlignment = Alignment.Top) {
                            VideoThumbnail(video, Modifier.width(128.dp).aspectRatio(16f / 9f))
                            Column(Modifier.weight(1f).padding(start = 9.dp)) {
                                ReadableVideoTitle(video.title, maxLines = 4)
                                Spacer(Modifier.height(6.dp))
                                ReadableVideoMetadata(video.channelTitle, maxLines = 2)
                            }
                        }
                    } else {
                        Column(card) {
                            VideoThumbnail(video, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                            Spacer(Modifier.height(6.dp))
                            ReadableVideoTitle(video.title)
                            Spacer(Modifier.height(4.dp))
                            ReadableVideoMetadata(video.channelTitle, maxLines = 1)
                        }
                    }
                }
                if (canLoadMore) item(key = "more-$title") {
                    Box(Modifier.width(96.dp).fillParentMaxHeight(),
                        contentAlignment = Alignment.Center) {
                        if (moreLoading) CircularProgressIndicator(
                            Modifier.size(24.dp), strokeWidth = 2.dp
                        ) else TextButton(onClick = {
                            lastRequestedSize = videos.size; onMore()
                        }) { Text("More") }
                    }
                }
            }
        }
    }
}

/** A real full-width spotlight for cinema, glass and editorial themes. */
@Composable
private fun HomeSpotlight(
    video: HomeVideo,
    eyebrow: String,
    onOpenVideo: (HomeVideo) -> Unit
) {
    val profile = LocalDeckThemeProfile.current
    val editorial = profile.homePresentation == DeckHomePresentation.MINIMAL_EDITORIAL ||
        profile.homePresentation == DeckHomePresentation.MIDNIGHT_EDITORIAL
    val cinema = profile.homePresentation in setOf(
        DeckHomePresentation.CINEMA_STRIPS, DeckHomePresentation.CINEMA_DECK_STAGE,
        DeckHomePresentation.STREAMPLUS_LOUNGE
    )
    val shape = RoundedCornerShape(if (editorial) 0.dp else profile.cornerRadiusDp.dp)
    Surface(
        color = if (editorial) TubeDeckBackground else TubeDeckSurfaceVariant,
        shape = shape,
        modifier = Modifier.fillMaxWidth().tvInputHighlight(shape).clickable { onOpenVideo(video) }
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (editorial) {
                Text(eyebrow, color = TubeDeckAccentCyan,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 10.dp))
                Text(video.title, color = TubeDeckTextPrimary,
                    fontSize = 23.sp, lineHeight = 27.sp, maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
            Box {
                VideoThumbnail(
                    video,
                    Modifier.fillMaxWidth().aspectRatio(if (cinema) 2.3f else 16f / 9f)
                )
                if (LocalDeckExperience.current.artworkAtmosphere) {
                    Box(
                        Modifier.matchParentSize().background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = if (cinema) .45f else .20f)
                                )
                            )
                        )
                    )
                }
                if (!editorial) Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                    color = TubeDeckBackground.copy(alpha = .88f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(eyebrow, color = TubeDeckAccentCyan, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            if (!editorial) {
                Text(video.title, color = TubeDeckTextPrimary,
                    fontSize = if (cinema) 20.sp else 17.sp,
                    lineHeight = if (cinema) 24.sp else 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp))
            }
            Text(video.channelTitle, color = TubeDeckTextSecondary, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 4.dp))
        }
    }
}

/**
 * Independent bounded vertical scrolling keeps History, Recommended and Trending
 * side by side on TV/tablet, with swipeable columns on narrow phones.
 * Loading is triggered only as the user approaches the end of this category.
 */
@Composable
private fun HomeVerticalFeedPanel(
    feedName: String,
    videos: List<HomeVideo>,
    visibleCount: Int,
    batchSize: Int,
    moreLoading: Boolean,
    canLoadMore: Boolean,
    trendingStatus: String,
    tvNavigation: Boolean,
    focusVideoId: String?,
    firstItemFocus: FocusRequester?,
    onIncreaseCount: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onVideoFocused: (HomeVideo) -> Unit,
    onOpenVideo: (HomeVideo) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val visible = videos.take(visibleCount)
    var lastRequestedSize by remember(feedName) { mutableIntStateOf(-1) }
    val lastVisibleIndex by remember(listState) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    }
    LaunchedEffect(lastVisibleIndex, visible.size, videos.size, moreLoading, canLoadMore) {
        if (visible.isNotEmpty() && lastVisibleIndex >= visible.size - 2) {
            when {
                videos.size > visibleCount -> onIncreaseCount(visibleCount + batchSize)
                canLoadMore && !moreLoading && lastRequestedSize != videos.size -> {
                    lastRequestedSize = videos.size
                    onLoadMore()
                }
            }
        }
    }
    Surface(
        modifier = modifier,
        color = TubeDeckSurface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 12.dp, end = 5.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (feedName == "Relevant") "Recommended" else feedName,
                    color = TubeDeckTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        lastRequestedSize = -1
                        onRefresh()
                    },
                    modifier = Modifier.size(38.dp).tvInputHighlight(RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        if (feedName == "History") Icons.Default.DeleteOutline else Icons.Default.Refresh,
                        contentDescription = if (feedName == "History") "Clear history" else "Refresh $feedName",
                        tint = TubeDeckTextSecondary
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    visible,
                    key = { index, video -> "$feedName-$index-${video.videoId}" }
                ) { _, video ->
                    AdaptiveHomeVideoCard(
                        video = video,
                        listView = true,
                        tvNavigation = tvNavigation,
                        firstItemFocus = if (focusVideoId == video.videoId)
                            firstItemFocus else null,
                        onVideoFocused = onVideoFocused,
                        onOpenVideo = onOpenVideo,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item(key = "feed-status-$feedName") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            moreLoading -> CircularProgressIndicator(
                                modifier = Modifier.size(22.dp), strokeWidth = 2.dp
                            )
                            videos.isEmpty() -> Text(
                                if (feedName == "Trending" && trendingStatus == "Retry")
                                    "Trending unavailable — refresh to retry"
                                else "No videos yet",
                                color = TubeDeckTextSecondary, fontSize = 12.sp
                            )
                            !canLoadMore && visible.size >= videos.size -> Text(
                                "End of $feedName",
                                color = TubeDeckTextSecondary, fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One vertical list item or one responsive tile. Unlike horizontal theme rails,
 * the card owns its natural height so long titles never get ellipsized or clipped.
 * All navigation stays on the single parent LazyColumn for TV D-pad consistency.
 */
@Composable
private fun AdaptiveHomeVideoCard(
    video: HomeVideo,
    listView: Boolean,
    tvNavigation: Boolean,
    firstItemFocus: FocusRequester?,
    onVideoFocused: (HomeVideo) -> Unit,
    onOpenVideo: (HomeVideo) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(LocalDeckThemeProfile.current.cornerRadiusDp.dp)
    Surface(
        modifier = modifier
            .then(if (firstItemFocus != null) Modifier.focusRequester(firstItemFocus) else Modifier)
            .onFocusChanged { if (it.isFocused) onVideoFocused(video) }
            .tvInputHighlight(shape)
            .clickable {
                onVideoFocused(video)
                onOpenVideo(video)
            },
        color = TubeDeckSurface,
        shape = shape
    ) {
        if (listView) {
            Row(
                Modifier.fillMaxWidth().padding(9.dp),
                verticalAlignment = Alignment.Top
            ) {
                VideoThumbnail(
                    video,
                    Modifier.width(if (tvNavigation) 180.dp else 118.dp)
                        .aspectRatio(16f / 9f)
                )
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    ReadableVideoTitle(video.title, maxLines = Int.MAX_VALUE)
                    Spacer(Modifier.height(6.dp))
                    ReadableVideoMetadata(video.channelTitle, maxLines = 3)
                    VideoViews(video.viewCount)
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                VideoThumbnail(video, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                Spacer(Modifier.height(7.dp))
                ReadableVideoTitle(video.title, maxLines = Int.MAX_VALUE)
                Spacer(Modifier.height(5.dp))
                ReadableVideoMetadata(video.channelTitle, maxLines = 3)
                VideoViews(video.viewCount)
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
    val gridState = rememberLazyGridState()
    val profile = LocalDeckThemeProfile.current
    val titleMetrics = rememberVideoTitleMetrics()
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
    val lastIndex by remember(state, gridState, profile.homePresentation) {
        derivedStateOf {
            if (profile.homePresentation == DeckHomePresentation.ARCADE_GALLERY) {
                gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            } else state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }
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
                    val heading = when (profile.homePresentation) {
                        DeckHomePresentation.DESKTOP_TABLE -> "> ${title.uppercase(Locale.ROOT)} /"
                        DeckHomePresentation.HUD_CONSOLE -> "▸ ${title.uppercase(Locale.ROOT)} · ${videos.size}"
                        DeckHomePresentation.ARCADE_GALLERY -> title.uppercase(Locale.ROOT)
                        DeckHomePresentation.IPTV_DASHBOARD -> "${title.uppercase(Locale.ROOT)}  •  ${videos.size}"
                        DeckHomePresentation.AMOLED_INDEX -> title.uppercase(Locale.ROOT)
                        DeckHomePresentation.MINIMAL_EDITORIAL -> "${title}."
                        else -> title
                    }
                    Text(
                        heading, color = if (profile.homePresentation == DeckHomePresentation.ARCADE_GALLERY ||
                                profile.homePresentation == DeckHomePresentation.HUD_CONSOLE)
                                TubeDeckAccentCyan else TubeDeckTextPrimary,
                        fontSize = if (profile.homePresentation == DeckHomePresentation.MINIMAL_EDITORIAL) 20.sp
                            else if (profile.homePresentation == DeckHomePresentation.DESKTOP_TABLE) 13.sp
                            else 16.sp,
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
                                ReadableVideoTitle(video.title, maxLines = 4)
                                ReadableVideoMetadata(video.channelTitle, maxLines = 2)
                            }
                        }
                    } else {
                        Column(itemModifier) {
                            VideoThumbnail(video, Modifier.fillMaxWidth().aspectRatio(imageRatio))
                            Spacer(Modifier.height(5.dp))
                            ReadableVideoTitle(video.title)
                            Spacer(Modifier.height(3.dp))
                            ReadableVideoMetadata(video.channelTitle, maxLines = 2)
                            VideoViews(video.viewCount)
                        }
                    }
                }
                if (profile.homePresentation == DeckHomePresentation.ARCADE_GALLERY) {
                    // A true arcade selection grid, not recolored horizontal shelves.
                    BoxWithConstraints(Modifier.fillMaxWidth().height(620.dp)) {
                        val columns = if (maxWidth >= 500.dp) 3 else 2
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            gridItemsIndexed(videos, key = { index, item -> "${item.videoId}-$index" }) { _, video ->
                                HomeCard(video, Modifier.fillMaxWidth())
                            }
                        }
                    }
                } else if (horizontal) {
                    LazyRow(
                        state = state,
                        modifier = Modifier.fillMaxWidth().height(
                            if (listView) titleMetrics.listCardHeightDp.dp
                            else if (profile.geometry == DeckCardGeometry.CINEMA)
                                (titleMetrics.shelfCardHeightDp + 25).dp
                            else titleMetrics.shelfCardHeightDp.dp
                        ),
                        contentPadding = PaddingValues(end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(videos, key = { index, item -> "${item.videoId}-$index" }) { _, video ->
                            HomeCard(video, Modifier.width(
                                if (listView) 330.dp else if (profile.geometry == DeckCardGeometry.CINEMA) 290.dp else 255.dp
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
    val cardColumns = HomeResponsiveLayoutPolicy.gridColumns(
        LocalConfiguration.current.screenWidthDp - 40, rememberAdaptiveTvNavigation()
    )
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
                        videos.chunked(cardColumns).forEach { row ->
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
                                repeat(cardColumns - row.size) { Spacer(Modifier.weight(1f)) }
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
            ReadableVideoTitle(video.title, maxLines = Int.MAX_VALUE)
            Spacer(Modifier.height(4.dp))
            ReadableVideoMetadata(video.channelTitle, maxLines = 2)
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
        ReadableVideoTitle(video.title, maxLines = Int.MAX_VALUE)
        Spacer(Modifier.height(3.dp))
        ReadableVideoMetadata(video.channelTitle, maxLines = 2)
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

/**
 * A fixed-height search launcher, never an editable field in the Home page.
 * It stays one row in portrait AND landscape. Creating the search dialog only
 * from a user action avoids Android restoring IME focus when Home launches.
 */
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
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val compactHeader = configuration.screenWidthDp < 420
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchDialogMode by remember { mutableStateOf<String?>(null) }
    // Do not preserve a focused search dialog across app relaunch, back stack
    // restoration or orientation changes.
    LaunchedEffect(configuration.orientation) {
        searchDialogMode = null
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = TubeDeckSurface,
        tonalElevation = if (profile.navVisual == DeckNavVisual.ICONS) 0.dp else 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = if (landscape) 8.dp else 10.dp, vertical = 4.dp)
                .heightIn(min = 46.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.SmartDisplay, contentDescription = null,
                tint = TubeDeckAccentCyan, modifier = Modifier.size(23.dp)
            )
            if (!landscape && !compactHeader) {
                Text(
                    "MyTube", color = TubeDeckTextPrimary,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
            }
            val searchShape = RoundedCornerShape(14.dp)
            Surface(
                modifier = Modifier.weight(1f)
                    .height(42.dp)
                    .tvInputHighlight(searchShape)
                    .clickable { searchDialogMode = "search" },
                color = TubeDeckSurfaceVariant,
                shape = searchShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Icon(
                        Icons.Default.Search, contentDescription = null,
                        tint = TubeDeckAccentCyan, modifier = Modifier.size(19.dp)
                    )
                    Text(
                        searchPlaceholder,
                        color = TubeDeckTextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (recentSearches.isNotEmpty() && !compactHeader &&
                (!landscape || configuration.screenWidthDp >= 720)) {
                IconButton(
                    onClick = { searchDialogMode = "history" },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.History, contentDescription = "Recent searches",
                        tint = TubeDeckTextPrimary
                    )
                }
            }
            if (!compactHeader && (!landscape || configuration.screenWidthDp >= 700)) {
                GoogleAccountButton(
                    account = googleAccount,
                    onClick = onGoogleAccountClick,
                    showLabelWhenSignedOut = false
                )
            }
            IconButton(onClick = onOpenDownloads, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Download, "Downloads", tint = TubeDeckTextPrimary)
            }
        }
    }
    if (searchDialogMode != null) {
        com.drivedeck.tubedeck.ui.components.CompactSearchPanel(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            onSubmit = onSearch,
            recentSearches = recentSearches,
            onRemoveRecentSearch = onRemoveRecentSearch,
            onClearRecentSearches = onClearRecentSearches,
            onDismiss = { searchDialogMode = null },
            placeholder = searchPlaceholder,
            startWithKeyboard = searchDialogMode == "search",
            historyFirst = searchDialogMode == "history"
        )
    }
}
