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

package com.browser.adfree.download

import com.browser.adfree.model.VideoQualityOption
import java.net.URI
import java.util.Locale

object VideoQualityParser {

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", kb)
        }
    }

    fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return ""
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    fun sanitizeFileName(title: String, qualityLabel: String? = null, extension: String = "mp4"): String {
        val cleanTitle = title
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(60)

        val cleanQuality = qualityLabel
            ?.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_")
            ?.trim('_')
            ?: ""

        val base = if (cleanTitle.isNotBlank()) cleanTitle else "video_${System.currentTimeMillis()}"
        val suffix = if (cleanQuality.isNotBlank()) "_$cleanQuality" else ""
        val ext = extension.trimStart('.').ifBlank { "mp4" }
        return "$base$suffix.$ext"
    }

    /**
     * Parses an HLS .m3u8 master playlist text to extract resolution variants (#EXT-X-STREAM-INF)
     */
    fun parseM3u8MasterPlaylist(
        playlistText: String,
        masterUrl: String,
        headers: Map<String, String> = emptyMap()
    ): List<VideoQualityOption> {
        val options = mutableListOf<VideoQualityOption>()
        val lines = playlistText.lines()
        var currentResolution: String? = null
        var currentBandwidth: Long = 0L
        var currentCodecs: String? = null

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-STREAM-INF:")) {
                // Extract RESOLUTION=WxH
                val resMatch = Regex("RESOLUTION=(\\d+x\\d+)", RegexOption.IGNORE_CASE).find(trimmed)
                currentResolution = resMatch?.groupValues?.get(1)

                // Extract BANDWIDTH=N
                val bwMatch = Regex("BANDWIDTH=(\\d+)", RegexOption.IGNORE_CASE).find(trimmed)
                currentBandwidth = bwMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L

                // Extract CODECS="..."
                val codecsMatch = Regex("CODECS=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(trimmed)
                currentCodecs = codecsMatch?.groupValues?.get(1)
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                // Stream URL line
                val streamUrl = resolveUrl(masterUrl, trimmed)
                val height = currentResolution?.substringAfter("x")?.toIntOrNull() ?: 0
                val label = when {
                    height >= 2160 -> "4K Ultra HD"
                    height >= 1440 -> "1440p QHD"
                    height >= 1080 -> "1080p Full HD"
                    height >= 720 -> "720p HD"
                    height >= 480 -> "480p SD"
                    height >= 360 -> "360p"
                    height >= 240 -> "240p"
                    height > 0 -> "${height}p"
                    else -> "HLS Stream"
                }

                val isAv1 = currentCodecs?.contains("av01", ignoreCase = true) == true
                val codecTag = when {
                    isAv1 -> "AV1"
                    currentCodecs?.contains("vp09", ignoreCase = true) == true -> "VP9"
                    currentCodecs?.contains("avc1", ignoreCase = true) == true -> "H.264"
                    currentCodecs?.contains("mp4a", ignoreCase = true) == true -> "AAC"
                    else -> if (isAv1) "AV1" else "HLS"
                }

                options.add(
                    VideoQualityOption(
                        label = label,
                        resolution = currentResolution ?: "",
                        codec = codecTag,
                        format = "M3U8",
                        url = streamUrl,
                        isAudioOnly = false,
                        isAv1 = isAv1,
                        mimeType = "application/x-mpegURL",
                        headers = headers
                    )
                )

                // Reset stream state
                currentResolution = null
                currentBandwidth = 0L
                currentCodecs = null
            }
        }

        return options.distinctBy { it.label + it.codec }
    }

    /**
     * Parses a googlevideo.com/videoplayback stream URL to extract resolution and format.
     * Strips &range= parameter to allow downloading the entire video stream file.
     */
    fun parseYouTubeItag(url: String): VideoQualityOption? {
        if (!url.contains("googlevideo.com/videoplayback")) return null
        val itag = Regex("[?&]itag=(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val cleanUrl = url.replace(Regex("&range=[^&]+"), "")

        return when (itag) {
            18 -> VideoQualityOption(label = "360p SD (Video + Audio)", resolution = "640x360", codec = "H.264", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = false, mimeType = "video/mp4")
            22 -> VideoQualityOption(label = "720p HD (Video + Audio)", resolution = "1280x720", codec = "H.264", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = false, mimeType = "video/mp4")
            137 -> VideoQualityOption(label = "1080p Full HD", resolution = "1920x1080", codec = "H.264", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = false, mimeType = "video/mp4")
            136 -> VideoQualityOption(label = "720p HD", resolution = "1280x720", codec = "H.264", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = false, mimeType = "video/mp4")
            135 -> VideoQualityOption(label = "480p SD", resolution = "854x480", codec = "H.264", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = false, mimeType = "video/mp4")
            134 -> VideoQualityOption(label = "360p SD", resolution = "640x360", codec = "H.264", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = false, mimeType = "video/mp4")
            140 -> VideoQualityOption(label = "Audio Track (M4A • 128 kbps)", resolution = "", codec = "AAC", format = "M4A", url = cleanUrl, isAudioOnly = true, isAv1 = false, mimeType = "audio/mp4")
            251 -> VideoQualityOption(label = "Audio Track (Opus • 160 kbps)", resolution = "", codec = "Opus", format = "WEBM", url = cleanUrl, isAudioOnly = true, isAv1 = false, mimeType = "audio/webm")
            248 -> VideoQualityOption(label = "1080p Full HD (WebM)", resolution = "1920x1080", codec = "VP9", format = "WEBM", url = cleanUrl, isAudioOnly = false, isAv1 = false, mimeType = "video/webm")
            247 -> VideoQualityOption(label = "720p HD (WebM)", resolution = "1280x720", codec = "VP9", format = "WEBM", url = cleanUrl, isAudioOnly = false, isAv1 = false, mimeType = "video/webm")
            399 -> VideoQualityOption(label = "1080p Full HD (AV1)", resolution = "1920x1080", codec = "AV1", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = true, mimeType = "video/mp4")
            398 -> VideoQualityOption(label = "720p HD (AV1)", resolution = "1280x720", codec = "AV1", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = true, mimeType = "video/mp4")
            397 -> VideoQualityOption(label = "480p SD (AV1)", resolution = "854x480", codec = "AV1", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = true, mimeType = "video/mp4")
            else -> {
                val isAudio = url.contains("mime=audio")
                VideoQualityOption(
                    label = if (isAudio) "Audio Track (Direct Stream)" else "Direct Video Stream",
                    resolution = "",
                    codec = if (isAudio) "AAC" else "H.264",
                    format = if (isAudio) "M4A" else "MP4",
                    url = cleanUrl,
                    isAudioOnly = isAudio,
                    isAv1 = false,
                    mimeType = if (isAudio) "audio/mp4" else "video/mp4"
                )
            }
        }
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        return try {
            val base = URI(baseUrl)
            base.resolve(relativeUrl).toString()
        } catch (e: Exception) {
            if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
                relativeUrl
            } else {
                val prefix = baseUrl.substringBeforeLast("/")
                "$prefix/$relativeUrl"
            }
        }
    }
}
