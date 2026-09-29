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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.model.DownloadItem
import com.browser.adfree.model.DownloadStatus
import com.browser.adfree.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsSheet(
    downloads: List<DownloadItem>,
    onOpenFile: (DownloadItem) -> Unit,
    onRemove: (DownloadItem) -> Unit,
    onClearCompleted: () -> Unit,
    onDismissRequest: () -> Unit,
    onOpenFullScreen: () -> Unit = {},
    onPause: (DownloadItem) -> Unit = {},
    onResume: (DownloadItem) -> Unit = {},
    onCancel: (DownloadItem) -> Unit = {},
    onShare: (DownloadItem) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = SurfaceDark,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Downloads (${downloads.size})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (downloads.any { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }) {
                        TextButton(
                            onClick = onClearCompleted,
                            colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                        ) {
                            Text("Clear", fontSize = 13.sp)
                        }
                    }

                    IconButton(
                        onClick = {
                            onDismissRequest()
                            onOpenFullScreen()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Expand Fullscreen",
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (downloads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No active or recent downloads",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .padding(bottom = 16.dp)
                ) {
                    items(downloads, key = { it.id }) { item ->
                        DownloadItemRow(
                            item = item,
                            onClick = { onOpenFile(item) },
                            onDelete = { onRemove(item) },
                            onPause = { onPause(item) },
                            onResume = { onResume(item) },
                            onCancel = { onCancel(item) },
                            onShare = { onShare(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadItemRow(
    item: DownloadItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onCancel: () -> Unit = {},
    onShare: () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceElevated)
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .clickable {
                if (item.status == DownloadStatus.COMPLETED) onClick()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Icon based on status
        Icon(
            imageVector = when (item.status) {
                DownloadStatus.DOWNLOADING -> Icons.Default.Downloading
                DownloadStatus.PAUSED -> Icons.Default.PauseCircle
                DownloadStatus.COMPLETED -> Icons.Default.CheckCircle
                DownloadStatus.FAILED -> Icons.Default.Error
                DownloadStatus.CANCELLED -> Icons.Default.Cancel
            },
            contentDescription = null,
            tint = when (item.status) {
                DownloadStatus.DOWNLOADING -> AccentCyan
                DownloadStatus.PAUSED -> WarningAmber
                DownloadStatus.COMPLETED -> ShieldGreen
                DownloadStatus.FAILED -> DangerRed
                DownloadStatus.CANCELLED -> TextSecondary
            },
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.fileName,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            when (item.status) {
                DownloadStatus.DOWNLOADING -> {
                    Column {
                        LinearProgressIndicator(
                            progress = (item.progress / 100f).coerceIn(0f, 1f),
                            color = AccentCyan,
                            trackColor = SurfaceCard,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${item.progress}% (${formatFileSize(item.currentBytes)} / ${formatFileSize(item.totalBytes)})",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                DownloadStatus.PAUSED -> {
                    Column {
                        LinearProgressIndicator(
                            progress = (item.progress / 100f).coerceIn(0f, 1f),
                            color = WarningAmber,
                            trackColor = SurfaceCard,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Paused • ${item.progress}% (${formatFileSize(item.currentBytes)})",
                            color = WarningAmber,
                            fontSize = 11.sp
                        )
                    }
                }
                DownloadStatus.COMPLETED -> {
                    Text(
                        text = "Completed • ${formatFileSize(item.totalBytes)}",
                        color = ShieldGreen,
                        fontSize = 11.sp
                    )
                }
                DownloadStatus.FAILED -> {
                    Text(
                        text = "Failed",
                        color = DangerRed,
                        fontSize = 11.sp
                    )
                }
                DownloadStatus.CANCELLED -> {
                    Text(
                        text = "Cancelled",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Action Buttons Row
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (item.status) {
                DownloadStatus.DOWNLOADING -> {
                    IconButton(
                        onClick = onPause,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pause",
                            tint = AccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = DangerRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                DownloadStatus.PAUSED -> {
                    IconButton(
                        onClick = onResume,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Resume",
                            tint = AccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = DangerRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                DownloadStatus.COMPLETED -> {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = AccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                    IconButton(
                        onClick = onResume,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            tint = AccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
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
