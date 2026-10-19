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

import androidx.compose.foundation.layout.imePadding

import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.browser.adfree.model.HistoryItem
import com.browser.adfree.model.TimeframeWipe
import com.browser.adfree.storage.HistoryRepository
import com.browser.adfree.ui.theme.*
import java.net.URI
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

import org.mozilla.geckoview.GeckoRuntime
import com.browser.adfree.tabs.TabManager

@Composable
fun HistoryPage(
    historyRepository: HistoryRepository,
    tabManager: TabManager,
    runtime: GeckoRuntime,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showClearSheet by remember { mutableStateOf(false) }

    val filteredList = remember(historyRepository.history.size, searchQuery) {
        if (searchQuery.isBlank()) {
            historyRepository.history
        } else {
            historyRepository.history.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.url.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Group items by date bucket
    val groupedHistory = remember(filteredList) {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val yesterdayStart = todayStart - (24 * 60 * 60 * 1000L)
        val lastWeekStart = todayStart - (7 * 24 * 60 * 60 * 1000L)

        val groups = linkedMapOf<String, MutableList<HistoryItem>>()

        for (item in filteredList) {
            val key = when {
                item.timestamp >= todayStart -> "Today"
                item.timestamp >= yesterdayStart -> "Yesterday"
                item.timestamp >= lastWeekStart -> "Last 7 Days"
                else -> "Older"
            }
            groups.getOrPut(key) { mutableListOf() }.add(item)
        }
        groups
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .imePadding()
    ) {
        BrutalPageBar(title = "History", onBack = onClose) {
            if (historyRepository.history.isNotEmpty()) {
                BrutalIconButton(Icons.Default.DeleteOutline, "Clear history", { showClearSheet = true })
            }
        }
        BrutalSearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = "Search history",
            modifier = Modifier.padding(start = 16.dp, end = 19.dp, top = 12.dp, bottom = 4.dp)
        )

        if (filteredList.isEmpty()) {
            BrutalEmptyState(
                title = if (searchQuery.isBlank()) "No history yet" else "Nothing matches",
                message = if (searchQuery.isBlank()) "Pages you open in normal tabs show up here. Private tabs are never recorded."
                else "No page title or address contains \"$searchQuery\".",
                actionLabel = if (searchQuery.isBlank()) null else "Clear search",
                onAction = { searchQuery = "" }
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
                state = rememberLazyListState()
            ) {
                groupedHistory.forEach { (sectionTitle, items) ->
                    item(key = "section_$sectionTitle") { BrutalSectionLabel(sectionTitle) }
                    item(key = "rule_top_$sectionTitle") { BrutalRule() }
                    items(items, key = { it.id }) { entry ->
                        HistoryRow(
                            item = entry,
                            onClick = {
                                onNavigate(entry.url)
                                onClose()
                            },
                            onDelete = { historyRepository.deleteHistoryItem(entry.id) },
                            modifier = Modifier.animateItem()
                        )
                        BrutalHairline(startIndent = 62.dp)
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }

    if (showClearSheet) {
        ClearBrowsingDataSheet(
            historyRepository = historyRepository,
            tabManager = tabManager,
            runtime = runtime,
            onDismissRequest = { showClearSheet = false }
        )
    }
}

@Composable
private fun HistoryRow(
    item: HistoryItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val time = remember(item.timestamp) { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(item.timestamp)) }
    val host = remember(item.url) { try { URI(item.url).host?.removePrefix("www.") ?: item.url } catch (e: Exception) { item.url } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .brutalTap(shape = androidx.compose.ui.graphics.RectangleShape, haptic = false, pressScale = 1f, onClick = onClick)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
    ) {
        SiteFavicon(item.url)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title.ifBlank { host },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "$host  |  $time",
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        BrutalIconButton(Icons.Default.Close, "Remove ${item.title} from history", onDelete, tint = TextSecondary, iconSize = 18.dp)
    }
}
