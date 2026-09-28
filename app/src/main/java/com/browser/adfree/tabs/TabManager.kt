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
    val sessionRepository = TabSessionRepository(context)

    var onInsecureHttpPrompt: ((url: String, onProceed: () -> Unit) -> Unit)? = null
    val allowedHttpUrls = mutableSetOf<String>()

    private val mainHandler = Handler(Looper.getMainLooper())


    companion object {
        const val HOME_URL = "about:home"
        const val MODERN_MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; SM-S948B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36"
        const val DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64; rv:122.0) Gecko/20100101 Firefox/122.0"
    }

    private fun buildSession(tab: TabModel): GeckoSession {
        val session = GeckoSession()

        // Modern Mobile User Agent & Viewport
        session.settings.userAgentOverride = if (tab.isDesktopMode) DESKTOP_UA else MODERN_MOBILE_UA
        session.settings.userAgentMode = if (tab.isDesktopMode) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        session.settings.viewportMode = if (tab.isDesktopMode) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        session.settings.useTrackingProtection = true

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
                        val js = "if (typeof applyDarkWebMode === 'function') { applyDarkWebMode(true); }"
                        session.loadUri("javascript:(function(){$js})();")
                    }
                    if (tab.zoomLevel != 100) {
                        val zoom = tab.zoomLevel
                        val js = "if (typeof applyTextZoom === 'function') { applyTextZoom($zoom); } else { document.body.style.zoom = '${zoom / 100f}'; }"
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
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                val uri = request.uri
                val httpsOnly = preferencesRepository?.httpsOnlyMode ?: true

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
        val js = "if (typeof applyTextZoom === 'function') { applyTextZoom($percent); } else { document.body.style.zoom = '${percent / 100f}'; }"
        tab.session.loadUri("javascript:(function(){$js})();")
        saveSession()
    }

    fun toggleDarkWeb(tab: TabModel) {
        tab.isDarkWeb = !tab.isDarkWeb
        val enabled = tab.isDarkWeb
        val js = "if (typeof applyDarkWebMode === 'function') { applyDarkWebMode($enabled); }"
        tab.session.loadUri("javascript:(function(){$js})();")
    }

    fun setDarkWebModeAll(enabled: Boolean) {
        for (tab in tabs) {
            tab.isDarkWeb = enabled
            val js = "if (typeof applyDarkWebMode === 'function') { applyDarkWebMode($enabled); }"
            tab.session.loadUri("javascript:(function(){$js})();")
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

        // If launching from about:home, reset the session so previous site history doesn't leak into new site
        if (tab.url == HOME_URL) {
            try {
                tab.session.close()
            } catch (e: Exception) {}
            val freshSession = buildSession(tab)
            freshSession.open(runtime)
            tab.session = freshSession
        }

        tab.url = resolved
        tab.session.loadUri(resolved)
        saveSession()
    }

    fun resolveInputUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed == HOME_URL) return HOME_URL

        val searchEngine = preferencesRepository?.searchEngine ?: SearchEngine.GOOGLE
        val blockAi = preferencesRepository?.blockAiOverviews ?: true

        if (trimmed.startsWith("https://") || trimmed.startsWith("about:") || trimmed.startsWith("javascript:")) {
            if (blockAi && (trimmed.startsWith("https://www.google.com/search") || trimmed.startsWith("https://google.com/search"))) {
                if (!trimmed.contains("udm=")) {
                    return if (trimmed.contains("?")) "$trimmed&udm=14" else "$trimmed?udm=14"
                }
            }
            return trimmed
        }

        if (trimmed.startsWith("http://")) {
            return trimmed
        }

        val domainPattern = Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$")
        if (domainPattern.matches(trimmed) && !trimmed.contains(" ")) {
            return "https://$trimmed"
        }

        return try {
            var url = searchEngine.searchUrl + URLEncoder.encode(trimmed, "UTF-8")
            if (blockAi && searchEngine == SearchEngine.GOOGLE) {
                url += "&udm=14"
            }
            url
        } catch (e: Exception) {
            var url = searchEngine.searchUrl + trimmed.replace(" ", "+")
            if (blockAi && searchEngine == SearchEngine.GOOGLE) {
                url += "&udm=14"
            }
            url
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
