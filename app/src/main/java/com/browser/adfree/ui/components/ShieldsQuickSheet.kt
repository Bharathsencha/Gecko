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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/** Per-site protection panel; the idea follows Brave Shields (https://brave.com/shields/). */
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
    onSetPermission: (com.browser.adfree.storage.SitePermission, Boolean?) -> Unit = { _, _ -> },
    onZoom: (Int) -> Unit = {},
    onReader: () -> Unit = {},
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
        containerColor = SurfaceDark,
        contentColor = TextPrimary,
        tonalElevation = 0.dp,
        dragHandle = { BrutalSheetHandle() },
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
    ) {
        val isYouTube = com.browser.adfree.tabs.TabManager.isYouTube(tab.url)
        val isShieldsActive = siteConfig.shieldsEnabled
        val blockCount = if (isShieldsActive) tab.adBlockCount.coerceAtLeast(0) else 0

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(domain.ifBlank { "Start page" }, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        text = if (tab.url.startsWith("https://")) "Encrypted connection" else "Not encrypted",
                        fontSize = 12.5.sp,
                        color = if (tab.url.startsWith("https://")) TextSecondary else DangerRed
                    )
                }
                BrutalIconButton(Icons.Default.Close, "Close", onDismissRequest)
            }

            // Count panel
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 16.dp, end = 20.dp, bottom = 16.dp)
                    .fillMaxWidth()
                    .brutalBox(
                        fill = if (isShieldsActive) Color(0xFF7FDCA4) else SurfaceCard,
                        border = BorderSubtle,
                        shadow = HardShadow
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = blockCount.toString(),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isShieldsActive) InkBlack else TextPrimary
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = when {
                            isShieldsActive -> "Blocked on this page"
                            else -> "Protection is off"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isShieldsActive) InkBlack else TextPrimary
                    )
                    Text(
                        text = when {
                            isShieldsActive -> "Trackers and ad requests stopped by tracking protection and the request filter."
                            else -> "Trackers and ads are allowed on this site."
                        },
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = if (isShieldsActive) InkBlack else TextSecondary
                    )
                }
            }

            BrutalRule()
            BrutalListRow(
                title = "Protection on this site",
                subtitle = "Tracking protection on this domain. uBlock Origin keeps filtering ads.",
                icon = Icons.Default.Shield,
                checked = siteConfig.shieldsEnabled,
                enabled = !isYouTube,
                onCheckedChange = { onToggleShieldsForSite(it) }
            )
            BrutalHairline(startIndent = 64.dp)
            BrutalListRow(
                title = "Background playback",
                subtitle = "Keep media playing when the app is in the background",
                icon = Icons.Rounded.PlayArrow,
                checked = siteConfig.backgroundMediaEnabled,
                onCheckedChange = { onToggleBackgroundMedia(it) }
            )
            BrutalHairline(startIndent = 64.dp)
            BrutalListRow(
                title = "Night mode on this site",
                subtitle = "Remembered for this domain",
                icon = Icons.Rounded.DarkMode,
                checked = tab.isDarkWeb,
                onCheckedChange = { onToggleDarkWeb() }
            )
            BrutalHairline(startIndent = 64.dp)
            BrutalListRow(
                title = "Desktop site",
                subtitle = "Remembered for this domain",
                icon = Icons.Rounded.DesktopWindows,
                checked = tab.isDesktopMode,
                onCheckedChange = { onToggleDesktop() }
            )
            BrutalHairline(startIndent = 64.dp)
            BrutalListRow(
                title = "Text size",
                subtitle = "${tab.zoomLevel}%",
                icon = Icons.Default.ZoomIn,
                trailing = {
                    Row {
                        BrutalIconButton(Icons.Default.Remove, "Smaller", { onZoom(tab.zoomLevel - 10) }, enabled = tab.zoomLevel > 50)
                        BrutalIconButton(Icons.Default.Add, "Larger", { onZoom(tab.zoomLevel + 10) }, enabled = tab.zoomLevel < 200)
                    }
                }
            )
            BrutalHairline(startIndent = 64.dp)
            BrutalListRow(
                title = "Reader view",
                subtitle = if (tab.isExtractingArticle) "Finding the article" else "Show only the article text",
                icon = Icons.Default.ChromeReaderMode,
                enabled = !tab.isExtractingArticle && tab.url.startsWith("http"),
                onClick = onReader
            )
            // Permissions this site asked for, with the answer the user gave.
            val permHost = com.browser.adfree.storage.SitePermissions.hostOf(tab.url)
            val asked = com.browser.adfree.storage.SitePermission.entries.mapNotNull { p ->
                com.browser.adfree.storage.SitePermissions.get(permHost, p, tab.isPrivate)?.let { p to it }
            }
            BrutalSectionLabel("Permissions")
            if (asked.isEmpty()) {
                Text(
                    "This site has not asked for location, camera, microphone or other permissions.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                asked.forEach { (p, allowed) ->
                    PermissionRow(p, allowed) { onSetPermission(p, it) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** One permission with Allow, Block and Ask choices. */
@Composable
fun PermissionRow(
    permission: com.browser.adfree.storage.SitePermission,
    allowed: Boolean?,
    onChange: (Boolean?) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        BrutalIconTile(permissionIcon(permission))
        Spacer(Modifier.width(14.dp))
        Text(permission.label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(true to "Allow", false to "Block", null to "Ask").forEach { (value, label) ->
                val on = allowed == value
                Text(
                    label,
                    color = if (on) InkBlack else TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .brutalPressable(
                            fill = if (on) (if (value == false) Color(0xFFFF9EC7) else AccentCyan) else SurfaceDark,
                            border = BorderSubtle,
                            shadow = HardShadow,
                            shadowOffset = if (on) 0.dp else 2.dp,
                            role = androidx.compose.ui.semantics.Role.RadioButton,
                            onClick = { onChange(value) }
                        )
                        .padding(horizontal = 9.dp, vertical = 7.dp)
                )
            }
        }
    }
}

fun permissionIcon(p: com.browser.adfree.storage.SitePermission): ImageVector = when (p) {
    com.browser.adfree.storage.SitePermission.LOCATION -> Icons.Default.LocationOn
    com.browser.adfree.storage.SitePermission.CAMERA -> Icons.Default.CameraAlt
    com.browser.adfree.storage.SitePermission.MICROPHONE -> Icons.Default.Mic
    com.browser.adfree.storage.SitePermission.NOTIFICATIONS -> Icons.Default.Notifications
    com.browser.adfree.storage.SitePermission.DRM -> Icons.Default.Movie
    com.browser.adfree.storage.SitePermission.STORAGE -> Icons.Default.Storage
    com.browser.adfree.storage.SitePermission.XR -> Icons.Default.ViewInAr
    com.browser.adfree.storage.SitePermission.LOCAL_NETWORK -> Icons.Default.Router
    com.browser.adfree.storage.SitePermission.COOKIES -> Icons.Default.Cookie
}
