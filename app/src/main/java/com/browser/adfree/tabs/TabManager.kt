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
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.MediaSession
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
    val sessionRepository = TabSessionRepository(context)

    var onInsecureHttpPrompt: ((url: String, onProceed: () -> Unit) -> Unit)? = null
    val allowedHttpUrls = mutableSetOf<String>()

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val HOME_URL = "about:home"
        const val MODERN_MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; SM-S948B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.6613.127 Mobile Safari/537.36"
        const val DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }

    private fun buildSession(tab: TabModel): GeckoSession {
        val session = GeckoSession()

        // Chrome-like UA for proper Google/site layout; Desktop UA for desktop mode
        session.settings.userAgentOverride = if (tab.isDesktopMode) DESKTOP_UA else MODERN_MOBILE_UA
        session.settings.userAgentMode = if (tab.isDesktopMode) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        session.settings.viewportMode = if (tab.isDesktopMode) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        
        val siteConfig = shieldsRepository?.getConfigForUrl(tab.url)
        val shieldsActive = siteConfig?.shieldsEnabled ?: (preferencesRepository?.adBlockingEnabled ?: true)
        val bgMedia = siteConfig?.backgroundMediaEnabled ?: true
        
        session.settings.useTrackingProtection = shieldsActive
        session.settings.suspendMediaWhenInactive = !bgMedia

        // Media Session Delegate for Android Foreground Service & Lockscreen playback
        session.mediaSessionDelegate = object : MediaSession.Delegate {
            override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
                com.browser.adfree.media.MediaPlaybackService.start(context, tab.title, tab.url)
            }
            override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
                com.browser.adfree.media.MediaPlaybackService.stop(context)
            }
        }

        // Content Blocking / Ad & Tracker Shield Delegate
        session.contentBlockingDelegate = object : org.mozilla.geckoview.ContentBlocking.Delegate {
            override fun onContentBlocked(
                session: GeckoSession,
                event: org.mozilla.geckoview.ContentBlocking.BlockEvent
            ) {
                mainHandler.post {
                    tab.adBlockCount++
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
                    tab.isLoading = true
                    tab.progress = 10
                    tab.adBlockCount = 0
                    tab.isReaderMode = false
                    tab.articleData = null

                    // Dynamically apply per-site shield settings
                    val currentSiteConfig = shieldsRepository?.getConfigForUrl(url)
                    val currentShieldsActive = currentSiteConfig?.shieldsEnabled ?: (preferencesRepository?.adBlockingEnabled ?: true)
                    val currentBgMedia = currentSiteConfig?.backgroundMediaEnabled ?: true
                    session.settings.useTrackingProtection = currentShieldsActive
                    session.settings.suspendMediaWhenInactive = !currentBgMedia
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                mainHandler.post {
                    tab.progress = progress
                    if (progress >= 100) {
                        tab.isLoading = false
                    }
                }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                mainHandler.post {
                    tab.isLoading = false
                    tab.progress = 100
                    if (success && tab.url != HOME_URL && !tab.url.startsWith("about:")) {
                        historyRepository?.addHistory(tab.title, tab.url)
                    }
                    if (tab.isDarkWeb) {
                        injectDarkWebMode(tab, true)
                    }
                    if (tab.zoomLevel != 100) {
                        val factor = tab.zoomLevel / 100f
                        val js = "try { document.documentElement.style.zoom = '$factor'; document.body.style.zoom = '$factor'; } catch(e) { document.body.style.transform = 'scale($factor)'; document.body.style.transformOrigin = '0 0'; }"
                        session.loadUri("javascript:(function(){$js})();")
                    }
                    saveSession()
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
                            historyRepository?.addHistory(tab.title, tab.url)
                            saveSession()
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
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>
            ) {
                url?.let {
                    mainHandler.post {
                        if (it != "about:blank" && it != HOME_URL) {
                            tab.url = it
                            saveSession()
                        }
                    }
                }
            }
        }

        return session
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
                    initialSession = dummySession
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
        if (tabs.isNotEmpty()) {
            sessionRepository.saveSession(tabs, activeTabIndex.intValue)
        }
    }

    fun createNewTab(
        runtime: GeckoRuntime,
        initialUrl: String = HOME_URL
    ): TabModel {
        // Create a placeholder session
        val dummySession = GeckoSession()
        val tab = TabModel(
            initialTitle = if (initialUrl == HOME_URL) "Start Page" else "New Tab",
            initialUrl = initialUrl,
            initialSession = dummySession
        )

        val session = buildSession(tab)
        session.open(runtime)
        tab.session = session

        if (initialUrl != HOME_URL && initialUrl != "about:blank") {
            session.loadUri(resolveInputUrl(initialUrl))
        }

        tabs.add(tab)
        activeTabIndex.intValue = tabs.size - 1
        saveSession()
        return tab
    }

    fun selectTab(index: Int) {
        if (index in tabs.indices) {
            activeTabIndex.intValue = index
            saveSession()
        }
    }

    fun nextTab() {
        if (tabs.size > 1) {
            val next = (activeTabIndex.intValue + 1) % tabs.size
            activeTabIndex.intValue = next
            saveSession()
        }
    }

    fun previousTab() {
        if (tabs.size > 1) {
            val prev = if (activeTabIndex.intValue - 1 < 0) tabs.size - 1 else activeTabIndex.intValue - 1
            activeTabIndex.intValue = prev
            saveSession()
        }
    }

    fun closeTab(index: Int, runtime: GeckoRuntime) {
        if (index in tabs.indices) {
            val closingTab = tabs[index]
            closingTab.session.close()
            tabs.removeAt(index)

            if (tabs.isEmpty()) {
                createNewTab(runtime, HOME_URL)
            } else if (activeTabIndex.intValue >= tabs.size) {
                activeTabIndex.intValue = tabs.size - 1
            }
            saveSession()
        }
    }

    fun closeAllTabs(runtime: GeckoRuntime) {
        for (tab in tabs) {
            tab.session.close()
        }
        tabs.clear()
        createNewTab(runtime, HOME_URL)
        saveSession()
    }

    fun toggleDesktopMode(tab: TabModel) {
        tab.isDesktopMode = !tab.isDesktopMode
        if (tab.isDesktopMode) {
            tab.session.settings.userAgentOverride = DESKTOP_UA
            tab.session.settings.userAgentMode = GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            tab.session.settings.viewportMode = GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
        } else {
            tab.session.settings.userAgentOverride = MODERN_MOBILE_UA
            tab.session.settings.userAgentMode = GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            tab.session.settings.viewportMode = GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        }
        tab.session.reload()
        saveSession()
    }

    fun setZoom(tab: TabModel, percent: Int) {
        tab.zoomLevel = percent
        val factor = percent / 100f
        val js = "try { document.documentElement.style.zoom = '$factor'; document.body.style.zoom = '$factor'; } catch(e) { document.body.style.transform = 'scale($factor)'; document.body.style.transformOrigin = '0 0'; }"
        tab.session.loadUri("javascript:(function(){$js})();")
        saveSession()
    }

    fun injectDarkWebMode(tab: TabModel, enabled: Boolean) {
        tab.isDarkWeb = enabled
        val js = """
            (function() {
                var el = document.getElementById('gecko-dark-web-style');
                if ($enabled) {
                    if (!el) {
                        el = document.createElement('style');
                        el.id = 'gecko-dark-web-style';
                        el.textContent = 'html { filter: invert(90%) hue-rotate(180deg) !important; background: #121212 !important; } img, video, canvas, svg, picture, iframe, [style*="background-image"] { filter: invert(100%) hue-rotate(180deg) !important; }';
                        (document.head || document.documentElement).appendChild(el);
                    }
                } else {
                    if (el) el.remove();
                }
            })();
        """.trimIndent().replace("\n", " ")
        tab.session.loadUri("javascript:(function(){$js})();")
    }

    fun toggleDarkWeb(tab: TabModel) {
        injectDarkWebMode(tab, !tab.isDarkWeb)
    }

    fun setDarkWebModeAll(enabled: Boolean) {
        for (tab in tabs) {
            injectDarkWebMode(tab, enabled)
        }
    }

    fun updateAdBlocking(enabled: Boolean) {
        for (tab in tabs) {
            tab.session.settings.useTrackingProtection = enabled
        }
    }

    fun updateCosmeticBlocking(enabled: Boolean) {
        val js = "if (typeof cosmeticBlockingActive !== 'undefined') { cosmeticBlockingActive = $enabled; if ($enabled) { injectStyleShield(); cleanAdNodes(document); } else { removeStyleShield(); } }"
        for (tab in tabs) {
            tab.session.loadUri("javascript:(function(){$js})();")
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

        val searchEngine = preferencesRepository?.searchEngine ?: SearchEngine.GOOGLE

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

    fun resetToHome(runtime: GeckoRuntime) {
        for (tab in tabs) {
            try {
                tab.session.close()
            } catch (e: Exception) {}
        }
        tabs.clear()
        sessionRepository.clearSession()
        createNewTab(runtime, HOME_URL)
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
