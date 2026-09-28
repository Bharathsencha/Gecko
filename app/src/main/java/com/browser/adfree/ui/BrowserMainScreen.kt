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

package com.browser.adfree.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.browser.adfree.download.BrowserDownloadManager
import com.browser.adfree.model.ArticleData
import com.browser.adfree.model.DownloadStatus
import com.browser.adfree.model.SearchEngine
import com.browser.adfree.storage.BrowserPreferencesRepository
import com.browser.adfree.storage.FavoritesRepository
import com.browser.adfree.storage.HistoryRepository
import com.browser.adfree.storage.SecurePasswordRepository
import com.browser.adfree.tabs.TabManager
import com.browser.adfree.ui.components.*
import com.browser.adfree.ui.theme.DarkBg
import com.browser.adfree.vpn.FreeVpnManager
import org.mozilla.geckoview.GeckoRuntime

@Composable
fun BrowserMainScreen(
    runtime: GeckoRuntime,
    tabManager: TabManager,
    downloadManager: BrowserDownloadManager,
    preferencesRepo: BrowserPreferencesRepository,
    passwordRepo: SecurePasswordRepository,
    historyRepo: HistoryRepository,
    favoritesRepo: FavoritesRepository,
    shieldsRepo: com.browser.adfree.shields.ShieldsRepository,
    vpnManager: FreeVpnManager,
    pendingSaveLogin: PendingSaveLogin? = null,
    onSaveLogin: () -> Unit = {},
    onDismissSaveLogin: () -> Unit = {}
) {
    val context = LocalContext.current
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showDownloadsSheet by remember { mutableStateOf(false) }
    var showPageMenu by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showPasswordsSheet by remember { mutableStateOf(false) }
    var showHistoryPage by remember { mutableStateOf(false) }
    var showBookmarksPage by remember { mutableStateOf(false) }
    var showShieldsSheet by remember { mutableStateOf(false) }

    var pendingInsecureHttpUrl by remember { mutableStateOf<String?>(null) }
    var pendingInsecureProceedCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    val activeTab = tabManager.activeTab
    var urlText by remember(activeTab?.url) { mutableStateOf(activeTab?.url ?: "about:home") }

    val isCurrentPageBookmarked = remember(activeTab?.url, favoritesRepo.favorites.size) {
        val cur = activeTab?.url ?: ""
        cur.isNotBlank() && cur != "about:home" && favoritesRepo.favorites.any { it.url == cur }
    }

    val activeDownloadsCount = downloadManager.downloadsList.count { it.status == DownloadStatus.DOWNLOADING }
    val isHomePage = activeTab == null || activeTab.url == "about:home"

    // Debounced history filter — runs 150ms after typing stops to prevent per-keystroke lag
    var historySuggestions by remember { mutableStateOf<List<com.browser.adfree.model.HistoryItem>>(emptyList()) }
    LaunchedEffect(urlText) {
        if (urlText.isBlank() || urlText == "about:home") {
            historySuggestions = emptyList()
        } else {
            kotlinx.coroutines.delay(150)
            historySuggestions = historyRepo.history
                .filter { item ->
                    item.url.contains(urlText, ignoreCase = true) ||
                    item.title.contains(urlText, ignoreCase = true)
                }
                .take(5)
        }
    }

    // Set up insecure HTTP warning prompt listener
    LaunchedEffect(tabManager) {
        tabManager.preferencesRepository = preferencesRepo
        tabManager.onInsecureHttpPrompt = { url, proceedCallback ->
            pendingInsecureHttpUrl = url
            pendingInsecureProceedCallback = proceedCallback
        }
    }

    // Intercept back button when full-screen History or Bookmarks Page is displayed
    BackHandler(enabled = showHistoryPage) {
        showHistoryPage = false
    }
    BackHandler(enabled = showBookmarksPage) {
        showBookmarksPage = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Omnibox with History & Settings Buttons
            TopAddressBar(
                urlInput = urlText,
                onUrlChange = { urlText = it },
                onSearch = { query ->
                    activeTab?.let { tab ->
                        tabManager.loadUri(tab, query, runtime)
                    }
                },
                adBlockCount = activeTab?.adBlockCount ?: 0,
                isReaderMode = activeTab?.isReaderMode ?: false,
                isDarkWeb = activeTab?.isDarkWeb ?: false,
                onOpenPageMenu = { showPageMenu = true },
                onOpenSettings = { showSettingsSheet = true },
                onOpenHistory = { showHistoryPage = true },
                onOpenShields = { showShieldsSheet = true },
                onReload = {
                    activeTab?.session?.reload()
                },
                isBookmarked = isCurrentPageBookmarked,
                onToggleBookmark = {
                    val curUrl = activeTab?.url ?: ""
                    if (curUrl.isNotBlank() && curUrl != "about:home") {
                        val existing = favoritesRepo.favorites.find { it.url == curUrl }
                        if (existing != null) {
                            favoritesRepo.removeFavorite(existing)
                        } else {
                            favoritesRepo.addFavorite(activeTab?.title ?: curUrl, curUrl)
                        }
                    }
                },
                historySuggestions = historySuggestions
            )

            // 2. Main Web View Container OR Start Page with Ultra-Smooth Transition
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = Pair(activeTab?.id ?: "empty", isHomePage),
                    transitionSpec = {
                        (fadeIn(animationSpec = androidx.compose.animation.core.tween(240, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                         scaleIn(initialScale = 0.96f, animationSpec = androidx.compose.animation.core.tween(240, easing = androidx.compose.animation.core.FastOutSlowInEasing))) togetherWith
                        (fadeOut(animationSpec = androidx.compose.animation.core.tween(180, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                         scaleOut(targetScale = 1.03f, animationSpec = androidx.compose.animation.core.tween(180, easing = androidx.compose.animation.core.FastOutSlowInEasing)))
                    },
                    label = "mainViewTransition"
                ) { (_, homeActive) ->
                    if (homeActive) {
                        SafariHomePage(
                            favorites = favoritesRepo.favorites,
                            onNavigate = { targetUrl ->
                                activeTab?.let { tab ->
                                    tabManager.loadUri(tab, targetUrl, runtime)
                                }
                            },
                            onAddFavorite = { title, url ->
                                favoritesRepo.addFavorite(title, url)
                            },
                            onRemoveFavorite = { fav ->
                                favoritesRepo.removeFavorite(fav)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (activeTab != null) {
                        GeckoViewContainer(
                            session = activeTab.session,
                            isLoading = activeTab.isLoading,
                            onRefresh = { activeTab.session.reload() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // 3. Bottom Bar with Page Progress & Horizontal Gestures
            BottomBar(
                progress = activeTab?.progress ?: 0,
                isLoading = activeTab?.isLoading ?: false,
                canGoBack = activeTab?.canGoBack ?: false,
                canGoForward = activeTab?.canGoForward ?: false,
                activeDownloadsCount = activeDownloadsCount,
                onBack = {
                    if (activeTab != null && activeTab.canGoBack) {
                        activeTab.session.goBack()
                    } else if (!isHomePage) {
                        activeTab?.let { tabManager.loadUri(it, "about:home", runtime) }
                    }
                },
                onForward = { activeTab?.session?.goForward() },
                onShare = {
                    activeTab?.let { tab ->
                        shareUrl(context, tab.title, tab.url)
                    }
                },
                onOpenBookmarks = { showBookmarksPage = true },
                onOpenDownloads = { showDownloadsSheet = true },
                onOpenTabs = { showTabSwitcher = true },
                onSwipeLeft = { tabManager.nextTab() },
                onSwipeRight = { tabManager.previousTab() }
            )
        }

        // Dedicated Full Screen History Page
        if (showHistoryPage) {
            HistoryPage(
                historyRepository = historyRepo,
                runtime = runtime,
                onNavigate = { targetUrl ->
                    activeTab?.let { tab ->
                        tabManager.loadUri(tab, targetUrl, runtime)
                    }
                    showHistoryPage = false
                },
                onClose = { showHistoryPage = false },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Dedicated Full Screen Bookmarks Page
        if (showBookmarksPage) {
            BookmarksPage(
                favorites = favoritesRepo.favorites,
                onNavigate = { targetUrl ->
                    activeTab?.let { tab ->
                        tabManager.loadUri(tab, targetUrl, runtime)
                    }
                    showBookmarksPage = false
                },
                onRemove = { favoriteItem ->
                    favoritesRepo.removeFavorite(favoriteItem)
                },
                onBack = { showBookmarksPage = false },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Page Options Menu
        if (showPageMenu && activeTab != null) {
            SafariPageMenu(
                tab = activeTab,
                onZoomChange = { newZoom ->
                    tabManager.setZoom(activeTab, newZoom)
                },
                onToggleReader = {
                    showPageMenu = false
                    triggerReaderModeExtraction(activeTab)
                },
                onToggleDarkWeb = {
                    tabManager.toggleDarkWeb(activeTab)
                },
                onToggleDesktop = {
                    tabManager.toggleDesktopMode(activeTab)
                },
                onDismissRequest = { showPageMenu = false }
            )
        }

        // Browser Settings Sheet
        if (showSettingsSheet) {
            BrowserSettingsSheet(
                preferencesRepo = preferencesRepo,
                passwordRepo = passwordRepo,
                historyRepo = historyRepo,
                tabManager = tabManager,
                vpnManager = vpnManager,
                runtime = runtime,
                onOpenPasswords = {
                    showSettingsSheet = false
                    showPasswordsSheet = true
                },
                onOpenHistory = {
                    showSettingsSheet = false
                    showHistoryPage = true
                },
                onDismissRequest = { showSettingsSheet = false }
            )
        }

        // Insecure Connection Warning Dialog
        if (pendingInsecureHttpUrl != null) {
            InsecureHttpWarningDialog(
                url = pendingInsecureHttpUrl!!,
                onProceedAnyway = {
                    val callback = pendingInsecureProceedCallback
                    pendingInsecureHttpUrl = null
                    pendingInsecureProceedCallback = null
                    callback?.invoke()
                },
                onGoBack = {
                    pendingInsecureHttpUrl = null
                    pendingInsecureProceedCallback = null
                    if (activeTab != null && activeTab.canGoBack) {
                        activeTab.session.goBack()
                    } else {
                        activeTab?.let { tabManager.loadUri(it, "about:home", runtime) }
                    }
                }
            )
        }

        // Saved Passwords Sheet (AES-256)
        if (showPasswordsSheet) {
            SavedPasswordsSheet(
                passwordRepository = passwordRepo,
                onDismissRequest = { showPasswordsSheet = false }
            )
        }

        // Offer to Save Password Prompt
        if (pendingSaveLogin != null) {
            SavePasswordPromptDialog(
                pendingLogin = pendingSaveLogin,
                onSave = onSaveLogin,
                onDismiss = onDismissSaveLogin
            )
        }

        // Reader View Full Screen Overlay
        if (activeTab != null && activeTab.isReaderMode && activeTab.articleData != null) {
            SafariReaderView(
                article = activeTab.articleData!!,
                onCloseReader = {
                    activeTab.isReaderMode = false
                }
            )
        }

        // Tab Switcher Sheet
        if (showTabSwitcher) {
            TabSwitcherSheet(
                tabs = tabManager.tabs,
                activeTabIndex = tabManager.activeTabIndex.intValue,
                onTabSelected = { index ->
                    tabManager.selectTab(index)
                    showTabSwitcher = false
                },
                onCloseTab = { index ->
                    tabManager.closeTab(index, runtime)
                },
                onNewTab = {
                    tabManager.createNewTab(runtime, "about:home")
                    showTabSwitcher = false
                },
                onCloseAllTabs = {
                    tabManager.closeAllTabs(runtime)
                    showTabSwitcher = false
                },
                onDismissRequest = { showTabSwitcher = false }
            )
        }

        // Downloads Manager Modal
        if (showDownloadsSheet) {
            DownloadsSheet(
                downloads = downloadManager.downloadsList,
                onOpenFile = { item -> downloadManager.openDownloadedFile(item) },
                onRemove = { item -> downloadManager.removeDownload(item) },
                onClearCompleted = { downloadManager.clearCompleted() },
                onDismissRequest = { showDownloadsSheet = false }
            )
        }

        // Brave-style Shields Quick Panel (linked to existing Shield badge)
        if (showShieldsSheet && activeTab != null) {
            ShieldsQuickSheet(
                tab = activeTab,
                preferencesRepo = preferencesRepo,
                shieldsRepo = shieldsRepo,
                onToggleShieldsForSite = { enabled ->
                    shieldsRepo.setShieldsForUrl(activeTab.url, shieldsEnabled = enabled)
                    activeTab.session.settings.useTrackingProtection = enabled
                },
                onToggleBackgroundMedia = { enabled ->
                    shieldsRepo.setShieldsForUrl(activeTab.url, backgroundMediaEnabled = enabled)
                    activeTab.session.settings.suspendMediaWhenInactive = !enabled
                },
                onToggleDarkWeb = {
                    val newDark = !activeTab.isDarkWeb
                    shieldsRepo.setShieldsForUrl(activeTab.url, darkWebEnabled = newDark)
                    tabManager.toggleDarkWeb(activeTab)
                },
                onToggleDesktop = {
                    val newDesktop = !activeTab.isDesktopMode
                    shieldsRepo.setShieldsForUrl(activeTab.url, desktopModeEnabled = newDesktop)
                    tabManager.toggleDesktopMode(activeTab)
                },
                onDismissRequest = { showShieldsSheet = false }
            )
        }
    }
}

private fun triggerReaderModeExtraction(tab: com.browser.adfree.model.TabModel) {
    if (tab.isReaderMode) {
        tab.isReaderMode = false
        return
    }

    tab.articleData = ArticleData(
        title = if (tab.title.isBlank() || tab.title == "Start Page") "Web Article" else tab.title,
        byline = null,
        contentHtml = "<p>Reading mode active for: <b>" + tab.url + "</b></p>",
        isArticle = true
    )
    tab.isReaderMode = true
}

private fun shareUrl(context: Context, title: String, url: String) {
    try {
        val clean = com.browser.adfree.model.UrlSanitizer.cleanUrl(url)
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, clean)
            putExtra(Intent.EXTRA_TITLE, title)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Clean Link")
        context.startActivity(shareIntent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
