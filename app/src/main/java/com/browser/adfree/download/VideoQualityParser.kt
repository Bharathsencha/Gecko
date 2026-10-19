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
            .replace(Regex("[\\\\/:*?\"<>|•\u0000-\u001f]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(60)

        val cleanQuality = qualityLabel
            ?.replace(Regex("[\\\\/:*?\"<>|•\u0000-\u001f\\s]"), "_")
            ?.trim('_')
            ?: ""

        val base = if (cleanTitle.isNotBlank()) cleanTitle else "video_${System.currentTimeMillis()}"
        val suffix = if (cleanQuality.isNotBlank()) "_$cleanQuality" else ""
        val ext = extension.trimStart('.').ifBlank { "mp4" }
        return "$base$suffix.$ext"
    }

    /**
     * Checks whether an M3U8 playlist is a master playlist (containing variant streams).
     */
    fun isMasterPlaylist(playlistText: String): Boolean {
        return playlistText.contains("#EXT-X-STREAM-INF")
    }

    /**
     * Parses an HLS .m3u8 media playlist to extract individual segment URLs.
     */
    fun parseM3u8Segments(playlistText: String, playlistUrl: String): List<String> {
        val segments = mutableListOf<String>()
        val lines = playlistText.lines()
        var initSegmentUrl: String? = null

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-MAP:")) {
                val uriMatch = Regex("URI=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(trimmed)
                if (uriMatch != null) {
                    initSegmentUrl = resolveUrl(playlistUrl, uriMatch.groupValues[1])
                    if (initSegmentUrl.isNotBlank() && !segments.contains(initSegmentUrl)) {
                        segments.add(initSegmentUrl)
                    }
                }
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val segUrl = resolveUrl(playlistUrl, trimmed)
                if (segUrl.isNotBlank()) {
                    segments.add(segUrl)
                }
            }
        }
        return segments
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
        var currentAudioGroup: String? = null

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

                // Extract AUDIO="group-id" so separate audio renditions can be fetched
                currentAudioGroup = attribute(trimmed, "AUDIO")
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
                    else -> if (isAv1) "AV1" else "H.264"
                }

                options.add(
                    VideoQualityOption(
                        label = label,
                        resolution = currentResolution ?: (if (height > 0) "${height}p" else "Auto"),
                        codec = codecTag,
                        format = "M3U8",
                        url = streamUrl,
                        isAudioOnly = false,
                        isAv1 = isAv1,
                        mimeType = "application/x-mpegURL",
                        headers = headers,
                        audioGroupId = currentAudioGroup
                    )
                )

                // Reset stream state
                currentResolution = null
                currentBandwidth = 0L
                currentCodecs = null
                currentAudioGroup = null
            }
        }

        return options.distinctBy { it.label + it.resolution + it.codec }
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
            18 -> VideoQualityOption(label = "360p SD (Video + Audio)", resolution = "640x360", codec = "H.264", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = false, isProgressive = true, mimeType = "video/mp4")
            22 -> VideoQualityOption(label = "720p HD (Video + Audio)", resolution = "1280x720", codec = "H.264", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = false, isProgressive = true, mimeType = "video/mp4")
            313 -> VideoQualityOption(label = "4K Ultra HD (WebM)", resolution = "3840x2160", codec = "VP9", format = "WEBM", url = cleanUrl, isAudioOnly = false, isAv1 = false, mimeType = "video/webm")
            271 -> VideoQualityOption(label = "1440p QHD (WebM)", resolution = "2560x1440", codec = "VP9", format = "WEBM", url = cleanUrl, isAudioOnly = false, isAv1 = false, mimeType = "video/webm")
            299, 303 -> VideoQualityOption(label = "1080p60 Full HD", resolution = "1920x1080", codec = "H.264", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = false, mimeType = "video/mp4")
            137 -> VideoQualityOption(label = "1080p Full HD", resolution = "1920x1080", codec = "H.264", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = false, mimeType = "video/mp4")
            298, 302 -> VideoQualityOption(label = "720p60 HD", resolution = "1280x720", codec = "H.264", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = false, mimeType = "video/mp4")
            136 -> VideoQualityOption(label = "720p HD", resolution = "1280x720", codec = "H.264", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = false, mimeType = "video/mp4")
            135 -> VideoQualityOption(label = "480p SD", resolution = "854x480", codec = "H.264", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = false, mimeType = "video/mp4")
            134 -> VideoQualityOption(label = "360p SD", resolution = "640x360", codec = "H.264", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = false, mimeType = "video/mp4")
            133 -> VideoQualityOption(label = "240p SD", resolution = "426x240", codec = "H.264", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = false, mimeType = "video/mp4")
            160 -> VideoQualityOption(label = "144p", resolution = "256x144", codec = "H.264", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = false, mimeType = "video/mp4")
            140 -> VideoQualityOption(label = "Audio Track (M4A • 128 kbps)", resolution = "", codec = "AAC", format = "M4A", url = cleanUrl, isAudioOnly = true, isAv1 = false, isProgressive = true, mimeType = "audio/mp4")
            251 -> VideoQualityOption(label = "Audio Track (Opus • 160 kbps)", resolution = "", codec = "Opus", format = "WEBM", url = cleanUrl, isAudioOnly = true, isAv1 = false, isProgressive = true, mimeType = "audio/webm")
            248 -> VideoQualityOption(label = "1080p Full HD (WebM)", resolution = "1920x1080", codec = "VP9", format = "WEBM", url = cleanUrl, isAudioOnly = false, isAv1 = false, mimeType = "video/webm")
            247 -> VideoQualityOption(label = "720p HD (WebM)", resolution = "1280x720", codec = "VP9", format = "WEBM", url = cleanUrl, isAudioOnly = false, isAv1 = false, mimeType = "video/webm")
            244 -> VideoQualityOption(label = "480p SD (WebM)", resolution = "854x480", codec = "VP9", format = "WEBM", url = cleanUrl, isAudioOnly = false, isAv1 = false, mimeType = "video/webm")
            401 -> VideoQualityOption(label = "4K Ultra HD (AV1)", resolution = "3840x2160", codec = "AV1", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = true, mimeType = "video/mp4")
            400 -> VideoQualityOption(label = "1440p QHD (AV1)", resolution = "2560x1440", codec = "AV1", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = true, mimeType = "video/mp4")
            399 -> VideoQualityOption(label = "1080p Full HD (AV1)", resolution = "1920x1080", codec = "AV1", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = true, mimeType = "video/mp4")
            398 -> VideoQualityOption(label = "720p HD (AV1)", resolution = "1280x720", codec = "AV1", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = true, mimeType = "video/mp4")
            397 -> VideoQualityOption(label = "480p SD (AV1)", resolution = "854x480", codec = "AV1", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = true, mimeType = "video/mp4")
            396 -> VideoQualityOption(label = "360p SD (AV1)", resolution = "640x360", codec = "AV1", format = "MP4", url = cleanUrl, isAudioOnly = false, isAv1 = true, mimeType = "video/mp4")
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
                    isProgressive = !isAudio,
                    mimeType = if (isAudio) "audio/mp4" else "video/mp4"
                )
            }
        }
    }

    /**
     * Parses `#EXT-X-MEDIA:TYPE=AUDIO` entries from a master playlist and returns
     * a map of audio group id -> rendition playlist URI.
     *
     * Picking only the highest-resolution variant used to select a video-only
     * stream, which is why HLS downloads frequently came out silent.
     */
    fun parseM3u8AudioRenditions(playlistText: String, masterUrl: String): Map<String, String> {
        val result = mutableMapOf<String, String>()

        for (line in playlistText.lines()) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("#EXT-X-MEDIA:") || !trimmed.contains("TYPE=AUDIO", ignoreCase = true)) continue

            val groupId = attribute(trimmed, "GROUP-ID") ?: continue
            val uri = attribute(trimmed, "URI") ?: continue
            if (uri.isBlank()) continue

            // Prefer the first rendition per group; several playlists list multiple languages.
            if (!result.containsKey(groupId)) {
                result[groupId] = resolveUrl(masterUrl, uri)
            }
        }
        return result
    }

    /** Extracts KEY="value" from an M3U attribute list. */
    private fun attribute(line: String, key: String): String? {
        val regex = Regex("$key=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
        return regex.find(line)?.groupValues?.get(1)
    }

    fun resolveUrl(baseUrl: String, relativeUrl: String): String {
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

    /**
     * Sorts video quality options in descending order of resolution / quality,
     * placing video streams first (4K -> 144p) and audio-only streams at the end.
     * Deduplicates options strictly by resolution and format.
     */
    /**
     * True when [a] and [b] address the same document (same host + path).
     *
     * Used to decide whether a newly detected stream belongs to the entry we
     * already hold: a listing page must never be merged into the entry for the
     * video page, or the download sheet ends up naming the site instead of the
     * video.
     */
    fun samePage(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        if (a.equals(b, ignoreCase = true)) return true
        return try {
            val ua = java.net.URI(a)
            val ub = java.net.URI(b)
            ua.host.equals(ub.host, ignoreCase = true) &&
                ua.path.trimEnd('/') == ub.path.trimEnd('/')
        } catch (e: Exception) {
            false
        }
    }

    fun sortQualities(list: List<VideoQualityOption>): List<VideoQualityOption> {
        fun getScore(option: VideoQualityOption): Int {
            if (option.isAudioOnly) return -100
            // An HLS master with no height in its URL carries every rendition
            // (and any separate audio group), so it outranks the individual
            // variants we may have sniffed alongside it.
            if (option.format.equals("M3U8", true) && option.resolution.isBlank()) return 5000
            val heightFromRes = option.resolution.substringAfter("x").filter { it.isDigit() }.toIntOrNull()
            if (heightFromRes != null && heightFromRes > 0) return heightFromRes
            val labelLower = option.label.lowercase(Locale.US)
            return when {
                labelLower.contains("4k") || labelLower.contains("2160") -> 2160
                labelLower.contains("1440") -> 1440
                labelLower.contains("1080") -> 1080
                labelLower.contains("720") -> 720
                labelLower.contains("480") -> 480
                labelLower.contains("360") -> 360
                labelLower.contains("240") -> 240
                labelLower.contains("144") -> 144
                else -> 100
            }
        }

        val deduped = list.distinctBy {
            val normRes = if (it.resolution.contains("x")) it.resolution.substringAfter("x") else it.resolution
            "${normRes.trim()}_${it.format.uppercase().trim()}_${it.isAv1}_${it.isAudioOnly}"
        }

        return deduped.sortedWith(
            compareByDescending<VideoQualityOption> { getScore(it) }
                .thenByDescending { it.estimatedSizeBytes }
                .thenBy { if (it.format == "MP4") 0 else 1 }
        )
    }

}

