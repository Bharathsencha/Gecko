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

package com.browser.adfree.ui.components

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChromeReaderMode
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.HideSource
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Https
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.browser.adfree.BuildConfig
import com.browser.adfree.model.CookiePolicy
import com.browser.adfree.model.SearchEngine
import com.browser.adfree.model.StartupBehavior
import com.browser.adfree.model.TabModel
import com.browser.adfree.model.VpnCountry
import com.browser.adfree.model.DesignPresets
import com.browser.adfree.model.WallDesign
import com.browser.adfree.storage.AppAccentColor
import com.browser.adfree.storage.AppThemeMode
import com.browser.adfree.storage.BrowserPreferencesRepository
import com.browser.adfree.storage.HistoryRepository
import com.browser.adfree.storage.HomepageWallpaper
import com.browser.adfree.storage.SecurePasswordRepository
import com.browser.adfree.tabs.TabManager
import com.browser.adfree.ui.theme.*
import com.browser.adfree.vpn.VpnManager
import com.browser.adfree.vpn.VpnMode
import com.browser.adfree.vpn.VpnStatus
import org.mozilla.geckoview.GeckoRuntime
import java.io.File

private enum class SettingsScreen { MAIN, VPN, APPEARANCE, ABOUT, DESIGNER }

/**
 * Settings as a full page with pushed subpages. Back (gesture, button or the
 * bar's back tile) pops a subpage first, then closes the page.
 */
@Composable
fun SettingsPage(
    preferencesRepo: BrowserPreferencesRepository,
    passwordRepo: SecurePasswordRepository,
    historyRepo: HistoryRepository,
    tabManager: TabManager,
    vpnManager: VpnManager,
    runtime: GeckoRuntime,
    activeTab: TabModel?,
    onExtensionConfigChanged: () -> Unit,
    onOpenPasswords: () -> Unit,
    vaultLock: com.browser.adfree.storage.VaultLock,
    onChangePin: () -> Unit,
    onOpenClearData: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** False while another page is open on top of Settings. */
    backEnabled: Boolean = true
) {
    var screen by rememberSaveable { mutableStateOf(SettingsScreen.MAIN) }
    // Design being edited in the designer: a saved design's id, or null for a new one.
    var designerTarget by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = backEnabled) {
        screen = when (screen) {
            SettingsScreen.MAIN -> { onClose(); SettingsScreen.MAIN }
            SettingsScreen.DESIGNER -> SettingsScreen.APPEARANCE
            else -> SettingsScreen.MAIN
        }
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            if (targetState != SettingsScreen.MAIN) {
                slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
            } else {
                slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg),
        label = "settingsScreen"
    ) { current ->
        when (current) {
            SettingsScreen.MAIN -> MainSettings(
                preferencesRepo = preferencesRepo,
                passwordRepo = passwordRepo,
                tabManager = tabManager,
                vpnManager = vpnManager,
                runtime = runtime,
                activeTab = activeTab,
                onExtensionConfigChanged = onExtensionConfigChanged,
                onOpenVpn = { screen = SettingsScreen.VPN },
                onOpenAppearance = { screen = SettingsScreen.APPEARANCE },
                onOpenAbout = { screen = SettingsScreen.ABOUT },
                onOpenPasswords = onOpenPasswords,
                vaultLock = vaultLock,
                onChangePin = onChangePin,
                onOpenClearData = onOpenClearData,
                onClose = onClose
            )
            SettingsScreen.VPN -> VpnSettings(
                vpnManager = vpnManager,
                onBack = { screen = SettingsScreen.MAIN }
            )
            SettingsScreen.ABOUT -> AboutSettings(
                onOpenUrl = { url ->
                    onOpenUrl(url)
                    onClose()
                },
                onBack = { screen = SettingsScreen.MAIN }
            )
            SettingsScreen.APPEARANCE -> AppearanceSettings(
                preferencesRepo = preferencesRepo,
                onOpenDesigner = { id ->
                    designerTarget = id
                    screen = SettingsScreen.DESIGNER
                },
                onBack = { screen = SettingsScreen.MAIN }
            )
            SettingsScreen.DESIGNER -> {
                val existing = preferencesRepo.customDesigns.firstOrNull { it.id == designerTarget }
                WallpaperDesigner(
                    initial = existing?.design ?: DesignPresets.STARTER,
                    initialName = existing?.name ?: "Design ${preferencesRepo.customDesigns.size + 1}",
                    onSave = { name, design ->
                        preferencesRepo.saveDesign(existing?.id, name, design)
                        screen = SettingsScreen.APPEARANCE
                    },
                    onDelete = existing?.let { d ->
                        {
                            preferencesRepo.deleteDesign(d.id)
                            screen = SettingsScreen.APPEARANCE
                        }
                    },
                    onBack = { screen = SettingsScreen.APPEARANCE }
                )
            }
        }
    }
}

@Composable
private fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .imePadding()
    ) {
        BrutalPageBar(title = title, onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            content()
        }
    }
}

/** Shadow colours for settings cards, cycled section by section like the reference's coloured bars. */
private val CardShadows = listOf(
    Color(0xFFFFD84D), Color(0xFF7FD8C8), Color(0xFFFFB067), Color(0xFFB49CFF), Color(0xFFFF9EC7), Color(0xFF8EC5FF)
)

/** Raised card holding a group of rows: ink outline and a coloured hard shadow. */
@Composable
private fun SettingsGroup(shadow: Color = CardShadows[0], content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(start = 16.dp, end = 21.dp, bottom = 5.dp)
            .fillMaxWidth()
            .brutalBox(fill = SurfaceDark, border = BorderSubtle, shadow = shadow, shadowOffset = 5.dp)
            .clip(BrutalShape)
    ) {
        content()
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .brutalTap(role = Role.RadioButton, shape = androidx.compose.ui.graphics.RectangleShape, pressScale = 1f, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = TextSecondary, fontSize = 12.5.sp, lineHeight = 16.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        SquareRadio(selected)
    }
}

@Composable
private fun SquareRadio(selected: Boolean) {
    val fill by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) AccentCyan else SurfaceDark,
        animationSpec = androidx.compose.animation.core.tween(140),
        label = "radioFill"
    )
    Box(
        modifier = Modifier
            .size(22.dp)
            .brutalBox(
                fill = fill,
                border = BorderSubtle,
                shape = RoundedCornerShape(4.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = selected,
            enter = androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(dampingRatio = 0.5f)),
            exit = androidx.compose.animation.scaleOut()
        ) {
            androidx.compose.material3.Icon(Icons.Default.Check, contentDescription = null, tint = OnAccent, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun Chevron() {
    androidx.compose.material3.Icon(
        Icons.Default.KeyboardArrowRight,
        contentDescription = null,
        tint = TextSecondary,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
private fun ValueChip(text: String, fill: Color = SurfaceCard, color: Color = TextPrimary) {
    Box(
        modifier = Modifier
            .brutalBox(fill = fill, border = BorderSubtle, borderWidth = 1.5.dp, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------------ Main

@Composable
private fun MainSettings(
    preferencesRepo: BrowserPreferencesRepository,
    passwordRepo: SecurePasswordRepository,
    tabManager: TabManager,
    vpnManager: VpnManager,
    runtime: GeckoRuntime,
    activeTab: TabModel?,
    onExtensionConfigChanged: () -> Unit,
    onOpenVpn: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenPasswords: () -> Unit,
    vaultLock: com.browser.adfree.storage.VaultLock,
    onChangePin: () -> Unit,
    onOpenClearData: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var isDefault by remember { mutableStateOf(isAppDefaultBrowser(context)) }
    val defaultBrowserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = isAppDefaultBrowser(context)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isDefault = isAppDefaultBrowser(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsScaffold(title = "Settings", onBack = onClose) {
        val page = activeTab?.takeIf { it.url.isNotBlank() && it.url != TabManager.HOME_URL }
        if (page != null) {
            BrutalSectionLabel("This page")
            SettingsGroup(shadow = CardShadows[0]) {
                BrutalListRow(
                    title = "Zoom",
                    subtitle = "${page.zoomLevel}%",
                    icon = Icons.Default.ZoomIn,
                    trailing = {
                        Row {
                            BrutalIconButton(Icons.Default.Remove, "Zoom out", { if (page.zoomLevel > 50) tabManager.setZoom(page, page.zoomLevel - 10) }, enabled = page.zoomLevel > 50)
                            BrutalIconButton(Icons.Default.Add, "Zoom in", { if (page.zoomLevel < 200) tabManager.setZoom(page, page.zoomLevel + 10) }, enabled = page.zoomLevel < 200)
                        }
                    }
                )
                BrutalHairline(startIndent = 64.dp)
                BrutalListRow(
                    title = "Night mode",
                    subtitle = "Invert this page's colours",
                    icon = Icons.Default.DarkMode,
                    checked = page.isDarkWeb,
                    onCheckedChange = { tabManager.setDarkWeb(page, it) }
                )
                BrutalHairline(startIndent = 64.dp)
                BrutalListRow(
                    title = "Desktop site",
                    icon = Icons.Default.DesktopWindows,
                    checked = page.isDesktopMode,
                    onCheckedChange = { tabManager.toggleDesktopMode(page) }
                )
                BrutalHairline(startIndent = 64.dp)
                BrutalListRow(
                    title = "Reader view",
                    subtitle = if (page.isExtractingArticle) "Extracting article" else "Show the article text only",
                    icon = Icons.Default.ChromeReaderMode,
                    enabled = !page.isExtractingArticle,
                    onClick = {
                        tabManager.requestReaderMode(page)
                        onClose()
                    },
                    trailing = { Chevron() }
                )
            }
        }

        BrutalSectionLabel("Privacy")
        SettingsGroup(shadow = CardShadows[1]) {
            val status = vpnManager.status
            BrutalListRow(
                title = "VPN",
                subtitle = when (status) {
                    VpnStatus.Off -> "Off. Route all traffic through Tor or a free relay"
                    is VpnStatus.Connecting -> status.message
                    is VpnStatus.Connected -> "${if (status.mode == VpnMode.TOR) "Tor" else "Free relay"}, exit ${VpnCountry.nameFor(status.exitCountry)}"
                    is VpnStatus.Failed -> status.reason
                },
                icon = Icons.Default.VpnLock,
                iconFill = if (status is VpnStatus.Connected) AccentCyan else null,
                onClick = onOpenVpn,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (status) {
                            is VpnStatus.Connected -> ValueChip("ON", fill = AccentCyan, color = OnAccent)
                            is VpnStatus.Connecting -> ValueChip("...")
                            is VpnStatus.Failed -> ValueChip("ERROR", fill = DangerRed, color = InkBlack)
                            VpnStatus.Off -> ValueChip("OFF")
                        }
                        Chevron()
                    }
                }
            )
            BrutalHairline(startIndent = 64.dp)
            BrutalListRow(
                title = "Block ads and trackers",
                subtitle = "Gecko tracking protection and the built-in request filter. uBlock Origin always runs (except on YouTube, where ads are fast-forwarded).",
                icon = Icons.Default.Shield,
                checked = preferencesRepo.adBlockingEnabled,
                onCheckedChange = {
                    preferencesRepo.setAdBlockingPreference(it)
                    tabManager.updateAdBlocking(it)
                    onExtensionConfigChanged()
                }
            )
            BrutalHairline(startIndent = 64.dp)
            BrutalListRow(
                title = "Hide ad placeholders",
                subtitle = "Collapse empty ad boxes left on pages",
                icon = Icons.Default.HideSource,
                checked = preferencesRepo.cosmeticBlockingEnabled,
                onCheckedChange = {
                    preferencesRepo.setCosmeticBlockingPreference(it)
                    onExtensionConfigChanged()
                }
            )
            BrutalHairline(startIndent = 64.dp)
            BrutalListRow(
                title = "Hide Google AI Overviews",
                icon = Icons.Default.FilterAlt,
                checked = preferencesRepo.blockAiOverviews,
                onCheckedChange = {
                    preferencesRepo.setBlockAiOverviewsPreference(it)
                    onExtensionConfigChanged()
                }
            )
            BrutalHairline(startIndent = 64.dp)
            BrutalListRow(
                title = "Incognito keyboard",
                subtitle = "Ask the keyboard not to learn or remember what you type. Always on in private tabs",
                icon = Icons.Default.Keyboard,
                checked = preferencesRepo.incognitoKeyboard,
                onCheckedChange = { preferencesRepo.setIncognitoKeyboardPreference(it) }
            )
            BrutalHairline(startIndent = 64.dp)
            BrutalListRow(
                title = "HTTPS-Only mode",
                subtitle = "Warn before loading unencrypted pages",
                icon = Icons.Default.Https,
                checked = preferencesRepo.httpsOnlyMode,
                onCheckedChange = { preferencesRepo.setHttpsOnlyModePreference(it) }
            )
        }

        BrutalSectionLabel("Cookies")
        SettingsGroup(shadow = CardShadows[2]) {
            CookiePolicy.values().forEachIndexed { index, policy ->
                if (index > 0) BrutalHairline(startIndent = 16.dp)
                ChoiceRow(
                    title = policy.title,
                    subtitle = policy.description,
                    selected = preferencesRepo.cookiePolicy == policy,
                    onClick = {
                        preferencesRepo.setCookiePolicyPreference(policy)
                        try {
                            runtime.settings.contentBlocking.setCookieBehavior(policy.geckoValue)
                        } catch (e: Exception) {
                            Log.w("Settings", "Cookie policy not applied: ${e.message}")
                        }
                    }
                )
            }
        }

        BrutalSectionLabel("Appearance")
        SettingsGroup(shadow = CardShadows[3]) {
            BrutalListRow(
                title = "Theme and layout",
                subtitle = "${preferencesRepo.themeMode.displayName}, ${preferencesRepo.accentColor.displayName}, address bar ${if (preferencesRepo.bottomAddressBar) "bottom" else "top"}",
                icon = Icons.Default.Palette,
                onClick = onOpenAppearance,
                trailing = { Chevron() }
            )
            BrutalHairline(startIndent = 64.dp)
            BrutalListRow(
                title = "Night mode for all sites",
                subtitle = "Invert page colours by default",
                icon = Icons.Default.DarkMode,
                checked = preferencesRepo.darkWebDefault,
                onCheckedChange = {
                    preferencesRepo.setDarkWebDefaultPreference(it)
                    tabManager.setDarkWebModeAll(it)
                    onExtensionConfigChanged()
                }
            )
        }

        BrutalSectionLabel("Search engine")
        SettingsGroup(shadow = CardShadows[4]) {
            SearchEngine.values().forEachIndexed { index, engine ->
                if (index > 0) BrutalHairline(startIndent = 16.dp)
                ChoiceRow(
                    title = engine.title,
                    selected = preferencesRepo.searchEngine == engine,
                    onClick = { preferencesRepo.setSearchEnginePreference(engine) }
                )
            }
        }

        BrutalSectionLabel("On startup")
        SettingsGroup(shadow = CardShadows[5]) {
            StartupBehavior.values().forEachIndexed { index, behavior ->
                if (index > 0) BrutalHairline(startIndent = 16.dp)
                ChoiceRow(
                    title = behavior.title,
                    subtitle = behavior.description,
                    selected = preferencesRepo.startupBehavior == behavior,
                    onClick = { preferencesRepo.setStartupBehaviorPreference(behavior) }
                )
            }
        }

        BrutalSectionLabel("Downloads")
        SettingsGroup(shadow = CardShadows[0]) {
            BrutalListRow(
                title = "Download button",
                subtitle = "Show a button to save video or audio from the page you are on",
                icon = Icons.Default.SmartDisplay,
                checked = preferencesRepo.videoDownloaderEnabled,
                onCheckedChange = { preferencesRepo.setVideoDownloaderEnabledPreference(it) }
            )
            BrutalHairline(startIndent = 64.dp)
            BrutalListRow(
                title = "Prefer AV1",
                subtitle = "Smaller files; older phones may not play them",
                icon = Icons.Default.HighQuality,
                checked = preferencesRepo.preferAv1Codec,
                onCheckedChange = { preferencesRepo.setPreferAv1CodecPreference(it) }
            )
            BrutalHairline(startIndent = 64.dp)
            BrutalListRow(
                title = "Show WebM formats",
                icon = Icons.Default.Download,
                checked = preferencesRepo.showWebmFormats,
                onCheckedChange = { preferencesRepo.setShowWebmFormatsPreference(it) }
            )
        }

        BrutalSectionLabel("Passwords")
        SettingsGroup(shadow = CardShadows[1]) {
            BrutalListRow(
                title = "Saved passwords",
                subtitle = "Encrypted with a hardware-backed key",
                icon = Icons.Default.Key,
                onClick = onOpenPasswords,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ValueChip("${passwordRepo.logins.size}")
                        Chevron()
                    }
                }
            )
            var unlockMethod by remember { mutableStateOf(vaultLock.method) }
            BrutalHairline(startIndent = 16.dp)
            ChoiceRow(
                title = "Unlock with fingerprint or screen lock",
                subtitle = if (vaultLock.systemAvailable) "Uses the phone's own security" else "Set up a fingerprint or screen lock on the phone first",
                selected = unlockMethod == com.browser.adfree.storage.VaultLock.Method.SYSTEM && vaultLock.systemAvailable,
                onClick = {
                    if (vaultLock.systemAvailable) {
                        vaultLock.method = com.browser.adfree.storage.VaultLock.Method.SYSTEM
                        unlockMethod = vaultLock.method
                    }
                }
            )
            BrutalHairline(startIndent = 16.dp)
            ChoiceRow(
                title = "Unlock with app PIN",
                subtitle = if (vaultLock.hasPin) "4 digits. Tap to change" else "4 digits, set the first time you open passwords",
                selected = unlockMethod == com.browser.adfree.storage.VaultLock.Method.PIN || !vaultLock.systemAvailable,
                onClick = {
                    vaultLock.method = com.browser.adfree.storage.VaultLock.Method.PIN
                    unlockMethod = vaultLock.method
                    onChangePin()
                }
            )
        }

        BrutalSectionLabel("Browsing data")
        SettingsGroup(shadow = CardShadows[2]) {
            BrutalListRow(
                title = "Clear data when leaving",
                subtitle = "History, cookies and cache are wiped when the app closes",
                icon = Icons.Default.LocalFireDepartment,
                checked = preferencesRepo.wipeOnExit,
                onCheckedChange = { preferencesRepo.setWipeOnExitPreference(it) }
            )
            BrutalHairline(startIndent = 64.dp)
            BrutalListRow(
                title = "Clear browsing data",
                icon = Icons.Default.DeleteOutline,
                titleColor = DangerRed,
                onClick = onOpenClearData,
                trailing = { Chevron() }
            )
        }

        if (!isDefault) {
            BrutalSectionLabel("Default browser")
            SettingsGroup(shadow = CardShadows[3]) {
                BrutalListRow(
                    title = "Set as default browser",
                    subtitle = "Open links from other apps here",
                    icon = Icons.Default.Language,
                    onClick = { requestSetDefaultBrowser(context, defaultBrowserLauncher) },
                    trailing = { Chevron() }
                )
            }
        }

        BrutalSectionLabel("About")
        SettingsGroup(shadow = CardShadows[4]) {
            BrutalListRow(
                title = "Gecko ${BuildConfig.VERSION_NAME}",
                subtitle = "Free software under the GNU GPL v3. Credits and licences",
                icon = Icons.Default.Info,
                onClick = onOpenAbout,
                trailing = { Chevron() }
            )
        }
    }
}

// ------------------------------------------------------------------ VPN

@Composable
private fun VpnSettings(vpnManager: VpnManager, onBack: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(vpnManager.lastMode) }
    val status = vpnManager.status
    val activeMode = when (status) {
        is VpnStatus.Connected -> status.mode
        is VpnStatus.Connecting -> status.mode
        is VpnStatus.Failed -> status.mode
        VpnStatus.Off -> null
    }

    fun connect(country: String) {
        if (mode == VpnMode.TOR) vpnManager.connectTor(country) else vpnManager.connectProxy(country)
    }

    val selectedCountry = if (mode == VpnMode.TOR) vpnManager.torExitCountry else vpnManager.proxyCountry

    SettingsScaffold(title = "VPN", onBack = onBack) {
        // Status panel
        Column(
            modifier = Modifier
                .padding(start = 16.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
                .fillMaxWidth()
                .brutalBox(
                    fill = if (status is VpnStatus.Connected) AccentCyan else SurfaceDark,
                    border = BorderSubtle,
                    shadow = HardShadow
                )
                .padding(16.dp)
        ) {
            val onPanel = if (status is VpnStatus.Connected) OnAccent else TextPrimary
            val onPanelSecondary = if (status is VpnStatus.Connected) OnAccent.copy(alpha = 0.75f) else TextSecondary
            Text(
                text = when (status) {
                    VpnStatus.Off -> "Not connected"
                    is VpnStatus.Connecting -> "Connecting"
                    is VpnStatus.Connected -> "Connected"
                    is VpnStatus.Failed -> "Connection failed"
                },
                color = onPanel,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            when (status) {
                VpnStatus.Off -> Text("Your real IP address is visible to sites.", color = onPanelSecondary, fontSize = 13.sp)
                is VpnStatus.Connecting -> {
                    Text(status.message, color = onPanelSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    BrutalProgressLine(progress = status.percent?.let { it / 100f })
                }
                is VpnStatus.Connected -> {
                    Text(
                        "Via ${if (status.mode == VpnMode.TOR) "Tor" else "free relay"}, exit in ${VpnCountry.nameFor(status.exitCountry)}",
                        color = onPanelSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Exit IP ${status.exitIp}" + (status.latencyMs?.let { "  |  ${it} ms" } ?: ""),
                        color = onPanel,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                is VpnStatus.Failed -> Text(status.reason, color = onPanelSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(14.dp))
            when (status) {
                VpnStatus.Off -> BrutalButton(
                    text = if (mode == VpnMode.TOR) "Connect to Tor" else "Connect to free relay",
                    onClick = { connect(selectedCountry) },
                    modifier = Modifier.fillMaxWidth()
                )
                is VpnStatus.Connecting -> BrutalOutlineButton("Cancel", { vpnManager.disconnect() }, Modifier.fillMaxWidth())
                is VpnStatus.Connected -> BrutalOutlineButton("Disconnect", { vpnManager.disconnect() }, Modifier.fillMaxWidth())
                is VpnStatus.Failed -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BrutalButton("Retry", { connect(selectedCountry) }, Modifier.weight(1f))
                    BrutalOutlineButton("Turn off", { vpnManager.disconnect() }, Modifier.weight(1f))
                }
            }
        }

        BrutalSectionLabel("Network")
        SettingsGroup(shadow = CardShadows[5]) {
            ChoiceRow(
                title = "Tor",
                subtitle = "Three encrypted hops, strongest anonymity. Pages load slower and some sites show captchas.",
                selected = mode == VpnMode.TOR,
                onClick = { mode = VpnMode.TOR }
            )
            BrutalHairline(startIndent = 16.dp)
            ChoiceRow(
                title = "Free relay",
                subtitle = "Public SOCKS5 relay, found and tested when you connect. Faster, but its operator can see traffic to non-HTTPS sites.",
                selected = mode == VpnMode.PROXY,
                onClick = { mode = VpnMode.PROXY }
            )
        }

        BrutalSectionLabel(if (mode == VpnMode.TOR) "Exit country" else "Relay country")
        SettingsGroup(shadow = CardShadows[0]) {
            val options = buildList {
                if (mode == VpnMode.TOR) add(VpnCountry("", "Any country (fastest)"))
                addAll(VpnCountry.COUNTRIES)
            }
            options.forEachIndexed { index, country ->
                if (index > 0) BrutalHairline(startIndent = 16.dp)
                ChoiceRow(
                    title = country.name,
                    selected = selectedCountry.equals(country.code, ignoreCase = true) &&
                        (mode == VpnMode.TOR || country.code.isNotBlank()),
                    onClick = {
                        // Picking a country connects (or reconnects) right away.
                        connect(country.code)
                    },
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 28.dp)
                                .brutalBox(fill = SurfaceCard, border = BorderSubtle, borderWidth = 1.5.dp, shape = RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                country.code.ifBlank { "ANY" },
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                )
            }
        }
        if (activeMode != null && activeMode != mode) {
            Text(
                "Picking a country switches the connection to ${if (mode == VpnMode.TOR) "Tor" else "a free relay"}.",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

// ------------------------------------------------------------------ About

private data class Credit(val name: String, val role: String, val license: String, val url: String)

private val CREDITS = listOf(
    Credit("Mozilla GeckoView", "Web engine", "MPL 2.0", "https://mozilla.github.io/geckoview/"),
    Credit("uBlock Origin", "Ad and tracker blocking", "GPL 3.0", "https://github.com/gorhill/uBlock"),
    Credit("Tor", "Anonymous network for the VPN", "BSD 3-Clause", "https://www.torproject.org/"),
    Credit("tor-android", "Tor packaging for Android (Guardian Project)", "BSD 3-Clause", "https://github.com/guardianproject/tor-android"),
    Credit("jtorctl", "Tor control protocol", "BSD 3-Clause", "https://github.com/guardianproject/jtorctl"),
    Credit("yt-dlp", "Video and audio extraction", "Unlicense", "https://github.com/yt-dlp/yt-dlp"),
    Credit("youtubedl-android", "yt-dlp runtime for Android", "GPL 3.0", "https://github.com/JunkFood02/youtubedl-android"),
    Credit("NewPipe Extractor", "YouTube stream extraction", "GPL 3.0", "https://github.com/TeamNewPipe/NewPipeExtractor"),
    Credit("OkHttp", "Native HTTP client", "Apache 2.0", "https://square.github.io/okhttp/"),
    Credit("Coil", "Image loading", "Apache 2.0", "https://coil-kt.github.io/coil/"),
    Credit("SnakeYAML", "Engine configuration parsing", "Apache 2.0", "https://bitbucket.org/snakeyaml/snakeyaml"),
    Credit("Jetpack Compose and AndroidX", "User interface", "Apache 2.0", "https://developer.android.com/jetpack"),
    Credit("Material Icons", "Icons", "Apache 2.0", "https://fonts.google.com/icons"),
    Credit("Space Grotesk", "Typeface (Florian Karsten)", "SIL OFL 1.1", "https://github.com/floriankarsten/space-grotesk")
)

@Composable
private fun AboutSettings(onOpenUrl: (String) -> Unit, onBack: () -> Unit) {
    SettingsScaffold(title = "About", onBack = onBack) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.browser.adfree.R.drawable.gecko_logo),
                contentDescription = null,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Gecko", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Version ${BuildConfig.VERSION_NAME}", color = TextSecondary, fontSize = 13.sp)
            }
        }
        Text(
            "Gecko is free software: you can redistribute it and modify it under the terms of the " +
                "GNU General Public License, version 3 or later. It comes with no warranty.",
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(16.dp)
        )
        SettingsGroup(shadow = CardShadows[1]) {
            BrutalListRow(
                title = "Source code",
                subtitle = "github.com/Bharathsencha/Gecko",
                onClick = { onOpenUrl("https://github.com/Bharathsencha/Gecko") },
                trailing = { Chevron() }
            )
            BrutalHairline(startIndent = 16.dp)
            BrutalListRow(
                title = "GNU General Public License v3",
                onClick = { onOpenUrl("https://www.gnu.org/licenses/gpl-3.0.html") },
                trailing = { Chevron() }
            )
        }

        BrutalSectionLabel("Built with")
        SettingsGroup(shadow = CardShadows[2]) {
            CREDITS.forEachIndexed { index, credit ->
                if (index > 0) BrutalHairline(startIndent = 16.dp)
                BrutalListRow(
                    title = credit.name,
                    subtitle = credit.role,
                    onClick = { onOpenUrl(credit.url) },
                    trailing = { ValueChip(credit.license) }
                )
            }
        }
        Text(
            "Gecko is not affiliated with Mozilla, the Tor Project or any project listed here. " +
                "Firefox and Tor are trademarks of their owners.",
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(16.dp)
        )
    }
}

// ------------------------------------------------------------------ Appearance

@Composable
private fun AppearanceSettings(preferencesRepo: BrowserPreferencesRepository, onOpenDesigner: (String?) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val saved = saveCustomWallpaper(context, uri)
            if (!saved.isNullOrBlank()) preferencesRepo.setHomepageWallpaperPreference(HomepageWallpaper.CUSTOM, saved)
        }
    }

    SettingsScaffold(title = "Theme and layout", onBack = onBack) {
        BrutalSectionLabel("Theme")
        SettingsGroup(shadow = CardShadows[3]) {
            AppThemeMode.values().forEachIndexed { index, mode ->
                if (index > 0) BrutalHairline(startIndent = 16.dp)
                ChoiceRow(
                    title = mode.displayName,
                    subtitle = if (mode == AppThemeMode.SYSTEM) "Follow the phone's dark theme setting" else null,
                    selected = preferencesRepo.themeMode == mode,
                    onClick = { preferencesRepo.setThemeModePreference(mode) }
                )
            }
        }

        BrutalSectionLabel("Accent colour")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppAccentColor.values().forEach { accent ->
                val selected = preferencesRepo.accentColor == accent
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .brutalPressable(
                            fill = Color(accent.hex),
                            border = BorderSubtle,
                            shadow = HardShadow,
                            shadowOffset = if (selected) 0.dp else 3.dp,
                            role = Role.RadioButton,
                            onClick = { preferencesRepo.setAccentColorPreference(accent) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        androidx.compose.material3.Icon(Icons.Default.Check, contentDescription = accent.displayName, tint = InkBlack, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        BrutalSectionLabel("Address bar")
        SettingsGroup(shadow = CardShadows[4]) {
            ChoiceRow(
                title = "Top",
                selected = !preferencesRepo.bottomAddressBar,
                onClick = { preferencesRepo.setBottomAddressBarPreference(false) }
            )
            BrutalHairline(startIndent = 16.dp)
            ChoiceRow(
                title = "Bottom",
                subtitle = "Above the toolbar, within thumb reach",
                selected = preferencesRepo.bottomAddressBar,
                onClick = { preferencesRepo.setBottomAddressBarPreference(true) }
            )
        }

        BrutalSectionLabel("Start page")
        SettingsGroup(shadow = CardShadows[5]) {
            BrutalListRow(
                title = "Show favourites",
                icon = Icons.Default.Star,
                checked = preferencesRepo.showFavoritesGrid,
                onCheckedChange = { preferencesRepo.setShowFavoritesGridPreference(it) }
            )
            if (preferencesRepo.showFavoritesGrid) {
                BrutalHairline(startIndent = 64.dp)
                BrutalListRow(
                    title = "Favourites per row",
                    subtitle = "${preferencesRepo.favoritesGridColumns}",
                    icon = Icons.Default.GridView,
                    trailing = {
                        Row {
                            BrutalIconButton(Icons.Default.Remove, "Fewer", { preferencesRepo.setFavoritesGridColumnsPreference(preferencesRepo.favoritesGridColumns - 1) }, enabled = preferencesRepo.favoritesGridColumns > 3)
                            BrutalIconButton(Icons.Default.Add, "More", { preferencesRepo.setFavoritesGridColumnsPreference(preferencesRepo.favoritesGridColumns + 1) }, enabled = preferencesRepo.favoritesGridColumns < 5)
                        }
                    }
                )
            }
            BrutalHairline(startIndent = 64.dp)
            Text(
                "Background",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 10.dp)
            )
            WallpaperGrid(
                preferencesRepo = preferencesRepo,
                onPhoto = { photoPicker.launch("image/*") },
                onNewDesign = { onOpenDesigner(null) },
                onEditDesign = { id -> onOpenDesigner(id) }
            )
            val active = preferencesRepo.customDesigns.firstOrNull { it.id == preferencesRepo.selectedDesignId }
            if (preferencesRepo.homepageWallpaper == HomepageWallpaper.DESIGN && active != null) {
                BrutalHairline(startIndent = 64.dp)
                BrutalListRow(
                    title = "Edit \"${active.name}\"",
                    subtitle = "Change shapes and colours, rename or delete",
                    icon = Icons.Default.Palette,
                    onClick = { onOpenDesigner(active.id) },
                    trailing = { Chevron() }
                )
            }
            if (preferencesRepo.homepageWallpaper == HomepageWallpaper.CUSTOM || wallpaperBrush(preferencesRepo.homepageWallpaper) != null) {
                BrutalHairline(startIndent = 16.dp)
                LabeledSlider("Dim", preferencesRepo.wallpaperDim, 0f..0.8f) { preferencesRepo.setWallpaperDimPreference(it) }
                if (preferencesRepo.homepageWallpaper == HomepageWallpaper.CUSTOM) {
                    LabeledSlider("Blur", preferencesRepo.wallpaperBlur, 0f..25f) { preferencesRepo.setWallpaperBlurPreference(it) }
                }
            }
        }
    }
}

/**
 * Background tiles: block presets, the user's own designs and a "+" tile to
 * make another, then patterns, pastels, gradients and a photo.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun WallpaperGrid(
    preferencesRepo: BrowserPreferencesRepository,
    onPhoto: () -> Unit,
    onNewDesign: () -> Unit,
    onEditDesign: (String) -> Unit
) {
    val selected = preferencesRepo.homepageWallpaper
    // Kinds are kept apart (no headings): blocks and own designs, patterns,
    // flat colours, gradients, photo.
    val groups = listOf(
        listOf(HomepageWallpaper.BLOCKS, HomepageWallpaper.STACK, HomepageWallpaper.SHAPES, HomepageWallpaper.MONDRIAN, HomepageWallpaper.DESIGN),
        listOf(HomepageWallpaper.DOTS, HomepageWallpaper.GRID, HomepageWallpaper.STRIPES, HomepageWallpaper.CHECKER),
        listOf(HomepageWallpaper.DEFAULT, HomepageWallpaper.BUTTER, HomepageWallpaper.PEACH, HomepageWallpaper.MINT, HomepageWallpaper.LAVENDER),
        listOf(HomepageWallpaper.AURORA, HomepageWallpaper.NEBULA, HomepageWallpaper.CYBER, HomepageWallpaper.OLED, HomepageWallpaper.FOREST, HomepageWallpaper.SUNSET),
        listOf(HomepageWallpaper.CUSTOM)
    )
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 19.dp, bottom = 16.dp)
    ) {
        val tileWidth = (maxWidth - 12.dp * 3) / 4
        Column {
            groups.forEachIndexed { index, group ->
                if (index > 0) {
                    Spacer(Modifier.height(16.dp))
                    BrutalHairline()
                    Spacer(Modifier.height(16.dp))
                }
                androidx.compose.foundation.layout.FlowRow(
                    maxItemsInEachRow = 4,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    group.forEach { wp ->
                        if (wp == HomepageWallpaper.DESIGN) {
                            preferencesRepo.customDesigns.forEach { saved ->
                                WallpaperTile(
                                    width = tileWidth,
                                    label = saved.name,
                                    selected = selected == HomepageWallpaper.DESIGN && preferencesRepo.selectedDesignId == saved.id,
                                    onClick = { preferencesRepo.selectDesign(saved.id) },
                                    onLongClick = { onEditDesign(saved.id) }
                                ) { mod -> DesignCanvas(saved.design, mod) }
                            }
                            WallpaperTile(width = tileWidth, label = "New design", selected = false, onClick = onNewDesign) { _ ->
                                androidx.compose.material3.Icon(Icons.Default.Add, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(26.dp))
                            }
                        } else {
                            WallpaperTile(
                                width = tileWidth,
                                label = wp.displayName,
                                selected = wp == selected,
                                onClick = { if (wp == HomepageWallpaper.CUSTOM) onPhoto() else preferencesRepo.setHomepageWallpaperPreference(wp) }
                            ) { mod ->
                                if (wp == HomepageWallpaper.CUSTOM) {
                                    androidx.compose.material3.Icon(Icons.Default.Image, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(22.dp))
                                } else {
                                    WallpaperBackground(wallpaper = wp, modifier = mod)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun WallpaperTile(
    width: androidx.compose.ui.unit.Dp,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    preview: @Composable (Modifier) -> Unit
) {
    val view = androidx.compose.ui.platform.LocalView.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(width)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .brutalBox(fill = SurfaceCard, border = BorderSubtle, shadow = if (selected) null else HardShadow, shadowOffset = 3.dp)
                .combinedClickable(
                    role = Role.RadioButton,
                    onClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        onClick()
                    },
                    onLongClick = onLongClick?.let { l ->
                        {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            l()
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            preview(
                Modifier
                    .matchParentSize()
                    .padding(2.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            if (selected) {
                Box(
                    Modifier
                        .size(26.dp)
                        .brutalBox(fill = AccentCyan, border = BorderSubtle, shape = RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(Icons.Default.Check, contentDescription = null, tint = OnAccent, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = if (selected) TextPrimary else TextSecondary,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(56.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = BorderSubtle,
                activeTrackColor = BorderSubtle,
                inactiveTrackColor = SurfaceCard
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

// ------------------------------------------------------------------ Helpers

fun isAppDefaultBrowser(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"))
    val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.resolveActivity(
            intent,
            android.content.pm.PackageManager.ResolveInfoFlags.of(android.content.pm.PackageManager.MATCH_DEFAULT_ONLY.toLong())
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
    }
    return resolveInfo?.activityInfo?.packageName == context.packageName
}

fun requestSetDefaultBrowser(context: Context, launcher: ActivityResultLauncher<Intent>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
            try {
                launcher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
                return
            } catch (e: Exception) {
                Log.e("Settings", "RoleManager error", e)
            }
        }
    }
    try {
        launcher.launch(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    } catch (e: Exception) {
        try {
            launcher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            })
        } catch (e2: Exception) {
            Log.w("Settings", "No settings screen for default browser")
        }
    }
}

fun saveCustomWallpaper(context: Context, uri: Uri): String? {
    return try {
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val ext = when {
            mimeType.contains("gif") -> "gif"
            mimeType.contains("png") -> "png"
            mimeType.contains("webp") -> "webp"
            else -> "jpg"
        }
        val destinationFile = File(context.filesDir, "custom_wallpaper.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            destinationFile.outputStream().use { output -> input.copyTo(output) }
        }
        destinationFile.absolutePath
    } catch (e: Exception) {
        null
    }
}
