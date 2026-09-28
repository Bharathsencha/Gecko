/*
 * Copyright (C) 2024-2026 Gibbly Gobbly <gibblygobbly@proton.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.browser.adfree.ui.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.model.TabModel
import com.browser.adfree.ui.theme.*
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafariPageMenu(
    tab: TabModel,
    onZoomChange: (Int) -> Unit,
    onToggleReader: () -> Unit,
    onToggleDarkWeb: () -> Unit,
    onToggleDesktop: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val domain = try {
        val uri = URI(tab.url)
        val h = uri.host ?: ""
        if (h.startsWith("www.")) h.substring(4) else h
    } catch (e: Exception) {
        tab.url
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = SurfaceDark,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            // Header / Domain
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (domain.isBlank()) "Page Options" else domain,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            // 1. Zoom Control Bar (aA [ - ] 100% [ + ])
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "aA",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AccentCyan
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (tab.zoomLevel > 50) onZoomChange(tab.zoomLevel - 10)
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Text(
                            text = "−",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Text(
                        text = "${tab.zoomLevel}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    IconButton(
                        onClick = {
                            if (tab.zoomLevel < 200) onZoomChange(tab.zoomLevel + 10)
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Text(
                            text = "+",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Action Grid (Reader View, Dark Web, Desktop Mode)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SafariMenuCard(
                    title = if (tab.isReaderMode) "Hide Reader" else "Reader View",
                    icon = Icons.Default.MenuBook,
                    isActive = tab.isReaderMode,
                    onClick = onToggleReader,
                    modifier = Modifier.weight(1f)
                )

                SafariMenuCard(
                    title = if (tab.isDarkWeb) "Light Web" else "Dark Web",
                    icon = Icons.Default.DarkMode,
                    isActive = tab.isDarkWeb,
                    onClick = onToggleDarkWeb,
                    modifier = Modifier.weight(1f)
                )

                SafariMenuCard(
                    title = if (tab.isDesktopMode) "Mobile Site" else "Desktop Site",
                    icon = Icons.Default.DesktopWindows,
                    isActive = tab.isDesktopMode,
                    onClick = onToggleDesktop,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Privacy Report Card (Safari Style)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(SurfaceElevated, SurfaceCard)
                        )
                    )
                    .border(1.dp, ShieldGreen.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(ShieldGreen.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = ShieldGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Privacy Report",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${tab.adBlockCount} ads & trackers blocked",
                        fontSize = 12.sp,
                        color = ShieldGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun SafariMenuCard(
    title: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isActive) AccentCyan.copy(alpha = 0.18f) else SurfaceElevated)
            .border(
                1.dp,
                if (isActive) AccentCyan else BorderSubtle,
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) AccentCyan else TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isActive) AccentCyan else TextPrimary,
            maxLines = 1
        )
    }
}
