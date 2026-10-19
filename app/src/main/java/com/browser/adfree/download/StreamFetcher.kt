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

import android.net.Uri
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream

/**
 * Fetches one media stream to a file: ranged HTTP with retries and User-Agent
 * fallbacks for ordinary hosts, and yt-dlp for YouTube's adaptive streams.
 */
internal class StreamFetcher(private val okHttpClient: OkHttpClient) {

    private companion object {
        const val TAG = "BrowserDownloadManager"
        /** First Range window for hosts that refuse an unbounded request. */
        const val MIN_RANGE_WINDOW_BYTES = 256L * 1024
        const val MAX_RANGE_WINDOW_BYTES = 16L * 1024 * 1024
        const val MAX_WINDOW_ATTEMPTS = 3
        val RETRYABLE_HTTP_CODES = setOf(403, 408, 425, 429, 500, 502, 503, 504)
        val RANGE_PARAM = Regex("&range=[^&]+")
    }

    private fun hostOf(url: String): String? = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull()


    /** Outcome of one stream transfer, carrying the reason so the UI can explain failures. */
    data class StreamOutcome(
        val success: Boolean,
        val httpCode: Int = 0,
        val reason: String? = null
    )

    /**
     * Direct transfer first. When YouTube cuts a stream off (HTTP 403 after
     * the first megabyte, which happens to Android-client URLs without a
     * PO token), the same format is fetched again through yt-dlp.
     */
    fun fetch(
        pageUrl: String?,
        url: String,
        targetFile: File,
        userAgent: String?,
        referer: String?,
        extraHeaders: Map<String, String>? = null,
        cookieHeader: String? = null,
        height: Int? = null,
        isActive: () -> Boolean,
        onProgress: (current: Long, total: Long) -> Unit
    ): StreamOutcome {
        val itag = YtDlpDownloader.itagOf(url)
        val viaYtDlp = url.contains("googlevideo.com") && itag != null && pageUrl != null &&
            com.browser.adfree.tabs.TabManager.isYouTube(pageUrl) && UniversalVideoExtractor.isReady
        // YouTube cuts every direct fetch of an adaptive stream off part way
        // (256 KB to ~100 MB) and yt-dlp then has to start again from zero, so
        // YouTube streams go straight to yt-dlp.
        val direct = if (viaYtDlp) StreamOutcome(false, 0, "YouTube stream")
            else downloadStream(url, targetFile, userAgent, referer, extraHeaders, cookieHeader, isActive, onProgress)
        if (direct.success || !isActive()) return direct
        if (!url.contains("googlevideo.com") || itag == null || pageUrl == null ||
            !com.browser.adfree.tabs.TabManager.isYouTube(pageUrl)
        ) return direct

        Log.i(TAG, "Fetching itag $itag with yt-dlp (${direct.reason})")
        val total = Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val selector = YtDlpDownloader.selectorFor(url, itag, height)
        val error = YtDlpDownloader.download(pageUrl, selector, targetFile, isActive) { percent ->
            if (total > 0) onProgress(total * percent / 100, total)
        }
        return if (error == null) {
            StreamOutcome(success = true)
        } else {
            StreamOutcome(success = false, httpCode = direct.httpCode, reason = "YouTube cut the stream off and yt-dlp could not finish it: $error")
        }
    }

    private fun downloadStream(
        url: String,
        targetFile: File,
        userAgent: String?,
        referer: String?,
        extraHeaders: Map<String, String>? = null,
        cookieHeader: String? = null,
        isActive: () -> Boolean,
        onProgress: (current: Long, total: Long) -> Unit
    ): StreamOutcome {
        var existingBytes = if (targetFile.exists()) targetFile.length() else 0L
        val cleanUrl = url.replace(RANGE_PARAM, "")
        val isGooglevideo = cleanUrl.contains("googlevideo.com")

        val browserUa = com.browser.adfree.tabs.TabManager.MODERN_MOBILE_UA

        // googlevideo validates that the request User-Agent matches the client that
        // signed the URL, and falls back to 403 when it does not. The extractor
        // already stored that exact UA on the format's headers, so it wins over
        // every guess below.
        val headerUa = extraHeaders?.entries
            ?.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) && it.value.isNotBlank() }
            ?.value
        val clientMarkerUa = YouTubeStreamExtractor.userAgentForClientMarker(
            Regex("(?:^|[?&])c=([^&]+)").find(cleanUrl)?.groupValues?.get(1)
        )

        val defaultUa = when {
            !headerUa.isNullOrBlank() -> headerUa
            !userAgent.isNullOrBlank() -> userAgent
            clientMarkerUa != null -> clientMarkerUa
            isGooglevideo -> YouTubeStreamExtractor.ANDROID_USER_AGENT
            else -> browserUa
        }

        val headerCookie = extraHeaders?.entries
            ?.firstOrNull { it.key.equals("Cookie", ignoreCase = true) && it.value.isNotBlank() }
            ?.value
        val effectiveCookie = cookieHeader?.takeIf { it.isNotBlank() } ?: headerCookie

        fun buildRequest(ua: String, includeReferer: Boolean, rangeHeader: String?): Request {
            val builder = Request.Builder().url(cleanUrl)
            builder.header("User-Agent", ua)

            if (includeReferer && !isGooglevideo && !referer.isNullOrBlank()) {
                builder.header("Referer", referer)
            }

            extraHeaders?.forEach { (k, v) ->
                if (!k.equals("User-Agent", true) && !k.equals("Range", true) && !k.equals("Cookie", true) &&
                    !(isGooglevideo && k.equals("Referer", true)) && v.isNotBlank()
                ) {
                    builder.header(k, v)
                }
            }

            if (!effectiveCookie.isNullOrBlank()) builder.header("Cookie", effectiveCookie)

            if (rangeHeader != null) {
                // Content-Range is only meaningful over an uncompressed body.
                builder.header("Range", rangeHeader)
                builder.header("Accept-Encoding", "identity")
            }
            return builder.build()
        }

        // Candidate attempts, in order. Referer is dropped for googlevideo because
        // that host rejects it, and a plain browser UA is tried as a fallback
        // because many CDNs 403 on an unexpected Referer.
        val candidates: List<Triple<String, Boolean, Boolean>> = buildList {
            add(Triple(defaultUa, !isGooglevideo, false))
            if (isGooglevideo) {
                add(Triple(YouTubeStreamExtractor.IOS_USER_AGENT, false, false))
                add(Triple(YouTubeStreamExtractor.WEB_USER_AGENT, false, false))
                add(Triple(browserUa, false, false))
            } else {
                add(Triple(browserUa, false, false))
            }
        }.distinctBy { it.first }

        var lastCode = 0
        var bytesOnDisk = existingBytes
        var remoteTotal = -1L

        for ((ua, includeReferer, _) in candidates) {
            var start = bytesOnDisk
            var window = if (isGooglevideo) MIN_RANGE_WINDOW_BYTES else MAX_RANGE_WINDOW_BYTES
            // googlevideo 403s on a body-length request and on an open-ended
            // `bytes=N-` resume; ordinary hosts keep the plain one-shot request.
            var useRange = isGooglevideo || start > 0
            var failures = 0

            while (isActive()) {
                val rangeHeader = if (useRange) {
                    val end = if (remoteTotal > 0) minOf(start + window - 1, remoteTotal - 1)
                    else start + window - 1
                    "bytes=$start-$end"
                } else null

                var response: Response? = null
                try {
                    response = okHttpClient.newCall(buildRequest(ua, includeReferer, rangeHeader)).execute()
                    val code = response.code
                    lastCode = code

                    if (code == 416 && start > 0) {
                        // Partial file no longer matches the remote length: restart clean.
                        response.close()
                        response = null
                        targetFile.delete()
                        start = 0L
                        bytesOnDisk = 0L
                        remoteTotal = -1L
                        useRange = isGooglevideo
                        window = if (isGooglevideo) MIN_RANGE_WINDOW_BYTES else MAX_RANGE_WINDOW_BYTES
                        continue
                    }

                    if (code == 200 || code == 206) {
                        val contentType = response.header("Content-Type")?.substringBefore(";")?.trim()?.lowercase().orEmpty()
                        if (code == 200 && start == 0L && isNonMediaType(contentType, targetFile.name)) {
                            response.close()
                            response = null
                            return StreamOutcome(false, lastCode, "Server sent ${contentType} instead of media")
                        }
                        val contentRange = response.header("Content-Range")
                        parseContentRangeTotal(contentRange)?.let { remoteTotal = it }

                        if (code == 200) {
                            // Body is the whole resource; ignore anything on disk.
                            start = 0L
                        }
                        val written = writeStreamToFile(response, targetFile, start, remoteTotal, isActive, onProgress)
                        response = null
                        if (written < 0L) {
                            return StreamOutcome(false, lastCode, "Transfer was interrupted")
                        }
                        start += written
                        bytesOnDisk = start

                        if (written == 0L) {
                            return StreamOutcome(false, lastCode, "Server returned an empty body")
                        }
                        if (code == 200 || (remoteTotal in 1..start)) {
                            return StreamOutcome(true, lastCode)
                        }
                        if (written < window && remoteTotal <= 0) {
                            return StreamOutcome(true, lastCode)
                        }
                        window = minOf(window * 2, MAX_RANGE_WINDOW_BYTES)
                        useRange = true
                        failures = 0
                        continue
                    }

                    response.close()
                    response = null

                    if (code in RETRYABLE_HTTP_CODES) {
                        // A 403 that only appears once the window crosses the
                        // host's allowed prefix means we are asking for too much,
                        // not that the link is dead: shrink and keep going.
                        if (code == 403 && useRange && window > MIN_RANGE_WINDOW_BYTES) {
                            window = maxOf(MIN_RANGE_WINDOW_BYTES, window / 2)
                            continue
                        }
                        failures++
                        if (failures <= MAX_WINDOW_ATTEMPTS) {
                            Thread.sleep(500L * failures)
                            continue
                        }
                    }
                    break
                } catch (e: Exception) {
                    Log.d(TAG, "Attempt with UA '${ua.take(28)}...' failed: ${e.message}")
                    runCatching { response?.close() }
                    lastCode = 0
                    failures++
                    if (failures <= MAX_WINDOW_ATTEMPTS) {
                        Thread.sleep(500L * failures)
                        continue
                    }
                    break
                }
            }
        }

        Log.e(TAG, "All attempts failed for ${hostOf(cleanUrl)} (last HTTP $lastCode, got $bytesOnDisk/$remoteTotal bytes)")
        val reason = when {
            lastCode == 403 && bytesOnDisk > 0 && (remoteTotal <= 0 || bytesOnDisk < remoteTotal) ->
                if (isGooglevideo) {
                    "Only ${bytesOnDisk / 1024} KB of the stream were retrievable before the server " +
                        "stopped answering (HTTP 403). YouTube caps adaptive formats at roughly the first " +
                        "minute unless a PO token is supplied - try the combined 360p stream instead."
                } else {
                    "Only ${bytesOnDisk / 1024} KB of the stream were retrievable before the server " +
                        "stopped answering (HTTP 403). The host is likely rate-limiting this IP."
                }
            lastCode == 403 || lastCode == 401 ->
                "Server refused the request (HTTP $lastCode). The link may have expired, the host may " +
                    "require a session cookie, or it is rejecting this network."
            lastCode == 404 -> "Server returned 404 for this stream"
            lastCode > 0 -> "Server returned HTTP $lastCode"
            else -> "Network error while transferring the stream"
        }
        return StreamOutcome(false, lastCode, reason)
    }

    private fun parseContentRangeTotal(contentRange: String?): Long? {
        if (contentRange.isNullOrBlank()) return null
        val total = contentRange.substringAfterLast('/', "").trim()
        if (total.isEmpty() || total == "*") return null
        return total.toLongOrNull()?.takeIf { it > 0 }
    }

    /**
     * True when the response body is clearly not media: error pages, JSON,
     * protobuf blobs. Those answer 200 and would otherwise be written to disk as a
     * "completed" video.
     */
    private fun isNonMediaType(contentType: String, fileName: String = ""): Boolean {
        if (contentType.isEmpty()) return false
        // Playlists are plain text on some hosts.
        if (fileName.endsWith(".m3u8", true) || fileName.endsWith(".m3u", true) || fileName.endsWith(".mpd", true)) {
            if (contentType.startsWith("text/")) return false
        }
        val mediaMarkers = listOf(
            "video/", "audio/", "mpegurl", "octet-stream", "mp2t", "mp4", "mp3",
            "webm", "matroska", "quicktime", "mp2a", "aac", "flac", "ogg", "mpd", "dash"
        )
        if (mediaMarkers.any { contentType.contains(it) }) return false
        val nonMedia = listOf("text/", "json", "protobuf", "xml", "html", "yaml", "csv")
        return nonMedia.any { contentType.contains(it) }
    }

    private fun writeStreamToFile(
        response: Response,
        targetFile: File,
        writeOffset: Long,
        totalBytesHint: Long,
        isActive: () -> Boolean,
        onProgress: (current: Long, total: Long) -> Unit
    ): Long {
        val body = response.body ?: return -1L
        val contentLength = body.contentLength()
        val totalBytes = when {
            totalBytesHint > 0 -> totalBytesHint
            contentLength > 0 -> writeOffset + contentLength
            else -> -1L
        }
        val append = writeOffset > 0

        return try {
            FileOutputStream(targetFile, append).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break
                        if (!isActive()) return -1L
                        out.write(buffer, 0, bytesRead)
                        written += bytesRead
                        onProgress(writeOffset + written, totalBytes)
                    }
                    out.flush()
                    written
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeStreamToFile error: ${e.message}")
            -1L
        } finally {
            response.close()
        }
    }
}
