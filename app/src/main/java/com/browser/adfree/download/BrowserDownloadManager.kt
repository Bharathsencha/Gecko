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

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.FileProvider
import com.browser.adfree.download.DownloadForegroundService
import com.browser.adfree.model.DownloadItem
import com.browser.adfree.model.DownloadStatus
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timer

class BrowserDownloadManager(private val context: Context) {
    val downloadsList = mutableStateListOf<DownloadItem>()
    private val systemDownloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    private val okHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    init {
        // Start periodic status poller for active system downloads
        timer(name = "download-poller", daemon = true, period = 1000L, initialDelay = 1000L) {
            updateDownloadProgress()
        }
    }

    private fun notifyForegroundService() {
        try {
            val activeItems = downloadsList.filter { it.status == DownloadStatus.DOWNLOADING }
            if (activeItems.isEmpty()) {
                DownloadForegroundService.stop(context)
                return
            }

            val primary = activeItems.first()
            val count = activeItems.size
            val title = if (count > 1) "${primary.fileName} (+${count - 1} more)" else primary.fileName

            DownloadForegroundService.updateProgress(
                context = context,
                title = title,
                progress = primary.progress,
                downloadedBytes = primary.currentBytes,
                totalBytes = primary.totalBytes,
                activeCount = count
            )
        } catch (e: Exception) {
            Log.w("DownloadManager", "Error updating foreground service: ${e.message}")
        }
    }

    fun startDownload(
        url: String,
        contentDisposition: String? = null,
        mimeType: String? = null,
        userAgent: String? = null,
        referer: String? = null,
        customFileName: String? = null,
        extraHeaders: Map<String, String>? = null,
        audioUrl: String? = null
    ): Long {
        if (url.startsWith("data:")) {
            val fileName = customFileName ?: extractFileName(url, contentDisposition, mimeType)
            return saveBase64Download(fileName, url, mimeType)
        }

        val effectiveMime = mimeType ?: "video/mp4"
        val isHls = url.contains(".m3u8") || effectiveMime.contains("mpegurl", ignoreCase = true) || effectiveMime.contains("application/x-mpegURL", ignoreCase = true)

        // 1. If HLS (.m3u8), route to native HLS segment download engine
        if (isHls) {
            val fileName = customFileName ?: extractFileName(url, contentDisposition, "video/mp4")
            return startHlsDownload(
                playlistUrl = url,
                fileName = fileName,
                mimeType = "video/mp4",
                userAgent = userAgent,
                referer = referer,
                extraHeaders = extraHeaders
            )
        }

        // 2. If audioUrl is provided and distinct from videoUrl, trigger the muxed stream download pipeline
        if (!audioUrl.isNullOrBlank() && audioUrl != url) {
            val fileName = customFileName ?: extractFileName(url, contentDisposition, effectiveMime)
            return startMuxedDownload(
                videoUrl = url,
                audioUrl = audioUrl,
                fileName = fileName,
                mimeType = effectiveMime,
                userAgent = userAgent,
                referer = referer,
                extraHeaders = extraHeaders
            )
        }

        // 3. For direct streaming media (YouTube googlevideo, v.redd.it, mp4, webm, m4a, or when headers/referer present)
        val isStreamingMedia = url.contains("googlevideo.com") || url.contains("v.redd.it") ||
                url.contains("fbcdn.net") || url.contains("twimg.com") || url.contains("tiktokcdn.com") ||
                url.contains(".mp4") || url.contains(".webm") || url.contains(".m4a") || url.contains(".mp3") ||
                effectiveMime.startsWith("video/") || effectiveMime.startsWith("audio/") ||
                !referer.isNullOrBlank() || !extraHeaders.isNullOrEmpty()

        if (isStreamingMedia) {
            val fileName = customFileName ?: extractFileName(url, contentDisposition, effectiveMime)
            return startInternalStreamDownload(
                url = url,
                fileName = fileName,
                mimeType = effectiveMime,
                userAgent = userAgent,
                referer = referer,
                extraHeaders = extraHeaders
            )
        }

        // 4. Standard file downloads fallback to system DownloadManager
        try {
            val fileName = customFileName ?: extractFileName(url, contentDisposition, mimeType)
            val uri = Uri.parse(url)
            val request = DownloadManager.Request(uri).apply {
                setTitle(fileName)
                setDescription("Downloading file...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                val effectiveUa = userAgent ?: com.browser.adfree.tabs.TabManager.MODERN_MOBILE_UA
                addRequestHeader("User-Agent", effectiveUa)
                if (!referer.isNullOrBlank()) {
                    addRequestHeader("Referer", referer)
                }
                extraHeaders?.forEach { (k, v) ->
                    if (k != "User-Agent" && k != "Referer" && v.isNotBlank()) {
                        addRequestHeader(k, v)
                    }
                }
                mimeType?.let { setMimeType(it) }
            }

            val downloadId = systemDownloadManager.enqueue(request)
            val destinationPath = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            ).absolutePath

            val item = DownloadItem(
                id = downloadId,
                fileName = fileName,
                url = url,
                mimeType = mimeType ?: "application/octet-stream",
                destinationPath = destinationPath,
                initialProgress = 0,
                initialStatus = DownloadStatus.DOWNLOADING,
                audioUrl = null,
                userAgent = userAgent,
                referer = referer
            )

            mainHandler.post {
                downloadsList.add(0, item)
                Toast.makeText(context, "Downloading $fileName", Toast.LENGTH_SHORT).show()
            }

            return downloadId
        } catch (e: Exception) {
            e.printStackTrace()
            // If system DownloadManager fails, fallback to internal stream downloader
            val fileName = customFileName ?: extractFileName(url, contentDisposition, mimeType)
            return startInternalStreamDownload(
                url = url,
                fileName = fileName,
                mimeType = mimeType ?: "application/octet-stream",
                userAgent = userAgent,
                referer = referer,
                extraHeaders = extraHeaders
            )
        }
    }

    /**
     * Downloads an HLS (.m3u8) master or media stream by fetching segments (.ts / .m4s)
     * and writing them seamlessly into a playable .mp4 file.
     */
    fun startHlsDownload(
        playlistUrl: String,
        fileName: String,
        mimeType: String = "video/mp4",
        userAgent: String? = null,
        referer: String? = null,
        extraHeaders: Map<String, String>? = null
    ): Long {
        val downloadId = System.currentTimeMillis()
        val cleanName = sanitizeFileName(if (fileName.endsWith(".mp4", ignoreCase = true)) fileName else "$fileName.mp4")

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        var targetFile = File(downloadsDir, cleanName)
        val nameWithoutExt = cleanName.substringBeforeLast(".")
        val ext = cleanName.substringAfterLast(".", "mp4")
        var counter = 1
        while (targetFile.exists()) {
            targetFile = File(downloadsDir, "$nameWithoutExt ($counter).$ext")
            counter++
        }

        val item = DownloadItem(
            id = downloadId,
            fileName = targetFile.name,
            url = playlistUrl,
            mimeType = "video/mp4",
            destinationPath = targetFile.absolutePath,
            initialProgress = 0,
            initialStatus = DownloadStatus.DOWNLOADING,
            audioUrl = null,
            userAgent = userAgent,
            referer = referer
        )

        mainHandler.post {
            downloadsList.add(0, item)
            notifyForegroundService()
            Toast.makeText(context, "Downloading ${targetFile.name} (HLS Stream)", Toast.LENGTH_SHORT).show()
        }

        launchHlsDownloadJob(item, extraHeaders)
        return downloadId
    }

    private fun launchHlsDownloadJob(item: DownloadItem, extraHeaders: Map<String, String>? = null) {
        val job = downloadScope.launch {
            try {
                notifyForegroundService()
                val ua = item.userAgent ?: com.browser.adfree.tabs.TabManager.MODERN_MOBILE_UA
                val reqBuilder = Request.Builder().url(item.url)
                    .header("User-Agent", ua)
                if (!item.referer.isNullOrBlank()) reqBuilder.header("Referer", item.referer)
                extraHeaders?.forEach { (k, v) -> if (k != "User-Agent" && k != "Referer" && v.isNotBlank()) reqBuilder.header(k, v) }

                val resp = okHttpClient.newCall(reqBuilder.build()).execute()
                if (!resp.isSuccessful) {
                    resp.close()
                    mainHandler.post {
                        item.status = DownloadStatus.FAILED
                        notifyForegroundService()
                        Toast.makeText(context, "HLS playlist request failed (${resp.code})", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                val playlistText = resp.body?.string() ?: ""
                resp.close()

                var mediaPlaylistUrl = item.url
                var mediaPlaylistText = playlistText

                // If it's a master playlist, extract the best variant
                if (VideoQualityParser.isMasterPlaylist(playlistText)) {
                    val variants = VideoQualityParser.parseM3u8MasterPlaylist(playlistText, item.url)
                    if (variants.isNotEmpty()) {
                        val bestVariant = variants.maxByOrNull {
                            val h = it.resolution.substringAfter("x").takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0
                            h
                        } ?: variants.first()
                        mediaPlaylistUrl = bestVariant.url

                        val variantReq = Request.Builder().url(mediaPlaylistUrl).header("User-Agent", ua).apply {
                            if (!item.referer.isNullOrBlank()) header("Referer", item.referer)
                            extraHeaders?.forEach { (k, v) -> if (k != "User-Agent" && k != "Referer" && v.isNotBlank()) header(k, v) }
                        }.build()

                        val variantResp = okHttpClient.newCall(variantReq).execute()
                        if (variantResp.isSuccessful) {
                            mediaPlaylistText = variantResp.body?.string() ?: ""
                        }
                        variantResp.close()
                    }
                }

                val segments = VideoQualityParser.parseM3u8Segments(mediaPlaylistText, mediaPlaylistUrl)
                if (segments.isEmpty()) {
                    mainHandler.post {
                        item.status = DownloadStatus.FAILED
                        notifyForegroundService()
                        Toast.makeText(context, "No stream segments found in HLS playlist", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val tempFile = File(context.cacheDir, "hls_${item.id}.tmp")
                if (tempFile.exists()) tempFile.delete()

                var downloadedSegments = 0
                val totalSegments = segments.size
                var success = true

                FileOutputStream(tempFile, true).use { out ->
                    for (segUrl in segments) {
                        if (!isActive || item.status != DownloadStatus.DOWNLOADING) {
                            success = false
                            break
                        }
                        val segReq = Request.Builder().url(segUrl).header("User-Agent", ua).apply {
                            if (!item.referer.isNullOrBlank()) header("Referer", item.referer)
                            extraHeaders?.forEach { (k, v) -> if (k != "User-Agent" && k != "Referer" && v.isNotBlank()) header(k, v) }
                        }.build()

                        var segDownloaded = false
                        for (retry in 0..2) {
                            try {
                                val segResp = okHttpClient.newCall(segReq).execute()
                                if (segResp.isSuccessful) {
                                    val segBody = segResp.body
                                    if (segBody != null) {
                                        segBody.byteStream().use { input ->
                                            input.copyTo(out)
                                        }
                                        segDownloaded = true
                                    }
                                }
                                segResp.close()
                                if (segDownloaded) break
                            } catch (e: Exception) {
                                delay(400)
                            }
                        }

                        downloadedSegments++
                        val pct = ((downloadedSegments * 100) / totalSegments).coerceIn(0, 99)
                        mainHandler.post {
                            item.progress = pct
                            item.currentBytes = tempFile.length()
                            notifyForegroundService()
                        }
                    }
                }

                if (success && tempFile.exists() && tempFile.length() > 0) {
                    val destFile = File(item.destinationPath)
                    tempFile.copyTo(destFile, overwrite = true)
                    try { tempFile.delete() } catch (e: Exception) {}

                    mainHandler.post {
                        item.progress = 100
                        item.status = DownloadStatus.COMPLETED
                        item.currentBytes = destFile.length()
                        item.totalBytes = destFile.length()
                        notifyForegroundService()
                        Toast.makeText(context, "Saved ${destFile.name} to Downloads", Toast.LENGTH_SHORT).show()
                    }
                    registerCompletedDownload(destFile, "video/mp4")
                } else if (item.status == DownloadStatus.DOWNLOADING) {
                    mainHandler.post {
                        item.status = DownloadStatus.FAILED
                        notifyForegroundService()
                    }
                }
            } catch (e: Exception) {
                Log.e("BrowserDownloadManager", "HLS download error", e)
                if (isActive && item.status == DownloadStatus.DOWNLOADING) {
                    mainHandler.post {
                        item.status = DownloadStatus.FAILED
                        notifyForegroundService()
                    }
                }
            } finally {
                activeJobs.remove(item.id)
                mainHandler.post { notifyForegroundService() }
            }
        }
        activeJobs[item.id] = job
    }

    /**
     * Downloads a direct media stream using OkHttp directly into Downloads folder with progress & resume support.
     */
    fun startInternalStreamDownload(
        url: String,
        fileName: String,
        mimeType: String = "video/mp4",
        userAgent: String? = null,
        referer: String? = null,
        extraHeaders: Map<String, String>? = null
    ): Long {
        val downloadId = System.currentTimeMillis()
        val cleanName = sanitizeFileName(fileName)

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        var targetFile = File(downloadsDir, cleanName)
        val nameWithoutExt = cleanName.substringBeforeLast(".")
        val ext = cleanName.substringAfterLast(".", "mp4")
        var counter = 1
        while (targetFile.exists()) {
            targetFile = File(downloadsDir, "$nameWithoutExt ($counter).$ext")
            counter++
        }

        val item = DownloadItem(
            id = downloadId,
            fileName = targetFile.name,
            url = url,
            mimeType = mimeType,
            destinationPath = targetFile.absolutePath,
            initialProgress = 0,
            initialStatus = DownloadStatus.DOWNLOADING,
            audioUrl = null,
            userAgent = userAgent,
            referer = referer
        )

        mainHandler.post {
            downloadsList.add(0, item)
            notifyForegroundService()
            Toast.makeText(context, "Downloading ${targetFile.name}", Toast.LENGTH_SHORT).show()
        }

        launchInternalStreamDownloadJob(item, extraHeaders)
        return downloadId
    }

    private fun launchInternalStreamDownloadJob(item: DownloadItem, extraHeaders: Map<String, String>? = null) {
        val job = downloadScope.launch {
            try {
                notifyForegroundService()
                val tempFile = File(context.cacheDir, "stream_${item.id}.tmp")
                val streamOk = downloadStream(
                    url = item.url,
                    targetFile = tempFile,
                    userAgent = item.userAgent,
                    referer = item.referer,
                    isActive = { isActive && item.status == DownloadStatus.DOWNLOADING },
                    onProgress = { cur, tot ->
                        mainHandler.post {
                            item.currentBytes = cur
                            if (tot > 0) {
                                item.totalBytes = tot
                                item.progress = ((cur * 100) / tot).toInt().coerceIn(0, 99)
                            }
                            notifyForegroundService()
                        }
                    }
                )

                if (!isActive || item.status != DownloadStatus.DOWNLOADING || !streamOk) {
                    if (item.status == DownloadStatus.DOWNLOADING) {
                        mainHandler.post {
                            item.status = DownloadStatus.FAILED
                            notifyForegroundService()
                        }
                    }
                    return@launch
                }

                val destFile = File(item.destinationPath)
                tempFile.copyTo(destFile, overwrite = true)
                try { tempFile.delete() } catch (e: Exception) {}

                if (destFile.exists() && destFile.length() > 0) {
                    mainHandler.post {
                        item.progress = 100
                        item.status = DownloadStatus.COMPLETED
                        item.currentBytes = destFile.length()
                        item.totalBytes = destFile.length()
                        notifyForegroundService()
                        Toast.makeText(context, "Saved ${destFile.name} to Downloads", Toast.LENGTH_SHORT).show()
                    }
                    registerCompletedDownload(destFile, item.mimeType)
                } else {
                    mainHandler.post {
                        item.status = DownloadStatus.FAILED
                        notifyForegroundService()
                    }
                }
            } catch (e: Exception) {
                Log.e("BrowserDownloadManager", "Internal stream download error", e)
                if (isActive && item.status == DownloadStatus.DOWNLOADING) {
                    mainHandler.post {
                        item.status = DownloadStatus.FAILED
                        notifyForegroundService()
                    }
                }
            } finally {
                activeJobs.remove(item.id)
                mainHandler.post { notifyForegroundService() }
            }
        }
        activeJobs[item.id] = job
    }

    /**
     * Downloads an adaptive video stream and audio stream concurrently, then losslessly remuxes them
     * into a single complete MP4 file with audio using VideoAudioMuxer.
     */
    fun startMuxedDownload(
        videoUrl: String,
        audioUrl: String,
        fileName: String,
        mimeType: String = "video/mp4",
        userAgent: String? = null,
        referer: String? = null,
        extraHeaders: Map<String, String>? = null
    ): Long {
        val downloadId = System.currentTimeMillis()
        val cleanName = sanitizeFileName(if (fileName.endsWith(".mp4", ignoreCase = true)) fileName else "$fileName.mp4")

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        var targetFile = File(downloadsDir, cleanName)
        val nameWithoutExt = cleanName.substringBeforeLast(".")
        val ext = cleanName.substringAfterLast(".", "mp4")
        var counter = 1
        while (targetFile.exists()) {
            targetFile = File(downloadsDir, "$nameWithoutExt ($counter).$ext")
            counter++
        }

        val item = DownloadItem(
            id = downloadId,
            fileName = targetFile.name,
            url = videoUrl,
            mimeType = mimeType,
            destinationPath = targetFile.absolutePath,
            initialProgress = 0,
            initialStatus = DownloadStatus.DOWNLOADING,
            audioUrl = audioUrl,
            userAgent = userAgent,
            referer = referer
        )

        mainHandler.post {
            downloadsList.add(0, item)
            notifyForegroundService()
            Toast.makeText(context, "Downloading ${targetFile.name} (with Audio)", Toast.LENGTH_SHORT).show()
        }

        launchMuxedDownloadJob(item)
        return downloadId
    }

    private fun launchMuxedDownloadJob(item: DownloadItem) {
        val job = downloadScope.launch {
            try {
                notifyForegroundService()
                val videoTmp = File(context.cacheDir, "vid_${item.id}.tmp")
                val audioTmp = item.audioUrl?.let { File(context.cacheDir, "aud_${item.id}.tmp") }

                var videoDone = false
                var audioDone = audioTmp == null

                // 1. Download Video Stream
                val videoOk = downloadStream(
                    url = item.url,
                    targetFile = videoTmp,
                    userAgent = item.userAgent,
                    referer = item.referer,
                    isActive = { isActive && item.status == DownloadStatus.DOWNLOADING },
                    onProgress = { cur, tot ->
                        mainHandler.post {
                            val audioLen = audioTmp?.length() ?: 0L
                            item.currentBytes = cur + audioLen
                            if (tot > 0) {
                                val estimatedTotal = tot + (if (audioTmp != null) (tot * 0.15f).toLong() else 0L)
                                item.totalBytes = estimatedTotal
                                item.progress = ((item.currentBytes * 85) / estimatedTotal).toInt().coerceIn(0, 85)
                            }
                            notifyForegroundService()
                        }
                    }
                )

                if (!isActive || item.status != DownloadStatus.DOWNLOADING || !videoOk) {
                    return@launch
                }
                videoDone = true

                // 2. Download Audio Stream (if separate)
                if (audioTmp != null && !item.audioUrl.isNullOrBlank()) {
                    val audioOk = downloadStream(
                        url = item.audioUrl,
                        targetFile = audioTmp,
                        userAgent = item.userAgent,
                        referer = item.referer,
                        isActive = { isActive && item.status == DownloadStatus.DOWNLOADING },
                        onProgress = { cur, tot ->
                            mainHandler.post {
                                item.currentBytes = videoTmp.length() + cur
                                if (tot > 0) {
                                    val pct = 85 + ((cur * 13) / tot).toInt()
                                    item.progress = pct.coerceIn(85, 98)
                                }
                                notifyForegroundService()
                            }
                        }
                    )

                    if (!isActive || item.status != DownloadStatus.DOWNLOADING || !audioOk) {
                        return@launch
                    }
                    audioDone = true
                }

                // 3. Lossless Muxing
                if (videoDone && audioDone) {
                    mainHandler.post {
                        item.progress = 99
                        notifyForegroundService()
                    }

                    val destFile = File(item.destinationPath)
                    val muxSuccess = if (audioTmp != null && audioTmp.exists()) {
                        VideoAudioMuxer.mux(videoTmp, audioTmp, destFile)
                    } else {
                        videoTmp.copyTo(destFile, overwrite = true)
                        true
                    }

                    try { videoTmp.delete() } catch (e: Exception) {}
                    try { audioTmp?.delete() } catch (e: Exception) {}

                    if (muxSuccess && destFile.exists() && destFile.length() > 0) {
                        mainHandler.post {
                            item.progress = 100
                            item.status = DownloadStatus.COMPLETED
                            item.currentBytes = destFile.length()
                            item.totalBytes = destFile.length()
                            notifyForegroundService()
                            Toast.makeText(context, "Saved ${destFile.name} to Downloads", Toast.LENGTH_SHORT).show()
                        }
                        registerCompletedDownload(destFile, item.mimeType)
                    } else {
                        mainHandler.post {
                            item.status = DownloadStatus.FAILED
                            notifyForegroundService()
                            Toast.makeText(context, "Failed to complete ${item.fileName}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BrowserDownloadManager", "Muxed download error", e)
                if (isActive && item.status == DownloadStatus.DOWNLOADING) {
                    mainHandler.post {
                        item.status = DownloadStatus.FAILED
                        notifyForegroundService()
                    }
                }
            } finally {
                activeJobs.remove(item.id)
                mainHandler.post { notifyForegroundService() }
            }
        }
        activeJobs[item.id] = job
    }

    private fun downloadStream(
        url: String,
        targetFile: File,
        userAgent: String?,
        referer: String?,
        isActive: () -> Boolean,
        onProgress: (current: Long, total: Long) -> Unit
    ): Boolean {
        var existingBytes = if (targetFile.exists()) targetFile.length() else 0L
        val reqBuilder = Request.Builder().url(url)
        val ua = userAgent ?: com.browser.adfree.tabs.TabManager.MODERN_MOBILE_UA
        reqBuilder.header("User-Agent", ua)
        if (!referer.isNullOrBlank()) {
            reqBuilder.header("Referer", referer)
        }
        if (existingBytes > 0) {
            reqBuilder.header("Range", "bytes=$existingBytes-")
        }

        try {
            val response = okHttpClient.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                response.close()
                // If 416 (Range Not Satisfiable), clear partial file and retry
                if (response.code == 416) {
                    targetFile.delete()
                    existingBytes = 0L
                    val retryResp = okHttpClient.newCall(
                        Request.Builder().url(url).header("User-Agent", ua).apply {
                            if (!referer.isNullOrBlank()) header("Referer", referer)
                        }.build()
                    ).execute()
                    if (!retryResp.isSuccessful) {
                        retryResp.close()
                        return false
                    }
                    return writeStreamToFile(retryResp, targetFile, 0L, isActive, onProgress)
                }
                return false
            }

            return writeStreamToFile(response, targetFile, existingBytes, isActive, onProgress)
        } catch (e: Exception) {
            Log.e("BrowserDownloadManager", "Stream download exception: ${e.message}")
            return false
        }
    }

    private fun writeStreamToFile(
        response: Response,
        targetFile: File,
        existingBytes: Long,
        isActive: () -> Boolean,
        onProgress: (current: Long, total: Long) -> Unit
    ): Boolean {
        val body = response.body ?: return false
        val contentLength = body.contentLength()
        val totalBytes = if (contentLength > 0) existingBytes + contentLength else -1L
        val append = existingBytes > 0 && response.code == 206
        val appendOffset = if (append) existingBytes else 0L

        try {
            FileOutputStream(targetFile, append).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var currentBytes = appendOffset

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (!isActive()) {
                            return false
                        }
                        out.write(buffer, 0, bytesRead)
                        currentBytes += bytesRead
                        onProgress(currentBytes, totalBytes)
                    }
                    out.flush()
                }
            }
            return true
        } catch (e: Exception) {
            return false
        } finally {
            response.close()
        }
    }

    fun pauseDownload(id: Long) {
        val item = downloadsList.firstOrNull { it.id == id } ?: return
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        item.status = DownloadStatus.PAUSED
        mainHandler.post {
            notifyForegroundService()
            Toast.makeText(context, "Download paused", Toast.LENGTH_SHORT).show()
        }
    }

    fun resumeDownload(id: Long) {
        val item = downloadsList.firstOrNull { it.id == id } ?: return
        if (item.status == DownloadStatus.PAUSED || item.status == DownloadStatus.FAILED) {
            item.status = DownloadStatus.DOWNLOADING
            val isHls = item.url.contains(".m3u8") || item.mimeType.contains("mpegurl", ignoreCase = true)
            if (isHls) {
                launchHlsDownloadJob(item)
            } else if (!item.audioUrl.isNullOrBlank() && item.audioUrl != item.url) {
                launchMuxedDownloadJob(item)
            } else if (item.url.startsWith("http")) {
                launchInternalStreamDownloadJob(item)
            }
            mainHandler.post {
                notifyForegroundService()
                Toast.makeText(context, "Resuming download...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun cancelDownload(id: Long) {
        val item = downloadsList.firstOrNull { it.id == id } ?: return
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        try {
            systemDownloadManager.remove(id)
        } catch (e: Exception) {}
        try { File(context.cacheDir, "vid_${id}.tmp").delete() } catch (e: Exception) {}
        try { File(context.cacheDir, "aud_${id}.tmp").delete() } catch (e: Exception) {}
        try { File(context.cacheDir, "hls_${id}.tmp").delete() } catch (e: Exception) {}
        try { File(context.cacheDir, "stream_${id}.tmp").delete() } catch (e: Exception) {}
        item.status = DownloadStatus.CANCELLED
        mainHandler.post {
            notifyForegroundService()
            Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteDownload(item: DownloadItem) {
        activeJobs[item.id]?.cancel()
        activeJobs.remove(item.id)
        try {
            systemDownloadManager.remove(item.id)
        } catch (e: Exception) {}
        try { File(item.destinationPath).delete() } catch (e: Exception) {}
        try { File(context.cacheDir, "vid_${item.id}.tmp").delete() } catch (e: Exception) {}
        try { File(context.cacheDir, "aud_${item.id}.tmp").delete() } catch (e: Exception) {}
        try { File(context.cacheDir, "hls_${item.id}.tmp").delete() } catch (e: Exception) {}
        try { File(context.cacheDir, "stream_${item.id}.tmp").delete() } catch (e: Exception) {}
        downloadsList.remove(item)
        mainHandler.post {
            notifyForegroundService()
            Toast.makeText(context, "Deleted ${item.fileName}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareDownload(context: Context, item: DownloadItem) {
        try {
            val file = File(item.destinationPath)
            if (!file.exists()) {
                Toast.makeText(context, "File does not exist on disk", Toast.LENGTH_SHORT).show()
                return
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share ${item.fileName}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveBase64Download(fileName: String, dataUrl: String, mimeType: String? = null): Long {
        try {
            val cleanName = sanitizeFileName(if (fileName.isNotBlank()) fileName else "download_${System.currentTimeMillis()}.bin")
            val base64Index = dataUrl.indexOf("base64,")
            val rawBytes = if (base64Index != -1) {
                val base64String = dataUrl.substring(base64Index + 7).trim()
                android.util.Base64.decode(base64String, android.util.Base64.DEFAULT)
            } else if (dataUrl.startsWith("data:")) {
                val commaIndex = dataUrl.indexOf(',')
                if (commaIndex != -1) {
                    java.net.URLDecoder.decode(dataUrl.substring(commaIndex + 1), "UTF-8").toByteArray()
                } else {
                    dataUrl.toByteArray()
                }
            } else {
                dataUrl.toByteArray()
            }

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            var targetFile = File(downloadsDir, cleanName)
            var counter = 1
            val nameWithoutExt = cleanName.substringBeforeLast(".")
            val ext = cleanName.substringAfterLast(".", "")
            while (targetFile.exists()) {
                val numberedName = if (ext.isNotEmpty()) "$nameWithoutExt ($counter).$ext" else "$nameWithoutExt ($counter)"
                targetFile = File(downloadsDir, numberedName)
                counter++
            }

            targetFile.outputStream().use { output ->
                output.write(rawBytes)
                output.flush()
            }

            val finalMime = mimeType ?: (MimeTypeMap.getSingleton().getMimeTypeFromExtension(targetFile.extension.lowercase()) ?: "application/octet-stream")
            val id = System.currentTimeMillis()

            registerCompletedDownload(targetFile, finalMime)

            val item = DownloadItem(
                id = id,
                fileName = targetFile.name,
                url = "data:$finalMime",
                mimeType = finalMime,
                destinationPath = targetFile.absolutePath,
                initialProgress = 100,
                initialStatus = DownloadStatus.COMPLETED
            ).apply {
                currentBytes = targetFile.length()
                totalBytes = targetFile.length()
            }

            mainHandler.post {
                downloadsList.add(0, item)
                Toast.makeText(context, "Saved ${targetFile.name} to Downloads", Toast.LENGTH_SHORT).show()
            }
            return id
        } catch (e: Exception) {
            Log.e("AdFreeBrowser", "Error saving base64 download", e)
            mainHandler.post {
                Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
            return -1L
        }
    }

    private fun registerCompletedDownload(file: File, mimeType: String) {
        try {
            systemDownloadManager.addCompletedDownload(
                file.name,
                "Downloaded media",
                true,
                mimeType,
                file.absolutePath,
                file.length(),
                true
            )
        } catch (e: Exception) {
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf(mimeType),
                    null
                )
            } catch (ex: Exception) {}
        }
    }

    private fun updateDownloadProgress() {
        val activeItems = downloadsList.filter { it.status == DownloadStatus.DOWNLOADING && it.audioUrl == null }
        if (activeItems.isEmpty()) return

        for (item in activeItems) {
            val query = DownloadManager.Query().setFilterById(item.id)
            var cursor: Cursor? = null
            try {
                cursor = systemDownloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalBytesIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                    if (statusIndex != -1) {
                        val status = cursor.getInt(statusIndex)
                        val downloaded = if (bytesDownloadedIndex != -1) cursor.getLong(bytesDownloadedIndex) else 0L
                        val total = if (totalBytesIndex != -1) cursor.getLong(totalBytesIndex) else -1L

                        mainHandler.post {
                            item.currentBytes = downloaded
                            item.totalBytes = total

                            when (status) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    item.progress = 100
                                    item.status = DownloadStatus.COMPLETED
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    item.status = DownloadStatus.FAILED
                                }
                                DownloadManager.STATUS_RUNNING -> {
                                    if (total > 0) {
                                        item.progress = ((downloaded * 100) / total).toInt()
                                    }
                                }
                            }
                            notifyForegroundService()
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore query errors
            } finally {
                cursor?.close()
            }
        }
    }

    fun openDownloadedFile(item: DownloadItem) {
        try {
            val file = File(item.destinationPath)
            if (!file.exists()) {
                Toast.makeText(context, "File does not exist on disk", Toast.LENGTH_SHORT).show()
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val extension = file.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: item.mimeType

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "Open file with...").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun removeDownload(item: DownloadItem) {
        deleteDownload(item)
    }

    fun clearCompleted() {
        downloadsList.removeAll { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }
    }

    private fun extractFileName(url: String, contentDisposition: String?, mimeType: String?): String {
        // Try content disposition
        if (!contentDisposition.isNullOrBlank()) {
            val regex = Regex("filename\\*?=(?:UTF-8'')?[\"']?([^\"';\\n]+)[\"']?", RegexOption.IGNORE_CASE)
            val match = regex.find(contentDisposition)
            if (match != null) {
                val extracted = match.groupValues[1].trim()
                if (extracted.isNotEmpty()) return sanitizeFileName(extracted)
            }
        }

        // Try extracting from URL path
        try {
            val uri = Uri.parse(url)
            val lastSegment = uri.lastPathSegment
            if (!lastSegment.isNullOrBlank() && lastSegment.contains(".")) {
                return sanitizeFileName(lastSegment)
            }
        } catch (e: Exception) {}

        // Fallback with timestamp and extension
        val ext = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "bin"
        return "download_${System.currentTimeMillis()}.$ext"
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }
}

