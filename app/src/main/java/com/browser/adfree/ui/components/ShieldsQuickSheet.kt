/*
 * Copyright (C) 2024-2026 Gibbly Gobbly <gibblygobbly@proton.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.browser.adfree.shields.ShieldsRepository
import com.browser.adfree.storage.BrowserPreferencesRepository
import com.browser.adfree.ui.theme.*
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldsQuickSheet(
    tab: TabModel,
    preferencesRepo: BrowserPreferencesRepository,
    shieldsRepo: ShieldsRepository,
    onToggleShieldsForSite: (Boolean) -> Unit,
    onToggleBackgroundMedia: (Boolean) -> Unit,
    onToggleDarkWeb: () -> Unit,
    onToggleDesktop: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val domain = remember(tab.url) {
        try {
            val uri = URI(tab.url)
            val h = uri.host ?: ""
            if (h.startsWith("www.")) h.substring(4) else h
        } catch (e: Exception) {
            tab.url
        }
    }

    val siteConfig = shieldsRepo.getConfigForUrl(tab.url)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF141416),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(Color(0xFF3A3A3C), RoundedCornerShape(2.dp))
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 6.dp)
        ) {
            // Header: Domain + Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                if (siteConfig.shieldsEnabled) ShieldGreen.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (siteConfig.shieldsEnabled) ShieldGreen else Color(0xFF8E8E93),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = domain.ifBlank { "Home" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (tab.url.startsWith("https://")) "Encrypted Connection (TLS)" else "Standard Web Connection",
                            fontSize = 11.5.sp,
                            color = if (tab.url.startsWith("https://")) ShieldGreen else Color(0xFF8E8E93)
                        )
                    }
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF2C2C2E), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Stats Hero Banner
            val isShieldsActive = siteConfig.shieldsEnabled
            val blockCount = if (isShieldsActive) maxOf(tab.adBlockCount, 0) else 0

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isShieldsActive)
                            Brush.linearGradient(
                                listOf(Color(0xFF0D2818), Color(0xFF141F18))
                            )
                        else
                            Brush.linearGradient(
                                listOf(Color(0xFF1F1F22), Color(0xFF18181A))
                            )
                    )
                    .border(
                        1.dp,
                        if (isShieldsActive) ShieldGreen.copy(alpha = 0.4f) else Color(0xFF3A3A3C),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (isShieldsActive) ShieldGreen.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (isShieldsActive) ShieldGreen else Color(0xFF8E8E93),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = if (isShieldsActive) "$blockCount Ads & Trackers Blocked" else "Shields are Down",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isShieldsActive)
                                "Real uBlock Origin 1.75.0 + Strict Anti-Tracking"
                            else
                                "Ads and trackers permitted on this domain",
                            fontSize = 12.sp,
                            color = if (isShieldsActive) Color(0xFF34C759) else Color(0xFF8E8E93)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Controls Container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1C1E))
            ) {
                // Master Shields Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleShieldsForSite(!siteConfig.shieldsEnabled) }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Shields for this site",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = if (siteConfig.shieldsEnabled)
                                "Ad blocking, cookie shield & anti-fingerprinting active"
                            else
                                "Protection paused on this domain",
                            fontSize = 11.5.sp,
                            color = Color(0xFF8E8E93)
                        )
                    }

                    Switch(
                        checked = siteConfig.shieldsEnabled,
                        onCheckedChange = { onToggleShieldsForSite(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ShieldGreen,
                            uncheckedThumbColor = Color(0xFF8E8E93),
                            uncheckedTrackColor = Color(0xFF2C2C2E)
                        )
                    )
                }

                Divider(color = Color(0xFF2C2C2E), thickness = 0.5.dp)

                // Sub-toggles
                ShieldsOptionRow(
                    icon = Icons.Rounded.PlayArrow,
                    title = "Background Media Playback",
                    subtitle = "Keep video & audio playing when minimized",
                    checked = siteConfig.backgroundMediaEnabled,
                    onCheckedChange = { onToggleBackgroundMedia(it) }
                )

                Divider(color = Color(0xFF2C2C2E), thickness = 0.5.dp)

                ShieldsOptionRow(
                    icon = Icons.Rounded.DarkMode,
                    title = "Dark Reader Dynamic Engine",
                    subtitle = "OLED pitch-black contrast inversion",
                    checked = tab.isDarkWeb,
                    onCheckedChange = { onToggleDarkWeb() }
                )

                Divider(color = Color(0xFF2C2C2E), thickness = 0.5.dp)

                ShieldsOptionRow(
                    icon = Icons.Rounded.DesktopWindows,
                    title = "Desktop Site Mode",
                    subtitle = "Request full desktop web version",
                    checked = tab.isDesktopMode,
                    onCheckedChange = { onToggleDesktop() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ShieldsOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) AccentCyan else Color(0xFF8E8E93),
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = Color(0xFF8E8E93)
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentCyan,
                uncheckedThumbColor = Color(0xFF8E8E93),
                uncheckedTrackColor = Color(0xFF2C2C2E)
            ),
            modifier = Modifier.height(28.dp)
        )
    }
}
