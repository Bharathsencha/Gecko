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
import androidx.compose.runtime.snapshots.SnapshotStateList
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
import com.browser.adfree.model.FavoriteItem
import com.browser.adfree.ui.theme.*
import java.net.URI
import java.net.URLEncoder

@Composable
fun BookmarksPage(
    favorites: SnapshotStateList<FavoriteItem>,
    onNavigate: (String) -> Unit,
    onRemove: (FavoriteItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredList = remember(favorites.size, searchQuery) {
        if (searchQuery.isBlank()) {
            favorites
        } else {
            favorites.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.url.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .imePadding()
    ) {
        BrutalPageBar(title = "Bookmarks", onBack = onBack) {
            if (favorites.isNotEmpty()) {
                Text(
                    "${favorites.size}",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
        }
        if (favorites.isNotEmpty()) {
            BrutalSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search bookmarks",
                modifier = Modifier.padding(start = 16.dp, end = 19.dp, top = 12.dp, bottom = 16.dp)
            )
        }

        if (filteredList.isEmpty()) {
            BrutalEmptyState(
                title = if (searchQuery.isBlank()) "No bookmarks yet" else "Nothing matches",
                message = if (searchQuery.isBlank()) "Tap the bookmark icon in the address bar to save the page you are on."
                else "No bookmark title or address contains \"$searchQuery\".",
                actionLabel = if (searchQuery.isBlank()) null else "Clear search",
                onAction = { searchQuery = "" }
            )
        } else {
            BrutalRule()
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredList, key = { it.id }) { item ->
                    BookmarkRow(
                        item = item,
                        onClick = {
                            onNavigate(item.url)
                            onBack()
                        },
                        onDelete = { onRemove(item) },
                        modifier = Modifier.animateItem()
                    )
                    BrutalHairline(startIndent = 62.dp)
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun BookmarkRow(
    item: FavoriteItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            Text(item.title.ifBlank { host }, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(host, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BrutalIconButton(Icons.Default.DeleteOutline, "Delete bookmark ${item.title}", onDelete, tint = TextSecondary, iconSize = 20.dp)
    }
}
