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
import com.browser.adfree.model.DownloadItem
import com.browser.adfree.model.DownloadPipeline
import com.browser.adfree.model.DownloadStatus
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.timer

class BrowserDownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "BrowserDownloadManager"
        private val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "aac", "ogg", "opus", "weba", "flac", "wav")


        /**
         * Lets extractors reach the cookie cache without holding a manager
         * reference: InnerTube's TVHTML5 client only answers with stream URLs
         * when the caller presents a real session cookie.
         */
        @Volatile
        private var cookieProvider: ((String) -> String?)? = null

        fun cookieHeaderFor(url: String): String? = cookieProvider?.invoke(url)
    }

    val downloadsList = mutableStateListOf<DownloadItem>()

    private val systemDownloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Separate scope for status polling so cancelAll() does not silently kill it. */
    private val pollerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    /**
     * Monotonic id source. Wall-clock ids collided when two downloads started in
     * the same millisecond, which made activeJobs.delete() and firstOrNull{id}
     * address the wrong download.
     */
    private val idSequence = AtomicLong(System.currentTimeMillis())

    /** Cookie headers harvested from the Gecko cookie store, keyed by host. */
    private val cookieCache = ConcurrentHashMap<String, String>()

    private val okHttpClient = OkHttpClient.Builder()
        .proxySelector(com.browser.adfree.vpn.NetworkRoute)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val fetcher = StreamFetcher(okHttpClient)

    init {
        cookieProvider = { cookiesFor(it) }
        // Periodic status poller for active *system* downloads only.
        timer(name = "download-poller", daemon = true, period = 1000L, initialDelay = 1000L) {
            updateSystemDownloadProgress()
        }
    }

    fun nextDownloadId(): Long = idSequence.incrementAndGet()

    /** Records the Cookie header for [url]'s host so downloads can authenticate. */
    fun cacheCookies(url: String, cookieHeader: String) {
        if (cookieHeader.isBlank()) return
        val host = hostOf(url)?.lowercase() ?: return
        cookieCache[host] = cookieHeader

        // Alias YouTube/Googlevideo domains so playback URLs get the same session cookies.
        when {
            host == "youtube.com" || host == "www.youtube.com" || host == "m.youtube.com" || host == "youtu.be" -> {
                cookieCache["youtube.com"] = cookieHeader
                cookieCache["googlevideo.com"] = cookieHeader
            }
            host.endsWith(".youtube.com") -> {
                cookieCache["youtube.com"] = cookieHeader
                cookieCache["googlevideo.com"] = cookieHeader
            }
            host.endsWith(".googlevideo.com") || host == "googlevideo.com" -> {
                cookieCache["googlevideo.com"] = cookieHeader
            }
        }
    }

    fun clearCookies() = cookieCache.clear()

    /** True when a Cookie header is already cached for [url]'s host or a parent domain. */
    fun hasCachedCookies(url: String): Boolean = cookiesFor(url) != null

    private fun hostOf(url: String): String? = try {
        Uri.parse(url).host?.lowercase()
    } catch (e: Exception) {
        null
    }

    private fun cookiesFor(url: String): String? {
        val host = hostOf(url) ?: return null
        cookieCache[host]?.let { return it }
        // Fall back to a parent domain, e.g. media.example.cdn -> example.cdn
        val parts = host.split(".")
        if (parts.size > 2) {
            val parent = parts.subList(parts.size - 2, parts.size).joinToString(".")
            cookieCache[parent]?.let { return it }
        }
        // Explicit aliases for YouTube playback domains.
        if (host.endsWith(".googlevideo.com") || host == "googlevideo.com" || host == "youtu.be") {
            cookieCache["googlevideo.com"]?.let { return it }
            cookieCache["youtube.com"]?.let { return it }
            cookieCache["www.youtube.com"]?.let { return it }
            cookieCache["m.youtube.com"]?.let { return it }
        }
        if (host.endsWith(".youtube.com") || host == "youtube.com" || host == "www.youtube.com" || host == "m.youtube.com") {
            cookieCache["googlevideo.com"]?.let { return it }
        }
        return null
    }

    // ---------------------------------------------------------------- UI helpers

    private fun toast(message: String) {
        mainHandler.post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    /** Marks the item failed with a reason that stays visible in the download list. */
    private fun failWith(item: DownloadItem, reason: String) {
        Log.w(TAG, "Download failed: ${item.fileName} -> $reason")
        mainHandler.post {
            item.status = DownloadStatus.FAILED
            item.errorMessage = reason
            notifyForegroundService()
        }
    }

    private fun completeWith(item: DownloadItem, file: File, mimeType: String, note: String? = null) {
        // A host that answers with an error page or protocol blob still counts as a
        // 200; without this a "successful" 0 KB file lands in the gallery.
        val isPlaylist = file.name.endsWith(".m3u8", true) || mimeType.contains("mpegurl", true)
        if (!isPlaylist && file.length() in 0..2047L) {
            failWith(item, "The server sent only ${file.length()} bytes, which is not a media file")
            runCatching { file.delete() }
            return
        }
        mainHandler.post {
            item.progress = 100
            item.status = DownloadStatus.COMPLETED
            item.currentBytes = file.length()
            item.totalBytes = file.length()
            item.destinationPath = file.absolutePath
            item.mimeType = mimeType
            item.errorMessage = note
            notifyForegroundService()
        }
        registerCompletedDownload(file, mimeType)
    }

    private fun notifyForegroundService() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { notifyForegroundService() }
            return
        }
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
            Log.w(TAG, "Error updating foreground service: ${e.message}")
        }
    }

    // ---------------------------------------------------------------- Routing

    private fun resolveTargetFile(fileName: String, container: VideoAudioMuxer.Container): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val finalName = DownloadNaming.withExtension(fileName, container)
        var targetFile = File(downloadsDir, finalName)
        val nameWithoutExt = finalName.substringBeforeLast(".")
        val ext = finalName.substringAfterLast(".", container.extension)
        var counter = 1
        while (targetFile.exists()) {
            targetFile = File(downloadsDir, "$nameWithoutExt ($counter).$ext")
            counter++
        }
        return targetFile
    }

    fun startDownload(
        url: String,
        contentDisposition: String? = null,
        mimeType: String? = null,
        userAgent: String? = null,
        referer: String? = null,
        customFileName: String? = null,
        extraHeaders: Map<String, String>? = null,
        audioUrl: String? = null,
        format: String? = null,
        pageUrl: String? = null
    ): Long {
        if (url.startsWith("data:")) {
            val fileName = customFileName ?: DownloadNaming.extractFileName(url, contentDisposition, mimeType)
            return saveBase64Download(fileName, url, mimeType)
        }

        val isHls = url.contains(".m3u8") || mimeType?.contains("mpegurl", ignoreCase = true) == true

        // 1. HLS (.m3u8) -> segment assembly
        if (isHls) {
            val fileName = customFileName ?: DownloadNaming.extractFileName(url, contentDisposition, "video/mp4")
            return startHlsDownload(
                playlistUrl = url,
                fileName = fileName,
                mimeType = "video/mp4",
                userAgent = userAgent,
                referer = referer,
                extraHeaders = extraHeaders
            )
        }

        // 2. Separate video + audio -> remux
        if (!audioUrl.isNullOrBlank() && audioUrl != url) {
            val fileName = customFileName ?: DownloadNaming.extractFileName(url, contentDisposition, mimeType)
            return startMuxedDownload(
                videoUrl = url,
                audioUrl = audioUrl,
                fileName = fileName,
                mimeType = mimeType,
                userAgent = userAgent,
                referer = referer,
                extraHeaders = extraHeaders,
                pageUrl = pageUrl
            )
        }

        // 3. Direct media stream. Routing uses an explicit type; defaulting to
        //    "video/mp4" here used to swallow every non-media file into the
        //    media pipeline and made the system DownloadManager unreachable.
        val effectiveMime = mimeType ?: "application/octet-stream"
        val isStreamingMedia = url.contains("googlevideo.com") || url.contains("v.redd.it") ||
                url.contains("fbcdn.net") || url.contains("twimg.com") || url.contains("tiktokcdn.com") ||
                DownloadNaming.MEDIA_EXTENSIONS.any { url.substringBefore("?").lowercase().endsWith(".$it") } ||
                effectiveMime.startsWith("video/") || effectiveMime.startsWith("audio/") ||
                effectiveMime.contains("mpegurl") ||
                !referer.isNullOrBlank() || !extraHeaders.isNullOrEmpty()

        if (isStreamingMedia) {
            val container = DownloadNaming.containerFor(effectiveMime, format, false)
            val fileName = customFileName ?: DownloadNaming.extractFileName(url, contentDisposition, effectiveMime)
            return startInternalStreamDownload(
                url = url,
                fileName = DownloadNaming.withExtension(fileName, container),
                mimeType = effectiveMime,
                userAgent = userAgent,
                referer = referer,
                extraHeaders = extraHeaders,
                pageUrl = pageUrl
            )
        }

        // 4. Everything else -> system DownloadManager, unless the VPN is on: the
        //    system service would fetch from the real address, so stay in-app.
        if (com.browser.adfree.vpn.NetworkRoute.socks != null) {
            val fileName = customFileName ?: DownloadNaming.extractFileName(url, contentDisposition, mimeType)
            return startInternalStreamDownload(
                url = url,
                fileName = fileName,
                mimeType = effectiveMime,
                userAgent = userAgent,
                referer = referer,
                extraHeaders = extraHeaders
            )
        }
        return enqueueSystemDownload(url, contentDisposition, mimeType, userAgent, referer, extraHeaders, customFileName)
    }

    private fun enqueueSystemDownload(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        userAgent: String?,
        referer: String?,
        extraHeaders: Map<String, String>?,
        customFileName: String?
    ): Long {
        return try {
            val fileName = customFileName ?: DownloadNaming.extractFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription("Downloading file...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                val effectiveUa = resolveUserAgent(url, userAgent, extraHeaders)
                addRequestHeader("User-Agent", effectiveUa)
                cookiesFor(url)?.let { addRequestHeader("Cookie", it) }
                if (!referer.isNullOrBlank()) addRequestHeader("Referer", referer)
                extraHeaders?.forEach { (k, v) ->
                    if (!k.equals("User-Agent", true) && !k.equals("Cookie", true) &&
                        !k.equals("Referer", true) && v.isNotBlank()
                    ) addRequestHeader(k, v)
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
                userAgent = userAgent,
                referer = referer,
                extraHeaders = extraHeaders.orEmpty(),
                isSystemDownload = true,
                pipeline = DownloadPipeline.SYSTEM
            )

            mainHandler.post {
                downloadsList.add(0, item)
                notifyForegroundService()
                toast("Downloading $fileName")
            }
            downloadId
        } catch (e: Exception) {
            Log.e(TAG, "System DownloadManager enqueue failed", e)
            val fileName = customFileName ?: DownloadNaming.extractFileName(url, contentDisposition, mimeType)
            startInternalStreamDownload(
                url = url,
                fileName = fileName,
                mimeType = mimeType ?: "application/octet-stream",
                userAgent = userAgent,
                referer = referer,
                extraHeaders = extraHeaders
            )
        }
    }

    // ---------------------------------------------------------------- HLS

    fun startHlsDownload(
        playlistUrl: String,
        fileName: String,
        mimeType: String = "video/mp4",
        userAgent: String? = null,
        referer: String? = null,
        extraHeaders: Map<String, String>? = null
    ): Long {
        val downloadId = nextDownloadId()
        val targetFile = resolveTargetFile(fileName, VideoAudioMuxer.Container.MP4)

        val item = DownloadItem(
            id = downloadId,
            fileName = targetFile.name,
            url = playlistUrl,
            mimeType = mimeType,
            destinationPath = targetFile.absolutePath,
            initialProgress = 0,
            initialStatus = DownloadStatus.DOWNLOADING,
            userAgent = userAgent,
            referer = referer,
            extraHeaders = extraHeaders.orEmpty(),
            pipeline = DownloadPipeline.HLS
        )

        mainHandler.post {
            downloadsList.add(0, item)
            notifyForegroundService()
            toast("Downloading ${targetFile.name} (HLS)")
        }

        launchHlsDownloadJob(item)
        return downloadId
    }

    /**
     * Picks the User-Agent to send with a download request.
     *
     * Order matters: a UA carried in [extraHeaders] was attached by the extractor at
     * the moment the URL was signed, so it is the only value guaranteed to match
     * googlevideo's signature check. Guessed UAs are a last resort.
     */
    private fun resolveUserAgent(
        url: String,
        userAgent: String?,
        extraHeaders: Map<String, String>?
    ): String {
        extraHeaders?.entries
            ?.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) && it.value.isNotBlank() }
            ?.value?.let { return it }
        if (!userAgent.isNullOrBlank()) return userAgent

        val clientMarker = Regex("(?:^|[?&])c=([^&]+)").find(url)?.groupValues?.get(1)
        YouTubeStreamExtractor.userAgentForClientMarker(clientMarker)?.let { return it }
        if (url.contains("googlevideo.com")) return YouTubeStreamExtractor.ANDROID_USER_AGENT
        return com.browser.adfree.tabs.TabManager.MODERN_MOBILE_UA
    }

    private fun buildHlsRequest(url: String, ua: String, referer: String?, extraHeaders: Map<String, String>?): Request {
        val effectiveUa = resolveUserAgent(url, ua, extraHeaders)
        return Request.Builder().url(url).header("User-Agent", effectiveUa).apply {
            if (!referer.isNullOrBlank()) header("Referer", referer)
            extraHeaders?.forEach { (k, v) ->
                if (!k.equals("User-Agent", true) && !k.equals("Cookie", true) &&
                    !k.equals("Referer", true) && v.isNotBlank()
                ) header(k, v)
            }
            cookiesFor(url)?.let { header("Cookie", it) }
        }.build()
    }

    private fun launchHlsDownloadJob(item: DownloadItem) {
        if (activeJobs.containsKey(item.id)) {
            Log.w(TAG, "HLS job already running for ${item.id}, ignoring duplicate launch")
            return
        }
        val job = downloadScope.launch {
            try {
                mainHandler.post { notifyForegroundService() }
                val ua = item.userAgent ?: com.browser.adfree.tabs.TabManager.MODERN_MOBILE_UA
                val extraHeaders = item.extraHeaders.takeIf { it.isNotEmpty() }

                okHttpClient.newCall(buildHlsRequest(item.url, ua, item.referer, extraHeaders)).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        failWith(item, "Playlist request failed with HTTP ${resp.code}")
                        return@launch
                    }
                    val playlistText = resp.body?.string().orEmpty()

                    var mediaPlaylistUrl = item.url
                    var mediaPlaylistText = playlistText
                    var audioRenditionUrl: String? = null

                    if (VideoQualityParser.isMasterPlaylist(playlistText)) {
                        val variants = VideoQualityParser.parseM3u8MasterPlaylist(playlistText, item.url)
                        val audioGroups = VideoQualityParser.parseM3u8AudioRenditions(playlistText, item.url)
                        val bestVariant = variants.maxByOrNull {
                            it.resolution.substringAfter("x").takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0
                        } ?: variants.firstOrNull()

                        if (bestVariant == null) {
                            failWith(item, "Master playlist contained no playable variants")
                            return@launch
                        }

                        mediaPlaylistUrl = bestVariant.url
                        audioRenditionUrl = audioGroups[bestVariant.audioGroupId]

                        val variantText = okHttpClient
                            .newCall(buildHlsRequest(mediaPlaylistUrl, ua, item.referer, extraHeaders))
                            .execute()
                            .use { r ->
                                if (!r.isSuccessful) {
                                    // Previously this fell through and parsed the MASTER text as a
                                    // media playlist, downloading playlist URLs as if they were segments.
                                    failWith(item, "Variant playlist request failed with HTTP ${r.code}")
                                    null
                                } else {
                                    r.body?.string().orEmpty()
                                }
                            } ?: return@launch

                        mediaPlaylistText = variantText
                    }

                    val segments = VideoQualityParser.parseM3u8Segments(mediaPlaylistText, mediaPlaylistUrl)
                    if (segments.isEmpty()) {
                        failWith(item, "No stream segments found in the playlist")
                        return@launch
                    }

                    val videoTmp = File(context.cacheDir, "hls_${item.id}.tmp")
                    if (videoTmp.exists()) videoTmp.delete()
                    var segmentsOk = downloadSegmentList(
                        segments, videoTmp, item, ua, extraHeaders,
                        progressFrom = 0, progressTo = if (audioRenditionUrl != null) 85 else 100
                    )

                    var audioTmp: File? = null
                    if (segmentsOk && audioRenditionUrl != null) {
                        val audioSegments = try {
                            val text = okHttpClient
                                .newCall(buildHlsRequest(audioRenditionUrl, ua, item.referer, extraHeaders))
                                .execute()
                                .use { if (it.isSuccessful) it.body?.string().orEmpty() else "" }
                            if (audioRenditionUrl.contains(".m3u8") && text.isNotBlank()) {
                                VideoQualityParser.parseM3u8Segments(text, audioRenditionUrl)
                            } else {
                                listOf(audioRenditionUrl)
                            }
                        } catch (e: Exception) {
                            emptyList()
                        }

                        if (audioSegments.isNotEmpty()) {
                            audioTmp = File(context.cacheDir, "hlsa_${item.id}.tmp")
                            if (audioTmp.exists()) audioTmp.delete()
                            segmentsOk = downloadSegmentList(
                                audioSegments, audioTmp, item, ua, extraHeaders,
                                progressFrom = 85, progressTo = 97
                            )
                        }
                    }

                    if (!segmentsOk || !videoTmp.exists() || videoTmp.length() == 0L) {
                        failWith(item, "One or more stream segments could not be downloaded")
                        return@launch
                    }

                    mainHandler.post {
                        item.progress = 98
                        notifyForegroundService()
                    }

                    val destFile = File(item.destinationPath)
                    deliverAssembledStream(item, videoTmp, audioTmp, destFile)
                    runCatching { videoTmp.delete() }
                    runCatching { audioTmp?.delete() }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.i(TAG, "HLS download cancelled: ${item.fileName}")
                mainHandler.post {
                    if (item.status == DownloadStatus.DOWNLOADING) {
                        item.status = DownloadStatus.CANCELLED
                        item.errorMessage = "Cancelled"
                        notifyForegroundService()
                    }
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "HLS download error", e)
                failWith(item, "HLS error: ${e.message}")
            } finally {
                activeJobs.remove(item.id)
                mainHandler.post { notifyForegroundService() }
            }
        }
        activeJobs[item.id] = job
    }

    private suspend fun downloadSegmentList(
        segments: List<String>,
        target: File,
        item: DownloadItem,
        ua: String,
        extraHeaders: Map<String, String>?,
        progressFrom: Int,
        progressTo: Int
    ): Boolean {
        val span = (progressTo - progressFrom).coerceAtLeast(1)
        var downloaded = 0
        var failed = false

        FileOutputStream(target, false).use { out ->
            for (segUrl in segments) {
                if (!currentCoroutineContext().isActive || item.status != DownloadStatus.DOWNLOADING) {
                    return false
                }
                var ok = false
                for (attempt in 0..2) {
                    try {
                        okHttpClient.newCall(buildHlsRequest(segUrl, ua, item.referer, extraHeaders)).execute().use { resp ->
                            if (resp.isSuccessful) {
                                resp.body?.byteStream()?.use { input -> input.copyTo(out) }
                                ok = true
                            }
                        }
                        if (ok) break
                    } catch (e: Exception) {
                        Log.d(TAG, "Segment attempt ${attempt + 1} failed: ${e.message}")
                        delay(400)
                    }
                }
                if (!ok) {
                    // Tolerate isolated failures but stop if the bulk is missing.
                    failed = true
                    if (downloaded > segments.size / 2) break
                }
                downloaded++
                val pct = progressFrom + ((downloaded * span) / segments.size)
                mainHandler.post {
                    item.progress = pct.coerceIn(0, 99)
                    item.currentBytes = target.length()
                    notifyForegroundService()
                }
            }
        }
        return !failed && downloaded >= (segments.size / 2)
    }

    /**
     * Takes a downloaded elementary stream (concatenated HLS segments) and puts
     * it into a real container. Raw MPEG-TS used to be renamed to ".mp4", which
     * produced a file most players refuse to open.
     */
    private suspend fun deliverAssembledStream(item: DownloadItem, videoTmp: File, audioTmp: File?, destFile: File) {
        val sniffed = sniffContainer(videoTmp)

        if (audioTmp != null && audioTmp.exists() && audioTmp.length() > 0L) {
            when (val result = withContext(Dispatchers.IO) { VideoAudioMuxer.mux(videoTmp, audioTmp, destFile) }) {
                is VideoAudioMuxer.Result.Success -> completeWith(item, result.file, result.container.mimeType)
                is VideoAudioMuxer.Result.Split -> {
                    // No single container fits both codecs. The streams were written
                    // out separately, so point at the video half and say where audio went.
                    val primary = result.videoFile ?: destFile
                    completeWith(item, primary, sniffed.mime, result.reason)
                }
                is VideoAudioMuxer.Result.Failure -> failWith(item, result.reason)
            }
            return
        }

        when (sniffed) {
            SniffedContainer.MP4, SniffedContainer.WEBM -> {
                // Already a valid container; only the name needed fixing.
                videoTmp.copyTo(File(item.destinationPath), overwrite = true)
                completeWith(item, File(item.destinationPath), sniffed.mime)
            }
            SniffedContainer.MPEG_TS -> {
                destFile.parentFile?.mkdirs()
                val result = withContext(Dispatchers.IO) {
                    VideoAudioMuxer.remux(videoTmp, File(item.destinationPath), VideoAudioMuxer.Container.MP4)
                }
                when (result) {
                    is VideoAudioMuxer.Result.Success -> completeWith(item, result.file, result.container.mimeType)
                    is VideoAudioMuxer.Result.Failure -> {
                        // Keep the raw transport stream rather than lying about the
                        // extension, and drop the placeholder .mp4 suffix.
                        val tsFile = File(destFile.parentFile, "${destFile.nameWithoutExtension}.ts")
                        videoTmp.copyTo(tsFile, overwrite = true)
                        completeWith(item, tsFile, "video/mp2t", "HLS segments were MPEG-TS and could not be remuxed; saved as .ts")
                    }
                    is VideoAudioMuxer.Result.Split -> completeWith(item, destFile, "video/mp2t", result.reason)
                }
            }
            SniffedContainer.UNKNOWN -> {
                failWith(item, "Downloaded stream was not a recognised media container")
            }
        }
    }

    private enum class SniffedContainer(val mime: String) {
        MP4("video/mp4"), WEBM("video/webm"), MPEG_TS("video/mp2t"), UNKNOWN("application/octet-stream")
    }

    private fun sniffContainer(file: File): SniffedContainer = try {
        file.inputStream().use { input ->
            val header = ByteArray(12)
            val read = input.read(header)
            when {
                read >= 12 && header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() &&
                    header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte() -> SniffedContainer.MP4
                read >= 4 && header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
                    header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte() -> SniffedContainer.WEBM
                read >= 1 && header[0] == 0x47.toByte() -> SniffedContainer.MPEG_TS
                else -> SniffedContainer.UNKNOWN
            }
        }
    } catch (e: Exception) {
        SniffedContainer.UNKNOWN
    }

    // ---------------------------------------------------------------- Direct stream

    fun startInternalStreamDownload(
        url: String,
        fileName: String,
        mimeType: String = "video/mp4",
        userAgent: String? = null,
        referer: String? = null,
        extraHeaders: Map<String, String>? = null,
        pageUrl: String? = null
    ): Long {
        val downloadId = nextDownloadId()
        val container = DownloadNaming.containerFor(mimeType, null, mimeType.startsWith("audio/"))
        val targetFile = resolveTargetFile(fileName, container)

        val item = DownloadItem(
            id = downloadId,
            fileName = targetFile.name,
            url = url,
            mimeType = mimeType,
            destinationPath = targetFile.absolutePath,
            initialProgress = 0,
            initialStatus = DownloadStatus.DOWNLOADING,
            userAgent = userAgent,
            referer = referer,
            extraHeaders = extraHeaders.orEmpty(),
            pipeline = DownloadPipeline.STREAM,
            sourcePageUrl = pageUrl
        )

        mainHandler.post {
            downloadsList.add(0, item)
            notifyForegroundService()
            toast("Downloading ${targetFile.name}")
        }

        launchInternalStreamDownloadJob(item)
        return downloadId
    }

    private fun launchInternalStreamDownloadJob(item: DownloadItem) {
        if (activeJobs.containsKey(item.id)) {
            Log.w(TAG, "Stream job already running for ${item.id}, ignoring duplicate launch")
            return
        }
        val job = downloadScope.launch {
            try {
                mainHandler.post { notifyForegroundService() }
                val tempFile = File(context.cacheDir, "stream_${item.id}.tmp")
                val outcome = fetcher.fetch(
                    pageUrl = item.sourcePageUrl,
                    url = item.url,
                    targetFile = tempFile,
                    userAgent = item.userAgent,
                    referer = item.referer,
                    extraHeaders = item.extraHeaders.takeIf { it.isNotEmpty() },
                    cookieHeader = cookiesFor(item.url),
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

                if (!isActive || item.status != DownloadStatus.DOWNLOADING) return@launch
                if (!outcome.success) {
                    failWith(item, outcome.reason ?: "Download failed")
                    return@launch
                }

                val destFile = File(item.destinationPath)
                if (isAudioOnlyItem(item)) {
                    val audio = VideoAudioMuxer.remuxAudio(tempFile, File(destFile.parentFile, destFile.nameWithoutExtension))
                    if (audio != null) {
                        runCatching { tempFile.delete() }
                        mainHandler.post {
                            item.fileName = audio.file.name
                            item.destinationPath = audio.file.absolutePath
                        }
                        completeWith(item, audio.file, audio.mimeType)
                        return@launch
                    }
                    // Unknown audio codec: keep the original bytes rather than fail.
                }
                tempFile.copyTo(destFile, overwrite = true)
                runCatching { tempFile.delete() }

                if (!destFile.exists() || destFile.length() == 0L) {
                    failWith(item, "Downloaded file was empty")
                    return@launch
                }
                completeWith(item, destFile, item.mimeType)
            } catch (e: Exception) {
                Log.e(TAG, "Internal stream download error", e)
                failWith(item, "Download error: ${e.message}")
            } finally {
                activeJobs.remove(item.id)
                mainHandler.post { notifyForegroundService() }
            }
        }
        activeJobs[item.id] = job
    }

    // ---------------------------------------------------------------- Muxed

    fun startMuxedDownload(
        videoUrl: String,
        audioUrl: String,
        fileName: String,
        mimeType: String? = null,
        userAgent: String? = null,
        referer: String? = null,
        extraHeaders: Map<String, String>? = null,
        pageUrl: String? = null
    ): Long {
        val downloadId = nextDownloadId()
        // Container is decided by the muxer from the real codecs; start from the
        // declared mime and let DownloadNaming.withExtension() avoid ".webm.mp4" style names.
        val declaredContainer = DownloadNaming.containerFor(mimeType, null, false)
        val targetFile = resolveTargetFile(fileName, declaredContainer)

        val item = DownloadItem(
            id = downloadId,
            fileName = targetFile.name,
            url = videoUrl,
            mimeType = mimeType ?: "video/mp4",
            destinationPath = targetFile.absolutePath,
            initialProgress = 0,
            initialStatus = DownloadStatus.DOWNLOADING,
            audioUrl = audioUrl,
            userAgent = userAgent,
            referer = referer,
            extraHeaders = extraHeaders.orEmpty(),
            pipeline = DownloadPipeline.MUXED,
            sourcePageUrl = pageUrl
        )

        mainHandler.post {
            downloadsList.add(0, item)
            notifyForegroundService()
            toast("Downloading ${targetFile.name}")
        }

        launchMuxedDownloadJob(item)
        return downloadId
    }

    private fun launchMuxedDownloadJob(item: DownloadItem) {
        if (activeJobs.containsKey(item.id)) {
            Log.w(TAG, "Muxed job already running for ${item.id}, ignoring duplicate launch")
            return
        }
        val job = downloadScope.launch {
            val videoTmp = File(context.cacheDir, "vid_${item.id}.tmp")
            val audioTmp = File(context.cacheDir, "aud_${item.id}.tmp")
            try {
                mainHandler.post { notifyForegroundService() }
                val extraHeaders = item.extraHeaders.takeIf { it.isNotEmpty() }
                val videoCookie = cookiesFor(item.url)
                val audioCookie = item.audioUrl?.let { cookiesFor(it) }

                // Video and audio are fetched concurrently, then combined.
                // Progress covers both streams together, so it never jumps backwards.
                val vBytes = LongArray(2)
                val aBytes = LongArray(2)
                var lastPost = 0L
                fun postProgress() {
                    val now = System.currentTimeMillis()
                    if (now - lastPost < 200) return
                    lastPost = now
                    val cur = vBytes[0] + aBytes[0]
                    val tot = vBytes[1] + aBytes[1]
                    mainHandler.post {
                        item.currentBytes = cur
                        if (vBytes[1] > 0 && aBytes[1] > 0) {
                            item.totalBytes = tot
                            item.progress = ((cur * 95) / tot).toInt().coerceIn(0, 95)
                        }
                        notifyForegroundService()
                    }
                }
                // One stream failing for good dooms the file; stop the other at once.
                val abort = java.util.concurrent.atomic.AtomicBoolean(false)
                val height = Regex("(\\d{3,4})p").find(item.fileName)?.groupValues?.get(1)?.toIntOrNull()
                val videoDeferred = async {
                    fetcher.fetch(
                        height = height,
                        pageUrl = item.sourcePageUrl,
                        url = item.url,
                        targetFile = videoTmp,
                        userAgent = item.userAgent,
                        referer = item.referer,
                        extraHeaders = extraHeaders,
                        cookieHeader = videoCookie,
                        isActive = { isActive && item.status == DownloadStatus.DOWNLOADING && !abort.get() },
                        onProgress = { cur, tot -> vBytes[0] = cur; vBytes[1] = tot; postProgress() }
                    ).also { if (!it.success) abort.set(true) }
                }
                val audioDeferred = async {
                    fetcher.fetch(
                        pageUrl = item.sourcePageUrl,
                        url = item.audioUrl.orEmpty(),
                        targetFile = audioTmp,
                        userAgent = item.userAgent,
                        referer = item.referer,
                        extraHeaders = extraHeaders,
                        cookieHeader = audioCookie,
                        isActive = { isActive && item.status == DownloadStatus.DOWNLOADING && !abort.get() },
                        onProgress = { cur, tot -> aBytes[0] = cur; aBytes[1] = tot; postProgress() }
                    ).also { if (!it.success) abort.set(true) }
                }

                val videoOutcome = videoDeferred.await()
                val audioOutcome = audioDeferred.await()
                // The stream that was stopped reports "interrupted"; show the real cause.
                if (!videoOutcome.success && !audioOutcome.success) {
                    failWith(item, (if (videoOutcome.reason == "Transfer was interrupted") audioOutcome.reason else videoOutcome.reason) ?: "Download failed")
                    return@launch
                }

                if (!isActive || item.status != DownloadStatus.DOWNLOADING) return@launch
                if (!videoOutcome.success) {
                    failWith(item, videoOutcome.reason ?: "Video stream failed")
                    return@launch
                }
                if (!audioOutcome.success) {
                    failWith(item, audioOutcome.reason ?: "Audio stream failed")
                    return@launch
                }

                mainHandler.post {
                    item.progress = 98
                    notifyForegroundService()
                }

                val destFile = File(item.destinationPath)
                when (val result = VideoAudioMuxer.mux(videoTmp, audioTmp, destFile)) {
                    is VideoAudioMuxer.Result.Success -> {
                        val finalFile = File(item.destinationPath)
                        if (finalFile.extension != result.container.extension) {
                            val renamed = File(finalFile.parentFile, "${finalFile.nameWithoutExtension}.${result.container.extension}")
                            finalFile.renameTo(renamed)
                            completeWith(item, renamed, result.container.mimeType)
                        } else {
                            completeWith(item, finalFile, result.container.mimeType)
                        }
                    }
                    is VideoAudioMuxer.Result.Split -> {
                        val primary = result.videoFile ?: destFile
                        completeWith(item, primary, result.videoFile?.let { sniffContainer(it).mime } ?: "video/mp4", result.reason)
                    }
                    is VideoAudioMuxer.Result.Failure -> failWith(item, result.reason)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // A cancelled coroutine reports itself when awaited; showing that
                // text to the user hides why the transfer really stopped.
                Log.i(TAG, "Muxed download cancelled: ${item.fileName}")
                mainHandler.post {
                    if (item.status == DownloadStatus.DOWNLOADING) {
                        item.status = DownloadStatus.CANCELLED
                        item.errorMessage = "Cancelled"
                        notifyForegroundService()
                    }
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Muxed download error", e)
                failWith(item, "Download error: ${e.message}")
            } finally {
                runCatching { videoTmp.delete() }
                runCatching { audioTmp.delete() }
                activeJobs.remove(item.id)
                mainHandler.post { notifyForegroundService() }
            }
        }
        activeJobs[item.id] = job
    }

    // ---------------------------------------------------------------- Controls

    fun pauseDownload(id: Long) {
        val item = downloadsList.firstOrNull { it.id == id } ?: return
        activeJobs.remove(id)?.cancel()
        item.status = DownloadStatus.PAUSED
        mainHandler.post {
            notifyForegroundService()
            toast("Download paused")
        }
    }

    fun resumeDownload(id: Long) {
        val item = downloadsList.firstOrNull { it.id == id } ?: return
        if (item.status != DownloadStatus.PAUSED && item.status != DownloadStatus.FAILED) return
        if (activeJobs.containsKey(id)) {
            Log.w(TAG, "Resume requested while a job is already active for $id")
            return
        }

        item.status = DownloadStatus.DOWNLOADING
        item.errorMessage = null

        when (item.pipeline) {
            DownloadPipeline.HLS -> launchHlsDownloadJob(item)
            DownloadPipeline.MUXED -> launchMuxedDownloadJob(item)
            DownloadPipeline.STREAM -> launchInternalStreamDownloadJob(item)
            DownloadPipeline.SYSTEM -> {
                // System downloads are re-enqueued by DownloadManager itself; here we
                // only re-attach the row to the existing system id.
                updateSystemDownloadProgress()
            }
        }
        mainHandler.post {
            notifyForegroundService()
            toast("Resuming download...")
        }
    }

    fun cancelDownload(id: Long) {
        val item = downloadsList.firstOrNull { it.id == id } ?: return
        activeJobs.remove(id)?.cancel()
        if (item.isSystemDownload) {
            runCatching { systemDownloadManager.remove(id) }
        }
        deleteTempFiles(id)
        item.status = DownloadStatus.CANCELLED
        mainHandler.post {
            notifyForegroundService()
            toast("Download cancelled")
        }
    }

    fun clearCompleted() {
        downloadsList.filter {
            it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED
        }.forEach { downloadsList.remove(it) }
    }

    fun deleteDownload(item: DownloadItem) {
        activeJobs.remove(item.id)?.cancel()
        if (item.isSystemDownload) {
            runCatching { systemDownloadManager.remove(item.id) }
        }
        runCatching { File(item.destinationPath).delete() }
        deleteTempFiles(item.id)
        mainHandler.post { downloadsList.remove(item) }
        mainHandler.post {
            notifyForegroundService()
            toast("Deleted ${item.fileName}")
        }
    }

    private fun deleteTempFiles(id: Long) {
        listOf("vid_$id.tmp", "aud_$id.tmp", "hls_$id.tmp", "hlsa_$id.tmp", "stream_$id.tmp")
            .forEach { runCatching { File(context.cacheDir, it).delete() } }
    }

    fun shareDownload(context: Context, item: DownloadItem) {
        try {
            val file = File(item.destinationPath)
            if (!file.exists()) {
                toast("File does not exist on disk")
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share ${item.fileName}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            toast("Failed to share: ${e.message}")
        }
    }

    // ---------------------------------------------------------------- Data URLs

    fun saveBase64Download(fileName: String, dataUrl: String, mimeType: String? = null): Long {
        return try {
            val cleanName = DownloadNaming.sanitizeFileName(if (fileName.isNotBlank()) fileName else "download_${System.currentTimeMillis()}.bin")
            val base64Index = dataUrl.indexOf("base64,")
            val rawBytes = if (base64Index != -1) {
                android.util.Base64.decode(dataUrl.substring(base64Index + 7).trim(), android.util.Base64.DEFAULT)
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

            targetFile.outputStream().use { it.write(rawBytes) }

            val finalMime = mimeType
                ?: (MimeTypeMap.getSingleton().getMimeTypeFromExtension(targetFile.extension.lowercase())
                    ?: "application/octet-stream")

            registerCompletedDownload(targetFile, finalMime)

            val item = DownloadItem(
                id = nextDownloadId(),
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
                toast("Saved ${targetFile.name} to Downloads")
            }
            item.id
        } catch (e: Exception) {
            Log.e(TAG, "Error saving base64 download", e)
            toast("Download failed: ${e.message}")
            -1L
        }
    }

    // ---------------------------------------------------------------- Engine responses

    /**
     * Saves a response Gecko already opened (a link the page cannot show, a
     * PDF's save button, or a context-menu download fetched through the
     * engine). The body is streamed as-is, so the site's cookies, the tab's
     * private or container state and the VPN route all carry over, the way a
     * normal browser download works.
     */
    fun saveWebResponse(response: org.mozilla.geckoview.WebResponse, fallbackName: String? = null): Long {
        val body = response.body
        if (body == null || response.statusCode >= 400) {
            toast(if (response.statusCode >= 400) "Download failed (HTTP ${response.statusCode})" else "Nothing to download")
            return -1L
        }
        val headers = response.headers
        fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
        val mimeType = header("Content-Type")?.substringBefore(";")?.trim()?.lowercase()?.ifBlank { null }
            ?: "application/octet-stream"
        var fileName = when {
            header("Content-Disposition")?.contains("filename", true) == true ->
                DownloadNaming.extractFileName(response.uri, header("Content-Disposition"), mimeType)
            !fallbackName.isNullOrBlank() -> DownloadNaming.sanitizeFileName(fallbackName)
            else -> DownloadNaming.extractFileName(response.uri, null, mimeType)
        }
        if (!fileName.contains('.')) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)?.let { fileName = "$fileName.$it" }
        }
        val total = header("Content-Length")?.toLongOrNull() ?: -1L
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        var target = File(downloadsDir, fileName)
        val stem = fileName.substringBeforeLast("."); val ext = fileName.substringAfterLast(".", "")
        var n = 1
        while (target.exists()) {
            target = File(downloadsDir, if (ext.isNotEmpty()) "$stem ($n).$ext" else "$stem ($n)")
            n++
        }

        val item = DownloadItem(
            id = nextDownloadId(),
            fileName = target.name,
            url = response.uri,
            mimeType = mimeType,
            destinationPath = target.absolutePath,
            initialStatus = DownloadStatus.DOWNLOADING
        ).apply { totalBytes = total }
        mainHandler.post {
            downloadsList.add(0, item)
            notifyForegroundService()
            toast("Downloading ${target.name}")
        }

        val job = downloadScope.launch {
            try {
                response.setReadTimeoutMillis(60_000)
                var written = 0L
                var lastPost = 0L
                FileOutputStream(target).use { out ->
                    body.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            if (!isActive || item.status != DownloadStatus.DOWNLOADING) throw kotlinx.coroutines.CancellationException()
                            out.write(buffer, 0, read)
                            written += read
                            val now = System.currentTimeMillis()
                            if (now - lastPost > 250) {
                                lastPost = now
                                val w = written
                                mainHandler.post {
                                    item.currentBytes = w
                                    if (total > 0) item.progress = ((w * 100) / total).toInt().coerceIn(0, 99)
                                    notifyForegroundService()
                                }
                            }
                        }
                    }
                }
                registerCompletedDownload(target, mimeType)
                mainHandler.post {
                    item.currentBytes = target.length()
                    item.totalBytes = target.length()
                    item.progress = 100
                    item.status = DownloadStatus.COMPLETED
                    notifyForegroundService()
                    toast("Saved ${target.name}")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                runCatching { target.delete() }
                mainHandler.post {
                    if (item.status == DownloadStatus.DOWNLOADING) item.status = DownloadStatus.CANCELLED
                    notifyForegroundService()
                }
            } catch (e: Exception) {
                runCatching { target.delete() }
                failWith(item, "Transfer stopped: ${e.message ?: "network error"}")
            } finally {
                activeJobs.remove(item.id)
            }
        }
        activeJobs[item.id] = job
        return item.id
    }

    private fun isAudioOnlyItem(item: DownloadItem): Boolean =
        item.mimeType.startsWith("audio/", ignoreCase = true) ||
            item.fileName.substringAfterLast(".", "").lowercase() in AUDIO_EXTENSIONS

    private fun registerCompletedDownload(file: File, rawMimeType: String) {
        if (!file.exists()) return
        // "audio/mp4; codecs=..." is not a type MediaStore understands, which is
        // how audio files ended up unlisted or filed as video.
        val mimeType = rawMimeType.substringBefore(";").trim().ifBlank { "application/octet-stream" }
        try {
            systemDownloadManager.addCompletedDownload(
                file.name, "Downloaded media", true, mimeType,
                file.absolutePath, file.length(), true
            )
        } catch (e: Exception) {
            Log.d(TAG, "addCompletedDownload unavailable, using MediaScanner: ${e.message}")
            try {
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
            } catch (ex: Exception) {
                Log.w(TAG, "MediaScanner failed: ${ex.message}")
            }
        }
    }

    private fun updateSystemDownloadProgress() {
        // The poller timer runs off the main thread, but downloadsList is Compose
        // snapshot state, so read it on the main thread first.
        pollerScope.launch {
            val activeItems = withContext(Dispatchers.Main) {
                downloadsList.filter { it.status == DownloadStatus.DOWNLOADING && it.isSystemDownload }
            }
            if (activeItems.isEmpty()) return@launch
            pollSystemDownloadQueries(activeItems)
        }
    }

    private suspend fun pollSystemDownloadQueries(activeItems: List<DownloadItem>) {
        for (item in activeItems) {
            var cursor: Cursor? = null
            try {
                cursor = withContext(Dispatchers.IO) {
                    systemDownloadManager.query(DownloadManager.Query().setFilterById(item.id))
                }
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val downloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    if (statusIndex == -1) continue

                    val status = cursor.getInt(statusIndex)
                    val downloaded = if (downloadedIndex != -1) cursor.getLong(downloadedIndex) else 0L
                    val total = if (totalIndex != -1) cursor.getLong(totalIndex) else -1L

                    withContext(Dispatchers.Main) {
                        item.currentBytes = downloaded
                        item.totalBytes = total
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                item.progress = 100
                                item.status = DownloadStatus.COMPLETED
                            }
                            DownloadManager.STATUS_FAILED -> {
                                item.status = DownloadStatus.FAILED
                                item.errorMessage = "System download manager failed this transfer"
                            }
                            DownloadManager.STATUS_RUNNING -> if (total > 0) {
                                item.progress = ((downloaded * 100) / total).toInt()
                            }
                        }
                        notifyForegroundService()
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "System download query failed: ${e.message}")
            } finally {
                cursor?.close()
            }
        }
    }

    fun openDownloadedFile(item: DownloadItem) {
        try {
            val file = File(item.destinationPath)
            if (!file.exists()) {
                toast("File does not exist on disk")
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: item.mimeType
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open file with...").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            toast("Cannot open file: ${e.message}")
        }
    }

    fun cancelAll() {
        activeJobs.values.toList().forEach { it.cancel() }
        activeJobs.clear()
        // Recreate the scope: a cancelled CoroutineScope can never launch again,
        // which would silently break every download started after this call.
        downloadScope.cancel()
        downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        mainHandler.post {
            downloadsList.filter { it.status == DownloadStatus.DOWNLOADING }.forEach {
                it.status = DownloadStatus.CANCELLED
            }
            notifyForegroundService()
        }
    }
}
