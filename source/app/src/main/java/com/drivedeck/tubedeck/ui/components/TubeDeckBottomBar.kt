package com.drivedeck.tubedeck.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivedeck.tubedeck.ActiveScreen
import com.drivedeck.tubedeck.ui.theme.*

private fun deckNavigationIcons(style: DeckNavVisual): List<ImageVector> = when (style) {
    DeckNavVisual.COMMAND -> listOf(
        Icons.Default.Dashboard, Icons.Default.ViewList, Icons.Default.CloudDownload,
        Icons.Default.FolderOpen, Icons.Default.Tune
    )
    DeckNavVisual.MINIMAL -> listOf(
        Icons.Default.Explore, Icons.Default.PlayCircleOutline, Icons.Default.Download,
        Icons.Default.Folder, Icons.Default.Settings
    )
    DeckNavVisual.COMPACT -> listOf(
        Icons.Default.GridView, Icons.Default.QueueMusic, Icons.Default.GetApp,
        Icons.Default.Folder, Icons.Default.Build
    )
    DeckNavVisual.ICONS -> listOf(
        Icons.Default.Home, Icons.Default.PlaylistPlay, Icons.Default.Download,
        Icons.Default.FolderOpen, Icons.Default.Settings
    )
    DeckNavVisual.PILL -> listOf(
        Icons.Default.Home, Icons.Default.QueueMusic, Icons.Default.Download,
        Icons.Default.Folder, Icons.Default.Settings
    )
}

@Composable
fun TubeDeckBottomBar(
    currentScreen: ActiveScreen,
    activeDownloadCount: Int,
    blockedAdsCount: Int,
    onNavigate: (ActiveScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = LocalDeckThemeProfile.current
    val icons = deckNavigationIcons(profile.navVisual)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = TubeDeckBorder)
            .then(when (profile.navigationPlacement) {
                DeckNavigationPlacement.BOTTOM -> Modifier.navigationBarsPadding()
                // A top-mounted navigation row must respect the entire display cutout:
                // status bar plus left/right notch insets on rotated Android TV hardware.
                DeckNavigationPlacement.TOP -> Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                )
                DeckNavigationPlacement.SIDE -> Modifier
            }),
        color = TubeDeckSurface,
        tonalElevation = if (profile.name == "AMOLED Black") 0.dp else 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = profile.contentGapDp.dp / 2,
                    vertical = if (profile.navigationPlacement == DeckNavigationPlacement.TOP) 2.dp
                        else (profile.navigationHeightDp - 48).coerceAtLeast(3).dp / 2
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. HOME (Renamed from Deck)
            DeckNavItem(
                icon = icons[0],
                label = "Home",
                isSelected = currentScreen == ActiveScreen.HOME,
                onClick = { onNavigate(ActiveScreen.HOME) },
                testTag = "nav_home_tab",
                modifier = Modifier.weight(1f)
            )

            // 2. MIXES/PLAYLIST (Renamed from Mixes)
            DeckNavItem(
                icon = icons[1],
                label = "Mixes/Playlist",
                isSelected = currentScreen == ActiveScreen.MIXES,
                onClick = { onNavigate(ActiveScreen.MIXES) },
                testTag = "nav_mixes_tab",
                modifier = Modifier.weight(1.2f)
            )

            // 3. CENTER: DOWNLOADS
            CenterDownloadsButton(
                icon = icons[2],
                isSelected = currentScreen == ActiveScreen.DOWNLOADS,
                badgeCount = activeDownloadCount,
                onClick = { onNavigate(ActiveScreen.DOWNLOADS) },
                modifier = Modifier.padding(horizontal = 2.dp)
            )

            // 4. VAULT
            DeckNavItem(
                icon = icons[3],
                label = "Vault",
                isSelected = currentScreen == ActiveScreen.OFFLINE_VAULT,
                onClick = { onNavigate(ActiveScreen.OFFLINE_VAULT) },
                testTag = "nav_vault_tab",
                modifier = Modifier.weight(1f)
            )

            // 5. SETTINGS
            DeckNavItem(
                icon = icons[4],
                label = "Settings",
                isSelected = currentScreen == ActiveScreen.SETTINGS,
                onClick = { onNavigate(ActiveScreen.SETTINGS) },
                testTag = "nav_settings_tab",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CenterDownloadsButton(
    icon: ImageVector,
    isSelected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = LocalDeckThemeProfile.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .tvInputHighlight(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag("nav_downloads_center_btn")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(if (profile.navVisual == DeckNavVisual.COMPACT) 34.dp else 42.dp)
                    .clip(if (profile.navVisual == DeckNavVisual.COMMAND) RoundedCornerShape(5.dp) else CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                TubeDeckAccentCyan.copy(alpha = if (isSelected) 0.95f else 0.25f),
                                TubeDeckAccentCyan.copy(alpha = if (isSelected) 0.65f else 0.10f)
                            )
                        )
                    )
                    .border(
                        1.5.dp,
                        if (isSelected) TubeDeckAccentCyanGlow else TubeDeckAccentCyan.copy(alpha = 0.5f),
                        if (profile.navVisual == DeckNavVisual.COMMAND) RoundedCornerShape(5.dp) else CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = "Downloads",
                    tint = if (isSelected) TubeDeckBackground else TubeDeckAccentCyan,
                    modifier = Modifier.size(22.dp)
                )
            }

            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(TubeDeckAccentAmber),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 9) "9+" else "$badgeCount",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TubeDeckBackground
                    )
                }
            }
        }

        Text(
            text = "Downloads",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) TubeDeckAccentCyan else TubeDeckTextPrimary
        )
    }
}

@Composable
private fun DeckNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = LocalDeckThemeProfile.current
    val commandTop = profile.navVisual == DeckNavVisual.COMMAND &&
        profile.navigationPlacement == DeckNavigationPlacement.TOP
    val iconOnly = profile.navVisual == DeckNavVisual.ICONS
    val shape = when (profile.navVisual) {
        DeckNavVisual.COMMAND, DeckNavVisual.COMPACT -> RoundedCornerShape(4.dp)
        DeckNavVisual.ICONS -> RoundedCornerShape(0.dp)
        DeckNavVisual.MINIMAL -> RoundedCornerShape(8.dp)
        DeckNavVisual.PILL -> RoundedCornerShape(24.dp)
    }
    val selectedBackground = if (!isSelected) Color.Transparent else when (profile.navVisual) {
        DeckNavVisual.PILL -> TubeDeckAccentCyan.copy(alpha = .20f)
        DeckNavVisual.COMMAND -> TubeDeckAccentCyan.copy(alpha = .12f)
        DeckNavVisual.COMPACT -> TubeDeckAccentCyan.copy(alpha = .17f)
        DeckNavVisual.MINIMAL -> Color.Transparent
        DeckNavVisual.ICONS -> TubeDeckAccentCyan.copy(alpha = .08f)
    }
    // Motion only runs on a real selection change; never schedules idle frames.
    val animatedSelection by animateColorAsState(
        targetValue = selectedBackground,
        animationSpec = tween(durationMillis = when (profile.motionStyle) {
            DeckMotionStyle.INSTANT -> 0
            DeckMotionStyle.SNAP -> 80
            DeckMotionStyle.GLIDE -> 260
            DeckMotionStyle.PULSE -> 165
        }),
        label = "Theme navigation selection"
    )
    Box(
        modifier = modifier
            .padding(horizontal = if (commandTop) 2.dp else 0.dp)
            .clip(shape)
            .background(animatedSelection)
            .tvInputHighlight(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (commandTop) 3.dp else 0.dp, vertical = 4.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        val iconTint = if (isSelected) TubeDeckAccentCyan else TubeDeckTextSecondary
        val iconSize = when (profile.navVisual) {
            DeckNavVisual.COMPACT -> 18.dp
            DeckNavVisual.ICONS -> 25.dp
            DeckNavVisual.MINIMAL -> 17.dp
            else -> 20.dp
        }
        if (commandTop) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(iconSize))
                Text(label, color = iconTint, fontSize = 10.sp,
                    maxLines = 1, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (iconOnly) 0.dp else 2.dp)) {
                Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(iconSize))
                if (!iconOnly) Text(label, color = iconTint,
                    fontSize = if (profile.navVisual == DeckNavVisual.COMPACT) 9.sp else 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1)
            }
        }
    }
}

/**
 * Optional TV-first vertical navigation. This is a real navigation placement,
 * not an overlay drawn across the playing video.
 */
@Composable
fun TubeDeckSideRail(
    currentScreen: ActiveScreen,
    activeDownloadCount: Int,
    onNavigate: (ActiveScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = LocalDeckThemeProfile.current
    val icons = deckNavigationIcons(profile.navVisual)
    val destinations = listOf(
        Triple(ActiveScreen.HOME, "Home", icons[0]),
        Triple(ActiveScreen.MIXES, "Mixes", icons[1]),
        Triple(ActiveScreen.DOWNLOADS, "Downloads", icons[2]),
        Triple(ActiveScreen.OFFLINE_VAULT, "Vault", icons[3]),
        Triple(ActiveScreen.SETTINGS, "Settings", icons[4])
    )
    NavigationRail(
        modifier = modifier.width(when (profile.navVisual) {
            DeckNavVisual.COMPACT -> 72.dp
            DeckNavVisual.COMMAND -> 94.dp
            else -> if (profile.name == "ATV") 98.dp else 78.dp
        })
            .fillMaxHeight().border(1.dp, TubeDeckBorder),
        containerColor = TubeDeckSurface,
        contentColor = TubeDeckTextPrimary,
        header = {
            Icon(
                Icons.Default.PlayCircleFilled,
                contentDescription = "MyTube",
                modifier = Modifier.padding(vertical = 14.dp).size(29.dp),
                tint = TubeDeckAccentCyan
            )
        }
    ) {
        destinations.forEach { (screen, label, icon) ->
            NavigationRailItem(
                selected = currentScreen == screen,
                onClick = { onNavigate(screen) },
                icon = {
                    BadgedBox(badge = {
                        if (screen == ActiveScreen.DOWNLOADS && activeDownloadCount > 0)
                            Badge { Text(activeDownloadCount.coerceAtMost(99).toString()) }
                    }) { Icon(icon, contentDescription = label) }
                },
                label = { Text(label, fontSize = if (profile.name == "ATV") 11.sp else 9.sp) },
                alwaysShowLabel = true,
                modifier = Modifier.tvInputHighlight(
                    RoundedCornerShape(profile.cornerRadiusDp.dp)
                )
            )
        }
    }
}
