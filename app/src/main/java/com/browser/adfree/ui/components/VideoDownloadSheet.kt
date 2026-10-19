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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.browser.adfree.download.VideoQualityParser
import com.browser.adfree.model.DetectedVideo
import com.browser.adfree.model.VideoQualityOption
import com.browser.adfree.ui.theme.*

private enum class MediaKind { VIDEO, AUDIO }

/** True when the extractor flagged the stream as having no audio track. */
private fun looksSilent(option: VideoQualityOption): Boolean =
    option.label.contains("Video only", ignoreCase = true) ||
        option.label.contains("Audio unknown", ignoreCase = true) ||
        option.label.contains("no audio", ignoreCase = true)

private fun heightOf(option: VideoQualityOption): Int? {
    Regex("x(\\d{3,4})").find(option.resolution)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
    return Regex("(\\d{3,4})p").find(option.resolution + " " + option.label)?.groupValues?.get(1)?.toIntOrNull()
}

private fun kbpsOf(option: VideoQualityOption): Int? =
    Regex("(\\d{2,3})\\s?k").find(option.label)?.groupValues?.get(1)?.toIntOrNull()

/**
 * One row per resolution: among the streams for a height, prefer one that
 * has sound, then the user's codec preference, then the widely playable MP4.
 */
private fun collapseVideo(list: List<VideoQualityOption>, preferAv1: Boolean): List<VideoQualityOption> {
    val (sized, unsized) = list.partition { heightOf(it) != null }
    val best = sized.groupBy { heightOf(it)!! }.map { (_, group) ->
        group.sortedWith(
            compareBy<VideoQualityOption>(
                { looksSilent(it) },
                { if (preferAv1) !it.isAv1 else it.isAv1 },
                { it.isWebm },
                { !it.isProgressive && it.audioUrl == null }
            )
        ).first()
    }.sortedByDescending { heightOf(it) }
    return best + unsized.distinctBy { it.label }
}

private fun titleFor(option: VideoQualityOption, kind: MediaKind): String = when (kind) {
    MediaKind.VIDEO -> heightOf(option)?.let { h -> if (h >= 2160) "4K" else "${h}p" } ?: option.label.ifBlank { "Video" }
    MediaKind.AUDIO -> kbpsOf(option)?.let { "$it kbps" } ?: option.label.ifBlank { "Audio" }
}

private fun detailFor(option: VideoQualityOption): String {
    val format = when {
        option.isAudioOnly && (option.codec?.contains("opus", true) == true || option.isWebm) -> "Opus"
        option.isAudioOnly -> "M4A"
        option.format.equals("M3U8", true) -> "Stream"
        option.isWebm -> "WebM"
        else -> "MP4"
    }
    val parts = mutableListOf(format)
    if (option.isAv1) parts += "AV1"
    if (option.estimatedSizeBytes > 0) parts += VideoQualityParser.formatFileSize(option.estimatedSizeBytes)
    return parts.joinToString("  |  ")
}

private fun extensionFor(option: VideoQualityOption): String = when {
    option.format == "M3U8" -> "mp4"
    option.isAudioOnly -> if (option.format == "WEBM" || option.codec?.contains("opus", true) == true) "opus" else if (option.format == "MP3") "mp3" else "m4a"
    option.format == "WEBM" -> "webm"
    else -> "mp4"
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
    val videoOptions = remember(video, preferAv1, showWebm) {
        val pool = video.videoQualities.let { all -> if (showWebm || all.none { !it.isWebm }) all else all.filter { !it.isWebm } }
        collapseVideo(pool, preferAv1)
    }
    val audioOptions = remember(video, showWebm) {
        val pool = video.audioQualities.let { all -> if (showWebm || all.none { !it.isWebm }) all else all.filter { !it.isWebm } }
        pool.sortedByDescending { kbpsOf(it) ?: 0 }.distinctBy { titleFor(it, MediaKind.AUDIO) + it.isWebm }
    }
    // Formats can arrive after the sheet opens; until the user picks a tab,
    // follow whichever list has content, video first.
    var chosenKind by remember { mutableStateOf<MediaKind?>(null) }
    val kind = chosenKind ?: if (videoOptions.isNotEmpty() || audioOptions.isEmpty()) MediaKind.VIDEO else MediaKind.AUDIO
    val options = if (kind == MediaKind.VIDEO) videoOptions else audioOptions
    var selectedId by remember(kind, options) {
        mutableStateOf(
            (if (kind == MediaKind.VIDEO) options.firstOrNull { !looksSilent(it) && (heightOf(it) ?: 0) <= 1080 } else null)?.id
                ?: options.firstOrNull()?.id
        )
    }
    val selected = options.firstOrNull { it.id == selectedId }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceDark,
        contentColor = TextPrimary,
        tonalElevation = 0.dp,
        dragHandle = { BrutalSheetHandle() },
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            // What is being downloaded
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 4.dp, bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 96.dp, height = 54.dp)
                        .brutalBox(fill = SurfaceCard, border = BorderSubtle, shape = RoundedCornerShape(5.dp))
                        .clip(RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    if (video.thumbnail != null) {
                        AsyncImage(
                            model = video.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                    if (video.durationSeconds > 0) {
                        Text(
                            VideoQualityParser.formatDuration(video.durationSeconds),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(3.dp)
                                .background(InkBlack, RoundedCornerShape(2.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(video.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
                    if (video.sourceDomain.isNotBlank()) {
                        Text(video.sourceDomain.removePrefix("www."), color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                    }
                }
                BrutalIconButton(Icons.Default.Close, "Close", onDismiss)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(start = 16.dp, end = 19.dp, bottom = 14.dp)
            ) {
                KindTab("Video", videoOptions.size, kind == MediaKind.VIDEO, Modifier.weight(1f)) { chosenKind = MediaKind.VIDEO }
                KindTab("Audio", audioOptions.size, kind == MediaKind.AUDIO, Modifier.weight(1f)) { chosenKind = MediaKind.AUDIO }
            }

            BrutalRule()
            if (options.isEmpty()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (kind == MediaKind.VIDEO) "Finding video formats" else "Finding audio formats",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))
                    BrutalProgressLine(progress = null)
                    Spacer(Modifier.height(8.dp))
                    Text("This can take a few seconds the first time.", color = TextSecondary, fontSize = 12.5.sp)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(options, key = { it.id }) { option ->
                        OptionRow(
                            title = titleFor(option, kind),
                            detail = detailFor(option),
                            warning = if (kind == MediaKind.VIDEO && looksSilent(option)) "No sound" else null,
                            selected = option.id == selectedId,
                            audio = kind == MediaKind.AUDIO,
                            onClick = { selectedId = option.id }
                        )
                        BrutalHairline()
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp)
            ) {
                BrutalButton(
                    text = selected?.let { "Download ${titleFor(it, kind)}" } ?: "Download",
                    icon = Icons.Default.FileDownload,
                    enabled = selected != null,
                    onClick = {
                        val option = selected ?: return@BrutalButton
                        val name = VideoQualityParser.sanitizeFileName(video.title, titleFor(option, kind), extensionFor(option))
                        onDownload(option, name)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(4.dp))
                BrutalIconButton(
                    icon = Icons.Default.ContentCopy,
                    contentDescription = "Copy stream link",
                    enabled = selected != null,
                    onClick = {
                        selected?.let {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("Media link", it.url))
                            Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun KindTab(label: String, count: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .height(44.dp)
            .brutalPressable(
                fill = if (selected) AccentCyan else SurfaceDark,
                border = BorderSubtle,
                shadow = HardShadow,
                shadowOffset = if (selected) 0.dp else 3.dp,
                role = Role.Tab,
                onClick = onClick
            )
    ) {
        Text(label, color = if (selected) OnAccent else TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), color = if (selected) OnAccent else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OptionRow(
    title: String,
    detail: String,
    warning: String?,
    selected: Boolean,
    audio: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .background(if (selected) AccentCyan.copy(alpha = 0.18f) else Color.Transparent)
            .brutalTap(role = Role.RadioButton, shape = androidx.compose.ui.graphics.RectangleShape, pressScale = 1f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (audio) {
            BrutalIconTile(Icons.Default.MusicNote)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (warning != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        warning,
                        color = InkBlack,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(WarningAmber, RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            Text(detail, color = TextSecondary, fontSize = 12.5.sp)
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .brutalBox(fill = if (selected) AccentCyan else SurfaceDark, border = BorderSubtle, shape = RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = OnAccent, modifier = Modifier.size(17.dp))
        }
    }
}

/** Short label for the floating button: what the sheet would pick first, e.g. "1080p | MP4 | 45 MB". */
fun downloadSummary(video: DetectedVideo, preferAv1: Boolean, showWebm: Boolean): String? {
    val pool = video.videoQualities.let { all -> if (showWebm || all.none { !it.isWebm }) all else all.filter { !it.isWebm } }
    val best = collapseVideo(pool, preferAv1).firstOrNull { !looksSilent(it) && (heightOf(it) ?: 0) <= 1080 }
        ?: collapseVideo(pool, preferAv1).firstOrNull()
    if (best != null) return titleFor(best, MediaKind.VIDEO) + "  |  " + detailFor(best)
    val audio = video.audioQualities.maxByOrNull { kbpsOf(it) ?: 0 } ?: return null
    return "Audio  |  " + detailFor(audio)
}
