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

    /**
     * InnerTube client versions. These age out quickly: a client whose version is
     * too old is refused, and current Android clients are increasingly served the
     * SABR-only streaming spec instead of plain progressive/adaptive URLs.
     *
     * Exposed so the downloader can replay the exact same User-Agent that minted
     * a stream URL; googlevideo returns HTTP 403 when the two disagree.
     */
    const val ANDROID_CLIENT_VERSION = "20.10.38"
    const val IOS_CLIENT_VERSION = "20.10.4"
    const val ANDROID_VR_CLIENT_VERSION = "1.60.19"
    const val WEB_CLIENT_VERSION = "2.20250312.04.00"

    /**
     * TVHTML5 is the only InnerTube client that is not gated behind a GVS PO
     * Token, so its adaptive URLs download in full. The version has to track the
     * live /tv page: a stale one is refused with "The page needs to be reloaded".
     */
    const val TVHTML5_CLIENT_VERSION = "7.20260930.10.00"

    const val ANDROID_USER_AGENT =
        "com.google.android.youtube/$ANDROID_CLIENT_VERSION (Linux; U; Android 14; SM-S948B) gzip"
    const val IOS_USER_AGENT =
        "com.google.ios.youtube/$IOS_CLIENT_VERSION (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X; en_US)"
    const val ANDROID_VR_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 12; Quest 2) AppleWebKit/537.36 (KHTML, like Gecko) OculusBrowser/23.0.0.0.0.2205209133 Oculus/23.0.0.0.0.2205209133 VRBrowser/1.0.0 Safari/537.36"
    const val WEB_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
    const val TVHTML5_USER_AGENT = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version"

    /** Maps the `c=` client marker in a googlevideo URL to a matching User-Agent. */
    fun userAgentForClientMarker(clientMarker: String?): String? = when (clientMarker?.uppercase()) {
        "ANDROID" -> ANDROID_USER_AGENT
        "ANDROID_VR" -> ANDROID_VR_USER_AGENT
        "IOS" -> IOS_USER_AGENT
        "WEB", "WEB_EMBEDDED_PLAYER", "MWEB" -> WEB_USER_AGENT
        "TVHTML5", "TVHTML5_SIMPLY_EMBEDDED_PLAYER" -> TVHTML5_USER_AGENT
        else -> null
    }

    private val VIDEO_ID_PATTERN = Pattern.compile(
        "(?:youtu\\.be\\/|youtube\\.com\\/(?:embed\\/|v\\/|shorts\\/|watch\\?v=|watch\\?.+&v=))([\\w-]{11})"
    )

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .proxySelector(com.browser.adfree.vpn.NetworkRoute)
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

    suspend fun extract(pageUrl: String, cookieHeader: String? = null): DetectedVideo? =
        withContext(Dispatchers.IO) {
            val videoId = extractVideoId(pageUrl) ?: return@withContext null
            return@withContext extractById(videoId, pageUrl, cookieHeader)
        }

    suspend fun extractById(
        videoId: String,
        pageUrl: String,
        cookieHeader: String? = null
    ): DetectedVideo? = withContext(Dispatchers.IO) {
        var detected: DetectedVideo? = null

        // Tier 0: InnerTube TVHTML5. The only client googlevideo serves in full
        // without a GVS PO Token, but it refuses unauthenticated callers with
        // "Sign in to confirm you're not a bot", so it needs the browser cookies.
        if (!cookieHeader.isNullOrBlank()) {
            detected = fetchInnerTube(
                videoId, pageUrl,
                clientName = "TVHTML5",
                clientVersion = TVHTML5_CLIENT_VERSION,
                sdkVersion = null,
                cookieHeader = cookieHeader
            )
        }

        // Tier 1: InnerTube ANDROID. Primary client: current app version, and it
        // keeps returning unciphered URLs far more often than the legacy clients.
        if (detected == null || detected.qualities.isEmpty()) {
            detected = fetchInnerTube(videoId, pageUrl, clientName = "ANDROID", clientVersion = ANDROID_CLIENT_VERSION, sdkVersion = 34)
        }

        // Tier 2: InnerTube ANDROID_VR. Ages faster than any other client, so it
        // is a fallback only; a hardcoded 1.43.32 now returns SABR-only payloads.
        if (detected == null || detected.qualities.isEmpty()) {
            detected = fetchInnerTube(videoId, pageUrl, clientName = "ANDROID_VR", clientVersion = ANDROID_VR_CLIENT_VERSION, sdkVersion = 32)
        }

        // Tier 3: WebPage direct extraction (real browser player response, needs cookies)
        if (detected == null || detected.qualities.isEmpty()) {
            detected = fetchFromWebPage(videoId, pageUrl)
        }

        // Tier 4: InnerTube IOS. Still the most reliable non-ciphered client when
        // Android is throttled, so keep it behind the web player.
        if (detected == null || detected.qualities.isEmpty()) {
            detected = fetchInnerTube(videoId, pageUrl, clientName = "IOS", clientVersion = IOS_CLIENT_VERSION, sdkVersion = null)
        }

        // Tier 5: InnerTube WEB. Unciphered, but often restricted to low quality.
        if (detected == null || detected.qualities.isEmpty()) {
            detected = fetchInnerTube(videoId, pageUrl, clientName = "WEB", clientVersion = WEB_CLIENT_VERSION, sdkVersion = null)
        }

        // Tier 6: InnerTube TVHTML5 Embedded Player
        if (detected == null || detected.qualities.isEmpty()) {
            detected = fetchInnerTube(videoId, pageUrl, clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER", clientVersion = TVHTML5_CLIENT_VERSION, sdkVersion = null)
        }

        // Tier 7: NewPipeExtractor Fallback
        if (detected == null || detected.qualities.isEmpty()) {
            detected = extractViaNewPipe(videoId, pageUrl)
        }

        if (detected != null && detected.qualities.isNotEmpty()) {
            Log.i(TAG, "Successfully extracted ${detected.qualities.size} streams")
        } else {
            Log.w(TAG, "All extraction tiers failed for $videoId")
        }

        return@withContext detected
    }

    private fun fetchFromWebPage(videoId: String, pageUrl: String): DetectedVideo? {
        return try {
            val watchUrl = "https://www.youtube.com/watch?v=$videoId"
            val request = okhttp3.Request.Builder()
                .url(watchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "CONSENT=PENDING+999; SOCS=CAESEwgDEgk2NDkzOTc0MTEaAmVuIAEaBgiA_LyaBg; PREF=f6=40000000&hl=en")
                .build()

            val response = httpClient.newCall(request).execute()
            val html = response.use {
                if (!it.isSuccessful) return null
                it.body?.string() ?: return null
            }

            val pattern = Pattern.compile("ytInitialPlayerResponse\\s*=\\s*(\\{.+?\\});(?:var|\\s*</script>)")
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val jsonStr = matcher.group(1) ?: return null
                val json = JSONObject(jsonStr)
                parsePlayerResponseJson(json, videoId, pageUrl)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "WebPage extraction fallback: ${e.message}")
            null
        }
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

            // Best audio stream for adaptive video muxing (prefer AAC / M4A)
            val bestAudioStream = extractor.audioStreams?.filter {
                val fmt = it.format?.getName()?.uppercase() ?: ""
                fmt.contains("M4A") || fmt.contains("AAC") || fmt.contains("MP4")
            }?.maxByOrNull { it.averageBitrate } ?: extractor.audioStreams?.maxByOrNull { it.averageBitrate }

            val bestAudioUrl = bestAudioStream?.content
            val bestOpusUrl = extractor.audioStreams?.filter {
                (it.format?.getName()?.uppercase() ?: "").let { f -> f.contains("WEBM") || f.contains("OPUS") }
            }?.maxByOrNull { it.averageBitrate }?.content
            val bestAudioFormat = bestAudioStream?.format?.getName()?.uppercase() ?: "M4A"

            // 1. Progressive streams (Video + Audio natively combined)
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
                            label = "$res (${if (isWebm) "WEBM" else "MP4"} • Video + Audio)",
                            resolution = res,
                            codec = codec,
                            format = formatName,
                            url = streamUrl,
                            estimatedSizeBytes = 0L,
                            isAudioOnly = false,
                            isAv1 = isAv1,
                            isProgressive = true,
                            audioUrl = null,
                            mimeType = if (isWebm) "video/webm" else "video/mp4"
                        )
                    )
                }
            }

            // 2. High-res Video Only streams (paired with best audio for muxing)
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
                            label = "$res ($codec • With Audio)",
                            resolution = res,
                            codec = codec,
                            format = formatName,
                            url = streamUrl,
                            estimatedSizeBytes = 0L,
                            isAudioOnly = false,
                            isAv1 = isAv1,
                            isProgressive = false,
                            audioUrl = if (isWebm) bestOpusUrl ?: bestAudioUrl else bestAudioUrl,
                            mimeType = if (isWebm) "video/webm" else "video/mp4"
                        )
                    )
                }
            }

            // 3. Audio streams
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
                            isProgressive = true,
                            audioUrl = null,
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
        if (directUrl.isNotBlank()) return directUrl.replace(Regex("&range=[^&]+"), "")

        val cipher = formatObj.optString("signatureCipher").ifBlank { formatObj.optString("cipher") }
        if (cipher.isNotBlank()) {
            var cipherUrl = ""
            var encryptedSignature = false
            for (p in cipher.split("&")) {
                if (p.startsWith("url=")) {
                    try {
                        cipherUrl = java.net.URLDecoder.decode(p.substring(4), "UTF-8")
                    } catch (e: Exception) {
                    }
                } else if (p.startsWith("s=") && p.length > 2) {
                    encryptedSignature = true
                }
            }
            // An encrypted signature needs the player JS to decipher, and this
            // extractor has no JS runtime. Emitting it would only produce a URL
            // googlevideo rejects with 403, so drop the format instead.
            if (encryptedSignature) return ""
            return cipherUrl.replace(Regex("&range=[^&]+"), "")
        }
        return ""
    }

    private fun fetchInnerTube(
        videoId: String,
        pageUrl: String,
        clientName: String,
        clientVersion: String,
        sdkVersion: Int?,
        cookieHeader: String? = null
    ): DetectedVideo? {
        return try {
            val clientId = when (clientName) {
                "ANDROID_VR" -> "28"
                "TVHTML5" -> "7"
                "TVHTML5_SIMPLY_EMBEDDED_PLAYER" -> "85"
                "IOS" -> "5"
                "WEB" -> "1"
                "ANDROID_TESTSUITE" -> "3"
                else -> "3"
            }

            val userAgent = when (clientName) {
                "ANDROID_VR" -> ANDROID_VR_USER_AGENT
                "TVHTML5" -> TVHTML5_USER_AGENT
                "TVHTML5_SIMPLY_EMBEDDED_PLAYER" -> TVHTML5_USER_AGENT
                "IOS" -> IOS_USER_AGENT
                "ANDROID_TESTSUITE" -> "com.google.android.youtube/1.9 (Linux; U; Android 11) gzip"
                "WEB" -> WEB_USER_AGENT
                else -> ANDROID_USER_AGENT
            }

            val clientJson = JSONObject().apply {
                put("clientName", clientName)
                put("clientVersion", clientVersion)
                put("hl", "en")
                put("gl", "US")
                if (clientName == "TVHTML5") put("userAgent", TVHTML5_USER_AGENT)
                sdkVersion?.let { put("androidSdkVersion", it) }
                if (clientName == "IOS") {
                    put("deviceModel", "iPhone16,2")
                    put("osName", "iOS")
                    put("osVersion", "17.5.1.21F90")
                }
            }

            val requestBody = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", clientJson)
                })
                if (clientName == "TVHTML5" || clientName == "TVHTML5_SIMPLY_EMBEDDED_PLAYER") {
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                    put("playbackContext", JSONObject().apply {
                        put("contentPlaybackContext", JSONObject().apply {
                            put("html5Preference", "HTML5_PREF_WANTS")
                        })
                    })
                }
            }

            val body = requestBody.toString().toRequestBody("application/json".toMediaType())
            val request = okhttp3.Request.Builder()
                .url(INNERTUBE_API_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", userAgent)
                .addHeader("X-YouTube-Client-Name", clientId)
                .addHeader("X-YouTube-Client-Version", clientVersion)
                .apply {
                    if (clientName.startsWith("TVHTML5")) {
                        addHeader("Origin", "https://www.youtube.com")
                        addHeader("Referer", "https://www.youtube.com/")
                    }
                    cookieHeader?.takeIf { it.isNotBlank() }?.let { addHeader("Cookie", it) }
                }
                .post(body)
                .build()

            val responseText = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string() ?: return null
            }
            val json = JSONObject(responseText)
            val parsed = parsePlayerResponseJson(json, videoId, pageUrl, userAgent)
            Log.i(TAG, "InnerTube $clientName -> ${parsed?.qualities?.size ?: 0} streams")
            return parsed
        } catch (e: Exception) {
            Log.d(TAG, "InnerTube $clientName exception: ${e.message}")
            null
        }
    }

    private fun parsePlayerResponseJson(
        json: JSONObject,
        videoId: String,
        pageUrl: String,
        clientUserAgent: String? = null
    ): DetectedVideo? {
        try {
            val playabilityStatus = json.optJSONObject("playabilityStatus")
            val status = playabilityStatus?.optString("status")
            if (status != null && status != "OK") {
                Log.i(TAG, "Player response status: $status reason=${playabilityStatus?.optString("reason")}")
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

            val streamHeaders = if (!clientUserAgent.isNullOrBlank()) {
                mapOf("User-Agent" to clientUserAgent)
            } else {
                emptyMap()
            }

            val qualityList = mutableListOf<VideoQualityOption>()

            // Best audio of each family. MP4 video gets AAC and WebM video gets
            // Opus, so the pair always fits in one file.
            var bestM4a: Triple<String, Int, Long>? = null
            var bestOpus: Triple<String, Int, Long>? = null
            if (adaptiveFormats != null) {
                for (i in 0 until adaptiveFormats.length()) {
                    val f = adaptiveFormats.getJSONObject(i)
                    val mimeType = f.optString("mimeType", "")
                    if (!mimeType.startsWith("audio/")) continue
                    val streamUrl = extractUrlFromFormat(f)
                    if (streamUrl.isBlank()) continue
                    // Skip dubbed and descriptive tracks; keep the original language.
                    val track = f.optJSONObject("audioTrack")
                    if (track != null && !track.optBoolean("audioIsDefault", true)) continue
                    val entry = Triple(streamUrl, f.optInt("bitrate", 0), f.optLong("contentLength", 0L))
                    if (mimeType.contains("mp4")) {
                        if (bestM4a == null || entry.second > bestM4a.second) bestM4a = entry
                    } else if (bestOpus == null || entry.second > bestOpus.second) {
                        bestOpus = entry
                    }
                }
            }
            fun audioFor(videoMime: String) =
                if (videoMime.contains("webm")) bestOpus ?: bestM4a else bestM4a ?: bestOpus
            val bestAudioUrl: String? = (bestM4a ?: bestOpus)?.first
            val bestAudioLength = (bestM4a ?: bestOpus)?.third ?: 0L

            // 1. Progressive formats (Video + Audio natively combined in a single stream)
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
                            isProgressive = true,
                            audioUrl = null,
                            mimeType = mimeType,
                            headers = streamHeaders
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
                                isProgressive = true,
                                audioUrl = null,
                                mimeType = mimeType,
                                headers = streamHeaders
                            )
                        )
                    } else {
                        val qualityLabel = f.optString("qualityLabel")
                        if (qualityLabel.isBlank()) continue
                        val codec = if (isAv1) "AV1" else if (mimeType.contains("vp9") || mimeType.contains("vp09")) "VP9" else "H.264"
                        val isWebm = mimeType.contains("webm")

                        // For adaptive video streams, pair with best audio stream so it will be muxed with audio!
                        qualityList.add(
                            VideoQualityOption(
                                label = "$qualityLabel ($codec • With Audio)",
                                resolution = if (f.has("width") && f.has("height")) "${f.getInt("width")}x${f.getInt("height")}" else qualityLabel,
                                codec = codec,
                                format = if (isWebm) "WEBM" else "MP4",
                                url = streamUrl,
                                estimatedSizeBytes = contentLength + (audioFor(mimeType)?.third ?: 0L),
                                isAudioOnly = false,
                                isAv1 = isAv1,
                                isProgressive = false,
                                audioUrl = audioFor(mimeType)?.first,
                                mimeType = mimeType,
                                headers = streamHeaders
                            )
                        )
                    }
                }
            }

            if (qualityList.isEmpty()) return null

            // Sort: Video progressive/high-res first, then audio tracks
            val sortedQualities = qualityList.sortedWith(
                compareBy<VideoQualityOption> { it.isAudioOnly }
                    .thenByDescending { parseHeight(it.resolution.ifBlank { it.label }) }
                    .thenByDescending { it.isProgressive }
                    .thenByDescending { it.estimatedSizeBytes }
            )

            return DetectedVideo(
                pageUrl = pageUrl,
                title = title,
                thumbnail = thumbnail,
                durationSeconds = durationSeconds,
                sourceDomain = "youtube.com",
                qualities = sortedQualities
            )
        } catch (e: Exception) {
            Log.d(TAG, "JSON parsing error: ${e.message}")
            return null
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

            return client.newCall(requestBuilder.build()).execute().use { okResponse ->
                Response(
                    okResponse.code,
                    okResponse.message,
                    okResponse.headers.toMultimap(),
                    okResponse.body?.string() ?: "",
                    okResponse.request.url.toString()
                )
            }
        }
    }
}
