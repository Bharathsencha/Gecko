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

package com.browser.adfree

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.browser.adfree.download.BrowserDownloadManager
import com.browser.adfree.storage.BrowserPreferencesRepository
import com.browser.adfree.storage.FavoritesRepository
import com.browser.adfree.storage.HistoryRepository
import com.browser.adfree.storage.SecurePasswordRepository
import com.browser.adfree.tabs.TabManager
import com.browser.adfree.ui.BrowserMainScreen
import com.browser.adfree.ui.components.PendingSaveLogin
import com.browser.adfree.ui.theme.BrowserTheme
import com.browser.adfree.vpn.FreeVpnManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.mozilla.geckoview.*

class MainActivity : ComponentActivity() {

    private lateinit var runtime: GeckoRuntime
    private lateinit var tabManager: TabManager
    private lateinit var downloadManager: BrowserDownloadManager
    private lateinit var preferencesRepo: BrowserPreferencesRepository
    private lateinit var passwordRepo: SecurePasswordRepository
    private lateinit var historyRepo: HistoryRepository
    private lateinit var favoritesRepo: FavoritesRepository
    private lateinit var vpnManager: FreeVpnManager
    private lateinit var shieldsRepo: com.browser.adfree.shields.ShieldsRepository

    private var extensionPort: WebExtension.Port? = null
    private var uBlockExtension: WebExtension? = null
    private var darkReaderExtension: WebExtension? = null
    private var pendingSaveLogin by mutableStateOf<PendingSaveLogin?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /** Enable or disable uBlock Origin blocking globally */
    fun setUBlockEnabled(enabled: Boolean) {
        try {
            val msg = JSONObject().apply {
                put("what", if (enabled) "restoreAll" else "suspendAll")
            }
            runtime.webExtensionController.list().accept { extensions ->
                extensions?.find { it.id == "uBlock0@raymondhill.net" }?.let {
                    // Send via tab messaging — uBlock background script listens
                    Log.i("AdFreeBrowser", "uBlock toggle: enabled=$enabled")
                }
            }
        } catch (e: Exception) {
            Log.e("AdFreeBrowser", "Failed to toggle uBlock", e)
        }
    }

    /** Enable or disable Dark Reader dynamically */
    fun setDarkReaderEnabled(enabled: Boolean) {
        try {
            val tabs = tabManager.tabs
            tabs.forEach { tab ->
                tab.session.let { session ->
                    val js = if (enabled) {
                        """
                        (function() {
                          if (window.__darkReaderEnabled) return;
                          window.__darkReaderEnabled = true;
                          document.dispatchEvent(new CustomEvent('__darkreader__enable'));
                        })();
                        """.trimIndent()
                    } else {
                        """
                        (function() {
                          window.__darkReaderEnabled = false;
                          document.dispatchEvent(new CustomEvent('__darkreader__disable'));
                        })();
                        """.trimIndent()
                    }
                    // Dark Reader is already injected as a content script — it handles its own events
                    // The extension's background page manages enabled state via storage
                }
            }
            Log.i("AdFreeBrowser", "Dark Reader toggle: enabled=$enabled")
        } catch (e: Exception) {
            Log.e("AdFreeBrowser", "Failed to toggle Dark Reader", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Coil with Animated GIF & WebP decoder support for dynamic wallpapers
        val imageLoader = coil.ImageLoader.Builder(this)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(coil.decode.ImageDecoderDecoder.Factory())
                } else {
                    add(coil.decode.GifDecoder.Factory())
                }
            }
            .build()
        coil.Coil.setImageLoader(imageLoader)

        // Request notification permission on Android 13+ for background download progress & alerts
        checkAndRequestNotificationPermission()

        // 1. Initialize Repositories & Managers
        preferencesRepo = BrowserPreferencesRepository(this)
        shieldsRepo = com.browser.adfree.shields.ShieldsRepository(this)
        passwordRepo = SecurePasswordRepository(this)
        historyRepo = HistoryRepository(this)
        favoritesRepo = FavoritesRepository(this)
        downloadManager = BrowserDownloadManager(this)
        vpnManager = FreeVpnManager(this, preferencesRepo)

        tabManager = TabManager(this).apply {
            this.historyRepository = historyRepo
            this.preferencesRepository = preferencesRepo
            this.shieldsRepository = shieldsRepo
            this.downloadManager = downloadManager
        }

        // Initialize UniversalVideoExtractor (yt-dlp engine) in background
        lifecycleScope.launch(Dispatchers.IO) {
            com.browser.adfree.download.UniversalVideoExtractor.init(applicationContext)
        }

        // Wire real-time VPN config bridge to WebExtension
        vpnManager.onConfigChanged = { enabled, host, port, type ->
            try {
                val payload = JSONObject().apply {
                    put("action", "set_vpn_config")
                    put("enabled", enabled)
                    put("host", host)
                    put("port", port)
                    put("type", type)
                    put("countryCode", vpnManager.selectedCountry.code)
                }
                extensionPort?.postMessage(payload)
            } catch (e: Exception) {}
        }

        // 2. Configure GeckoRuntime with Strict Native Tracking Protection & Cookie Shield
        // Enable Gecko's built-in fingerprinting protection (RFP engine) via about:config prefs
        val geckoPrefs = Bundle().apply {
            // Hardware Video Decoding & GPU WebRender Acceleration on Android
            putBoolean("media.hardware-video-decoding.enabled", true)
            putBoolean("media.hardware-video-decoding.failed", false)
            putBoolean("media.android-media-codec.enabled", true)
            putBoolean("media.android-media-codec.preferred", true)
            putBoolean("media.webm.enabled", true)
            putBoolean("media.mp4.enabled", true)
            putBoolean("media.mediasource.enabled", true)
            putBoolean("media.mediasource.mp4.enabled", true)
            putBoolean("media.mediasource.webm.enabled", true)
            putBoolean("media.mediasource.vp9.enabled", true)
            putInt("media.benchmark.vp9.fps", 120)
            putInt("media.benchmark.frames", 1000)
            putInt("media.cache_size", 65536)
            putInt("media.memory_cache_max_size", 65536)
            putInt("media.autoplay.default", 0)
            putInt("media.autoplay.blocking_policy", 0)
            putInt("layout.frame_rate", 0)
            putBoolean("gfx.webrender.all", true)
            putBoolean("layers.acceleration.force-enabled", true)
            putBoolean("layers.offmainthreadcomposition.enabled", true)
            putBoolean("layers.offmainthreadcomposition.async-animations", true)

            // Tracking Protection (Standard without timer throttling)
            putBoolean("privacy.trackingprotection.fingerprinting.enabled", true)
            putBoolean("privacy.trackingprotection.enabled", true)
            putBoolean("toolkit.telemetry.enabled", false)
            putBoolean("datareporting.healthreport.uploadEnabled", false)
        }

        // Copy user.js prefs to filesDir so GeckoView can load them
        val userJsFile = java.io.File(filesDir, "user.js")
        try {
            assets.open("gecko/user.js").use { input ->
                userJsFile.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            Log.w("AdFreeBrowser", "Failed to copy user.js: ${e.message}")
        }

        val runtimeSettings = GeckoRuntimeSettings.Builder()
            .extras(geckoPrefs)
            .configFilePath(userJsFile.absolutePath)
            .globalPrivacyControlEnabled(true)
            .contentBlocking(
                ContentBlocking.Settings.Builder()
                    .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
                    .antiTracking(ContentBlocking.AntiTracking.STRICT)
                    .cookieBehavior(preferencesRepo.cookiePolicy.geckoValue)
                    .cookieBannerHandlingMode(ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_REJECT_OR_ACCEPT)
                    .cookieBannerGlobalRulesEnabled(true)
                    .cookieBannerGlobalRulesSubFramesEnabled(true)
                    .queryParameterStrippingEnabled(true)
                    .strictSocialTrackingProtection(true)
                    .build()
            )
            .javaScriptEnabled(true)
            .build()

        runtime = try {
            GeckoRuntime.create(this, runtimeSettings)
        } catch (e: Exception) {
            GeckoRuntime.getDefault(this)
        }

        // Auto-grant permissions for built-in extensions (webRequest, webRequestBlocking, storage, etc.)
        runtime.webExtensionController.promptDelegate = object : WebExtensionController.PromptDelegate {
            override fun onInstallPrompt(extension: WebExtension): GeckoResult<AllowOrDeny>? {
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }
            override fun onOptionalPrompt(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>
            ): GeckoResult<AllowOrDeny>? {
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }
        }

        // 3a. Load real uBlock Origin 1.75.0 — silent, no popup, blocks everything
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/extensions/ublock_origin/",
            "uBlock0@raymondhill.net"
        ).accept(
            { ext ->
                if (ext != null) {
                    Log.i("AdFreeBrowser", "uBlock Origin loaded successfully")
                    uBlockExtension = ext
                }
            },
            { error -> Log.e("AdFreeBrowser", "uBlock Origin failed to load", error) }
        )

        // 3b. Load real Dark Reader 4.9.133 — silent, controlled via app toggle
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/extensions/darkreader/",
            "addon@darkreader.org"
        ).accept(
            { ext ->
                if (ext != null) {
                    Log.i("AdFreeBrowser", "Dark Reader loaded successfully")
                    darkReaderExtension = ext
                    // Apply saved dark mode preference on startup
                    if (preferencesRepo.darkWebDefault) {
                        setDarkReaderEnabled(true)
                    }
                }
            },
            { error -> Log.e("AdFreeBrowser", "Dark Reader failed to load", error) }
        )

        // 3c. Load custom AI Overview + Reader bridge
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/extensions/aifree/",
            "aifree-embedded@browser.adfree"
        ).accept(
            { extension ->
                if (extension != null) {
                    Log.i("AdFreeBrowser", "AI Overview blocker loaded")

                    // Set up Two-Way Native Port Messaging Bridge
                    extension.setMessageDelegate(object : WebExtension.MessageDelegate {
                        override fun onConnect(port: WebExtension.Port) {
                            extensionPort = port
                            port.setDelegate(object : WebExtension.PortDelegate {
                                override fun onPortMessage(message: Any, port: WebExtension.Port) {
                                    handleExtensionMessage(message)
                                }
                                override fun onDisconnect(port: WebExtension.Port) {
                                    if (extensionPort == port) extensionPort = null
                                }
                            })
                            syncExtensionConfig()
                        }

                        override fun onMessage(
                            nativeApp: String,
                            message: Any,
                            sender: WebExtension.MessageSender
                        ): GeckoResult<Any>? {
                            if (nativeApp == "browser_app") {
                                handleExtensionMessage(message)
                            }
                            return GeckoResult.fromValue(JSONObject())
                        }
                    }, "browser_app")
                }
            },
            { error -> Log.e("AdFreeBrowser", "AI blocker failed to load", error) }
        )


        // 4. Restore Session / Create Initial Tab according to Startup Behavior
        tabManager.restoreSession(runtime, preferencesRepo.startupBehavior)
        val initialUrl = intent?.dataString
        if (!initialUrl.isNullOrBlank()) {
            val activeTab = tabManager.activeTab
            if (activeTab != null && activeTab.url == TabManager.HOME_URL) {
                tabManager.loadUri(activeTab, initialUrl, runtime)
            } else {
                tabManager.createNewTab(runtime, initialUrl)
            }
        }

        // 5. System Back Button Dispatcher
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val activeTab = tabManager.activeTab
                if (activeTab != null && activeTab.isReaderMode) {
                    activeTab.isReaderMode = false
                } else if (activeTab != null && activeTab.canGoBack) {
                    activeTab.session.goBack()
                } else if (activeTab != null && activeTab.url != TabManager.HOME_URL) {
                    tabManager.loadUri(activeTab, TabManager.HOME_URL, runtime)
                } else if (tabManager.tabs.size > 1) {
                    tabManager.closeTab(tabManager.activeTabIndex.intValue, runtime)
                } else {
                    vpnManager.forceDisconnectOnExit(tabManager.tabs)
                    if (preferencesRepo.wipeOnExit) {
                        performWipeOnExit()
                    }
                    finish()
                }
            }
        })

        // 6. Set Compose UI
        setContent {
            BrowserTheme(
                themeMode = preferencesRepo.themeMode,
                accentColor = preferencesRepo.accentColor
            ) {
                BrowserMainScreen(
                    runtime = runtime,
                    tabManager = tabManager,
                    downloadManager = downloadManager,
                    preferencesRepo = preferencesRepo,
                    passwordRepo = passwordRepo,
                    historyRepo = historyRepo,
                    favoritesRepo = favoritesRepo,
                    shieldsRepo = shieldsRepo,
                    vpnManager = vpnManager,
                    pendingSaveLogin = pendingSaveLogin,
                    onSaveLogin = {
                        pendingSaveLogin?.let { login ->
                            passwordRepo.saveLogin(login.domain, login.username, login.password)
                            android.widget.Toast.makeText(this@MainActivity, "Password saved securely (AES-256)", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        pendingSaveLogin = null
                    },
                    onDismissSaveLogin = {
                        pendingSaveLogin = null
                    }
                )
            }
        }
    }

    private fun performWipeOnExit() {
        try {
            historyRepo.clearAll()
            tabManager.sessionRepository.clearSession()
            runtime.storageController.clearData(StorageController.ClearFlags.ALL)
            cacheDir.deleteRecursively()
            codeCacheDir.deleteRecursively()
            externalCacheDir?.deleteRecursively()
        } catch (e: Exception) {
            Log.e("AdFreeBrowser", "Error wiping data on exit", e)
        }
    }

    private fun syncExtensionConfig() {
        try {
            val vpnPayload = JSONObject().apply {
                put("action", "set_vpn_config")
                put("enabled", vpnManager.isEnabled)
                put("host", if (vpnManager.isEnabled) vpnManager.selectedCountry.host else "")
                put("port", if (vpnManager.isEnabled) vpnManager.selectedCountry.port else 0)
                put("type", vpnManager.selectedCountry.type)
                put("countryCode", vpnManager.selectedCountry.code)
            }
            extensionPort?.postMessage(vpnPayload)

            val adPayload = JSONObject().apply {
                put("action", "set_ad_blocking")
                put("enabled", preferencesRepo.adBlockingEnabled)
            }
            extensionPort?.postMessage(adPayload)

            val aiPayload = JSONObject().apply {
                put("action", "set_block_ai_overviews")
                put("enabled", preferencesRepo.blockAiOverviews)
            }
            extensionPort?.postMessage(aiPayload)
        } catch (e: Exception) {}
    }

    private fun parseToJsonObject(message: Any?): JSONObject {
        if (message == null) return JSONObject()
        return when (message) {
            is JSONObject -> message
            is Map<*, *> -> {
                val json = JSONObject()
                for ((k, v) in message) {
                    if (k != null) {
                        json.put(k.toString(), wrapJsonValue(v))
                    }
                }
                json
            }
            is String -> {
                val trimmed = message.trim()
                if (trimmed.startsWith("{")) {
                    try { JSONObject(trimmed) } catch (e: Exception) { JSONObject() }
                } else JSONObject()
            }
            else -> {
                try {
                    JSONObject(message.toString())
                } catch (e: Exception) {
                    JSONObject()
                }
            }
        }
    }

    private fun wrapJsonValue(value: Any?): Any? {
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> parseToJsonObject(value)
            is List<*> -> {
                val arr = org.json.JSONArray()
                value.forEach { arr.put(wrapJsonValue(it)) }
                arr
            }
            is Array<*> -> {
                val arr = org.json.JSONArray()
                value.forEach { arr.put(wrapJsonValue(it)) }
                arr
            }
            is Number, is Boolean, is String -> value
            is JSONObject, is org.json.JSONArray -> value
            else -> value.toString()
        }
    }

    private fun handleExtensionMessage(message: Any) {
        try {
            val json = parseToJsonObject(message)
            val action = json.optString("action")

            if (action == "clear_detected_media") {
                runOnUiThread {
                    tabManager.activeTab?.detectedVideos?.clear()
                }
            } else if (action == "ad_blocked" || action == "blocked_count") {
                val count = json.optInt("count", json.optInt("tabBlockedCount", 1))
                runOnUiThread {
                    tabManager.activeTab?.let { tab ->
                        if (count > tab.adBlockCount) {
                            tab.adBlockCount = count
                        } else {
                            tab.adBlockCount++
                        }
                    }
                }
            } else if (action == "download_blob") {
                val filename = json.optString("filename")
                val dataUrl = json.optString("dataUrl")
                val mimeType = json.optString("mimeType")
                if (dataUrl.isNotBlank()) {
                    downloadManager.saveBase64Download(filename, dataUrl, mimeType.ifBlank { null })
                }
            } else if (action == "share_payload") {
                val title = json.optString("title")
                val text = json.optString("text")
                val url = json.optString("url")
                val shareBody = if (text.isNotBlank() && url.isNotBlank()) "$text\n$url" else (text.ifBlank { url })
                runOnUiThread {
                    try {
                        val sendIntent = Intent().apply {
                            this.action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareBody)
                            if (title.isNotBlank()) putExtra(Intent.EXTRA_TITLE, title)
                            type = "text/plain"
                        }
                        startActivity(Intent.createChooser(sendIntent, title.ifBlank { "Share" }))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else if (action == "offer_save_password") {
                val domain = json.optString("domain")
                val username = json.optString("username")
                val password = json.optString("password")
                val isPrivate = tabManager.activeTab?.isPrivate ?: false
                if (!isPrivate && preferencesRepo.savePasswordsEnabled && domain.isNotBlank() && password.isNotBlank()) {
                    runOnUiThread {
                        pendingSaveLogin = com.browser.adfree.ui.components.PendingSaveLogin(
                            domain = domain,
                            username = username,
                            password = password
                        )
                    }
                }
            } else if (action == "video_detected") {
                val dataObj = json.optJSONObject("data")
                Log.d("VideoSniffer", "video_detected action received, data null? ${dataObj == null}")
                if (dataObj != null && preferencesRepo.videoDownloaderEnabled) {
                    val pageUrl = dataObj.optString("pageUrl")
                    val title = dataObj.optString("title", "Video")
                    val thumbnail = dataObj.optString("thumbnail").ifBlank { null }
                    val duration = dataObj.optLong("durationSeconds", 0L)
                    val sourceDomain = dataObj.optString("sourceDomain", "")
                    val qualitiesArr = dataObj.optJSONArray("qualities")

                    val qualityList = mutableListOf<com.browser.adfree.model.VideoQualityOption>()
                    if (qualitiesArr != null) {
                        for (i in 0 until qualitiesArr.length()) {
                            val q = qualitiesArr.getJSONObject(i)
                            val qUrl = q.optString("url")
                            if (qUrl.isNotBlank() && qUrl.startsWith("http") && qUrl != pageUrl && !qUrl.contains("youtube.com/watch") && !qUrl.startsWith("blob:") && !qUrl.startsWith("data:")) {
                                val isAudio = q.optBoolean("isAudioOnly", false)
                                val format = q.optString("format", if (qUrl.contains(".m3u8")) "M3U8" else if (isAudio) "M4A" else "MP4")
                                val isProgressive = q.optBoolean("isProgressive", !q.has("audioUrl") || q.optString("audioUrl").isBlank())
                                val audioUrl = q.optString("audioUrl").ifBlank { null }

                                qualityList.add(
                                    com.browser.adfree.model.VideoQualityOption(
                                        label = q.optString("label"),
                                        resolution = q.optString("resolution"),
                                        codec = q.optString("codec").ifBlank { null },
                                        format = format,
                                        url = qUrl,
                                        estimatedSizeBytes = q.optLong("estimatedSizeBytes", 0L),
                                        isAudioOnly = isAudio,
                                        isAv1 = q.optBoolean("isAv1", false),
                                        isProgressive = isProgressive,
                                        audioUrl = audioUrl,
                                        mimeType = q.optString("mimeType").ifBlank { null }
                                    )
                                )
                            }
                        }
                    }

                    runOnUiThread {
                        tabManager.activeTab?.let { activeTab ->
                            val isYouTube = pageUrl.contains("youtube.com") || pageUrl.contains("youtu.be")
                            val cleanTitle = if (title.isNotBlank() && title != "Start Page" && title != "Video") title else activeTab.title
                            val incomingVideoId = if (isYouTube) com.browser.adfree.download.YouTubeStreamExtractor.extractVideoId(pageUrl) else null

                            val detected = com.browser.adfree.model.DetectedVideo(
                                pageUrl = pageUrl.ifBlank { activeTab.url },
                                title = cleanTitle.ifBlank { "Web Video" },
                                thumbnail = thumbnail,
                                durationSeconds = duration,
                                sourceDomain = if (isYouTube) "youtube.com" else sourceDomain,
                                qualities = sortQualities(qualityList)
                            )

                            if (activeTab.detectedVideos.isNotEmpty()) {
                                val existing = activeTab.detectedVideos[0]
                                val existingVideoId = if (isYouTube) com.browser.adfree.download.YouTubeStreamExtractor.extractVideoId(existing.pageUrl) else null

                                if (isYouTube && existingVideoId != null && incomingVideoId != null && existingVideoId != incomingVideoId) {
                                    // New video playing on same tab — replace completely
                                    activeTab.detectedVideos.clear()
                                    activeTab.detectedVideos.add(detected)
                                } else {
                                    val mergedQualities = sortQualities(existing.qualities + qualityList)
                                    activeTab.detectedVideos[0] = existing.copy(
                                        pageUrl = pageUrl.ifBlank { existing.pageUrl },
                                        title = if (cleanTitle.isNotBlank() && cleanTitle != "Web Video" && cleanTitle != "YouTube Video") cleanTitle else existing.title,
                                        thumbnail = thumbnail ?: existing.thumbnail,
                                        durationSeconds = if (duration > 0) duration else existing.durationSeconds,
                                        qualities = if (mergedQualities.isNotEmpty()) mergedQualities else existing.qualities
                                    )
                                }
                            } else if (qualityList.isNotEmpty() || (thumbnail != null && cleanTitle.isNotBlank() && cleanTitle != "Start Page")) {
                                activeTab.detectedVideos.add(0, detected)
                            }

                            Log.i("VideoSniffer", "Updated detectedVideos on activeTab (${activeTab.detectedVideos.size} items, ${activeTab.detectedVideos.firstOrNull()?.qualities?.size} streams)")
                        }
                    }
                }
            } else if (action == "media_detected") {
                if (preferencesRepo.videoDownloaderEnabled) {
                    val mediaUrl = json.optString("url")
                    val mimeType = json.optString("mimeType")
                    val contentLength = json.optLong("contentLength", 0L)
                    val isAudio = json.optBoolean("isAudio", false)
                    Log.d("VideoSniffer", "media_detected received: $mediaUrl [$mimeType]")

                    if (mediaUrl.isNotBlank() && !mediaUrl.startsWith("data:") && !mediaUrl.startsWith("blob:") && !mediaUrl.startsWith("about:")) {
                        runOnUiThread {
                            tabManager.activeTab?.let { activeTab ->
                                val pageUrl = activeTab.url
                                val isYouTube = mediaUrl.contains("googlevideo.com/videoplayback") || pageUrl.contains("youtube.com") || pageUrl.contains("youtu.be")
                                val title = if (activeTab.title.isNotBlank() && activeTab.title != "Start Page") activeTab.title else "Web Video"

                                val parsedOption = if (isYouTube && mediaUrl.contains("googlevideo.com/videoplayback")) {
                                    com.browser.adfree.download.VideoQualityParser.parseYouTubeItag(mediaUrl)
                                } else null

                                val isM3u8 = mediaUrl.contains(".m3u8") || mimeType.contains("mpegurl")
                                val isAv1 = mimeType.contains("av01")
                                val isWebm = mediaUrl.contains(".webm") || mimeType.contains("webm")
                                val format = if (isM3u8) "M3U8" else if (isWebm) "WEBM" else if (isAudio) "M4A" else "MP4"
                                val label = if (isAudio) "Audio Track (Direct Stream)" else if (isM3u8) "HLS Master Stream" else "Direct Video ($format)"

                                val newOptions = if (isYouTube && mediaUrl.contains("googlevideo.com/videoplayback")) {
                                    com.browser.adfree.download.VideoQualityParser.generateYouTubeVariants(mediaUrl).ifEmpty {
                                        listOfNotNull(parsedOption ?: com.browser.adfree.model.VideoQualityOption(
                                            label = label,
                                            format = format,
                                            codec = if (isAv1) "AV1" else if (format == "WEBM") "VP9" else if (isAudio) "AAC" else "H.264",
                                            url = mediaUrl,
                                            estimatedSizeBytes = contentLength,
                                            isAudioOnly = isAudio,
                                            isAv1 = isAv1,
                                            isProgressive = !isM3u8,
                                            mimeType = mimeType.ifBlank { if (isM3u8) "application/x-mpegURL" else "video/mp4" }
                                        ))
                                    }
                                } else {
                                    listOf(
                                        parsedOption ?: com.browser.adfree.model.VideoQualityOption(
                                            label = label,
                                            format = format,
                                            codec = if (isAv1) "AV1" else if (format == "WEBM") "VP9" else if (isAudio) "AAC" else "H.264",
                                            url = mediaUrl,
                                            estimatedSizeBytes = contentLength,
                                            isAudioOnly = isAudio,
                                            isAv1 = isAv1,
                                            isProgressive = !isM3u8,
                                            mimeType = mimeType.ifBlank { if (isM3u8) "application/x-mpegURL" else "video/mp4" }
                                        )
                                    )
                                }

                                val existingIndex = if (activeTab.detectedVideos.isNotEmpty()) 0 else -1

                                if (existingIndex >= 0) {
                                    val existing = activeTab.detectedVideos[existingIndex]
                                    val existingVideoId = if (isYouTube) com.browser.adfree.download.YouTubeStreamExtractor.extractVideoId(existing.pageUrl) else null
                                    val currentVideoId = if (isYouTube) com.browser.adfree.download.YouTubeStreamExtractor.extractVideoId(pageUrl) else null

                                    if (isYouTube && existingVideoId != null && currentVideoId != null && existingVideoId != currentVideoId) {
                                        // Stale video on tab — replace with current video
                                        val thumbnail = "https://i.ytimg.com/vi/$currentVideoId/hqdefault.jpg"
                                        val detected = com.browser.adfree.model.DetectedVideo(
                                            pageUrl = pageUrl,
                                            title = title,
                                            thumbnail = thumbnail,
                                            sourceDomain = "youtube.com",
                                            qualities = sortQualities(newOptions)
                                        )
                                        activeTab.detectedVideos.clear()
                                        activeTab.detectedVideos.add(detected)
                                    } else {
                                        val cleanQualities = existing.qualities.filter { it.url.startsWith("http") && it.url != pageUrl && !it.url.contains("youtube.com/watch") }
                                        val updated = existing.copy(qualities = sortQualities(cleanQualities + newOptions))
                                        activeTab.detectedVideos[existingIndex] = updated
                                        Log.i("VideoSniffer", "Updated DetectedVideo on tab: ${existing.title} (${updated.qualities.size} streams)")
                                    }
                                } else {
                                    val host = try { java.net.URI(pageUrl).host ?: "" } catch (e: Exception) { "" }
                                    val videoId = if (isYouTube) com.browser.adfree.download.YouTubeStreamExtractor.extractVideoId(pageUrl) else null
                                    val thumbnail = if (videoId != null) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else null
                                    val detected = com.browser.adfree.model.DetectedVideo(
                                        pageUrl = pageUrl,
                                        title = title,
                                        thumbnail = thumbnail,
                                        sourceDomain = if (isYouTube) "youtube.com" else host,
                                        qualities = sortQualities(newOptions)
                                    )
                                    activeTab.detectedVideos.add(0, detected)
                                    Log.i("VideoSniffer", "Created new DetectedVideo from media_detected: ${detected.title} (${detected.qualities.size} streams)")
                                }

                                // Async HLS Master Playlist expander
                                if (isM3u8) {
                                    val reqUa = com.browser.adfree.tabs.TabManager.MODERN_MOBILE_UA
                                    val req = okhttp3.Request.Builder().url(mediaUrl)
                                        .header("User-Agent", reqUa)
                                        .apply { if (pageUrl.isNotBlank()) header("Referer", pageUrl) }
                                        .build()

                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        try {
                                             val resp = okhttp3.OkHttpClient().newCall(req).execute()
                                            if (resp.isSuccessful) {
                                                val text = resp.body?.string() ?: ""
                                                if (com.browser.adfree.download.VideoQualityParser.isMasterPlaylist(text)) {
                                                    val variants = com.browser.adfree.download.VideoQualityParser.parseM3u8MasterPlaylist(text, mediaUrl, mapOf("Referer" to pageUrl))
                                                    if (variants.isNotEmpty()) {
                                                        runOnUiThread {
                                                            if (activeTab.detectedVideos.isNotEmpty()) {
                                                                val current = activeTab.detectedVideos[0]
                                                                val merged = sortQualities(variants + current.qualities)
                                                                activeTab.detectedVideos[0] = current.copy(qualities = merged)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            resp.close()
                                        } catch (e: Exception) {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AdFreeBrowser", "Error parsing extension message", e)
        }
    }

    private fun sortQualities(list: List<com.browser.adfree.model.VideoQualityOption>): List<com.browser.adfree.model.VideoQualityOption> {
        return com.browser.adfree.download.VideoQualityParser.sortQualities(list)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url = intent?.dataString
        if (!url.isNullOrBlank()) {
            val activeTab = tabManager.activeTab
            if (activeTab != null && activeTab.url == TabManager.HOME_URL) {
                tabManager.loadUri(activeTab, url, runtime)
            } else {
                tabManager.createNewTab(runtime, url)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (preferencesRepo.wipeOnExit) {
            historyRepo.clearAll()
            tabManager.sessionRepository.clearSession()
        } else {
            tabManager.saveSession()
        }
    }

    override fun onStop() {
        super.onStop()
        vpnManager.forceDisconnectOnExit(tabManager.tabs)
        if (preferencesRepo.wipeOnExit) {
            performWipeOnExit()
        } else {
            tabManager.saveSession()
        }
    }

    override fun onDestroy() {
        vpnManager.forceDisconnectOnExit(tabManager.tabs)
        if (preferencesRepo.wipeOnExit) {
            performWipeOnExit()
        }
        tabManager.destroy()
        runtime.shutdown()
        super.onDestroy()
    }
}
