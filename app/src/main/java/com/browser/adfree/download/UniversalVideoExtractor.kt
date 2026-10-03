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

import android.content.Context
import android.util.Log
import com.browser.adfree.model.DetectedVideo
import com.browser.adfree.model.VideoQualityOption
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

object UniversalVideoExtractor {

    private const val TAG = "UniversalExtractor"
    @Volatile
    private var isInitialized = false
    @Volatile
    private var isInitializing = false

    fun init(context: Context) {
        if (isInitialized || isInitializing) return
        isInitializing = true
        try {
            YoutubeDL.getInstance().init(context.applicationContext)
            isInitialized = true
            Log.i(TAG, "YoutubeDL engine initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize YoutubeDL engine: ${e.message}", e)
        } finally {
            isInitializing = false
        }
    }

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, DetectedVideo>>()
    private val CACHE_TTL_MS = 3 * 60 * 1000L

    suspend fun extract(pageUrl: String, context: Context): DetectedVideo? = withContext(Dispatchers.IO) {
        if (pageUrl.isBlank() || pageUrl.startsWith("about:") || pageUrl == "about:home") {
            return@withContext null
        }

        val now = System.currentTimeMillis()
        val cached = cache[pageUrl]
        if (cached != null && (now - cached.first) < CACHE_TTL_MS) {
            return@withContext cached.second
        }

        if (!isInitialized) {
            init(context)
        }

        // Tier 1: yt-dlp Engine (youtubedl-android) supporting 1,800+ websites
        if (isInitialized) {
            val ytDlpResult = extractViaYtDlp(pageUrl)
            if (ytDlpResult != null && ytDlpResult.qualities.isNotEmpty()) {
                Log.i(TAG, "Successfully extracted via yt-dlp: ${ytDlpResult.title} (${ytDlpResult.qualities.size} streams)")
                cache[pageUrl] = Pair(System.currentTimeMillis(), ytDlpResult)
                return@withContext ytDlpResult
            }
        }

        // Tier 2: YouTube Specialized In-Page / InnerTube Extractor fallback for YouTube URLs
        val isYouTube = pageUrl.contains("youtube.com") || pageUrl.contains("youtu.be")
        if (isYouTube) {
            val ytResult = YouTubeStreamExtractor.extract(pageUrl)
            if (ytResult != null && ytResult.qualities.isNotEmpty()) {
                Log.i(TAG, "Successfully extracted via YouTubeStreamExtractor: ${ytResult.title}")
                cache[pageUrl] = Pair(System.currentTimeMillis(), ytResult)
                return@withContext ytResult
            }
        }

        return@withContext null
    }

    private fun extractViaYtDlp(pageUrl: String): DetectedVideo? {
        return try {
            val request = YoutubeDLRequest(pageUrl).apply {
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("--socket-timeout", "10")
            }

            val videoInfo: VideoInfo = YoutubeDL.getInstance().getInfo(request)
            val title = videoInfo.title?.ifBlank { null } ?: "Web Video"
            val thumbnail = videoInfo.thumbnail
            val durationSeconds = videoInfo.duration.toLong()
            val host = try { URI(pageUrl).host ?: "" } catch (e: Exception) { "" }

            val rawFormats: List<VideoFormat> = videoInfo.formats ?: emptyList()
            if (rawFormats.isEmpty()) {
                // If direct stream URL is provided on VideoInfo itself
                val directUrl = videoInfo.url
                if (!directUrl.isNullOrBlank() && directUrl.startsWith("http")) {
                    val isM3u8 = directUrl.contains(".m3u8")
                    val isWebm = directUrl.contains(".webm")
                    val format = if (isM3u8) "M3U8" else if (isWebm) "WEBM" else "MP4"
                    return DetectedVideo(
                        pageUrl = pageUrl,
                        title = title,
                        thumbnail = thumbnail,
                        durationSeconds = durationSeconds,
                        sourceDomain = host,
                        qualities = listOf(
                            VideoQualityOption(
                                label = if (isM3u8) "HLS Master Stream" else "Direct Video ($format)",
                                resolution = "Auto",
                                codec = if (isWebm) "VP9" else "H.264",
                                format = format,
                                url = directUrl,
                                estimatedSizeBytes = 0L,
                                isAudioOnly = false,
                                isAv1 = false,
                                isProgressive = !isM3u8,
                                mimeType = if (isM3u8) "application/x-mpegURL" else "video/mp4"
                            )
                        )
                    )
                }
                return null
            }

            // Find best audio stream for muxing with video-only streams (preferring M4A/AAC)
            val audioFormats = rawFormats.filter { f ->
                val vcodec = f.vcodec?.lowercase() ?: "none"
                val acodec = f.acodec?.lowercase() ?: "none"
                (vcodec == "none" || vcodec.isBlank()) && acodec != "none" && acodec.isNotBlank() && !f.url.isNullOrBlank()
            }

            val bestAudioFormat = audioFormats.maxByOrNull { f ->
                val acodec = f.acodec?.lowercase() ?: ""
                val isAac = acodec.contains("aac") || acodec.contains("mp4a") || (f.ext?.contains("m4a") == true)
                val bitrate = f.abr
                (if (isAac) 10000 else 0) + bitrate.toInt()
            }
            val bestAudioUrl = bestAudioFormat?.url

            val qualityList = mutableListOf<VideoQualityOption>()

            for (f in rawFormats) {
                val streamUrl = f.url
                if (streamUrl.isNullOrBlank() || !streamUrl.startsWith("http")) continue

                val vcodec = f.vcodec?.lowercase() ?: "none"
                val acodec = f.acodec?.lowercase() ?: "none"
                val isAudioOnly = (vcodec == "none" || vcodec.isBlank()) && acodec != "none" && acodec.isNotBlank()
                val isVideoOnly = (acodec == "none" || acodec.isBlank()) && vcodec != "none" && vcodec.isNotBlank()
                val isProgressive = !isAudioOnly && !isVideoOnly

                val height = f.height
                val width = f.width
                val ext = f.ext?.lowercase() ?: "mp4"
                val isM3u8 = ext.contains("m3u8") || streamUrl.contains(".m3u8")
                val isAv1 = vcodec.contains("av01") || vcodec.contains("av1")
                val isWebm = ext.contains("webm") || vcodec.contains("vp9") || vcodec.contains("vp09")

                val formatName = if (isM3u8) "M3U8" else if (isWebm) "WEBM" else if (isAudioOnly) (if (ext.contains("opus")) "OPUS" else "M4A") else "MP4"
                val codecName = if (isAv1) "AV1" else if (isWebm) "VP9" else if (isAudioOnly) (if (acodec.contains("opus")) "Opus" else "AAC") else "H.264"

                val resolutionStr = if (width > 0 && height > 0) "${width}x${height}" else if (height > 0) "${height}p" else ""
                val sizeBytes = if (f.fileSize > 0) f.fileSize else f.fileSizeApproximate

                val label = if (isAudioOnly) {
                    val abrStr = if (f.abr > 0) " • ${f.abr.toInt()} kbps" else if (f.tbr > 0) " • ${f.tbr.toInt()} kbps" else ""
                    "Audio Track ($formatName$abrStr)"
                } else {
                    val resPrefix = if (height >= 2160) "4K Ultra HD" else if (height >= 1440) "1440p QHD" else if (height >= 1080) "1080p Full HD" else if (height >= 720) "720p HD" else if (height >= 480) "480p SD" else if (height >= 360) "360p" else if (height > 0) "${height}p" else "Video"
                    val tag = if (isProgressive) "Video + Audio" else "With Audio"
                    "$resPrefix ($formatName • $tag)"
                }

                qualityList.add(
                    VideoQualityOption(
                        label = label,
                        resolution = resolutionStr,
                        codec = codecName,
                        format = formatName,
                        url = streamUrl,
                        estimatedSizeBytes = sizeBytes,
                        isAudioOnly = isAudioOnly,
                        isAv1 = isAv1,
                        isProgressive = isProgressive,
                        audioUrl = if (isVideoOnly) bestAudioUrl else null,
                        mimeType = if (isM3u8) "application/x-mpegURL" else if (isWebm) (if (isAudioOnly) "audio/webm" else "video/webm") else (if (isAudioOnly) "audio/mp4" else "video/mp4")
                    )
                )
            }

            if (qualityList.isEmpty()) return null

            val sortedQualities = VideoQualityParser.sortQualities(qualityList)

            DetectedVideo(
                pageUrl = pageUrl,
                title = title,
                thumbnail = thumbnail,
                durationSeconds = durationSeconds,
                sourceDomain = host,
                qualities = sortedQualities
            )
        } catch (e: Exception) {
            Log.d(TAG, "yt-dlp extraction failed for $pageUrl: ${e.message}")
            null
        }
    }
}
