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
            updateIfStale(context.applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize YoutubeDL engine: ${e.message}", e)
        } finally {
            isInitializing = false
        }
    }

    /**
     * YouTube changes its player often and an old yt-dlp stops working, so the
     * bundled copy is refreshed from yt-dlp's stable releases at most once a day.
     */
    private fun updateIfStale(context: Context) {
        val prefs = context.getSharedPreferences("ytdlp", Context.MODE_PRIVATE)
        val last = prefs.getLong("last_update_check", 0L)
        if (System.currentTimeMillis() - last < 24 * 60 * 60 * 1000L) return
        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE)
            prefs.edit().putLong("last_update_check", System.currentTimeMillis()).apply()
            Log.i(TAG, "yt-dlp update check: $status")
        } catch (e: Exception) {
            Log.w(TAG, "yt-dlp update failed: ${e.message}")
        }
    }

    val isReady: Boolean get() = isInitialized

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, DetectedVideo>>()
    private val CACHE_TTL_MS = 3 * 60 * 1000L

    // youtubedl-android shares one stdout pipe: two concurrent runs kill each
    // other with "I/O operation on closed file". One extractor at a time.
    internal val ytDlpLock = java.util.concurrent.locks.ReentrantLock(true)

    /**
     * True when a yt-dlp pass can plausibly produce something.
     *
     * Feeding it search engines, site roots and listing pages costs a full
     * network round trip per navigation and yields either "Unsupported URL" or a
     * page-wide fan-out, which is what cooked the device. The network sniffer
     * still reports media found on those pages.
     */
    private fun shouldRunYtDlp(pageUrl: String): Boolean {
        val lower = pageUrl.lowercase()
        if (Regex("(^|\\.)(duckduckgo|google|bing|yahoo|ecosia|startpage)\\.").containsMatchIn(lower)) return false
        if (Regex("[?&](q|query|search_query|s|search|text)=([^&]*)").containsMatchIn(lower)) return false

        val path = try {
            URI(pageUrl).path ?: ""
        } catch (e: Exception) {
            ""
        }
        if (path.isBlank() || path == "/") return false
        return !Regex(
            "^/(search|results|find|browse|categories|category|tags|channels|channels?|members|playlists|playlist)/",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(path)
    }

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

        // Tier 1: YouTube Specialized In-Page / InnerTube Extractor (Instantaneous & Unciphered)
        val isYouTube = pageUrl.contains("youtube.com") || pageUrl.contains("youtu.be")
        if (isYouTube) {
            val cookies = BrowserDownloadManager.cookieHeaderFor(pageUrl)
            val ytResult = YouTubeStreamExtractor.extract(pageUrl, cookies)
            if (ytResult != null && ytResult.qualities.isNotEmpty()) {
                Log.i(TAG, "Successfully extracted via YouTubeStreamExtractor: ${ytResult.title}")
                // Without cookies the PO-token-free TVHTML5 tier never ran, so the
                // result is worth retrying once the cookie prefetch has landed.
                if (cookies != null) cache[pageUrl] = Pair(System.currentTimeMillis(), ytResult)
                return@withContext ytResult
            }
        }

        // Tier 2: yt-dlp Engine (youtubedl-android) supporting 1,800+ websites
        if (isInitialized && shouldRunYtDlp(pageUrl)) {
            val ytDlpResult = extractViaYtDlp(pageUrl)
            if (ytDlpResult != null && ytDlpResult.qualities.isNotEmpty()) {
                Log.i(TAG, "Successfully extracted via yt-dlp: ${ytDlpResult.title} (${ytDlpResult.qualities.size} streams)")
                cache[pageUrl] = Pair(System.currentTimeMillis(), ytDlpResult)
                return@withContext ytDlpResult
            }
        }

        return@withContext null
    }

    private fun extractViaYtDlp(pageUrl: String): DetectedVideo? {
        ytDlpLock.lock()
        try {
        return try {
            val request = YoutubeDLRequest(pageUrl).apply {
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("--socket-timeout", "10")
                com.browser.adfree.vpn.NetworkRoute.ytDlpProxy?.let { addOption("--proxy", it) }
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
                    val cleanDirectUrl = directUrl.replace(Regex("&range=[^&]+"), "")
                    val isM3u8 = cleanDirectUrl.contains(".m3u8")
                    val isWebm = cleanDirectUrl.contains(".webm")
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
                                url = cleanDirectUrl,
                                estimatedSizeBytes = 0L,
                                isAudioOnly = false,
                                isAv1 = false,
                                isProgressive = !isM3u8,
                                mimeType = if (isM3u8) "application/x-mpegURL" else "video/mp4",
                                headers = videoInfo.httpHeaders ?: emptyMap()
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
            val bestAudioUrl = bestAudioFormat?.url?.replace(Regex("&range=[^&]+"), "")

            val qualityList = mutableListOf<VideoQualityOption>()

            for (f in rawFormats) {
                val streamUrl = f.url
                if (streamUrl.isNullOrBlank() || !streamUrl.startsWith("http")) continue
                val cleanStreamUrl = streamUrl.replace(Regex("&range=[^&]+"), "")

                val rawVcodec = f.vcodec?.lowercase()?.trim()
                val rawAcodec = f.acodec?.lowercase()?.trim()
                val vcodec = rawVcodec ?: "none"
                val acodec = rawAcodec ?: "none"
                val isAudioOnly = (vcodec == "none" || vcodec.isBlank()) && acodec != "none" && acodec.isNotBlank()
                val isVideoOnly = (acodec == "none" || acodec.isBlank()) && vcodec != "none" && vcodec.isNotBlank()
                // Unknown codecs mean we cannot promise the stream carries audio.
                // Calling it progressive produces silent files on DASH/hls sources.
                val codecsUnknown = !isAudioOnly && !isVideoOnly &&
                    (rawVcodec == null || rawVcodec == "unknown" || rawVcodec.isBlank()) &&
                    (rawAcodec == null || rawAcodec == "unknown" || rawAcodec.isBlank())
                val isProgressive = !isAudioOnly && !isVideoOnly && !codecsUnknown
                // Paired audio track when this entry is video-only (or unknown and
                // the source does publish a separate audio format).
                val pairAudioUrl = if (isVideoOnly || codecsUnknown) bestAudioUrl else null
                val hasAudio = isAudioOnly || isProgressive || pairAudioUrl != null

                val height = f.height
                val width = f.width
                val ext = f.ext?.lowercase() ?: "mp4"
                val isM3u8 = ext.contains("m3u8") || cleanStreamUrl.contains(".m3u8")
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
                    val tag = when {
                        isProgressive -> "Video + Audio"
                        hasAudio -> "With Audio"
                        codecsUnknown -> "Audio unknown"
                        else -> "Video only"
                    }
                    "$resPrefix ($formatName • $tag)"
                }

                val formatHeaders = f.httpHeaders ?: (videoInfo.httpHeaders ?: emptyMap())

                qualityList.add(
                    VideoQualityOption(
                        label = label,
                        resolution = resolutionStr,
                        codec = codecName,
                        format = formatName,
                        url = cleanStreamUrl,
                        estimatedSizeBytes = sizeBytes,
                        isAudioOnly = isAudioOnly,
                        isAv1 = isAv1,
                        isProgressive = isProgressive,
                        audioUrl = pairAudioUrl,
                        mimeType = if (isM3u8) "application/x-mpegURL" else if (isWebm) (if (isAudioOnly) "audio/webm" else "video/webm") else (if (isAudioOnly) "audio/mp4" else "video/mp4"),
                        headers = formatHeaders
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
        } finally {
            ytDlpLock.unlock()
        }
    }
}
