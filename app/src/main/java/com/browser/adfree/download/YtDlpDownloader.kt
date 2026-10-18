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

import android.util.Log
import com.browser.adfree.vpn.NetworkRoute
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.UUID

/**
 * Downloads one YouTube format through yt-dlp.
 *
 * Stream URLs taken from YouTube's Android client stop answering (HTTP 403)
 * after about 1 MB unless the request carries a proof-of-origin token. yt-dlp
 * picks clients that are allowed to fetch the whole file and downloads it in
 * ranged chunks, so it is the fallback whenever a direct fetch is cut off.
 */
object YtDlpDownloader {

    private const val TAG = "YtDlpDownloader"

    fun itagOf(streamUrl: String): String? =
        Regex("[?&]itag=(\\d+)").find(streamUrl)?.groupValues?.get(1)

    /**
     * yt-dlp asks YouTube with different clients, which do not always list the
     * same format ids as the stream URL came from. Ask for that id first, then
     * the closest stream of the same kind: same container, same height.
     */
    fun selectorFor(streamUrl: String, itag: String, height: Int?): String {
        val mime = java.net.URLDecoder.decode(Regex("[?&]mime=([^&]+)").find(streamUrl)?.groupValues?.get(1).orEmpty(), "UTF-8")
        val isAudio = mime.startsWith("audio")
        val ext = when {
            mime.contains("webm") -> "webm"
            isAudio -> "m4a"
            else -> "mp4"
        }
        if (isAudio) return "$itag/ba[ext=$ext]/ba"
        val h = height ?: 0
        return if (h > 0) "$itag/bv*[height=$h][ext=$ext]/bv*[height=$h]/bv*[height<=$h][ext=$ext]/bv*[height<=$h]"
        else "$itag/bv*[ext=$ext]/bv*"
    }

    /**
     * Returns null on success, otherwise a short reason. [onProgress] gets 0..100.
     * Runs blocking; call from a background thread.
     */
    fun download(
        pageUrl: String,
        formatId: String,
        target: File,
        isActive: () -> Boolean,
        onProgress: (Int) -> Unit
    ): String? {
        if (!UniversalVideoExtractor.isReady) return "Downloader engine is still starting"
        val processId = UUID.randomUUID().toString()
        target.delete()
        val request = YoutubeDLRequest(pageUrl).apply {
            addOption("-f", formatId)
            addOption("-o", target.absolutePath)
            addOption("--no-playlist")
            addOption("--no-part")
            addOption("--no-mtime")
            addOption("--force-overwrites")
            addOption("--no-warnings")
            addOption("--http-chunk-size", "10M")
            addOption("--retries", "5")
            NetworkRoute.ytDlpProxy?.let { addOption("--proxy", it) }
        }

        // yt-dlp runs one process at a time in this library (shared stdout pipe).
        UniversalVideoExtractor.ytDlpLock.lock()
        try {
            if (!isActive()) return "Cancelled"
            var loggedStep = -1
            val started = System.currentTimeMillis()
            YoutubeDL.getInstance().execute(request, processId) { progress, _, line ->
                if (!isActive()) {
                    YoutubeDL.getInstance().destroyProcessById(processId)
                    return@execute
                }
                if (progress >= 0f) onProgress(progress.toInt().coerceIn(0, 100))
                // Phases and every 10% go to the log, with addresses removed.
                val step = (progress / 10).toInt()
                if (progress < 0f || step != loggedStep) {
                    loggedStep = step
                    Log.i(TAG, "[$formatId] ${(System.currentTimeMillis() - started) / 1000}s " +
                        line.replace(Regex("https?://\\S+"), "<url>").replace(Regex("/data/\\S+"), "<file>").take(160))
                }
            }
        } catch (e: YoutubeDL.CanceledException) {
            return "Cancelled"
        } catch (e: Exception) {
            Log.w(TAG, "yt-dlp download failed for format $formatId: ${e.message?.replace(Regex("https?://\\S+"), "<url>")?.takeLast(600)}")
            return e.message?.lineSequence()?.lastOrNull { it.isNotBlank() }?.take(160) ?: "yt-dlp failed"
        } finally {
            UniversalVideoExtractor.ytDlpLock.unlock()
        }
        return if (target.exists() && target.length() > 0) null else "yt-dlp produced no file"
    }
}
