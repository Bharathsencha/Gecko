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
import androidx.fragment.app.FragmentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import com.browser.adfree.vpn.VpnManager
import android.os.Build
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.mozilla.geckoview.*

class MainActivity : FragmentActivity() {

    private lateinit var runtime: GeckoRuntime
    private lateinit var tabManager: TabManager
    private lateinit var downloadManager: BrowserDownloadManager
    private lateinit var preferencesRepo: BrowserPreferencesRepository
    private lateinit var passwordRepo: SecurePasswordRepository
    private lateinit var historyRepo: HistoryRepository
    private lateinit var favoritesRepo: FavoritesRepository
    private lateinit var vpnManager: VpnManager
    private lateinit var shieldsRepo: com.browser.adfree.shields.ShieldsRepository
    private lateinit var trackerStats: com.browser.adfree.storage.TrackerStatsRepository

    private var extensionPort: WebExtension.Port? = null
    private var appBridgeExtension: WebExtension? = null
    @Volatile private var latestExtensionCount = 0
    @Volatile private var countUpdateScheduled = false
    private var pendingSaveLogin by mutableStateOf<PendingSaveLogin?>(null)

    /** Location, camera and microphone for a site the user allowed. */
    private var sitePermissionCallback: ((Boolean) -> Unit)? = null
    private val sitePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val cb = sitePermissionCallback
        sitePermissionCallback = null
        cb?.invoke(results.isNotEmpty() && results.values.all { it })
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.i("AdFreeBrowser", "Legacy storage permission granted; downloads can write to Downloads")
        } else {
            Log.w("AdFreeBrowser", "Legacy storage permission denied; downloads will fail on API 28 and below")
        }
    }

    /**
     * API 26-28 still requires a runtime grant for writing to the public
     * Downloads folder. From API 29 scoped storage allows it without a grant.
     */
    private fun checkAndRequestLegacyStoragePermission() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) return
        val permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            storagePermissionLauncher.launch(permission)
        }
    }

    /** Pages scroll at the display's top refresh rate (e.g. 120 Hz) instead of whatever the system picks. */
    private fun requestHighestRefreshRate() {
        try {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else @Suppress("DEPRECATION") windowManager.defaultDisplay
            val current = display?.mode ?: return
            val best = display.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
        } catch (e: Exception) {
            Log.w("AdFreeBrowser", "Could not request refresh rate: ${e.message}")
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
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

        requestHighestRefreshRate()

        // Request notification permission on Android 13+ for background download progress & alerts
        checkAndRequestNotificationPermission()
        checkAndRequestLegacyStoragePermission()

        // 1. Initialize Repositories & Managers
        preferencesRepo = BrowserPreferencesRepository(this)
        shieldsRepo = com.browser.adfree.shields.ShieldsRepository(this)
        passwordRepo = SecurePasswordRepository(this)
        historyRepo = HistoryRepository(this)
        favoritesRepo = FavoritesRepository(this)
        trackerStats = com.browser.adfree.storage.TrackerStatsRepository(this)
        com.browser.adfree.model.TabContainer.init(this)
        com.browser.adfree.storage.SitePermissions.init(this)
        historyRepo.onCleared = { if (preferencesRepo.resetStatsWithHistory) trackerStats.reset() }
        downloadManager = BrowserDownloadManager(this)
        vpnManager = VpnManager(this, preferencesRepo)

        tabManager = TabManager(this).apply {
            this.historyRepository = historyRepo
            this.preferencesRepository = preferencesRepo
            this.shieldsRepository = shieldsRepo
            this.trackerStats = this@MainActivity.trackerStats
            this.downloadManager = downloadManager
            this.onCookiesRequested = { url -> requestCookiesFor(url) }
            this.onAndroidPermissions = { perms, done ->
                val missing = perms.filter {
                    androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity, it) !=
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                if (missing.isEmpty()) done(true)
                else {
                    sitePermissionCallback?.invoke(false)
                    sitePermissionCallback = done
                    sitePermissionLauncher.launch(missing.toTypedArray())
                }
            }
        }

        // Initialize UniversalVideoExtractor (yt-dlp engine) in background
        lifecycleScope.launch(Dispatchers.IO) {
            com.browser.adfree.download.UniversalVideoExtractor.init(applicationContext)
        }

        // VPN route -> extension proxy.onRequest
        vpnManager.onProxyChanged = { enabled, host, port ->
            postToExtension(JSONObject().apply {
                put("action", "set_vpn_config")
                put("enabled", enabled)
                put("host", host)
                put("port", port)
                put("type", "socks")
            })
        }

        // 2. Gecko prefs. GeckoView only reads arbitrary prefs from a YAML config
        //    file; Bundle extras and user.js are ignored by it.
        val configFile = java.io.File(filesDir, "geckoview-config.yaml")
        try {
            assets.open("gecko/geckoview-config.yaml").use { input ->
                configFile.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            Log.w("AdFreeBrowser", "Failed to copy Gecko config: ${e.message}")
        }

        val runtimeSettings = GeckoRuntimeSettings.Builder()
            .configFilePath(configFile.absolutePath)
            .globalPrivacyControlEnabled(true)
            .contentBlocking(
                ContentBlocking.Settings.Builder()
                    .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
                    .antiTracking(ContentBlocking.AntiTracking.STRICT)
                    .cookieBehavior(preferencesRepo.cookiePolicy.geckoValue)
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
            override fun onOptionalPrompt(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollectionPermissions: Array<String>
            ): GeckoResult<AllowOrDeny>? {
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }
        }

        com.browser.adfree.ui.components.applyDns(runtime, preferencesRepo)

        // 3a. uBlock Origin 1.75.0 (YouTube is exempt, see its js/start.js)
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/extensions/ublock_origin/",
            "uBlock0@raymondhill.net"
        ).accept(
            { ext ->
                if (ext != null) {
                    Log.i("AdFreeBrowser", "uBlock Origin loaded")
                    allowInPrivateTabs(ext)
                }
            },
            { error -> Log.e("AdFreeBrowser", "uBlock Origin failed to load", error) }
        )

        // 3b. App bridge: proxy routing, AI Overview removal, page commands, media detection
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/extensions/aifree/",
            "aifree-embedded@browser.adfree"
        ).accept(
            { extension ->
                if (extension != null) {
                    Log.i("AdFreeBrowser", "App bridge extension loaded")
                    appBridgeExtension = extension
                    allowInPrivateTabs(extension)
                    tabManager.attachPageBridge(extension)

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
            { error -> Log.e("AdFreeBrowser", "App bridge extension failed to load", error) }
        )

        // 4. Restore Session / Create Initial Tab according to Startup Behavior
        tabManager.restoreSession(runtime, preferencesRepo.startupBehavior)
        tabManager.closeInactiveTabs(preferencesRepo.autoCloseTabsDays, runtime)
        intent?.let { urlFromIntent(it) }?.takeIf { it.isNotBlank() }?.let { openIncomingUrl(it) }

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
                    if (preferencesRepo.wipeOnExit) {
                        performWipeOnExit()
                    }
                    finish()
                }
            }
        })

        // 6. Set Compose UI
        setContent {
            // Pages follow the app's theme choice, not only the system setting.
            val darkTheme = when (preferencesRepo.themeMode) {
                com.browser.adfree.storage.AppThemeMode.LIGHT -> false
                com.browser.adfree.storage.AppThemeMode.DARK -> true
                com.browser.adfree.storage.AppThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            androidx.compose.runtime.LaunchedEffect(darkTheme) {
                runtime.settings.preferredColorScheme =
                    if (darkTheme) GeckoRuntimeSettings.COLOR_SCHEME_DARK else GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
            }
            com.browser.adfree.ui.PrivateGeckoView.enabled = preferencesRepo.privateKeyboard
            com.browser.adfree.ui.PrivateKeyboard(enabled = preferencesRepo.privateKeyboard || tabManager.activeTab?.isPrivate == true) {
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
                    trackerStats = trackerStats,
                    shieldsRepo = shieldsRepo,
                    vpnManager = vpnManager,
                    onExtensionConfigChanged = { syncExtensionConfig() },
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

    private fun postToExtension(payload: JSONObject) {
        try {
            extensionPort?.postMessage(payload)
        } catch (e: Exception) {
            Log.w("AdFreeBrowser", "Extension message failed: ${e.message}")
        }
    }

    /** Pushes every app setting the extension enforces; runs on (re)connect and on change. */
    fun syncExtensionConfig() {
        vpnManager.resendRoute()
        postToExtension(JSONObject().apply {
            put("action", "set_ad_blocking")
            put("enabled", preferencesRepo.adBlockingEnabled)
        })
        postToExtension(JSONObject().apply {
            put("action", "set_content_flags")
            put("blockAi", preferencesRepo.blockAiOverviews)
            put("cosmetic", preferencesRepo.cosmeticBlockingEnabled)
            put("dark", preferencesRepo.darkWebDefault)
        })
    }

    private fun allowInPrivateTabs(extension: WebExtension) {
        if (extension.metaData.allowedInPrivateBrowsing) return
        runtime.webExtensionController.setAllowedInPrivateBrowsing(extension, true).accept(
            { Log.i("AdFreeBrowser", "${extension.id} allowed in private tabs") },
            { error -> Log.w("AdFreeBrowser", "Could not allow ${extension.id} in private tabs", error) }
        )
    }

    /**
     * Asks the WebExtension for the cookies of [pageUrl] so downloads from that
     * origin carry the session cookie. GeckoView 122 exposes no
     * `GeckoSession.CookieManager`, so the extension is the only cookie source.
     */
    private fun requestCookiesFor(pageUrl: String?) {
        val url = pageUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return
        if (downloadManager.hasCachedCookies(url)) return
        try {
            extensionPort?.postMessage(JSONObject().apply {
                put("action", "get_cookies")
                put("url", url)
            })
        } catch (e: Exception) {
            Log.w("AdFreeBrowser", "Cookie request failed: ${e.message}")
        }
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

            if (action == "vpn_error") {
                runOnUiThread { vpnManager.reportRouteError(json.optString("message")) }
            } else if (action == "clear_detected_media") {
                runOnUiThread {
                    tabManager.activeTab?.detectedVideos?.clear()
                }
            } else if (action == "cookies_result") {
                val url = json.optString("url")
                val cookieHeader = json.optString("cookieHeader")
                if (url.isNotBlank() && cookieHeader.isNotBlank()) {
                    downloadManager.cacheCookies(url, cookieHeader)
                }
            } else if (action == "blocked_count") {
                // Counted per page by the extension; a navigation resets it to 0.
                // Only the latest value matters, so publish it at most twice a second.
                latestExtensionCount = json.optInt("count", 0)
                if (!json.optBoolean("incognito", false)) {
                    json.optString("host").takeIf { it.isNotBlank() }?.let { trackerStats.record(it) }
                }
                if (json.optInt("count", 0) == 0 || !countUpdateScheduled) {
                    countUpdateScheduled = true
                    window.decorView.postDelayed({
                        countUpdateScheduled = false
                        tabManager.activeTab?.extensionBlockCount = latestExtensionCount
                    }, if (latestExtensionCount == 0) 0 else 500)
                }
            } else if (action == "download_blob") {
                val filename = json.optString("filename")
                val dataUrl = json.optString("dataUrl")
                val mimeType = json.optString("mimeType")
                if (dataUrl.isNotBlank()) {
                    downloadManager.saveBase64Download(filename, dataUrl, mimeType.ifBlank { null })
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
                    val pageHeaders = dataObj.optJSONObject("headers")?.let { h ->
                        buildMap {
                            h.keys().forEach { k -> h.optString(k).takeIf { v -> v.isNotBlank() }?.let { put(k, it) } }
                        }
                    } ?: emptyMap()
                    if (qualitiesArr != null) {
                        for (i in 0 until qualitiesArr.length()) {
                            val q = qualitiesArr.getJSONObject(i)
                            val qUrl = q.optString("url")
                            if (qUrl.isNotBlank() && qUrl.startsWith("http") && qUrl != pageUrl && !qUrl.contains("youtube.com/watch") && !qUrl.startsWith("blob:") && !qUrl.startsWith("data:")) {
                                val isAudio = q.optBoolean("isAudioOnly", false)
                                val format = q.optString("format", if (qUrl.contains(".m3u8")) "M3U8" else if (isAudio) "M4A" else "MP4")
                                val isProgressive = q.optBoolean("isProgressive", !q.has("audioUrl") || q.optString("audioUrl").isBlank())
                                val audioUrl = q.optString("audioUrl").ifBlank { null }
                                val qualityHeaders = q.optJSONObject("headers")?.let { h ->
                                    buildMap {
                                        h.keys().forEach { k -> h.optString(k).takeIf { v -> v.isNotBlank() }?.let { put(k, it) } }
                                    }
                                } ?: pageHeaders

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
                                        mimeType = q.optString("mimeType").ifBlank { null },
                                        headers = qualityHeaders
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
                                } else if (!isYouTube && !samePage(existing.pageUrl, detected.pageUrl)) {
                                    // The payload came from another page (e.g. the listing the
                                    // user started on). Swapping keeps title/page accurate.
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

                            // Enforce background extraction for robust high quality and audio formats
                            tabManager.checkForVideos(activeTab, activeTab.url)

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
                    Log.d("VideoSniffer", "media_detected received [$mimeType]")

                    // googlevideo replies to sniffed range probes with protocol
                    // fragments rather than media; the YouTube extractor already
                    // lists every real stream for this page.
                    val isGooglevideoStream = mediaUrl.contains("googlevideo.com")

                    if (mediaUrl.isNotBlank() && !isGooglevideoStream && !mediaUrl.startsWith("data:") && !mediaUrl.startsWith("blob:") && !mediaUrl.startsWith("about:")) {
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
                                // Sites commonly hand out a master playlist plus per-height
                                // variants. The height comes from the URL when present; without
                                // one we are looking at the master, which is the entry worth
                                // downloading because it carries the whole variant list (and any
                                // separate audio rendition).
                                val hlsHeight = Regex("(\\d{3,4})p", RegexOption.IGNORE_CASE).find(mediaUrl)?.value
                                val label = when {
                                    isAudio -> "Audio Track (Direct Stream)"
                                    isM3u8 && hlsHeight != null -> "HLS $hlsHeight Stream"
                                    isM3u8 -> "HLS (auto quality)"
                                    else -> "Direct Video ($format)"
                                }

                                val newOptions = listOfNotNull(
                                    parsedOption ?: com.browser.adfree.model.VideoQualityOption(
                                        label = label,
                                        resolution = if (isM3u8) (hlsHeight ?: "") else "",
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
                                    } else if (!samePage(existing.pageUrl, pageUrl)) {
                                        // Stream belongs to a different document than the entry we
                                        // already hold (listing page vs the actual video page).
                                        // Replacing keeps the sheet titled with the real video.
                                        val host = try { java.net.URI(pageUrl).host ?: "" } catch (e: Exception) { "" }
                                        val videoId = if (isYouTube) com.browser.adfree.download.YouTubeStreamExtractor.extractVideoId(pageUrl) else null
                                        val replacement = com.browser.adfree.model.DetectedVideo(
                                            pageUrl = pageUrl,
                                            title = title,
                                            thumbnail = if (videoId != null) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else null,
                                            sourceDomain = if (isYouTube) "youtube.com" else host,
                                            qualities = sortQualities(newOptions)
                                        )
                                        activeTab.detectedVideos.clear()
                                        activeTab.detectedVideos.add(replacement)
                                        Log.i("VideoSniffer", "Replaced stale entry (${replacement.qualities.size} streams)")
                                    } else {
                                        val cleanQualities = existing.qualities.filter { it.url.startsWith("http") && it.url != pageUrl && !it.url.contains("youtube.com/watch") }
                                        val updated = existing.copy(qualities = sortQualities(cleanQualities + newOptions))
                                        activeTab.detectedVideos[existingIndex] = updated
                                        Log.i("VideoSniffer", "Updated DetectedVideo on tab (${updated.qualities.size} streams)")
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
                                    Log.i("VideoSniffer", "Created new DetectedVideo from media_detected (${detected.qualities.size} streams)")
                                }

                                // Trigger rich stream extraction for full quality and audio options
                                tabManager.checkForVideos(activeTab, activeTab.url)

                                // Async HLS Master Playlist expander
                                if (isM3u8) {
                                    val reqUa = com.browser.adfree.tabs.TabManager.MODERN_MOBILE_UA
                                    val req = okhttp3.Request.Builder().url(mediaUrl)
                                        .header("User-Agent", reqUa)
                                        .apply { if (pageUrl.isNotBlank()) header("Referer", pageUrl) }
                                        .build()

                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        try {
                                             val resp = okhttp3.OkHttpClient.Builder().proxySelector(com.browser.adfree.vpn.NetworkRoute).build().newCall(req).execute()
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

    /**
     * True when [a] and [b] point at the same document (same host + path).
     * Detection coming from a listing page must not be blended into the entry
     * for the video page the user is actually on — otherwise the sheet keeps
     * showing the site's home page instead of the video.
     */
    private fun samePage(a: String, b: String): Boolean =
        com.browser.adfree.download.VideoQualityParser.samePage(a, b)

    private fun sortQualities(list: List<com.browser.adfree.model.VideoQualityOption>): List<com.browser.adfree.model.VideoQualityOption> {
        return com.browser.adfree.download.VideoQualityParser.sortQualities(list)
    }

    /**
     * A link, a file another app opened with Gecko, or text shared to it.
     * Other apps never get to run script, open internal about: pages or read
     * this app's own private files.
     */
    private fun urlFromIntent(intent: Intent): String? = rawUrlFromIntent(intent)?.trim()?.takeIf { raw ->
        val lower = raw.lowercase()
        !lower.startsWith("javascript:") && !lower.startsWith("about:") && !lower.startsWith("moz-extension:") &&
            !lower.startsWith("resource:") && !lower.startsWith("chrome:") &&
            !(lower.startsWith("file:") && (lower.contains("/data/") || lower.contains(packageName)))
    }

    private fun rawUrlFromIntent(intent: Intent): String? = when (intent.action) {
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.let { text ->
            Regex("https?://\\S+").find(text)?.value ?: text
        }
        Intent.ACTION_WEB_SEARCH -> intent.getStringExtra(android.app.SearchManager.QUERY)
        else -> intent.dataString
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url = urlFromIntent(intent)
        if (!url.isNullOrBlank()) openIncomingUrl(url)
    }

    /** Opens a link another app handed over: in the blank tab in view if there is one, else a new tab. */
    private fun openIncomingUrl(url: String) {
        val activeTab = tabManager.activeTab
        val inPrivate = preferencesRepo.openExternalInPrivate
        when {
            activeTab != null && activeTab.url == TabManager.HOME_URL && (!inPrivate || activeTab.isPrivate) ->
                tabManager.loadUri(activeTab, url, runtime)
            else -> tabManager.createNewTab(runtime, url, isPrivate = inPrivate)
        }
    }

    override fun onStart() {
        super.onStart()
        // A tab can go stale while the app sits in the background.
        tabManager.closeInactiveTabs(preferencesRepo.autoCloseTabsDays, runtime)
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
        if (preferencesRepo.wipeOnExit) {
            performWipeOnExit()
        } else {
            tabManager.saveSession()
        }
    }

    override fun onDestroy() {
        vpnManager.shutdown()
        if (preferencesRepo.wipeOnExit) {
            performWipeOnExit()
        }
        tabManager.destroy()
        runtime.shutdown()
        super.onDestroy()
    }
}
