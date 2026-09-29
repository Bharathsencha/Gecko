/*
 * Copyright (C) 2024-2026 Gibbly Gobbly <gibblygobbly@proton.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.browser.adfree.download

import android.util.Log
import com.browser.adfree.model.DetectedVideo
import com.browser.adfree.model.VideoQualityOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object YouTubeStreamExtractor {

    private const val TAG = "YouTubeExtractor"
    private const val INNERTUBE_API_URL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"

    private val VIDEO_ID_PATTERN = Pattern.compile(
        "(?:youtu\\.be\\/|youtube\\.com\\/(?:embed\\/|v\\/|shorts\\/|watch\\?v=|watch\\?.+&v=))([\\w-]{11})"
    )

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private var isNewPipeInitialized = false

    private fun ensureNewPipeInit() {
        if (!isNewPipeInitialized) {
            synchronized(this) {
                if (!isNewPipeInitialized) {
                    try {
                        val downloader = OkHttpDownloader(httpClient)
                        NewPipe.init(downloader)
                        isNewPipeInitialized = true
                    } catch (e: Exception) {
                        Log.w(TAG, "NewPipe init failed: ${e.message}")
                    }
                }
            }
        }
    }

    fun extractVideoId(url: String): String? {
        val matcher = VIDEO_ID_PATTERN.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    suspend fun extract(pageUrl: String): DetectedVideo? = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(pageUrl) ?: return@withContext null
        return@withContext extractById(videoId, pageUrl)
    }

    suspend fun extractById(videoId: String, pageUrl: String): DetectedVideo? = withContext(Dispatchers.IO) {
        // Tier 1: InnerTube ANDROID_VR (Direct Playback Stream URLs - Fastest, No Cipher)
        var detected = fetchInnerTube(videoId, pageUrl, clientName = "ANDROID_VR", clientVersion = "1.43.32", sdkVersion = 30)

        // Tier 2: InnerTube TVHTML5
        if (detected == null || detected.qualities.isEmpty()) {
            detected = fetchInnerTube(videoId, pageUrl, clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER", clientVersion = "2.0", sdkVersion = null)
        }

        // Tier 3: InnerTube IOS
        if (detected == null || detected.qualities.isEmpty()) {
            detected = fetchInnerTube(videoId, pageUrl, clientName = "IOS", clientVersion = "19.09.3", sdkVersion = null)
        }

        // Tier 4: InnerTube ANDROID
        if (detected == null || detected.qualities.isEmpty()) {
            detected = fetchInnerTube(videoId, pageUrl, clientName = "ANDROID", clientVersion = "19.09.37", sdkVersion = 30)
        }

        // Tier 5: NewPipeExtractor Fallback
        if (detected == null || detected.qualities.isEmpty()) {
            detected = extractViaNewPipe(videoId, pageUrl)
        }

        if (detected != null && detected.qualities.isNotEmpty()) {
            Log.i(TAG, "Successfully extracted ${detected.qualities.size} streams for ${detected.title} [$videoId]")
        } else {
            Log.w(TAG, "All extraction tiers failed for $videoId")
        }

        return@withContext detected
    }

    private fun extractViaNewPipe(videoId: String, pageUrl: String): DetectedVideo? {
        return try {
            ensureNewPipeInit()
            val watchUrl = "https://www.youtube.com/watch?v=$videoId"
            val service = ServiceList.YouTube
            val extractor = service.getStreamExtractor(watchUrl) as YoutubeStreamExtractor
            extractor.fetchPage()

            val title = extractor.name.ifBlank { "YouTube Video" }
            val durationSeconds = extractor.length
            val thumbnail = extractor.thumbnails.maxByOrNull { it.height }?.url
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

            val qualityList = mutableListOf<VideoQualityOption>()

            extractor.videoStreams?.forEach { stream ->
                val streamUrl = stream.content
                if (!streamUrl.isNullOrBlank() && streamUrl.startsWith("http")) {
                    val res = stream.resolution ?: "720p"
                    val formatName = stream.format?.getName()?.uppercase() ?: "MP4"
                    val isWebm = formatName.contains("WEBM")
                    val isAv1 = formatName.contains("AV01") || formatName.contains("AV1")
                    val codec = if (isAv1) "AV1" else if (isWebm) "VP9" else "H.264"

                    qualityList.add(
                        VideoQualityOption(
                            label = "$res ($formatName • Video + Audio)",
                            resolution = res,
                            codec = codec,
                            format = formatName,
                            url = streamUrl,
                            estimatedSizeBytes = 0L,
                            isAudioOnly = false,
                            isAv1 = isAv1,
                            mimeType = if (isWebm) "video/webm" else "video/mp4"
                        )
                    )
                }
            }

            extractor.videoOnlyStreams?.forEach { stream ->
                val streamUrl = stream.content
                if (!streamUrl.isNullOrBlank() && streamUrl.startsWith("http")) {
                    val res = stream.resolution ?: "1080p"
                    val formatName = stream.format?.getName()?.uppercase() ?: "MP4"
                    val isWebm = formatName.contains("WEBM")
                    val isAv1 = formatName.contains("AV01") || formatName.contains("AV1")
                    val codec = if (isAv1) "AV1" else if (isWebm) "VP9" else "H.264"

                    qualityList.add(
                        VideoQualityOption(
                            label = "$res ($codec • High Definition)",
                            resolution = res,
                            codec = codec,
                            format = formatName,
                            url = streamUrl,
                            estimatedSizeBytes = 0L,
                            isAudioOnly = false,
                            isAv1 = isAv1,
                            mimeType = if (isWebm) "video/webm" else "video/mp4"
                        )
                    )
                }
            }

            extractor.audioStreams?.forEach { stream ->
                val streamUrl = stream.content
                if (!streamUrl.isNullOrBlank() && streamUrl.startsWith("http")) {
                    val formatName = stream.format?.getName()?.uppercase() ?: "M4A"
                    val isM4a = formatName.contains("M4A") || formatName.contains("AAC") || formatName.contains("MP4")
                    val codec = if (isM4a) "AAC" else "Opus"
                    val bitrate = stream.averageBitrate
                    val bitrateStr = if (bitrate > 0) "${bitrate} kbps" else "HQ"

                    qualityList.add(
                        VideoQualityOption(
                            label = "Audio Track ($formatName • $bitrateStr)",
                            resolution = "",
                            codec = codec,
                            format = if (isM4a) "M4A" else "WEBM",
                            url = streamUrl,
                            estimatedSizeBytes = 0L,
                            isAudioOnly = true,
                            isAv1 = false,
                            mimeType = if (isM4a) "audio/mp4" else "audio/webm"
                        )
                    )
                }
            }

            if (qualityList.isEmpty()) return null

            val sortedQualities = qualityList.sortedWith(
                compareBy<VideoQualityOption> { it.isAudioOnly }
                    .thenByDescending { parseHeight(it.resolution.ifBlank { it.label }) }
            )

            DetectedVideo(
                pageUrl = pageUrl,
                title = title,
                thumbnail = thumbnail,
                durationSeconds = durationSeconds,
                sourceDomain = "youtube.com",
                qualities = sortedQualities
            )
        } catch (e: Exception) {
            Log.d(TAG, "NewPipe fallback exception: ${e.message}")
            null
        }
    }

    private fun extractUrlFromFormat(formatObj: JSONObject): String {
        val directUrl = formatObj.optString("url")
        if (directUrl.isNotBlank()) return directUrl

        val cipher = formatObj.optString("signatureCipher").ifBlank { formatObj.optString("cipher") }
        if (cipher.isNotBlank()) {
            val params = cipher.split("&")
            for (p in params) {
                if (p.startsWith("url=")) {
                    try {
                        return java.net.URLDecoder.decode(p.substring(4), "UTF-8")
                    } catch (e: Exception) {}
                }
            }
        }
        return ""
    }

    private fun fetchInnerTube(
        videoId: String,
        pageUrl: String,
        clientName: String,
        clientVersion: String,
        sdkVersion: Int?
    ): DetectedVideo? {
        return try {
            val clientId = when (clientName) {
                "ANDROID_VR" -> "28"
                "TVHTML5_SIMPLY_EMBEDDED_PLAYER" -> "85"
                "ANDROID" -> "3"
                "IOS" -> "5"
                else -> "3"
            }

            val userAgent = when (clientName) {
                "ANDROID_VR" -> "Mozilla/5.0 (Linux; Android 10; Quest 2) AppleWebKit/537.36 (KHTML, like Gecko) OculusBrowser/15.0.0.0.22.280302912 Safari/537.36"
                "TVHTML5_SIMPLY_EMBEDDED_PLAYER" -> "Mozilla/5.0 (SMART-TV; Linux; Tizen 5.0) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/2.2 Chrome/69.0.3497.106 TV Safari/537.36"
                "IOS" -> "com.google.ios.youtube/19.09.3 (iPhone14,2; U; CPU iOS 17_4 like Mac OS X)"
                else -> "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip"
            }

            val clientJson = JSONObject().apply {
                put("clientName", clientName)
                put("clientVersion", clientVersion)
                put("hl", "en")
                put("gl", "US")
                sdkVersion?.let { put("androidSdkVersion", it) }
            }

            val requestBody = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", clientJson)
                })
            }

            val body = requestBody.toString().toRequestBody("application/json".toMediaType())
            val request = okhttp3.Request.Builder()
                .url(INNERTUBE_API_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", userAgent)
                .addHeader("X-YouTube-Client-Name", clientId)
                .addHeader("X-YouTube-Client-Version", clientVersion)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val responseText = response.body?.string() ?: return null
            val json = JSONObject(responseText)

            val playabilityStatus = json.optJSONObject("playabilityStatus")
            val status = playabilityStatus?.optString("status")
            if (status != null && status != "OK") {
                Log.d(TAG, "InnerTube $clientName status: $status")
                return null
            }

            val videoDetails = json.optJSONObject("videoDetails") ?: JSONObject()
            val title = videoDetails.optString("title", "YouTube Video")
            val durationSeconds = videoDetails.optLong("lengthSeconds", 0L)
            val thumbnailArr = videoDetails.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumbnail = if (thumbnailArr != null && thumbnailArr.length() > 0) {
                thumbnailArr.getJSONObject(thumbnailArr.length() - 1).optString("url")
            } else {
                "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            }

            val streamingData = json.optJSONObject("streamingData") ?: return null
            val formats = streamingData.optJSONArray("formats")
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")

            val qualityList = mutableListOf<VideoQualityOption>()

            // 1. Progressive formats (Video + Audio combined in single stream)
            if (formats != null) {
                for (i in 0 until formats.length()) {
                    val f = formats.getJSONObject(i)
                    val streamUrl = extractUrlFromFormat(f)
                    if (streamUrl.isBlank() || !streamUrl.startsWith("http")) continue

                    val qualityLabel = f.optString("qualityLabel", if (f.has("height")) "${f.getInt("height")}p" else "720p")
                    val mimeType = f.optString("mimeType", "video/mp4")
                    val isAv1 = mimeType.contains("av01")
                    val codec = if (isAv1) "AV1" else if (mimeType.contains("webm")) "VP9" else "H.264"
                    val isWebm = mimeType.contains("webm")
                    val contentLength = f.optLong("contentLength", 0L)

                    qualityList.add(
                        VideoQualityOption(
                            label = "$qualityLabel (${if (isWebm) "WEBM" else "MP4"} • Video + Audio)",
                            resolution = if (f.has("width") && f.has("height")) "${f.getInt("width")}x${f.getInt("height")}" else qualityLabel,
                            codec = codec,
                            format = if (isWebm) "WEBM" else "MP4",
                            url = streamUrl,
                            estimatedSizeBytes = contentLength,
                            isAudioOnly = false,
                            isAv1 = isAv1,
                            mimeType = mimeType
                        )
                    )
                }
            }

            // 2. Adaptive formats (high resolutions 1080p, 1440p, 4K, and audio streams)
            if (adaptiveFormats != null) {
                for (i in 0 until adaptiveFormats.length()) {
                    val f = adaptiveFormats.getJSONObject(i)
                    val streamUrl = extractUrlFromFormat(f)
                    if (streamUrl.isBlank() || !streamUrl.startsWith("http")) continue

                    val mimeType = f.optString("mimeType", "")
                    val isAudio = mimeType.startsWith("audio/")
                    val isAv1 = mimeType.contains("av01")
                    val contentLength = f.optLong("contentLength", 0L)

                    if (isAudio) {
                        val bitrate = if (f.has("bitrate")) "${Math.round(f.getInt("bitrate") / 1000f)} kbps" else "128 kbps"
                        val isM4a = mimeType.contains("mp4") || mimeType.contains("m4a")
                        val codec = if (isM4a) "AAC" else "Opus"
                        val format = if (isM4a) "M4A" else "WEBM"

                        qualityList.add(
                            VideoQualityOption(
                                label = "Audio Track ($format • $bitrate)",
                                resolution = "",
                                codec = codec,
                                format = format,
                                url = streamUrl,
                                estimatedSizeBytes = contentLength,
                                isAudioOnly = true,
                                isAv1 = false,
                                mimeType = mimeType
                            )
                        )
                    } else {
                        val qualityLabel = f.optString("qualityLabel")
                        if (qualityLabel.isBlank()) continue
                        val codec = if (isAv1) "AV1" else if (mimeType.contains("vp9") || mimeType.contains("vp09")) "VP9" else "H.264"
                        val isWebm = mimeType.contains("webm")

                        qualityList.add(
                            VideoQualityOption(
                                label = "$qualityLabel ($codec • High Definition)",
                                resolution = if (f.has("width") && f.has("height")) "${f.getInt("width")}x${f.getInt("height")}" else qualityLabel,
                                codec = codec,
                                format = if (isWebm) "WEBM" else "MP4",
                                url = streamUrl,
                                estimatedSizeBytes = contentLength,
                                isAudioOnly = false,
                                isAv1 = isAv1,
                                mimeType = mimeType
                            )
                        )
                    }
                }
            }

            if (qualityList.isEmpty()) return null

            val sortedQualities = qualityList.sortedWith(
                compareBy<VideoQualityOption> { it.isAudioOnly }
                    .thenByDescending { parseHeight(it.resolution.ifBlank { it.label }) }
                    .thenByDescending { it.estimatedSizeBytes }
            )

            DetectedVideo(
                pageUrl = pageUrl,
                title = title,
                thumbnail = thumbnail,
                durationSeconds = durationSeconds,
                sourceDomain = "youtube.com",
                qualities = sortedQualities
            )
        } catch (e: Exception) {
            Log.d(TAG, "InnerTube $clientName exception: ${e.message}")
            null
        }
    }

    private fun parseHeight(resStr: String): Int {
        return try {
            if (resStr.contains("x")) {
                resStr.substringAfter("x").takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            } else if (resStr.contains("p")) {
                resStr.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    private class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {
        companion object {
            private const val USER_AGENT =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
        }

        @Throws(IOException::class, org.schabi.newpipe.extractor.exceptions.ReCaptchaException::class)
        override fun execute(request: Request): Response {
            val httpMethod = request.httpMethod()
            val url = request.url()
            val headers = request.headers()
            val dataToSend = request.dataToSend()

            val requestBuilder = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Cookie", "CONSENT=YES+1; PREF=f6=40000000&hl=en")

            headers?.forEach { (headerName, headerValueList) ->
                if (headerValueList.isNotEmpty()) {
                    requestBuilder.removeHeader(headerName)
                    for (headerValue in headerValueList) {
                        requestBuilder.addHeader(headerName, headerValue)
                    }
                }
            }

            val body = dataToSend?.toRequestBody(null)
            requestBuilder.method(httpMethod, body)

            val okResponse = client.newCall(requestBuilder.build()).execute()
            val responseBody = okResponse.body?.string() ?: ""
            val responseHeaders = okResponse.headers.toMultimap()

            return Response(
                okResponse.code,
                okResponse.message,
                responseHeaders,
                responseBody,
                okResponse.request.url.toString()
            )
        }
    }
}
