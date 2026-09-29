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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.browser.adfree.download.VideoQualityParser
import com.browser.adfree.model.DetectedVideo
import com.browser.adfree.model.VideoQualityOption
import com.browser.adfree.ui.theme.*

private enum class DownloadMediaType {
    VIDEO,
    AUDIO
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDownloadSheet(
    video: DetectedVideo,
    onDownload: (option: VideoQualityOption, filename: String) -> Unit,
    onDismiss: () -> Unit,
    preferAv1: Boolean = false,
    showWebm: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val rawVideoQualities = remember(video) { video.videoQualities }
    val videoQualities = remember(rawVideoQualities, showWebm) {
        if (showWebm) rawVideoQualities else rawVideoQualities.filter { !it.isWebm && !it.format.equals("WEBM", ignoreCase = true) }
    }
    val rawAudioQualities = remember(video) { video.audioQualities }
    val audioQualities = remember(rawAudioQualities, showWebm) {
        if (showWebm) rawAudioQualities else rawAudioQualities.filter { !it.isWebm && !it.format.equals("WEBM", ignoreCase = true) }
    }

    var selectedTab by remember {
        mutableStateOf(if (videoQualities.isNotEmpty()) DownloadMediaType.VIDEO else DownloadMediaType.AUDIO)
    }

    var selectedOption by remember(selectedTab, video, videoQualities, audioQualities, preferAv1) {
        mutableStateOf(
            if (selectedTab == DownloadMediaType.VIDEO) {
                (if (preferAv1) videoQualities.firstOrNull { it.isAv1 } else null)
                    ?: videoQualities.firstOrNull()
                    ?: video.qualities.firstOrNull()
            } else {
                audioQualities.firstOrNull() ?: video.qualities.firstOrNull()
            }
        )
    }

    // AV1 confirmation dialog state
    var showAv1ChoiceDialog by remember { mutableStateOf(false) }
    var pendingDownloadOption by remember { mutableStateOf<VideoQualityOption?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkBg,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                color = TextSecondary.copy(alpha = 0.35f),
                shape = RoundedCornerShape(2.dp)
            ) {
                Box(modifier = Modifier.size(width = 36.dp, height = 4.dp))
            }
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp)
        ) {
            // Header Row: Title + Close
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DownloadForOffline,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Download Media",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Video Preview Card
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                    .padding(10.dp)
            ) {
                // Thumbnail Box
                Box(
                    modifier = Modifier
                        .size(width = 84.dp, height = 56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E2430)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!video.thumbnail.isNullOrBlank()) {
                        AsyncImage(
                            model = video.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = if (selectedTab == DownloadMediaType.VIDEO) Icons.Default.PlayCircle else Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    if (video.durationSeconds > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.75f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = VideoQualityParser.formatDuration(video.durationSeconds),
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title + Domain
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (video.sourceDomain.isNotBlank()) {
                        Text(
                            text = video.sourceDomain,
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Top Segmented Tabs: Video vs Audio
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(21.dp))
                    .padding(3.dp)
            ) {
                // Video Tab
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selectedTab == DownloadMediaType.VIDEO) AccentCyan else Color.Transparent)
                        .clickable {
                            selectedTab = DownloadMediaType.VIDEO
                            selectedOption = videoQualities.firstOrNull() ?: video.qualities.firstOrNull()
                        }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = if (selectedTab == DownloadMediaType.VIDEO) DarkBg else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Video (${videoQualities.size})",
                            color = if (selectedTab == DownloadMediaType.VIDEO) DarkBg else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == DownloadMediaType.VIDEO) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                // Audio Tab
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selectedTab == DownloadMediaType.AUDIO) AccentCyan else Color.Transparent)
                        .clickable {
                            selectedTab = DownloadMediaType.AUDIO
                            selectedOption = audioQualities.firstOrNull() ?: video.qualities.firstOrNull()
                        }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = if (selectedTab == DownloadMediaType.AUDIO) DarkBg else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Audio (${audioQualities.size})",
                            color = if (selectedTab == DownloadMediaType.AUDIO) DarkBg else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == DownloadMediaType.AUDIO) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quality Options List
            val currentList = if (selectedTab == DownloadMediaType.VIDEO) videoQualities else audioQualities

            if (currentList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedTab == DownloadMediaType.VIDEO) "No direct video streams found" else "No audio-only streams available",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(currentList, key = { it.id }) { option ->
                        val isSelected = selectedOption?.id == option.id

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) AccentCyan.copy(alpha = 0.12f) else SurfaceElevated)
                                .border(
                                    1.dp,
                                    if (isSelected) AccentCyan else BorderSubtle,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedOption = option }
                                .padding(horizontal = 14.dp, vertical = 11.dp)
                        ) {
                            // Left: Radio + Label + Codec
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedOption = option },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = AccentCyan,
                                        unselectedColor = TextSecondary.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier.size(20.dp)
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = option.label,
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )

                                        if (option.isAv1) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFF8B5CF6).copy(alpha = 0.25f))
                                                    .border(0.5.dp, Color(0xFF8B5CF6), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = "AV1",
                                                    color = Color(0xFFA78BFA),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else if (option.codec != null) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(SurfaceElevated)
                                                    .border(0.5.dp, BorderSubtle, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = option.codec,
                                                    color = TextSecondary,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    if (option.resolution.isNotBlank()) {
                                        Text(
                                            text = option.resolution,
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            // Right: Format badge & Estimated Size
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = option.format,
                                    color = AccentCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (option.estimatedSizeBytes > 0) {
                                    Text(
                                        text = VideoQualityParser.formatFileSize(option.estimatedSizeBytes),
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons: Download + Copy Link
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Copy Link Button
                OutlinedButton(
                    onClick = {
                        selectedOption?.let { opt ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Media URL", opt.url))
                            Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(BorderSubtle, BorderSubtle))),
                    modifier = Modifier
                        .height(48.dp)
                        .weight(0.35f)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Link",
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Primary Download Button
                Button(
                    onClick = {
                        selectedOption?.let { opt ->
                            // Check if an AV1 variant exists and user hasn't chosen
                            val matchingAv1 = videoQualities.firstOrNull { it.resolution == opt.resolution && it.isAv1 && it.id != opt.id }
                            if (!preferAv1 && matchingAv1 != null && !opt.isAv1) {
                                pendingDownloadOption = opt
                                showAv1ChoiceDialog = true
                            } else {
                                val ext = if (opt.format == "M3U8") "m3u8" else if (opt.format == "WEBM") "webm" else if (opt.isAudioOnly) "m4a" else "mp4"
                                val filename = VideoQualityParser.sanitizeFileName(video.title, opt.label, ext)
                                onDownload(opt, filename)
                                onDismiss()
                            }
                        }
                    },
                    enabled = selectedOption != null,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan,
                        contentColor = DarkBg,
                        disabledContainerColor = SurfaceElevated,
                        disabledContentColor = TextSecondary.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .weight(0.65f)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedOption != null) "Download" else "Select Quality",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }

    // AV1 Selection Dialog (when standard resolution is picked but AV1 is available)
    if (showAv1ChoiceDialog && pendingDownloadOption != null) {
        val standardOpt = pendingDownloadOption!!
        val av1Opt = videoQualities.firstOrNull { it.resolution == standardOpt.resolution && it.isAv1 }

        AlertDialog(
            onDismissRequest = { showAv1ChoiceDialog = false },
            containerColor = DarkBg,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "AV1 High Efficiency Available",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "An AV1 encoded stream is available for this resolution (${standardOpt.resolution}). AV1 provides higher video quality with smaller file sizes on supported devices.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = av1Opt ?: standardOpt
                        val ext = if (target.format == "WEBM") "webm" else "mp4"
                        val filename = VideoQualityParser.sanitizeFileName(video.title, "${target.label}_AV1", ext)
                        onDownload(target, filename)
                        showAv1ChoiceDialog = false
                        onDismiss()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = DarkBg)
                ) {
                    Text("Download AV1", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val ext = if (standardOpt.format == "WEBM") "webm" else "mp4"
                        val filename = VideoQualityParser.sanitizeFileName(video.title, standardOpt.label, ext)
                        onDownload(standardOpt, filename)
                        showAv1ChoiceDialog = false
                        onDismiss()
                    }
                ) {
                    Text("Standard MP4", color = TextPrimary)
                }
            }
        )
    }
}
