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

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.horizontalScroll

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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
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
    onNewTab: (isPrivate: Boolean, container: String?) -> Unit,
    onCloseAllTabs: () -> Unit,
    onCloseAllPrivateTabs: () -> Unit,
    onSetGroup: (TabModel, String?) -> Unit = { _, _ -> },
    onCloseGroup: (String) -> Unit = {},
    closedTabCount: Int = 0,
    onReopenClosedTab: () -> Unit = {},
    onDismissRequest: () -> Unit
) {
    var groupingTab by remember { mutableStateOf<TabModel?>(null) }
    var collapsed by remember { mutableStateOf(setOf<String>()) }
    val activeTab = if (activeTabIndex in tabs.indices) tabs[activeTabIndex] else null
    var isPrivateModeSelected by remember { mutableStateOf(activeTab?.isPrivate == true) }
    // Container for the next new tab; starts as the current tab's.
    var newTabContainer by remember { mutableStateOf(activeTab?.container) }

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
            onNewTab(isPrivateModeSelected, if (isPrivateModeSelected) null else newTabContainer)
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
        containerColor = DarkBg,
        contentColor = TextPrimary,
        tonalElevation = 0.dp,
        dragHandle = { BrutalSheetHandle() },
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .navigationBarsPadding()
                .alpha(contentAlpha)
                .scale(contentScale)
        ) {
            // Normal / private switch
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 19.dp, bottom = 14.dp)
            ) {
                ModeTab(
                    label = "Tabs",
                    count = normalTabs.size,
                    selected = !isPrivateModeSelected,
                    fill = AccentCyan,
                    modifier = Modifier.weight(1f),
                    onClick = { isPrivateModeSelected = false }
                )
                ModeTab(
                    label = "Private",
                    count = privateTabs.size,
                    selected = isPrivateModeSelected,
                    fill = PrivatePurple,
                    modifier = Modifier.weight(1f),
                    onClick = { isPrivateModeSelected = true }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 19.dp, bottom = 14.dp)
            ) {
                if (currentDisplayList.isNotEmpty() && !isClosingAll && selectedIndexToOpen == null) {
                    BrutalOutlineButton(
                        text = "Close all",
                        onClick = { isClosingAll = true }
                    )
                }
                if (!isPrivateModeSelected && closedTabCount > 0 && !isClosingAll && selectedIndexToOpen == null) {
                    Spacer(Modifier.width(8.dp))
                    BrutalOutlineButton(
                        text = "Reopen",
                        icon = Icons.Default.Restore,
                        onClick = {
                            onReopenClosedTab()
                            onDismissRequest()
                        }
                    )
                }
                Spacer(Modifier.weight(1f))
                BrutalButton(
                    text = if (isPrivateModeSelected) "New private tab" else "New tab",
                    icon = Icons.Default.Add,
                    fill = if (isPrivateModeSelected) PrivatePurple else AccentCyan,
                    contentColor = InkBlack,
                    onClick = {
                        if (!isClosingAll && selectedIndexToOpen == null && !isOpeningNewTab) isOpeningNewTab = true
                    }
                )
            }
            if (!isPrivateModeSelected && com.browser.adfree.model.TabContainer.all.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                ) {
                    Text("New tab in", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    ContainerChip("Default", null, newTabContainer == null) { newTabContainer = null }
                    com.browser.adfree.model.TabContainer.all.forEach { c ->
                        ContainerChip(c.displayName, Color(c.color), newTabContainer == c.id) { newTabContainer = c.id }
                    }
                }
            }
            BrutalRule()

            if (currentDisplayList.isEmpty()) {
                BrutalEmptyState(
                    title = if (isPrivateModeSelected) "No private tabs" else "No open tabs",
                    message = if (isPrivateModeSelected) "Private tabs keep history, cookies and site data in memory and erase them when closed."
                    else "Open a new tab to start browsing."
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 19.dp, top = 16.dp, bottom = 24.dp)
                ) {
                    val loose = currentDisplayList.filter { it.group == null }
                    val grouped = currentDisplayList.filter { it.group != null }.groupBy { it.group!! }
                    val card: @Composable (TabModel) -> Unit = { tab ->
                        val realIndex = tabs.indexOf(tab)
                        TabCard(
                            tab = tab,
                            isActive = realIndex == activeTabIndex,
                            onSelect = {
                                if (!isClosingAll && selectedIndexToOpen == null) selectedIndexToOpen = realIndex
                            },
                            onClose = {
                                if (!isClosingAll && selectedIndexToOpen == null) onCloseTab(realIndex)
                            },
                            onGroup = { groupingTab = tab }
                        )
                    }
                    items(loose, key = { it.id }) { tab -> Box(Modifier.animateItem()) { card(tab) } }
                    grouped.forEach { (name, members) ->
                        item(key = "group:$name", span = { GridItemSpan(maxLineSpan) }) {
                            GroupHeader(
                                name = name,
                                count = members.size,
                                expanded = name !in collapsed,
                                onToggle = { collapsed = if (name in collapsed) collapsed - name else collapsed + name },
                                onClose = { onCloseGroup(name) },
                                modifier = Modifier.animateItem()
                            )
                        }
                        if (name !in collapsed) {
                            items(members, key = { it.id }) { tab -> Box(Modifier.animateItem()) { card(tab) } }
                        }
                    }
                }
            }
        }
    }

    groupingTab?.let { tab ->
        GroupPickerDialog(
            current = tab.group,
            groups = tabs.filter { it.isPrivate == tab.isPrivate }.mapNotNull { it.group }.distinct(),
            onPick = { onSetGroup(tab, it); groupingTab = null },
            onDismiss = { groupingTab = null }
        )
    }
}

@Composable
private fun GroupHeader(
    name: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .brutalTap(pressScale = 1f, onClick = onToggle)
    ) {
        Icon(
            if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
            contentDescription = if (expanded) "Collapse group" else "Expand group",
            tint = TextPrimary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        BrutalIconButton(Icons.Default.Close, "Close group", onClose, iconSize = 18.dp)
    }
}

@Composable
private fun GroupPickerDialog(
    current: String?,
    groups: List<String>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    BrutalDialog(
        title = "Group",
        onDismiss = onDismiss,
        confirmLabel = if (newName.isBlank()) "Done" else "Create",
        onConfirm = { if (newName.isBlank()) onDismiss() else onPick(newName.trim()) },
        dismissLabel = if (current != null) "Ungroup" else "Cancel",
        onDismissAction = { if (current != null) onPick(null) else onDismiss() }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (groups.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 3.dp, end = 3.dp)
                ) {
                    groups.forEach { g -> ContainerChip(g, null, g == current) { onPick(g) } }
                }
            }
            BrutalTextField(
                value = newName,
                onValueChange = { newName = it.take(24) },
                label = "New group",
                placeholder = "Work, Trip, Recipes",
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                onImeAction = { if (newName.isNotBlank()) onPick(newName.trim()) }
            )
        }
    }
}

@Composable
private fun ModeTab(
    label: String,
    count: Int,
    selected: Boolean,
    fill: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .height(44.dp)
            .brutalPressable(
                fill = if (selected) fill else SurfaceDark,
                border = BorderSubtle,
                shadow = HardShadow,
                shadowOffset = if (selected) 0.dp else 3.dp,
                role = androidx.compose.ui.semantics.Role.Tab,
                onClick = onClick
            )
    ) {
        Text(label, color = if (selected) InkBlack else TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), color = if (selected) InkBlack else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TabCard(
    tab: TabModel,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onGroup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val host = remember(tab.url) {
        try { URI(tab.url).host?.removePrefix("www.") ?: "" } catch (e: Exception) { "" }
    }
    val isHome = tab.url == "about:home" || tab.url.isBlank()
    val container = com.browser.adfree.model.TabContainer.byId(tab.container)
    val headerFill = when {
        tab.isPrivate -> PrivatePurple
        container != null -> Color(container.color)
        isActive -> AccentCyan
        else -> SurfaceCard
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp)
            .brutalPressable(
                fill = SurfaceDark,
                border = BorderSubtle,
                shadow = HardShadow,
                shadowOffset = if (isActive) 5.dp else 3.dp,
                onClick = onSelect
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(headerFill, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .padding(start = 10.dp)
        ) {
            if (tab.isPrivate) {
                PrivateMask(maskColor = InkBlack, eyeColor = PrivatePurple, modifier = Modifier.size(width = 20.dp, height = 13.dp))
            } else if (!isHome) {
                SiteFavicon(tab.url, size = 22.dp)
            } else {
                Icon(Icons.Default.Public, contentDescription = null, tint = InkBlack, modifier = Modifier.size(18.dp))
            }
            if (container != null) {
                Spacer(Modifier.width(8.dp))
                Text(container.displayName, color = InkBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            BrutalIconButton(
                icon = if (tab.group != null) Icons.Default.Folder else Icons.Default.CreateNewFolder,
                contentDescription = "Group tab",
                onClick = onGroup,
                tint = if (tab.isPrivate || isActive || container != null) InkBlack else TextPrimary,
                iconSize = 17.dp,
                modifier = Modifier.size(36.dp)
            )
            BrutalIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Close tab",
                onClick = onClose,
                tint = if (tab.isPrivate || isActive || container != null) InkBlack else TextPrimary,
                iconSize = 18.dp,
                modifier = Modifier.size(40.dp)
            )
        }
        BrutalRule()
        Column(
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            Text(
                text = when {
                    isHome -> if (tab.isPrivate) "Private start page" else "Start page"
                    tab.title.isBlank() -> host
                    else -> tab.title
                },
                color = TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp
            )
            if (!isHome && host.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(host, color = TextSecondary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ContainerChip(label: String, color: Color?, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .brutalPressable(
                fill = if (selected) (color ?: AccentCyan) else SurfaceDark,
                border = BorderSubtle,
                shadow = HardShadow,
                shadowOffset = if (selected) 0.dp else 2.dp,
                role = androidx.compose.ui.semantics.Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        if (color != null && !selected) {
            Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = if (selected) InkBlack else TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
}
