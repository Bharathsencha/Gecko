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

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.HideSource
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Https
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import com.browser.adfree.storage.BrowserPreferencesRepository
import com.browser.adfree.storage.HistoryRepository
import com.browser.adfree.storage.SecurePasswordRepository
import com.browser.adfree.tabs.TabManager
import com.browser.adfree.ui.theme.*
import com.browser.adfree.vpn.VpnManager
import com.browser.adfree.vpn.VpnMode
import com.browser.adfree.vpn.VpnStatus
import org.mozilla.geckoview.GeckoRuntime

internal enum class SettingsScreen { MAIN, VPN, APPEARANCE, ABOUT, DESIGNER, PRIVACY_DOC, TERMS_DOC, ADVANCED, SITE_PERMISSIONS }

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
    onOpenTrackerReport: () -> Unit,
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
            SettingsScreen.PRIVACY_DOC, SettingsScreen.TERMS_DOC -> SettingsScreen.ABOUT
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
                onOpenTrackerReport = onOpenTrackerReport,
                onOpenAdvanced = { screen = SettingsScreen.ADVANCED },
                onOpenSitePermissions = { screen = SettingsScreen.SITE_PERMISSIONS },
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
                onOpenPrivacy = { screen = SettingsScreen.PRIVACY_DOC },
                onOpenTerms = { screen = SettingsScreen.TERMS_DOC },
                onBack = { screen = SettingsScreen.MAIN }
            )
            SettingsScreen.SITE_PERMISSIONS -> SitePermissionsPage(runtime) { screen = SettingsScreen.MAIN }
            SettingsScreen.ADVANCED -> AdvancedSettings(preferencesRepo, runtime) { screen = SettingsScreen.MAIN }
            SettingsScreen.PRIVACY_DOC -> LegalPage("PRIVACY.md", "Privacy policy") { screen = SettingsScreen.ABOUT }
            SettingsScreen.TERMS_DOC -> LegalPage("TERMS.md", "Terms of use") { screen = SettingsScreen.ABOUT }
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
internal fun SettingsScaffold(
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
internal val CardShadows = listOf(
    Color(0xFFFFD84D), Color(0xFF7FD8C8), Color(0xFFFFB067), Color(0xFFB49CFF), Color(0xFFFF9EC7), Color(0xFF8EC5FF)
)

/** Raised card holding a group of rows: ink outline and a coloured hard shadow. */
@Composable
internal fun SettingsGroup(shadow: Color = CardShadows[0], content: @Composable () -> Unit) {
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
internal fun ChoiceRow(
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
internal fun SquareRadio(selected: Boolean) {
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
internal fun Chevron() {
    androidx.compose.material3.Icon(
        Icons.Default.KeyboardArrowRight,
        contentDescription = null,
        tint = TextSecondary,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
internal fun ValueChip(text: String, fill: Color = SurfaceCard, color: Color = TextPrimary) {
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
    onOpenTrackerReport: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenSitePermissions: () -> Unit,
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
                subtitle = "Tracking protection and the built-in request filter. uBlock Origin always runs.",
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
                title = "Site permissions",
                subtitle = "Location, camera, microphone and notifications you allowed or blocked",
                icon = Icons.Default.LocationOn,
                onClick = onOpenSitePermissions,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val n = com.browser.adfree.storage.SitePermissions.all.size
                        if (n > 0) ValueChip("$n")
                        Chevron()
                    }
                }
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

        BrutalSectionLabel("Block screenshots")
        SettingsGroup(shadow = CardShadows[3]) {
            BrutalListRow(
                title = "Normal tabs",
                subtitle = "Also hides the page from screen recording and the recent-apps view",
                icon = Icons.Default.Screenshot,
                checked = preferencesRepo.blockScreenshots,
                onCheckedChange = { preferencesRepo.setBlockScreenshotsPreference(it) }
            )
            BrutalHairline(startIndent = 64.dp)
            BrutalListRow(
                title = "Private tabs",
                subtitle = "On by default",
                icon = Icons.Default.VisibilityOff,
                checked = preferencesRepo.blockScreenshotsPrivate,
                onCheckedChange = { preferencesRepo.setBlockScreenshotsPrivatePreference(it) }
            )
        }

        BrutalSectionLabel("Tracker report")
        SettingsGroup(shadow = CardShadows[5]) {
            val stats = LocalTrackerStats.current
            BrutalListRow(
                title = "${stats?.snapshot?.week ?: 0} blocked this week",
                subtitle = "Trackers and ads stopped in normal tabs, counted on this phone only",
                icon = Icons.Default.Shield,
                onClick = onOpenTrackerReport,
                trailing = { Chevron() }
            )
            BrutalHairline(startIndent = 64.dp)
            BrutalListRow(
                title = "Show on the start page",
                icon = Icons.Default.Star,
                checked = preferencesRepo.showTrackerStats,
                onCheckedChange = { preferencesRepo.setShowTrackerStatsPreference(it) }
            )
            BrutalHairline(startIndent = 64.dp)
            BrutalListRow(
                title = "Reset when history is cleared",
                subtitle = "Clearing history also clears the tracker report",
                icon = Icons.Default.History,
                checked = preferencesRepo.resetStatsWithHistory,
                onCheckedChange = { preferencesRepo.setResetStatsWithHistoryPreference(it) }
            )
        }

        BrutalSectionLabel("Containers")
        ContainersSection(runtime)

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
            if (preferencesRepo.videoDownloaderEnabled) {
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
            Text(
                "Clear button",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, top = 14.dp)
            )
            Text(
                "Icon and animation used when you clear browsing data",
                color = TextSecondary,
                fontSize = 12.5.sp,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 12.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 19.dp, bottom = 16.dp)
            ) {
                com.browser.adfree.storage.ClearStyle.values().forEach { style ->
                    val selected = preferencesRepo.clearStyle == style
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .brutalPressable(
                                    fill = clearStyleFill(style),
                                    border = BorderSubtle,
                                    shadow = HardShadow,
                                    shadowOffset = if (selected) 0.dp else 3.dp,
                                    role = Role.RadioButton,
                                    onClick = { preferencesRepo.setClearStylePreference(style) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Icon(clearStyleIcon(style), contentDescription = style.displayName, tint = InkBlack, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            style.displayName,
                            color = if (selected) TextPrimary else TextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
            BrutalHairline(startIndent = 16.dp)
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

        BrutalSectionLabel("Advanced")
        SettingsGroup(shadow = CardShadows[1]) {
            BrutalListRow(
                title = "Encrypted DNS",
                subtitle = preferencesRepo.dnsProvider.let { if (it == com.browser.adfree.storage.DnsProvider.OFF) "Off" else it.displayName + if (preferencesRepo.dnsStrict) ", encrypted only" else "" },
                icon = Icons.Default.Https,
                onClick = onOpenAdvanced,
                trailing = { Chevron() }
            )
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

/** Tracker stats for Settings, provided by the main screen. */
val LocalTrackerStats = androidx.compose.runtime.staticCompositionLocalOf<com.browser.adfree.storage.TrackerStatsRepository?> { null }
