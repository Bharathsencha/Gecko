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

package com.browser.adfree.tabs

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import android.net.Uri
import com.browser.adfree.download.BrowserDownloadManager
import com.browser.adfree.model.SearchEngine
import com.browser.adfree.model.StartupBehavior
import com.browser.adfree.model.TabModel
import com.browser.adfree.storage.BrowserPreferencesRepository
import com.browser.adfree.storage.HistoryRepository
import com.browser.adfree.storage.TabSessionRepository
import android.util.Log
import com.browser.adfree.download.YouTubeStreamExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.MediaSession
import org.mozilla.geckoview.WebExtension
import org.json.JSONObject
import org.mozilla.geckoview.WebResponse
import java.net.URLEncoder

class TabManager(private val context: Context) {
    val tabs = mutableStateListOf<TabModel>()
    val activeTabIndex = mutableIntStateOf(0)

    val activeTab: TabModel?
        get() = if (tabs.isNotEmpty() && activeTabIndex.intValue in tabs.indices) {
            tabs[activeTabIndex.intValue]
        } else null

    var historyRepository: HistoryRepository? = null
    var preferencesRepository: BrowserPreferencesRepository? = null
    var shieldsRepository: com.browser.adfree.shields.ShieldsRepository? = null
    var downloadManager: BrowserDownloadManager? = null
    /** Invoked on the main thread once a page finishes loading. */
    var onCookiesRequested: ((String) -> Unit)? = null
    val sessionRepository = TabSessionRepository(context)

    var onInsecureHttpPrompt: ((url: String, onProceed: () -> Unit) -> Unit)? = null
    val allowedHttpUrls = mutableSetOf<String>()

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Bridge extension, set once it loads; each session gets a page port through it. */
    private var pageBridge: WebExtension? = null

    companion object {
        const val HOME_URL = "about:home"

        /**
         * The UA resistFingerprinting reports for this engine. Pages get it from
         * Gecko itself; native requests (downloads, playlist fetches) send the
         * same string so the two never disagree. A Chrome UA on a Gecko engine
         * is a fingerprint on its own.
         */
        const val MODERN_MOBILE_UA = "Mozilla/5.0 (Android 10; Mobile; rv:122.0) Gecko/122.0 Firefox/122.0"
        const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:122.0) Gecko/20100101 Firefox/122.0"

        private val YOUTUBE_HOST = Regex("(^|\\.)(youtube\\.com|youtube-nocookie\\.com|youtu\\.be)$", RegexOption.IGNORE_CASE)

        /** YouTube runs without request blocking; content.js fast-forwards its ads instead. */
        fun isYouTube(url: String): Boolean {
            val host = try { Uri.parse(url).host ?: "" } catch (e: Exception) { "" }
            return YOUTUBE_HOST.containsMatchIn(host)
        }
    }

    private fun applyUserAgent(tab: TabModel, session: GeckoSession) {
        // Mobile: no override, RFP supplies the uniform UA.
        session.settings.userAgentOverride = if (tab.isDesktopMode) DESKTOP_UA else null
        session.settings.userAgentMode = if (tab.isDesktopMode) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        session.settings.viewportMode = if (tab.isDesktopMode) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
    }

    private fun trackingProtectionFor(tab: TabModel, url: String): Boolean {
        if (isYouTube(url)) return false
        if (tab.isPrivate) return true
        return shieldsRepository?.getConfigForUrl(url)?.shieldsEnabled ?: (preferencesRepository?.adBlockingEnabled ?: true)
    }

    fun attachPageBridge(extension: WebExtension) {
        pageBridge = extension
        tabs.forEach { bindPagePort(it, it.session) }
    }

    private fun bindPagePort(tab: TabModel, session: GeckoSession) {
        val extension = pageBridge ?: return
        session.webExtensionController.setMessageDelegate(extension, object : WebExtension.MessageDelegate {
            override fun onConnect(port: WebExtension.Port) {
                tab.pagePort = port
                port.setDelegate(object : WebExtension.PortDelegate {
                    override fun onPortMessage(message: Any, port: WebExtension.Port) {
                        val json = message as? JSONObject ?: return
                        mainHandler.post { handlePageMessage(tab, json) }
                    }

                    override fun onDisconnect(port: WebExtension.Port) {
                        if (tab.pagePort === port) tab.pagePort = null
                    }
                })
            }
        }, "browser_app")
    }

    private fun handlePageMessage(tab: TabModel, json: JSONObject) {
        when (json.optString("action")) {
            "page_ready" -> {
                // Per-page state is lost on navigation; re-apply what the user chose.
                if (tab.zoomLevel != 100) sendPageCommand(tab, "set_zoom") { put("percent", tab.zoomLevel) }
                val siteDark = shieldsRepository?.getConfigForUrl(tab.url)?.darkWebEnabled == true
                if (tab.isDarkWeb || siteDark) {
                    tab.isDarkWeb = true
                    sendPageCommand(tab, "set_dark") { put("enabled", true) }
                }
            }
            "article_result" -> {
                tab.isExtractingArticle = false
                val article = json.optJSONObject("article")
                if (article != null && article.optBoolean("isArticle", false)) {
                    tab.articleData = com.browser.adfree.model.ArticleData(
                        title = article.optString("title").ifBlank { tab.title },
                        byline = article.optString("byline").ifBlank { null },
                        contentHtml = article.optString("contentHtml"),
                        isArticle = true
                    )
                    tab.isReaderMode = true
                } else {
                    onReaderUnavailable?.invoke()
                }
            }
        }
    }

    /** Shown when a page has no readable article or no page bridge yet. */
    var onReaderUnavailable: (() -> Unit)? = null

    private fun sendPageCommand(tab: TabModel, cmd: String, args: JSONObject.() -> Unit = {}): Boolean {
        val port = tab.pagePort ?: return false
        return try {
            port.postMessage(JSONObject().apply { put("cmd", cmd); args() })
            true
        } catch (e: Exception) {
            Log.w("TabManager", "Page command $cmd failed: ${e.message}")
            false
        }
    }

    fun requestReaderMode(tab: TabModel) {
        if (tab.isReaderMode) {
            tab.isReaderMode = false
            return
        }
        if (sendPageCommand(tab, "extract_article")) {
            tab.isExtractingArticle = true
        } else {
            onReaderUnavailable?.invoke()
        }
    }

    private fun buildSession(tab: TabModel): GeckoSession {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(tab.isPrivate)
            .build()
        val session = GeckoSession(settings)

        applyUserAgent(tab, session)

        val siteConfig = shieldsRepository?.getConfigForUrl(tab.url)
        session.settings.useTrackingProtection = trackingProtectionFor(tab, tab.url)
        session.settings.suspendMediaWhenInactive = !(siteConfig?.backgroundMediaEnabled ?: true)
        bindPagePort(tab, session)

        // Media Session Delegate for Android Foreground Service & Lockscreen playback
        session.mediaSessionDelegate = object : MediaSession.Delegate {
            override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
                // Private tabs never put the page title or address on the lock screen.
                if (tab.isPrivate) {
                    com.browser.adfree.media.MediaPlaybackService.start(context, "Private tab", null)
                } else {
                    com.browser.adfree.media.MediaPlaybackService.start(context, tab.title, tab.url)
                }
                checkForVideos(tab, tab.url)
            }
            override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
                com.browser.adfree.media.MediaPlaybackService.stop(context)
            }
            override fun onMetadata(session: GeckoSession, mediaSession: MediaSession, meta: MediaSession.Metadata) {
                checkForVideos(tab, tab.url)
            }
        }

        // Content Blocking / Ad & Tracker Shield Delegate
        session.contentBlockingDelegate = object : org.mozilla.geckoview.ContentBlocking.Delegate {
            // Counted off the UI, published at most twice a second: pages block
            // dozens of requests while scrolling and each UI update stole time
            // from Gecko's touch handling on the main thread.
            private val pending = java.util.concurrent.atomic.AtomicInteger(0)
            private val scheduled = java.util.concurrent.atomic.AtomicBoolean(false)

            override fun onContentBlocked(
                session: GeckoSession,
                event: org.mozilla.geckoview.ContentBlocking.BlockEvent
            ) {
                pending.incrementAndGet()
                if (scheduled.compareAndSet(false, true)) {
                    mainHandler.postDelayed({
                        scheduled.set(false)
                        tab.trackerBlockCount += pending.getAndSet(0)
                    }, 500)
                }
            }
        }

        // Wire Session Delegates
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                mainHandler.post {
                    if (url != "about:blank" && url != HOME_URL) {
                        tab.url = url
                    }
                    // Cookie prefetch runs at navigation start so the extractor has a
                    // session cookie by the time onPageStop tries to pull stream URLs.
                    if (!tab.isPrivate && (url.startsWith("http://") || url.startsWith("https://"))) {
                        onCookiesRequested?.invoke(url)
                    }
                    tab.isLoading = true
                    tab.progress = 10
                    tab.trackerBlockCount = 0
                    tab.extensionBlockCount = 0
                    tab.isExtractingArticle = false
                    tab.isReaderMode = false
                    tab.articleData = null
                    val oldVideoId = YouTubeStreamExtractor.extractVideoId(tab.url)
                    val newVideoId = YouTubeStreamExtractor.extractVideoId(url)
                    val isSameYouTubeVideo = (oldVideoId != null && newVideoId != null && oldVideoId == newVideoId)
                    val isSameUrl = tab.url.equals(url, ignoreCase = true)

                    if (!isSameYouTubeVideo && !isSameUrl) {
                        tab.detectedVideos.clear()
                    }

                    // Per-site shield settings
                    val currentSiteConfig = shieldsRepository?.getConfigForUrl(url)
                    session.settings.useTrackingProtection = trackingProtectionFor(tab, url)
                    session.settings.suspendMediaWhenInactive = !(currentSiteConfig?.backgroundMediaEnabled ?: true)
                    val siteDesktop = currentSiteConfig?.desktopModeEnabled == true
                    if (siteDesktop && !tab.isDesktopMode) {
                        tab.isDesktopMode = true
                        applyUserAgent(tab, session)
                    }
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                mainHandler.post {
                    tab.progress = progress
                    if (progress >= 100) {
                        tab.isLoading = false
                        // Prefetch cookies once the page settled so a later download of a
                        // discovered media URL can authenticate without an extra round trip.
                        if (!tab.isPrivate) onCookiesRequested?.invoke(tab.url)
                    }
                }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                mainHandler.post {
                    tab.isLoading = false
                    tab.progress = 100
                    if (success && !tab.isPrivate && tab.url != HOME_URL && !tab.url.startsWith("about:")) {
                        historyRepository?.addHistory(tab.title, tab.url)
                    }
                    if (!tab.isPrivate) {
                        saveSession()
                    }
                    checkForVideos(tab, tab.url)
                }
            }

            override fun onSecurityChange(
                session: GeckoSession,
                securityInfo: GeckoSession.ProgressDelegate.SecurityInformation
            ) {}
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                title?.let { nonNullTitle ->
                    mainHandler.post {
                        if (nonNullTitle.isNotBlank() && nonNullTitle != "about:blank" && tab.url != HOME_URL) {
                            tab.title = nonNullTitle
                            if (!tab.isPrivate) {
                                historyRepository?.addHistory(tab.title, tab.url)
                                saveSession()
                            }
                        }
                    }
                }
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                mainHandler.post {
                    downloadManager?.startDownload(url = response.uri)
                }
            }
        }

        session.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onSharePrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.SharePrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                mainHandler.post {
                    try {
                        val sendIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, prompt.text ?: prompt.uri ?: "")
                            prompt.title?.let { putExtra(android.content.Intent.EXTRA_TITLE, it) }
                            type = "text/plain"
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val chooser = android.content.Intent.createChooser(sendIntent, prompt.title ?: "Share").apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(chooser)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                return GeckoResult.fromValue(prompt.confirm(GeckoSession.PromptDelegate.SharePrompt.Result.SUCCESS))
            }
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                val uri = request.uri
                val httpsOnly = preferencesRepository?.httpsOnlyMode ?: true

                // Direct file download extension interceptor
                val lowerUri = uri.lowercase().substringBefore("?")
                val isDownloadable = lowerUri.endsWith(".apk") || lowerUri.endsWith(".pdf") ||
                        lowerUri.endsWith(".zip") || lowerUri.endsWith(".rar") ||
                        lowerUri.endsWith(".7z") || lowerUri.endsWith(".tar.gz") ||
                        lowerUri.endsWith(".dmg") || lowerUri.endsWith(".iso") ||
                        lowerUri.endsWith(".exe") || lowerUri.endsWith(".epub") ||
                        lowerUri.endsWith(".csv") || lowerUri.endsWith(".json")

                if (isDownloadable && (uri.startsWith("http://") || uri.startsWith("https://"))) {
                    mainHandler.post {
                        downloadManager?.startDownload(url = uri)
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                // De-AMP Interception: Auto-bypass Google AMP wrappers to load canonical publisher page
                val deAmped = com.browser.adfree.model.UrlSanitizer.deAmp(uri)
                if (deAmped != uri) {
                    mainHandler.post {
                        session.loadUri(deAmped)
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                if (httpsOnly && uri.startsWith("http://")) {
                    val host = try { Uri.parse(uri).host ?: "" } catch (e: Exception) { "" }
                    val isExempt = allowedHttpUrls.contains(uri) || (host.isNotEmpty() && allowedHttpUrls.contains(host))

                    if (!isExempt) {
                        mainHandler.post {
                            onInsecureHttpPrompt?.invoke(uri) {
                                allowedHttpUrls.add(uri)
                                if (host.isNotEmpty()) allowedHttpUrls.add(host)
                                session.loadUri(uri)
                            }
                        }
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                mainHandler.post {
                    tab.canGoBack = canGoBack
                }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                mainHandler.post {
                    tab.canGoForward = canGoForward
                }
            }

            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                url?.let {
                    mainHandler.post {
                        if (it != "about:blank" && it != HOME_URL) {
                            val oldVideoId = YouTubeStreamExtractor.extractVideoId(tab.url)
                            val newVideoId = YouTubeStreamExtractor.extractVideoId(it)
                            val isSameYouTubeVideo = (oldVideoId != null && newVideoId != null && oldVideoId == newVideoId)
                            val isSameUrl = tab.url.equals(it, ignoreCase = true)

                            if (!isSameYouTubeVideo && !isSameUrl) {
                                tab.detectedVideos.clear()
                            }
                            tab.url = it
                            if (!tab.isPrivate) {
                                saveSession()
                            }
                            checkForVideos(tab, it)
                        }
                    }
                }
            }
        }

        return session
    }

    // Playback fires media_detected for every range request, and mediaSession
    // metadata events fire repeatedly, so without a guard one playing video
    // spawns dozens of concurrent InnerTube/yt-dlp runs and cooks the phone.
    private val inFlightExtractions: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val lastAttemptMs = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val lastSuccessMs = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val EXTRACT_SUCCESS_COOLDOWN_MS = 15_000L
    private val EXTRACT_RETRY_BACKOFF_MS = 45_000L

    fun checkForVideos(tab: TabModel, targetUrl: String, onDemand: Boolean = false) {
        if (preferencesRepository?.videoDownloaderEnabled == false) return
        // YouTube's player response already lists every stream (content.js), so
        // the heavy InnerTube/yt-dlp pass waits until the user opens the download
        // sheet instead of competing with playback start.
        if (!onDemand && isYouTube(targetUrl)) {
            // Show the download button straight away; formats load when it is opened.
            val videoId = YouTubeStreamExtractor.extractVideoId(targetUrl)
            if (videoId != null && !tab.isPrivate && tab.detectedVideos.none { YouTubeStreamExtractor.extractVideoId(it.pageUrl) == videoId }) {
                mainHandler.post {
                    tab.detectedVideos.clear()
                    tab.detectedVideos.add(
                        com.browser.adfree.model.DetectedVideo(
                            pageUrl = targetUrl,
                            title = tab.title.removeSuffix(" - YouTube").ifBlank { "YouTube video" },
                            thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                            sourceDomain = "youtube.com"
                        )
                    )
                }
            }
            return
        }
        // The native extractor refetches the page with the normal session's
        // cookies, which would tie a private tab to the user's identity.
        if (tab.isPrivate) return
        if (targetUrl.isBlank() || targetUrl == HOME_URL || targetUrl.startsWith("about:")) return

        // A search/playlist/channel tab is not one video: the extractor fans out
        // into an InnerTube call per row, which is what overheats the device.
        // The network sniffer still reports direct media found on those pages.
        if ((targetUrl.contains("youtube.com") || targetUrl.contains("youtu.be")) &&
            com.browser.adfree.download.YouTubeStreamExtractor.extractVideoId(targetUrl) == null
        ) return

        val now = System.currentTimeMillis()
        if ((lastSuccessMs[targetUrl] ?: 0L).let { now - it < EXTRACT_SUCCESS_COOLDOWN_MS }) return
        if ((lastAttemptMs[targetUrl] ?: 0L).let { now - it < EXTRACT_RETRY_BACKOFF_MS }) return
        if (inFlightExtractions.contains(targetUrl)) return

        val capturedUrl = targetUrl
        if (!inFlightExtractions.add(capturedUrl)) return
        CoroutineScope(Dispatchers.IO).launch {
            lastAttemptMs[capturedUrl] = System.currentTimeMillis()
            try {
                val detected = com.browser.adfree.download.UniversalVideoExtractor.extract(capturedUrl, context)
                if (detected != null && detected.qualities.isNotEmpty()) {
                    lastSuccessMs[capturedUrl] = System.currentTimeMillis()
                    mainHandler.post {
                    val currentUrl = tab.url
                    val isYouTube = capturedUrl.contains("youtube.com") || capturedUrl.contains("youtu.be")
                    val oldVideoId = if (isYouTube) YouTubeStreamExtractor.extractVideoId(currentUrl) else null
                    val newVideoId = if (isYouTube) YouTubeStreamExtractor.extractVideoId(capturedUrl) else null
                    val isMatching = if (isYouTube) {
                        (oldVideoId != null && oldVideoId == newVideoId) || currentUrl.contains(newVideoId ?: "")
                    } else {
                        com.browser.adfree.download.VideoQualityParser.samePage(currentUrl, capturedUrl)
                    }

                    if (isMatching || tab.detectedVideos.isEmpty()) {
                        val existingIndex = if (tab.detectedVideos.isNotEmpty()) 0 else -1
                        if (existingIndex >= 0) {
                            val existing = tab.detectedVideos[existingIndex]
                            val isSameTarget = if (isYouTube) {
                                val existId = YouTubeStreamExtractor.extractVideoId(existing.pageUrl)
                                existId != null && existId == newVideoId
                            } else {
                                com.browser.adfree.download.VideoQualityParser.samePage(existing.pageUrl, capturedUrl)
                            }

                            if (!isSameTarget) {
                                // Different video / page navigated — replace with fresh detection
                                tab.detectedVideos.clear()
                                tab.detectedVideos.add(detected)
                            } else {
                                val mergedQualities = com.browser.adfree.download.VideoQualityParser.sortQualities(detected.qualities + existing.qualities)
                                tab.detectedVideos[existingIndex] = existing.copy(
                                    pageUrl = capturedUrl,
                                    title = if (detected.title.isNotBlank() && detected.title != "Web Video" && detected.title != "YouTube Video") detected.title else existing.title,
                                    thumbnail = detected.thumbnail ?: existing.thumbnail,
                                    durationSeconds = if (detected.durationSeconds > 0) detected.durationSeconds else existing.durationSeconds,
                                    qualities = mergedQualities
                                )
                            }
                        } else {
                            tab.detectedVideos.add(0, detected)
                        }
                        Log.d("VideoSniffer", "Detected & Enriched media for tab: ${detected.title} (${detected.qualities.size} streams)")
                    }
                    }
                }
            } catch (e: Exception) {
                Log.w("VideoSniffer", "Extraction failed for $capturedUrl: ${e.message}")
            } finally {
                inFlightExtractions.remove(capturedUrl)
            }
        }
    }

    fun restoreSession(runtime: GeckoRuntime, startupBehavior: StartupBehavior) {
        if (preferencesRepository?.wipeOnExit == true) {
            sessionRepository.clearSession()
            historyRepository?.clearAll()
            tabs.clear()
            createNewTab(runtime, HOME_URL)
            return
        }

        val (savedTabs, savedActiveIndex) = sessionRepository.loadSession()

        if (savedTabs.isNotEmpty()) {
            tabs.clear()
            for (saved in savedTabs) {
                val dummySession = GeckoSession()
                val tab = TabModel(
                    id = saved.id,
                    initialTitle = saved.title,
                    initialUrl = saved.url,
                    initialSession = dummySession,
                    initialIsPrivate = false
                ).apply {
                    isDesktopMode = saved.isDesktopMode
                    zoomLevel = saved.zoomLevel
                }

                val session = buildSession(tab)
                session.open(runtime)
                tab.session = session

                if (saved.url != HOME_URL && saved.url != "about:blank") {
                    session.loadUri(resolveInputUrl(saved.url))
                }

                tabs.add(tab)
            }

            if (startupBehavior == StartupBehavior.CONTINUE_SESSION) {
                activeTabIndex.intValue = savedActiveIndex.coerceIn(0, tabs.size - 1)
            } else {
                // StartupBehavior.NEW_TAB:
                // Check if last tab was already a start page
                val lastTab = tabs.lastOrNull()
                if (lastTab != null && lastTab.url == HOME_URL) {
                    activeTabIndex.intValue = tabs.size - 1
                } else {
                    createNewTab(runtime, HOME_URL)
                }
            }
        } else {
            createNewTab(runtime, HOME_URL)
        }
        saveSession()
    }

    fun saveSession() {
        if (preferencesRepository?.wipeOnExit == true) {
            sessionRepository.clearSession()
            return
        }
        val normalTabs = tabs.filter { !it.isPrivate }
        if (normalTabs.isNotEmpty()) {
            val currentTab = activeTab
            val normalActiveIndex = if (currentTab != null && !currentTab.isPrivate) {
                normalTabs.indexOf(currentTab).coerceAtLeast(0)
            } else {
                0
            }
            sessionRepository.saveSession(normalTabs, normalActiveIndex)
        }
    }

    fun createNewTab(
        runtime: GeckoRuntime,
        initialUrl: String = HOME_URL,
        isPrivate: Boolean = false
    ): TabModel {
        // Create a placeholder session with proper private mode flag
        val dummySession = GeckoSession(GeckoSessionSettings.Builder().usePrivateMode(isPrivate).build())
        val tab = TabModel(
            initialTitle = if (initialUrl == HOME_URL) {
                if (isPrivate) "Private Browsing" else "Start Page"
            } else if (isPrivate) "Private Tab" else "New Tab",
            initialUrl = initialUrl,
            initialSession = dummySession,
            initialIsPrivate = isPrivate
        )

        val session = buildSession(tab)
        session.open(runtime)
        tab.session = session

        if (initialUrl != HOME_URL && initialUrl != "about:blank") {
            session.loadUri(resolveInputUrl(initialUrl))
        }

        tabs.add(tab)
        activeTabIndex.intValue = tabs.size - 1
        if (!isPrivate) {
            saveSession()
        }
        return tab
    }

    fun selectTab(index: Int) {
        if (index in tabs.indices) {
            activeTabIndex.intValue = index
            if (!tabs[index].isPrivate) {
                saveSession()
            }
        }
    }

    fun nextTab() {
        if (tabs.size > 1) {
            val next = (activeTabIndex.intValue + 1) % tabs.size
            activeTabIndex.intValue = next
            if (!tabs[next].isPrivate) {
                saveSession()
            }
        }
    }

    fun previousTab() {
        if (tabs.size > 1) {
            val prev = if (activeTabIndex.intValue - 1 < 0) tabs.size - 1 else activeTabIndex.intValue - 1
            activeTabIndex.intValue = prev
            if (!tabs[prev].isPrivate) {
                saveSession()
            }
        }
    }

    fun closeTab(index: Int, runtime: GeckoRuntime) {
        if (index in tabs.indices) {
            val closingTab = tabs[index]
            try {
                closingTab.session.progressDelegate = null
                closingTab.session.contentDelegate = null
                closingTab.session.navigationDelegate = null
                closingTab.session.promptDelegate = null
                closingTab.session.mediaSessionDelegate = null
                closingTab.session.contentBlockingDelegate = null
                closingTab.session.close()
            } catch (e: Exception) {}
            tabs.removeAt(index)

            if (tabs.isEmpty()) {
                createNewTab(runtime, HOME_URL, isPrivate = false)
            } else if (activeTabIndex.intValue >= tabs.size) {
                activeTabIndex.intValue = tabs.size - 1
            }
            saveSession()
        }
    }

    fun closePrivateTabs(runtime: GeckoRuntime) {
        val privateTabs = tabs.filter { it.isPrivate }
        for (tab in privateTabs) {
            try {
                tab.session.progressDelegate = null
                tab.session.contentDelegate = null
                tab.session.navigationDelegate = null
                tab.session.promptDelegate = null
                tab.session.mediaSessionDelegate = null
                tab.session.contentBlockingDelegate = null
                tab.session.close()
            } catch (e: Exception) {}
            tabs.remove(tab)
        }
        if (tabs.isEmpty()) {
            createNewTab(runtime, HOME_URL, isPrivate = false)
        } else {
            activeTabIndex.intValue = activeTabIndex.intValue.coerceIn(0, tabs.size - 1)
        }
        saveSession()
    }

    fun closeAllTabs(runtime: GeckoRuntime) {
        for (tab in tabs) {
            try {
                tab.session.progressDelegate = null
                tab.session.contentDelegate = null
                tab.session.navigationDelegate = null
                tab.session.promptDelegate = null
                tab.session.mediaSessionDelegate = null
                tab.session.contentBlockingDelegate = null
                tab.session.close()
            } catch (e: Exception) {}
        }
        tabs.clear()
        sessionRepository.clearSession()
        createNewTab(runtime, HOME_URL, isPrivate = false)
        saveSession()
    }

    fun toggleDesktopMode(tab: TabModel) {
        tab.isDesktopMode = !tab.isDesktopMode
        applyUserAgent(tab, tab.session)
        tab.session.reload()
        saveSession()
    }

    fun setZoom(tab: TabModel, percent: Int) {
        tab.zoomLevel = percent
        sendPageCommand(tab, "set_zoom") { put("percent", percent) }
        saveSession()
    }

    fun setDarkWeb(tab: TabModel, enabled: Boolean) {
        tab.isDarkWeb = enabled
        sendPageCommand(tab, "set_dark") { put("enabled", enabled) }
    }

    fun toggleDarkWeb(tab: TabModel) = setDarkWeb(tab, !tab.isDarkWeb)

    /** Applies the global Night Mode default to every open tab. New pages read it from the extension. */
    fun setDarkWebModeAll(enabled: Boolean) {
        tabs.forEach { setDarkWeb(it, enabled) }
    }

    fun updateAdBlocking(enabled: Boolean) {
        for (tab in tabs) {
            tab.session.settings.useTrackingProtection = enabled && !isYouTube(tab.url)
        }
    }

    fun loadUri(tab: TabModel, input: String, runtime: GeckoRuntime) {
        val resolved = resolveInputUrl(input)
        
        if (resolved == HOME_URL) {
            tab.url = HOME_URL
            tab.title = "Start Page"
            tab.canGoBack = false
            tab.canGoForward = false
            saveSession()
            return
        }

        if (!tab.session.isOpen) {
            tab.session.open(runtime)
        }

        tab.url = resolved
        tab.session.loadUri(resolved)
        saveSession()
    }

    fun resolveInputUrl(input: String): String {
        var trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed == HOME_URL) return HOME_URL

        // If markdown link format was pasted e.g. [coveryourtracks.eff.org](https://coveryourtracks.eff.org/)
        val mdMatch = Regex("\\[.*?\\]\\((https?://.*?)\\)").find(trimmed)
        if (mdMatch != null) {
            trimmed = mdMatch.groupValues[1]
        }

        val searchEngine = preferencesRepository?.searchEngine ?: SearchEngine.DUCKDUCKGO

        if (trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true)) {
            return com.browser.adfree.model.UrlSanitizer.cleanUrl(trimmed)
        }

        if (trimmed.startsWith("about:", ignoreCase = true) ||
            trimmed.startsWith("javascript:", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true)) {
            return trimmed
        }

        // Domain matching without spaces (e.g., coveryourtracks.eff.org, adblock-tester.com/test)
        val isDomain = !trimmed.contains(" ") && trimmed.contains(".") &&
                       !trimmed.startsWith(".") && !trimmed.endsWith(".") &&
                       (trimmed.substringBefore("/").split(".").lastOrNull()?.all { it.isLetter() } == true) &&
                       (trimmed.substringBefore("/").split(".").lastOrNull()?.length ?: 0) in 2..24

        if (isDomain) {
            return com.browser.adfree.model.UrlSanitizer.cleanUrl("https://$trimmed")
        }

        return try {
            searchEngine.searchUrl + URLEncoder.encode(trimmed, "UTF-8")
        } catch (e: Exception) {
            searchEngine.searchUrl + trimmed.replace(" ", "+")
        }
    }

    fun destroy() {
        saveSession()
        for (tab in tabs) {
            try {
                tab.session.close()
            } catch (e: Exception) {}
        }
        tabs.clear()
    }
}
