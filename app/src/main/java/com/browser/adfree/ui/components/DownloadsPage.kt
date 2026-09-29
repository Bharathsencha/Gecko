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

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.download.BrowserDownloadManager
import com.browser.adfree.model.DownloadItem
import com.browser.adfree.model.DownloadStatus
import com.browser.adfree.ui.theme.*

private enum class DownloadCategory(val title: String) {
    ALL("All"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    IMAGES("Images"),
    DOCUMENTS("Documents")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsPage(
    downloadManager: BrowserDownloadManager,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(DownloadCategory.ALL) }

    val allDownloads = downloadManager.downloadsList

    // Filter by Category and Search Query
    val filteredDownloads = remember(allDownloads.size, searchQuery, selectedCategory, allDownloads.map { it.status to it.progress }) {
        allDownloads.filter { item ->
            val matchesSearch = if (searchQuery.isBlank()) true else {
                item.fileName.contains(searchQuery, ignoreCase = true) ||
                item.mimeType.contains(searchQuery, ignoreCase = true)
            }

            val matchesCategory = when (selectedCategory) {
                DownloadCategory.ALL -> true
                DownloadCategory.VIDEOS -> item.mimeType.startsWith("video/") || item.fileName.endsWith(".mp4", ignoreCase = true) || item.fileName.endsWith(".webm", ignoreCase = true) || item.fileName.endsWith(".mkv", ignoreCase = true)
                DownloadCategory.AUDIO -> item.mimeType.startsWith("audio/") || item.fileName.endsWith(".mp3", ignoreCase = true) || item.fileName.endsWith(".m4a", ignoreCase = true) || item.fileName.endsWith(".aac", ignoreCase = true) || item.fileName.endsWith(".ogg", ignoreCase = true)
                DownloadCategory.IMAGES -> item.mimeType.startsWith("image/") || item.fileName.endsWith(".jpg", ignoreCase = true) || item.fileName.endsWith(".png", ignoreCase = true) || item.fileName.endsWith(".webp", ignoreCase = true) || item.fileName.endsWith(".gif", ignoreCase = true)
                DownloadCategory.DOCUMENTS -> item.mimeType.startsWith("application/") || item.mimeType.startsWith("text/") || item.fileName.endsWith(".pdf", ignoreCase = true) || item.fileName.endsWith(".doc", ignoreCase = true) || item.fileName.endsWith(".zip", ignoreCase = true) || item.fileName.endsWith(".apk", ignoreCase = true)
            }

            matchesSearch && matchesCategory
        }
    }

    // Storage footprint calculation
    val totalDownloadedBytes = remember(allDownloads.size, allDownloads.map { it.currentBytes }) {
        allDownloads.filter { it.status == DownloadStatus.COMPLETED }.sumOf { it.totalBytes.coerceAtLeast(it.currentBytes) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Navigation Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Downloads",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (allDownloads.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(AccentCyan.copy(alpha = 0.2f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${allDownloads.size}",
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (allDownloads.any { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }) {
                TextButton(
                    onClick = { downloadManager.clearCompleted() },
                    colors = ButtonDefaults.textButtonColors(contentColor = AccentCyan)
                ) {
                    Text("Clear All", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search downloads...", color = TextSecondary, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceElevated,
                    unfocusedContainerColor = SurfaceElevated,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AccentCyan,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            )
        }

        // Storage & Stat Summary Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceCard)
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Storage,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Total Downloaded Size",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
            Text(
                text = formatFileSize(totalDownloadedBytes),
                color = AccentCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (category in DownloadCategory.values()) {
                val isSelected = selectedCategory == category
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategory = category },
                    label = {
                        Text(
                            text = category.title,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCyan,
                        selectedLabelColor = DarkBg,
                        containerColor = SurfaceElevated,
                        labelColor = TextPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (isSelected) AccentCyan else BorderSubtle,
                        selectedBorderColor = AccentCyan,
                        borderWidth = 1.dp
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Content List
        if (filteredDownloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FileDownloadOff,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No downloads matching '$searchQuery'" else "No downloads in this category",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                items(filteredDownloads, key = { it.id }) { item ->
                    DownloadItemRow(
                        item = item,
                        onClick = { downloadManager.openDownloadedFile(item) },
                        onDelete = { downloadManager.deleteDownload(item) },
                        onPause = { downloadManager.pauseDownload(item.id) },
                        onResume = { downloadManager.resumeDownload(item.id) },
                        onCancel = { downloadManager.cancelDownload(item.id) },
                        onShare = { downloadManager.shareDownload(context, item) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
