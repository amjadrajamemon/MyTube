package com.drivedeck.tubedeck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivedeck.tubedeck.data.model.TubeDeckSettings
import com.drivedeck.tubedeck.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdBlockShieldSheet(
    blockedCount: Int,
    settings: TubeDeckSettings,
    onUpdateSettings: (TubeDeckSettings) -> Unit,
    onResetStats: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = TubeDeckSurface,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = TubeDeckBorder
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Shield Icon & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(TubeDeckAccentMint.copy(alpha = 0.15f))
                            .border(1.dp, TubeDeckAccentMint.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = "Request filtering",
                            tint = TubeDeckAccentMint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "MyTube Request Shield",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TubeDeckTextPrimary
                        )
                    }
                }

                Switch(
                    checked = settings.adBlockEnabled,
                    onCheckedChange = { isEnabled ->
                        onUpdateSettings(settings.copy(adBlockEnabled = isEnabled))
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TubeDeckBackground,
                        checkedTrackColor = TubeDeckAccentMint
                    ),
                    modifier = Modifier.testTag("adblock_master_switch")
                )
            }

            MetricCard(
                title = "Blocked requests",
                value = "$blockedCount",
                unit = "",
                color = TubeDeckAccentMint,
                modifier = Modifier.fillMaxWidth()
            )

            // Toggles Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, TubeDeckBorder, RoundedCornerShape(14.dp)),
                color = TubeDeckSurfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Filtering rules",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TubeDeckTextPrimary
                    )

                    RuleSwitchRow(
                        title = "Try to skip video ads",
                        subtitle = "",
                        checked = settings.blockVideoAds,
                        onChecked = { onUpdateSettings(settings.copy(blockVideoAds = it)) }
                    )

                    HorizontalDivider(color = TubeDeckBorder)

                    RuleSwitchRow(
                        title = "Filter known ad requests",
                        subtitle = "",
                        checked = settings.blockBannerAds,
                        onChecked = { onUpdateSettings(settings.copy(blockBannerAds = it)) }
                    )

                    HorizontalDivider(color = TubeDeckBorder)

                    RuleSwitchRow(
                        title = "Block Trackers & Telemetry",
                        subtitle = "",
                        checked = settings.blockTrackers,
                        onChecked = { onUpdateSettings(settings.copy(blockTrackers = it)) }
                    )

                    HorizontalDivider(color = TubeDeckBorder)

                    RuleSwitchRow(
                        title = "SponsorBlock Integration",
                        subtitle = "",
                        checked = settings.sponsorBlockEnabled,
                        onChecked = { onUpdateSettings(settings.copy(sponsorBlockEnabled = it)) }
                    )
                }
            }

            // Reset and Dismiss Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onResetStats,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TubeDeckTextSecondary),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(TubeDeckBorder)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset Stats", fontSize = 12.sp)
                }

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TubeDeckAccentCyan,
                        contentColor = TubeDeckBackground
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Done", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
        color = TubeDeckSurfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 11.sp, color = TubeDeckTextSecondary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                value,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            if (unit.isNotBlank()) Text(unit, fontSize = 10.sp, color = TubeDeckTextSecondary.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun RuleSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TubeDeckTextPrimary)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 10.sp, color = TubeDeckTextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TubeDeckBackground,
                checkedTrackColor = TubeDeckAccentMint
            ),
            modifier = Modifier.size(36.dp)
        )
    }
}
