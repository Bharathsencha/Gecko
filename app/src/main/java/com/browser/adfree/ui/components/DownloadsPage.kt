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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.download.BrowserDownloadManager
import com.browser.adfree.model.DownloadItem
import com.browser.adfree.model.DownloadStatus
import com.browser.adfree.ui.theme.*

private enum class DownloadFilter(val label: String) { ALL("All"), VIDEO("Video"), AUDIO("Audio"), IMAGES("Images"), FILES("Files") }

private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "ogg", "opus", "weba", "flac", "wav")
private val VIDEO_EXT = setOf("mp4", "webm", "mkv", "m4v", "mov", "3gp", "ts")
private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "svg")

private fun DownloadItem.kind(): DownloadFilter {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when {
        mimeType.startsWith("audio/") || ext in AUDIO_EXT -> DownloadFilter.AUDIO
        mimeType.startsWith("video/") || ext in VIDEO_EXT -> DownloadFilter.VIDEO
        mimeType.startsWith("image/") || ext in IMAGE_EXT -> DownloadFilter.IMAGES
        else -> DownloadFilter.FILES
    }
}

@Composable
fun DownloadsPage(
    downloadManager: BrowserDownloadManager,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(DownloadFilter.ALL) }
    val all = downloadManager.downloadsList
    val shown = all.filter { item ->
        (filter == DownloadFilter.ALL || item.kind() == filter) &&
            (query.isBlank() || item.fileName.contains(query, ignoreCase = true))
    }
    val hasFinished = all.any { it.status != DownloadStatus.DOWNLOADING && it.status != DownloadStatus.PAUSED }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .imePadding()
    ) {
        BrutalPageBar(title = "Downloads", onBack = onClose) {
            if (hasFinished) {
                BrutalIconButton(Icons.Default.DeleteSweep, "Clear finished and failed from the list", { downloadManager.clearCompleted() })
            }
        }

        if (all.isEmpty()) {
            BrutalEmptyState(
                title = "No downloads",
                message = "Files you download, and videos or audio you save from a page, appear here. They are stored in your phone's Downloads folder."
            )
        } else {
            BrutalSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search downloads",
                modifier = Modifier.padding(start = 16.dp, end = 19.dp, top = 12.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                DownloadFilter.values().forEach { f ->
                    val selected = f == filter
                    Box(
                        modifier = Modifier
                            .brutalPressable(
                                fill = if (selected) AccentCyan else SurfaceDark,
                                border = BorderSubtle,
                                shadow = HardShadow,
                                shadowOffset = if (selected) 0.dp else 2.dp,
                                role = Role.Tab,
                                onClick = { filter = f }
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(f.label, color = if (selected) OnAccent else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            BrutalRule()
            if (shown.isEmpty()) {
                BrutalEmptyState(
                    title = "Nothing here",
                    message = if (query.isNotBlank()) "No file name contains \"$query\"." else "No ${filter.label.lowercase()} downloads yet.",
                    actionLabel = "Show all",
                    onAction = { query = ""; filter = DownloadFilter.ALL }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(shown, key = { it.id }) { item ->
                        DownloadRow(
                            item = item,
                            onOpen = { downloadManager.openDownloadedFile(item) },
                            onPause = { downloadManager.pauseDownload(item.id) },
                            onResume = { downloadManager.resumeDownload(item.id) },
                            onCancel = { downloadManager.cancelDownload(item.id) },
                            onShare = { downloadManager.shareDownload(context, item) },
                            onDelete = { downloadManager.deleteDownload(item) },
                            modifier = Modifier.animateItem()
                        )
                        BrutalHairline(startIndent = 64.dp)
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, tileFill) = when (item.kind()) {
        DownloadFilter.VIDEO -> Icons.Default.VideoFile to Color(0xFFFFB067)
        DownloadFilter.AUDIO -> Icons.Default.AudioFile to Color(0xFF7FD8C8)
        DownloadFilter.IMAGES -> Icons.Default.Image to Color(0xFF8EC5FF)
        else -> (if (item.mimeType.startsWith("text/") || item.fileName.endsWith(".pdf", true)) Icons.Default.Description else Icons.Default.InsertDriveFile) to Color(0xFFB49CFF)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .brutalTap(
                enabled = item.status == DownloadStatus.COMPLETED,
                shape = RectangleShape,
                pressScale = 1f,
                onClick = onOpen
            )
            .animateContentSize()
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .brutalBox(fill = tileFill, border = BorderSubtle, borderWidth = 1.5.dp, shape = RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = InkBlack, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.fileName, color = TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when (item.status) {
                        DownloadStatus.DOWNLOADING -> if (item.totalBytes > 0) "${item.progress}%  |  ${formatFileSize(item.currentBytes)} of ${formatFileSize(item.totalBytes)}" else "Downloading  |  ${formatFileSize(item.currentBytes)}"
                        DownloadStatus.PAUSED -> "Paused at ${item.progress}%"
                        DownloadStatus.COMPLETED -> formatFileSize(item.totalBytes)
                        DownloadStatus.FAILED -> "Failed"
                        DownloadStatus.CANCELLED -> "Cancelled"
                    },
                    color = if (item.status == DownloadStatus.FAILED) DangerRed else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (item.status == DownloadStatus.FAILED) FontWeight.Bold else FontWeight.Normal
                )
            }
            when (item.status) {
                DownloadStatus.DOWNLOADING -> {
                    BrutalIconButton(Icons.Default.Pause, "Pause", onPause, iconSize = 20.dp)
                    BrutalIconButton(Icons.Default.Close, "Cancel", onCancel, iconSize = 20.dp)
                }
                DownloadStatus.PAUSED -> {
                    BrutalIconButton(Icons.Default.PlayArrow, "Resume", onResume, iconSize = 20.dp)
                    BrutalIconButton(Icons.Default.Close, "Cancel", onCancel, iconSize = 20.dp)
                }
                DownloadStatus.COMPLETED -> {
                    BrutalIconButton(Icons.Default.Share, "Share", onShare, iconSize = 20.dp)
                    BrutalIconButton(Icons.Default.DeleteOutline, "Delete file", onDelete, iconSize = 20.dp, tint = TextSecondary)
                }
                DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                    BrutalIconButton(Icons.Default.Refresh, "Retry", onResume, iconSize = 20.dp)
                    BrutalIconButton(Icons.Default.DeleteOutline, "Remove", onDelete, iconSize = 20.dp, tint = TextSecondary)
                }
            }
        }
        if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.PAUSED) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.padding(start = 48.dp, end = 12.dp)) {
                BrutalProgressLine(progress = if (item.totalBytes > 0) item.progress / 100f else null)
            }
        }
        if (item.status == DownloadStatus.FAILED) {
            item.errorMessage?.takeIf { it.isNotBlank() }?.let { reason ->
                Text(
                    reason,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(start = 48.dp, end = 12.dp, top = 4.dp)
                )
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "--"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        else -> String.format("%.0f KB", kb)
    }
}
