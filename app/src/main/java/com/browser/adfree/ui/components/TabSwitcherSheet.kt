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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.browser.adfree.model.TabModel
import com.browser.adfree.ui.theme.*
import kotlinx.coroutines.delay
import java.net.URI
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcherSheet(
    tabs: List<TabModel>,
    activeTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onNewTab: (isPrivate: Boolean) -> Unit,
    onCloseAllTabs: () -> Unit,
    onCloseAllPrivateTabs: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val activeTab = if (activeTabIndex in tabs.indices) tabs[activeTabIndex] else null
    var isPrivateModeSelected by remember { mutableStateOf(activeTab?.isPrivate == true) }

    val normalTabs = remember(tabs.map { it.id to it.isPrivate }) { tabs.filter { !it.isPrivate } }
    val privateTabs = remember(tabs.map { it.id to it.isPrivate }) { tabs.filter { it.isPrivate } }
    val currentDisplayList = if (isPrivateModeSelected) privateTabs else normalTabs

    var isClosingAll by remember { mutableStateOf(false) }
    var selectedIndexToOpen by remember { mutableStateOf<Int?>(null) }
    var isOpeningNewTab by remember { mutableStateOf(false) }

    val contentAlpha by animateFloatAsState(
        targetValue = if (isClosingAll || selectedIndexToOpen != null || isOpeningNewTab) 0f else 1f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "contentAlpha"
    )

    val contentScale by animateFloatAsState(
        targetValue = if (isClosingAll) 0.9f else if (selectedIndexToOpen != null || isOpeningNewTab) 1.04f else 1f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "contentScale"
    )

    // Trigger smooth tab opening transition
    LaunchedEffect(selectedIndexToOpen) {
        selectedIndexToOpen?.let { index ->
            delay(160L)
            onTabSelected(index)
            onDismissRequest()
        }
    }

    // Trigger smooth new tab transition
    LaunchedEffect(isOpeningNewTab) {
        if (isOpeningNewTab) {
            delay(160L)
            onNewTab(isPrivateModeSelected)
            onDismissRequest()
        }
    }

    // Trigger close action and dismiss after smooth animation finishes
    LaunchedEffect(isClosingAll) {
        if (isClosingAll) {
            delay(240L)
            if (isPrivateModeSelected) {
                onCloseAllPrivateTabs()
            } else {
                onCloseAllTabs()
            }
            onDismissRequest()
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

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
                .fillMaxHeight(0.88f)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .alpha(contentAlpha)
                .scale(contentScale)
        ) {
            // 1. Dual Mode Segment Selector [ Normal Tabs (N) ] | [ Private (N) ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Normal Tabs Segment
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (!isPrivateModeSelected) SurfaceCard else Color.Transparent)
                        .clickable { isPrivateModeSelected = false },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = if (!isPrivateModeSelected) AccentCyan else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tabs (${normalTabs.size})",
                            fontSize = 13.sp,
                            fontWeight = if (!isPrivateModeSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (!isPrivateModeSelected) TextPrimary else TextSecondary
                        )
                    }
                }

                // Private Tabs Segment
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (isPrivateModeSelected) Color(0xFF2E1065) else Color.Transparent)
                        .clickable { isPrivateModeSelected = true },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = if (isPrivateModeSelected) Color(0xFFA855F7) else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Private (${privateTabs.size})",
                            fontSize = 13.sp,
                            fontWeight = if (isPrivateModeSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isPrivateModeSelected) Color.White else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Action Bar: Close All + New Tab button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            ) {
                Text(
                    text = if (isPrivateModeSelected) "Private Tabs" else "Open Tabs",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPrivateModeSelected) Color(0xFFA855F7) else TextPrimary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (currentDisplayList.isNotEmpty() && !isClosingAll && selectedIndexToOpen == null) {
                        TextButton(
                            onClick = { isClosingAll = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = DangerRed)
                        ) {
                            Text(
                                text = if (isPrivateModeSelected) "Close All Private" else "Close All",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Button(
                        onClick = {
                            if (!isClosingAll && selectedIndexToOpen == null && !isOpeningNewTab) {
                                isOpeningNewTab = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPrivateModeSelected) Color(0xFFA855F7) else AccentCyan,
                            contentColor = if (isPrivateModeSelected) Color.White else DarkBg
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Tab",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPrivateModeSelected) "New Private Tab" else "New Tab",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 3. Grid of Tabs or Empty State
            if (currentDisplayList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(if (isPrivateModeSelected) Color(0xFF2E1065) else SurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPrivateModeSelected) Icons.Default.VisibilityOff else Icons.Default.LayersClear,
                                contentDescription = null,
                                tint = if (isPrivateModeSelected) Color(0xFFA855F7) else TextSecondary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (isPrivateModeSelected) "No Private Tabs Open" else "No Open Tabs",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isPrivateModeSelected) {
                                "Tap 'New Private Tab' above to browse in total isolation."
                            } else {
                                "Tap 'New Tab' above to start browsing."
                            },
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    itemsIndexed(currentDisplayList, key = { _, tab -> tab.id }) { _, tab ->
                        val realIndex = tabs.indexOf(tab)
                        TabCard(
                            tab = tab,
                            isActive = realIndex == activeTabIndex,
                            onSelect = {
                                if (!isClosingAll && selectedIndexToOpen == null) {
                                    selectedIndexToOpen = realIndex
                                }
                            },
                            onClose = {
                                if (!isClosingAll && selectedIndexToOpen == null) {
                                    onCloseTab(realIndex)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TabCard(
    tab: TabModel,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val host = remember(tab.url) {
        try {
            val uri = URI(tab.url)
            val h = uri.host ?: ""
            if (h.startsWith("www.")) h.substring(4) else h
        } catch (e: Exception) {
            tab.url
        }
    }

    val faviconUrl = remember(tab.url) {
        if (tab.url != "about:home" && tab.url.isNotBlank()) {
            try {
                val uri = URI(tab.url)
                val h = uri.host ?: tab.url
                "https://www.google.com/s2/favicons?domain_url=" + URLEncoder.encode(h, "UTF-8") + "&sz=128"
            } catch (e: Exception) {
                null
            }
        } else null
    }

    val activeBorderColor = if (tab.isPrivate) Color(0xFFA855F7) else AccentCyan
    val inactiveBorderColor = if (tab.isPrivate) Color(0xFFA855F7).copy(alpha = 0.35f) else BorderSubtle

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (tab.isPrivate) {
                    if (isActive) Color(0xFF2E1065) else Color(0xFF1E1035).copy(alpha = 0.8f)
                } else {
                    if (isActive) SurfaceCard else SurfaceElevated
                }
            )
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) activeBorderColor else inactiveBorderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onSelect() }
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Favicon / Globe / Mask + Close button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (tab.isPrivate) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFA855F7).copy(alpha = 0.25f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = "Private",
                            tint = Color(0xFFA855F7),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Private",
                            color = Color(0xFFA855F7),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (faviconUrl != null) {
                    AsyncImage(
                        model = faviconUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = if (isActive) AccentCyan else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(SurfaceDark)
                        .border(1.dp, BorderSubtle, CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Tab",
                        tint = TextPrimary.copy(alpha = 0.85f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Bottom Section: Title and Domain
            Column {
                Text(
                    text = if (tab.title.isBlank() || tab.title == "Start Page") {
                        if (tab.isPrivate) "Private Browsing" else "Start Page"
                    } else tab.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (host.isBlank() || host == "about:home") {
                        if (tab.isPrivate) "Private Session" else "Start Page"
                    } else host,
                    color = if (tab.isPrivate) Color(0xFFA855F7).copy(alpha = 0.8f) else TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
