package com.drivedeck.tubedeck.ui.components

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

@Composable
fun TubeDeckBottomBar(
    currentScreen: ActiveScreen,
    activeDownloadCount: Int,
    blockedAdsCount: Int,
    onNavigate: (ActiveScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = LocalDeckThemeProfile.current
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
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = currentScreen == ActiveScreen.HOME,
                onClick = { onNavigate(ActiveScreen.HOME) },
                testTag = "nav_home_tab",
                modifier = Modifier.weight(1f)
            )

            // 2. MIXES/PLAYLIST (Renamed from Mixes)
            DeckNavItem(
                icon = Icons.Default.QueueMusic,
                label = "Mixes/Playlist",
                isSelected = currentScreen == ActiveScreen.MIXES,
                onClick = { onNavigate(ActiveScreen.MIXES) },
                testTag = "nav_mixes_tab",
                modifier = Modifier.weight(1.2f)
            )

            // 3. CENTER: DOWNLOADS
            CenterDownloadsButton(
                isSelected = currentScreen == ActiveScreen.DOWNLOADS,
                badgeCount = activeDownloadCount,
                onClick = { onNavigate(ActiveScreen.DOWNLOADS) },
                modifier = Modifier.padding(horizontal = 2.dp)
            )

            // 4. VAULT
            DeckNavItem(
                icon = Icons.Default.Folder,
                label = "Vault",
                isSelected = currentScreen == ActiveScreen.OFFLINE_VAULT,
                onClick = { onNavigate(ActiveScreen.OFFLINE_VAULT) },
                testTag = "nav_vault_tab",
                modifier = Modifier.weight(1f)
            )

            // 5. SETTINGS
            DeckNavItem(
                icon = Icons.Default.Settings,
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
    isSelected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    .size(42.dp)
                    .clip(CircleShape)
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
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Download,
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .tvInputHighlight(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isSelected) TubeDeckAccentCyan else TubeDeckTextSecondary,
                modifier = Modifier.size(20.dp)
            )

            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) TubeDeckAccentCyan else TubeDeckTextSecondary,
                maxLines = 1
            )
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
    val destinations = listOf(
        Triple(ActiveScreen.HOME, "Home", Icons.Default.Home),
        Triple(ActiveScreen.MIXES, "Mixes", Icons.Default.QueueMusic),
        Triple(ActiveScreen.DOWNLOADS, "Downloads", Icons.Default.Download),
        Triple(ActiveScreen.OFFLINE_VAULT, "Vault", Icons.Default.Folder),
        Triple(ActiveScreen.SETTINGS, "Settings", Icons.Default.Settings)
    )
    NavigationRail(
        modifier = modifier.width(if (profile.name == "ATV") 98.dp else 78.dp)
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
