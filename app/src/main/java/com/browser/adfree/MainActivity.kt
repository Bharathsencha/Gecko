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

    private var pendingSaveLogin by mutableStateOf<PendingSaveLogin?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize Repositories & Managers
        preferencesRepo = BrowserPreferencesRepository(this)
        passwordRepo = SecurePasswordRepository(this)
        historyRepo = HistoryRepository(this)
        favoritesRepo = FavoritesRepository(this)
        downloadManager = BrowserDownloadManager(this)
        vpnManager = FreeVpnManager(this, preferencesRepo)

        tabManager = TabManager(this).apply {
            this.historyRepository = historyRepo
            this.preferencesRepository = preferencesRepo
        }

        // 2. Configure GeckoRuntime with Strict Native Tracking Protection & Cookie Shield
        val runtimeSettings = GeckoRuntimeSettings.Builder()
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

        runtime = GeckoRuntime.create(this, runtimeSettings)

        // 3. Register Embedded Native Content Blocker Extension
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/extensions/ublock/",
            "ublock-embedded@browser.adfree"
        ).accept(
            { extension ->
                if (extension != null) {
                    Log.i("AdFreeBrowser", "Content Blocker Shield successfully registered.")

                    // Set up Native Messaging Bridge for Ad Blocker Stats & Logins
                    extension.setMessageDelegate(object : WebExtension.MessageDelegate {
                        override fun onMessage(
                            nativeApp: String,
                            message: Any,
                            sender: WebExtension.MessageSender
                        ): GeckoResult<Any>? {
                            if (nativeApp == "browser-app") {
                                handleExtensionMessage(message)
                            }
                            return null
                        }
                    }, "browser-app")
                }
            },
            { error ->
                Log.e("AdFreeBrowser", "Failed to load content blocker extension", error)
            }
        )

        // 4. Restore Session / Create Initial Tab according to Startup Behavior
        tabManager.restoreSession(runtime, preferencesRepo.startupBehavior)

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
                        historyRepo.clearAll()
                        tabManager.sessionRepository.clearSession()
                        runtime.storageController.clearData(StorageController.ClearFlags.ALL)
                        try {
                            cacheDir.deleteRecursively()
                            codeCacheDir.deleteRecursively()
                            externalCacheDir?.deleteRecursively()
                        } catch (e: Exception) {}
                    }
                    finish()
                }
            }
        })

        // 6. Set Compose UI
        setContent {
            BrowserTheme {
                BrowserMainScreen(
                    runtime = runtime,
                    tabManager = tabManager,
                    downloadManager = downloadManager,
                    preferencesRepo = preferencesRepo,
                    passwordRepo = passwordRepo,
                    historyRepo = historyRepo,
                    favoritesRepo = favoritesRepo,
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

    private fun handleExtensionMessage(message: Any) {
        try {
            val json = JSONObject(message.toString())
            val action = json.optString("action")

            if (action == "ad_blocked") {
                val count = json.optInt("tabBlockedCount", 1)
                runOnUiThread {
                    tabManager.activeTab?.let { tab ->
                        if (count > tab.adBlockCount) {
                            tab.adBlockCount = count
                        } else {
                            tab.adBlockCount++
                        }
                    }
                }
            } else if (action == "offer_save_password") {
                val domain = json.optString("domain")
                val username = json.optString("username")
                val password = json.optString("password")
                if (preferencesRepo.savePasswordsEnabled && domain.isNotBlank() && password.isNotBlank()) {
                    runOnUiThread {
                        pendingSaveLogin = com.browser.adfree.ui.components.PendingSaveLogin(
                            domain = domain,
                            username = username,
                            password = password
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AdFreeBrowser", "Error parsing extension message", e)
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
            historyRepo.clearAll()
            tabManager.sessionRepository.clearSession()
            runtime.storageController.clearData(StorageController.ClearFlags.ALL)
            try {
                cacheDir.deleteRecursively()
                codeCacheDir.deleteRecursively()
                externalCacheDir?.deleteRecursively()
            } catch (e: Exception) {}
        } else {
            tabManager.saveSession()
        }
    }

    override fun onDestroy() {
        vpnManager.forceDisconnectOnExit(tabManager.tabs)
        if (preferencesRepo.wipeOnExit) {
            historyRepo.clearAll()
            tabManager.sessionRepository.clearSession()
            runtime.storageController.clearData(StorageController.ClearFlags.ALL)
            try {
                cacheDir.deleteRecursively()
                codeCacheDir.deleteRecursively()
                externalCacheDir?.deleteRecursively()
            } catch (e: Exception) {}
        }
        tabManager.destroy()
        runtime.shutdown()
        super.onDestroy()
    }
}
