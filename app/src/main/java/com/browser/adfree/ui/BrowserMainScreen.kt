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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.unit.dp
import com.browser.adfree.download.BrowserDownloadManager
import com.browser.adfree.download.VideoQualityParser
import com.browser.adfree.model.ArticleData
import com.browser.adfree.model.DownloadStatus
import com.browser.adfree.storage.BrowserPreferencesRepository
import com.browser.adfree.storage.FavoritesRepository
import com.browser.adfree.storage.HistoryRepository
import com.browser.adfree.storage.SecurePasswordRepository
import com.browser.adfree.tabs.TabManager
import com.browser.adfree.ui.components.*
import com.browser.adfree.ui.theme.blockTouchesBelow
import com.browser.adfree.vpn.VpnManager
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.StorageController

@Composable
fun BrowserMainScreen(
    runtime: GeckoRuntime,
    tabManager: TabManager,
    downloadManager: BrowserDownloadManager,
    preferencesRepo: BrowserPreferencesRepository,
    passwordRepo: SecurePasswordRepository,
    historyRepo: HistoryRepository,
    favoritesRepo: FavoritesRepository,
    trackerStats: com.browser.adfree.storage.TrackerStatsRepository,
    shieldsRepo: com.browser.adfree.shields.ShieldsRepository,
    vpnManager: VpnManager,
    onExtensionConfigChanged: () -> Unit = {},
    pendingSaveLogin: PendingSaveLogin? = null,
    onSaveLogin: () -> Unit = {},
    onDismissSaveLogin: () -> Unit = {}
) {
    val context = LocalContext.current
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showPasswordsSheet by remember { mutableStateOf(false) }
    var showTrackerReport by remember { mutableStateOf(false) }
    val vaultLock = remember { com.browser.adfree.storage.VaultLock(context) }
    var pinMode by remember { mutableStateOf<PinMode?>(null) }
    // Passwords need fingerprint, screen lock or the app PIN every time they are opened.
    val openVault: () -> Unit = {
        val activity = context as? androidx.fragment.app.FragmentActivity
        when {
            vaultLock.method == com.browser.adfree.storage.VaultLock.Method.SYSTEM && vaultLock.systemAvailable && activity != null ->
                vaultLock.promptSystem(activity, onSuccess = { showPasswordsSheet = true }, onFail = { msg ->
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                })
            vaultLock.hasPin -> pinMode = PinMode.VERIFY
            else -> pinMode = PinMode.CREATE
        }
    }
    var showHistoryPage by remember { mutableStateOf(false) }
    var showBookmarksPage by remember { mutableStateOf(false) }
    var showDownloadsPage by remember { mutableStateOf(false) }
    var showShieldsSheet by remember { mutableStateOf(false) }
    var showClearDataSheet by remember { mutableStateOf(false) }
    var showVideoDownloadSheet by remember { mutableStateOf(false) }
    var pendingWipe by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showDefaultPrompt by remember { mutableStateOf(!isAppDefaultBrowser(context) && !preferencesRepo.defaultBrowserPromptDismissed) }

    val defaultBrowserLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        showDefaultPrompt = false
    }

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

    // Leaving the app closes the vault.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) showPasswordsSheet = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Full YouTube extraction runs only once the user asks to download.
    LaunchedEffect(showVideoDownloadSheet) {
        if (showVideoDownloadSheet) activeTab?.let { tabManager.checkForVideos(it, it.url, onDemand = true) }
    }

    // No screenshots, screen recording or recent-apps preview: private tabs by
    // default, normal tabs when the user asks for it.
    val isPrivateActive = activeTab?.isPrivate == true
    val secure = if (isPrivateActive) preferencesRepo.blockScreenshotsPrivate else preferencesRepo.blockScreenshots
    DisposableEffect(secure) {
        val window = (context as? android.app.Activity)?.window
        if (secure) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { }
    }

    var contextMenu by remember { mutableStateOf<Pair<com.browser.adfree.model.TabModel, org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement>?>(null) }

    // Set up insecure HTTP warning prompt listener
    LaunchedEffect(tabManager) {
        tabManager.onReaderUnavailable = {
            android.widget.Toast.makeText(context, "No readable article on this page", android.widget.Toast.LENGTH_SHORT).show()
        }
        tabManager.preferencesRepository = preferencesRepo
        tabManager.onContextMenu = { tab, element -> contextMenu = tab to element }
        tabManager.onInsecureHttpPrompt = { url, proceedCallback ->
            pendingInsecureHttpUrl = url
            pendingInsecureProceedCallback = proceedCallback
        }
    }

    // Intercept back button when full-screen History, Bookmarks, or Downloads Page is displayed
    BackHandler(enabled = showHistoryPage) {
        showHistoryPage = false
    }
    BackHandler(enabled = showBookmarksPage) {
        showBookmarksPage = false
    }
    BackHandler(enabled = showDownloadsPage) {
        showDownloadsPage = false
    }
    BackHandler(enabled = showTrackerReport) {
        showTrackerReport = false
    }
    BackHandler(enabled = showPasswordsSheet) {
        showPasswordsSheet = false
    }
    BackHandler(enabled = showVideoDownloadSheet) {
        showVideoDownloadSheet = false
    }

    val hasModalOpen = showTrackerReport || showSettingsSheet || showHistoryPage || showBookmarksPage || showDownloadsPage || showTabSwitcher || showPasswordsSheet || showShieldsSheet || showClearDataSheet || showVideoDownloadSheet || showDefaultPrompt || pendingInsecureHttpUrl != null || pendingSaveLogin != null
    // Opening any page or sheet dismisses the keyboard, including one a web page opened.
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val rootView = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(hasModalOpen) {
        if (hasModalOpen) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                ?.hideSoftInputFromWindow(rootView.windowToken, 0)
        }
    }
    BackHandler(enabled = !hasModalOpen && activeTab != null && (activeTab.canGoBack || activeTab.url != "about:home")) {
        if (activeTab != null && activeTab.canGoBack) {
            activeTab.session.goBack()
        } else if (activeTab != null && activeTab.url != "about:home") {
            tabManager.loadUri(activeTab, "about:home", runtime)
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalTrackerStats provides trackerStats) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val addressBarComposable = @Composable {
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
                    tabCount = tabManager.tabs.size,
                    isPrivate = activeTab?.isPrivate ?: false,
                    onOpenClearData = { showClearDataSheet = true },
                    onOpenHistory = { showHistoryPage = true },
                    onOpenTabs = { showTabSwitcher = true },
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
                    historySuggestions = historySuggestions,
                    clearStyle = preferencesRepo.clearStyle,
                    container = com.browser.adfree.model.TabContainer.byId(activeTab?.container)
                )
            }

            // 1. Top Address Bar (if top mode)
            if (!preferencesRepo.bottomAddressBar) {
                addressBarComposable()
            }

            // 2. Main Web View Container OR Start Page with Ultra-Smooth Transition
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = Pair(activeTab?.id ?: "empty", isHomePage),
                    transitionSpec = {
                        fadeIn(animationSpec = androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing)) togetherWith
                        fadeOut(animationSpec = androidx.compose.animation.core.tween(150, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                    },
                    label = "mainViewTransition"
                ) { (_, homeActive) ->
                    if (homeActive) {
                        StartPage(
                            favorites = favoritesRepo.favorites,
                            preferencesRepo = preferencesRepo,
                            isPrivate = activeTab?.isPrivate ?: false,
                            onSwitchToPrivate = {
                                val privateIndex = tabManager.tabs.indexOfFirst { it.isPrivate }
                                if (privateIndex != -1) {
                                    tabManager.selectTab(privateIndex)
                                } else {
                                    tabManager.createNewTab(runtime, "about:home", isPrivate = true)
                                }
                            },
                            trackerStats = trackerStats,
                            onOpenTrackerReport = { showTrackerReport = true },
                            privateTabCount = tabManager.tabs.count { it.isPrivate },
                            onClosePrivateTabs = { tabManager.closePrivateTabs(runtime) },
                            onSwitchToNormal = {
                                val normalIndex = tabManager.tabs.indexOfFirst { !it.isPrivate }
                                if (normalIndex != -1) {
                                    tabManager.selectTab(normalIndex)
                                } else {
                                    tabManager.createNewTab(runtime, "about:home", isPrivate = false)
                                }
                            },
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

            // 1b. Bottom Address Bar (if bottom mode)
            if (preferencesRepo.bottomAddressBar) {
                addressBarComposable()
            }

            // 3. Docked toolbar
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
                onOpenDownloads = { showDownloadsPage = true },
                onOpenSettings = { showSettingsSheet = true },
                onSwipeLeft = { tabManager.nextTab() },
                onSwipeRight = { tabManager.previousTab() }
            )
        }

        // Dedicated Full Screen History Page
        androidx.compose.animation.AnimatedVisibility(
            visible = showHistoryPage,
            enter = androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(260)) { it / 3 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
            exit = androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(220)) { it / 3 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180))
        ) {
            Box(Modifier.fillMaxSize().blockTouchesBelow()) {
            HistoryPage(
                historyRepository = historyRepo,
                tabManager = tabManager,
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
}        // Dedicated Full Screen Bookmarks Page
        androidx.compose.animation.AnimatedVisibility(
            visible = showBookmarksPage,
            enter = androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(260)) { it / 3 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
            exit = androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(220)) { it / 3 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180))
        ) {
            Box(Modifier.fillMaxSize().blockTouchesBelow()) {
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
}

        // Dedicated Full Screen Downloads Page
        androidx.compose.animation.AnimatedVisibility(
            visible = showDownloadsPage,
            enter = androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(260)) { it / 3 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
            exit = androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(220)) { it / 3 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180))
        ) {
            Box(Modifier.fillMaxSize().blockTouchesBelow()) {
            DownloadsPage(
                downloadManager = downloadManager,
                onClose = { showDownloadsPage = false },
                modifier = Modifier.fillMaxSize()
            )
                    }
}

        // Settings: full page above the browser
        androidx.compose.animation.AnimatedVisibility(
            visible = showSettingsSheet,
            enter = androidx.compose.animation.slideInHorizontally { it },
            exit = androidx.compose.animation.slideOutHorizontally { it }
        ) {
            Box(Modifier.fillMaxSize().blockTouchesBelow()) {
            SettingsPage(
                preferencesRepo = preferencesRepo,
                passwordRepo = passwordRepo,
                historyRepo = historyRepo,
                tabManager = tabManager,
                vpnManager = vpnManager,
                runtime = runtime,
                activeTab = activeTab,
                onExtensionConfigChanged = onExtensionConfigChanged,
                onOpenPasswords = openVault,
                vaultLock = vaultLock,
                onChangePin = { pinMode = if (vaultLock.hasPin) PinMode.CHANGE else PinMode.CREATE },
                onOpenClearData = { showClearDataSheet = true },
                onOpenUrl = { url -> tabManager.createNewTab(runtime, url) },
                onOpenTrackerReport = { showTrackerReport = true },
                onClose = { showSettingsSheet = false },
                backEnabled = !showPasswordsSheet && !showTrackerReport
            )
                    }
}

        // Granular Clear Browsing Data Sheet
        if (showClearDataSheet) {
            ClearBrowsingDataSheet(
                historyRepository = historyRepo,
                tabManager = tabManager,
                runtime = runtime,
                onBurn = { wipe -> pendingWipe = wipe },
                clearStyle = preferencesRepo.clearStyle,
                onDismissRequest = {
                    showClearDataSheet = false
                    urlText = tabManager.activeTab?.url ?: "about:home"
                }
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

        androidx.compose.animation.AnimatedVisibility(
            visible = showPasswordsSheet,
            enter = androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(260)) { it / 3 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
            exit = androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(220)) { it / 3 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180))
        ) {
            Box(Modifier.fillMaxSize().blockTouchesBelow()) {
            PasswordsPage(
                passwordRepository = passwordRepo,
                onClose = { showPasswordsSheet = false }
            )
                    }
}

        androidx.compose.animation.AnimatedVisibility(
            visible = showTrackerReport,
            enter = androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(260)) { it / 3 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
            exit = androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(220)) { it / 3 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180))
        ) {
            Box(Modifier.fillMaxSize().blockTouchesBelow()) {
                TrackerReportPage(trackerStats) { showTrackerReport = false }
            }
        }

        pinMode?.let { mode -> androidx.compose.runtime.key(mode) {
            PinDialog(
                mode = mode,
                vaultLock = vaultLock,
                onUnlocked = {
                    if (mode == PinMode.CHANGE) {
                        pinMode = PinMode.CREATE
                    } else {
                        pinMode = null
                        showPasswordsSheet = true
                    }
                },
                onDismiss = { pinMode = null }
            )
        } }

        // Startup Default Browser Setup Prompt
        if (showDefaultPrompt) {
            DefaultBrowserPromptDialog(
                onSetDefault = {
                    requestSetDefaultBrowser(context, defaultBrowserLauncher)
                    showDefaultPrompt = false
                },
                onDismiss = {
                    preferencesRepo.setDefaultBrowserPromptDismissedPreference(true)
                    showDefaultPrompt = false
                }
            )
        }

        // Offer to Save Password Prompt (Disabled in private mode)
        if (pendingSaveLogin != null && activeTab?.isPrivate != true) {
            SavePasswordPromptDialog(
                pendingLogin = pendingSaveLogin,
                onSave = onSaveLogin,
                onDismiss = onDismissSaveLogin
            )
        }

        // Reader View Full Screen Overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = activeTab != null && activeTab.isReaderMode && activeTab.articleData != null,
            enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(280)) { it / 4 } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(220)) { it / 4 } + androidx.compose.animation.fadeOut()
        ) {
            Box(Modifier.fillMaxSize().blockTouchesBelow()) {
            val article = activeTab?.articleData ?: return@AnimatedVisibility
            ReaderView(
                article = article,
                onCloseReader = {
                    activeTab?.isReaderMode = false
                }
            )
                    }
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
                onNewTab = { isPrivate, container ->
                    tabManager.createNewTab(runtime, "about:home", isPrivate = isPrivate, container = container)
                    showTabSwitcher = false
                },
                onCloseAllTabs = {
                    tabManager.closeAllTabs(runtime)
                    showTabSwitcher = false
                },
                onCloseAllPrivateTabs = {
                    tabManager.closePrivateTabs(runtime)
                    showTabSwitcher = false
                },
                onSetGroup = { tab, group -> tabManager.setGroup(tab, group) },
                onCloseGroup = { group -> tabManager.closeGroup(group, runtime) },
                onDismissRequest = { showTabSwitcher = false }
            )
        }

        // Universal Video Downloader Sheet
        if (showVideoDownloadSheet && activeTab != null && activeTab.detectedVideos.isNotEmpty()) {
            // Entries from other pages can still be in the list; show the one the
            // user is actually looking at, so the sheet header matches the tab.
            val primaryVideo = activeTab.detectedVideos.firstOrNull { VideoQualityParser.samePage(it.pageUrl, activeTab.url) }
                ?: activeTab.detectedVideos.first()
            VideoDownloadSheet(
                video = primaryVideo,
                preferAv1 = preferencesRepo.preferAv1Codec,
                showWebm = preferencesRepo.showWebmFormats,
                onDownload = { option, filename ->
                    val extraHeaders = mutableMapOf<String, String>()
                    if (option.headers.isNotEmpty()) {
                        extraHeaders.putAll(option.headers)
                    }
                    val isGooglevideo = option.url.contains("googlevideo.com")
                    if (!isGooglevideo && activeTab.url.isNotBlank()) {
                        extraHeaders["Referer"] = activeTab.url
                    }
                    downloadManager.startDownload(
                        url = option.url,
                        customFileName = filename,
                        mimeType = option.mimeType,
                        referer = if (isGooglevideo) null else activeTab.url,
                        extraHeaders = extraHeaders,
                        audioUrl = option.audioUrl,
                        format = option.format,
                        pageUrl = primaryVideo.pageUrl.ifBlank { activeTab.url }
                    )
                },
                onDismiss = { showVideoDownloadSheet = false }
            )
        }

        // Per-site protection panel (opened from the address bar badge)
        if (showShieldsSheet && activeTab != null) {
            ShieldsQuickSheet(
                tab = activeTab,
                preferencesRepo = preferencesRepo,
                shieldsRepo = shieldsRepo,
                onToggleShieldsForSite = { enabled ->
                    shieldsRepo.setShieldsForUrl(activeTab.url, shieldsEnabled = enabled)
                    activeTab.session.settings.useTrackingProtection = enabled && !TabManager.isYouTube(activeTab.url)
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
                onZoom = { tabManager.setZoom(activeTab, it.coerceIn(50, 200)) },
                onReader = {
                    tabManager.requestReaderMode(activeTab)
                    showShieldsSheet = false
                },
                onSetPermission = { p, allowed ->
                    com.browser.adfree.storage.SitePermissions.set(
                        com.browser.adfree.storage.SitePermissions.hostOf(activeTab.url), p, allowed, activeTab.isPrivate, runtime
                    )
                },
                onDismissRequest = { showShieldsSheet = false }
            )
        }

        // Floating draggable video download pill
        val detectedVideosCountForFloat = if (preferencesRepo.videoDownloaderEnabled) (activeTab?.detectedVideos?.size ?: 0) else 0
        if (!showVideoDownloadSheet) {
            val pillVideo = activeTab?.detectedVideos?.let { list ->
                list.firstOrNull { VideoQualityParser.samePage(it.pageUrl, activeTab.url) } ?: list.firstOrNull()
            }
            FloatingVideoDownloadButton(
                videoCount = detectedVideosCountForFloat,
                summary = pillVideo?.let { downloadSummary(it, preferencesRepo.preferAv1Codec, preferencesRepo.showWebmFormats) },
                onTap = { showVideoDownloadSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(bottom = 72.dp, end = 16.dp)
            )
        }

        contextMenu?.let { (tab, element) ->
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            ContextMenuSheet(
                element = element,
                isPrivate = tab.isPrivate,
                onAction = { action ->
                    val link = element.linkUri.orEmpty()
                    val src = element.srcUri.orEmpty()
                    when (action) {
                        ContextAction.OPEN_TAB -> {
                            tabManager.createNewTab(runtime, link, container = tab.container, select = false).also { tabManager.setGroup(it, tab.group) }
                            android.widget.Toast.makeText(context, "Opened in a new tab", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        ContextAction.OPEN_PRIVATE -> tabManager.createNewTab(runtime, link, isPrivate = true, select = tab.isPrivate.not()).also {
                            if (tab.isPrivate) android.widget.Toast.makeText(context, "Opened in a new private tab", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        ContextAction.COPY_LINK -> clipboard.setText(androidx.compose.ui.text.AnnotatedString(link))
                        ContextAction.SHARE_LINK -> shareUrl(context, element.linkText ?: link, link)
                        ContextAction.DOWNLOAD_LINK -> tabManager.downloadUrl(tab, link, runtime)
                        ContextAction.OPEN_IMAGE -> tabManager.createNewTab(runtime, src, isPrivate = tab.isPrivate, container = tab.container)
                        ContextAction.DOWNLOAD_MEDIA -> tabManager.downloadUrl(tab, src, runtime)
                        ContextAction.COPY_MEDIA -> clipboard.setText(androidx.compose.ui.text.AnnotatedString(src))
                        ContextAction.SHARE_MEDIA -> shareUrl(context, element.title ?: src, src)
                    }
                },
                onDismiss = { contextMenu = null }
            )
        }

        com.browser.adfree.storage.SitePermissions.pending.firstOrNull()?.let { ask ->
            androidx.compose.runtime.key(ask) {
                PermissionPrompt(ask) { com.browser.adfree.storage.SitePermissions.pending.remove(ask) }
            }
        }

        FireBurnOverlay(
            playing = pendingWipe != null,
            style = preferencesRepo.clearStyle,
            onCovered = {
                pendingWipe?.invoke()
                urlText = tabManager.activeTab?.url ?: "about:home"
            },
            onFinished = { pendingWipe = null }
        )
    }
}
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
        android.util.Log.w("GeckoApp", "Ignored ${e.javaClass.simpleName}")
    }
}
