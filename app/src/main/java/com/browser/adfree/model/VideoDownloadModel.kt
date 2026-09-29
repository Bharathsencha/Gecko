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

package com.browser.adfree.model

import java.util.UUID

data class DetectedVideo(
    val id: String = UUID.randomUUID().toString(),
    val tabId: Int = -1,
    val pageUrl: String,
    val title: String,
    val thumbnail: String? = null,
    val durationSeconds: Long = 0,
    val sourceDomain: String = "",
    val qualities: List<VideoQualityOption> = emptyList()
) {
    val videoQualities: List<VideoQualityOption> get() = qualities.filter { !it.isAudioOnly }
    val audioQualities: List<VideoQualityOption> get() = qualities.filter { it.isAudioOnly }
}

data class VideoQualityOption(
    val id: String = UUID.randomUUID().toString(),
    val label: String,                // e.g. "1080p Full HD", "720p HD", "480p SD", "360p", "Audio (128 kbps)"
    val resolution: String = "",       // e.g. "1920x1080", "1280x720"
    val codec: String? = null,        // e.g. "AV1", "H.264", "VP9", "AAC", "Opus"
    val format: String = "MP4",       // e.g. "MP4", "WEBM", "M4A", "MP3", "M3U8"
    val url: String,
    val estimatedSizeBytes: Long = 0L,
    val isAudioOnly: Boolean = false,
    val isAv1: Boolean = false,
    val mimeType: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val alternativeAv1Option: VideoQualityOption? = null // Linked AV1 variant for the same resolution
)
