/*
 * Copyright (C) 2024-2026 Gibbly Gobbly <gibblygobbly@proton.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.browser.adfree.model

import java.util.UUID

data class DetectedVideo(
    val id: String = UUID.randomUUID().toString(),
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
    val isProgressive: Boolean = false, // True if single stream contains both video + audio natively
    val audioUrl: String? = null,       // Paired high-bitrate audio stream for adaptive video muxing
    val mimeType: String? = null,
    val headers: Map<String, String> = emptyMap(),
    /** For HLS master variants: the EXT-X-MEDIA audio group this variant plays with. */
    val audioGroupId: String? = null
) {
    val isWebm: Boolean get() = format.equals("WEBM", ignoreCase = true) || mimeType?.contains("webm", ignoreCase = true) == true || codec?.contains("vp", ignoreCase = true) == true || codec?.contains("opus", ignoreCase = true) == true
}

