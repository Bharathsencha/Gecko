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
    var showVpnCountrySelector by remember { mutableStateOf(false) }

    var pendingInsecureHttpUrl by remember { mutableStateOf<String?>(null) }
    var pendingInsecureProceedCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    val activeTab = tabManager.activeTab
    var urlText by remember(activeTab?.url) { mutableStateOf(activeTab?.url ?: "about:home") }

    val activeDownloadsCount = downloadManager.downloadsList.count { it.status == DownloadStatus.DOWNLOADING }
    val isHomePage = activeTab == null || activeTab.url == "about:home"

    // Set up insecure HTTP warning prompt listener
    LaunchedEffect(tabManager) {
        tabManager.preferencesRepository = preferencesRepo
        tabManager.onInsecureHttpPrompt = { url, proceedCallback ->
            pendingInsecureHttpUrl = url
            pendingInsecureProceedCallback = proceedCallback
        }
    }

    // Intercept back button when full-screen History Page is displayed
    BackHandler(enabled = showHistoryPage) {
        showHistoryPage = false
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
                onReload = {
                    activeTab?.session?.reload()
                }
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
                onOpenVpnSelector = {
                    showVpnCountrySelector = true
                },
                onDismissRequest = { showSettingsSheet = false }
            )
        }

        // VPN Country Selector Modal Sheet
        if (showVpnCountrySelector) {
            VpnCountrySelectorSheet(
                selectedCountry = vpnManager.selectedCountry,
                onSelectCountry = { country ->
                    vpnManager.selectCountry(country, runtime, tabManager.tabs)
                },
                onDismiss = { showVpnCountrySelector = false }
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
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, url)
            putExtra(Intent.EXTRA_TITLE, title)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Web Page")
        context.startActivity(shareIntent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
