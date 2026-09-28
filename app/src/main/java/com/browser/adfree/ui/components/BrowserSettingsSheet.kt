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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.model.CookiePolicy
import com.browser.adfree.model.SearchEngine
import com.browser.adfree.model.StartupBehavior
import com.browser.adfree.model.VpnCountry
import com.browser.adfree.storage.BrowserPreferencesRepository
import com.browser.adfree.storage.HistoryRepository
import com.browser.adfree.storage.SecurePasswordRepository
import com.browser.adfree.tabs.TabManager
import com.browser.adfree.ui.theme.*
import com.browser.adfree.vpn.FreeVpnManager
import org.mozilla.geckoview.GeckoRuntime

enum class SettingsScreen {
    MAIN,
    VPN_LOCATIONS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserSettingsSheet(
    preferencesRepo: BrowserPreferencesRepository,
    passwordRepo: SecurePasswordRepository,
    historyRepo: HistoryRepository,
    tabManager: TabManager,
    vpnManager: FreeVpnManager,
    runtime: GeckoRuntime,
    onOpenPasswords: () -> Unit,
    onOpenHistory: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(SettingsScreen.MAIN) }
    var showClearDialog by remember { mutableStateOf(false) }

    // Intercept back button when in VPN Locations subview
    BackHandler(enabled = currentScreen == SettingsScreen.VPN_LOCATIONS) {
        currentScreen = SettingsScreen.MAIN
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF141416),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(Color(0xFF3A3A3C), RoundedCornerShape(2.dp))
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                if (targetState == SettingsScreen.VPN_LOCATIONS) {
                    (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> -width } + fadeOut()
                    )
                } else {
                    (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> width } + fadeOut()
                    )
                }
            },
            label = "settingsNavigation"
        ) { screen ->
            when (screen) {
                SettingsScreen.MAIN -> {
                    MainSettingsView(
                        preferencesRepo = preferencesRepo,
                        passwordRepo = passwordRepo,
                        historyRepo = historyRepo,
                        tabManager = tabManager,
                        vpnManager = vpnManager,
                        runtime = runtime,
                        onOpenVpnLocations = { currentScreen = SettingsScreen.VPN_LOCATIONS },
                        onOpenPasswords = onOpenPasswords,
                        onOpenHistory = onOpenHistory,
                        onOpenClearDialog = { showClearDialog = true },
                        onClose = onDismissRequest
                    )
                }
                SettingsScreen.VPN_LOCATIONS -> {
                    VpnLocationsSubView(
                        vpnManager = vpnManager,
                        onBack = { currentScreen = SettingsScreen.MAIN },
                        onSelectCountry = { country ->
                            vpnManager.selectCountry(country, runtime, tabManager.tabs)
                            currentScreen = SettingsScreen.MAIN
                        }
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        ClearBrowsingDataSheet(
            historyRepository = historyRepo,
            runtime = runtime,
            onDismissRequest = { showClearDialog = false }
        )
    }
}

@Composable
private fun MainSettingsView(
    preferencesRepo: BrowserPreferencesRepository,
    passwordRepo: SecurePasswordRepository,
    historyRepo: HistoryRepository,
    tabManager: TabManager,
    vpnManager: FreeVpnManager,
    runtime: GeckoRuntime,
    onOpenVpnLocations: () -> Unit,
    onOpenPasswords: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenClearDialog: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Browser Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(0xFF2C2C2E), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = Color(0xFF8E8E93),
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Section 1: Free In-Browser VPN
        SettingsSectionHeader(title = "ENCRYPTED VPN RELAYS")
        SettingsCard {
            SettingsSwitchRow(
                title = "In-Browser VPN",
                subtitle = if (vpnManager.isEnabled) "Routing via ${vpnManager.selectedCountry.name}" else "Auto-disconnects on exit",
                icon = Icons.Rounded.Public,
                checked = vpnManager.isEnabled,
                onCheckedChange = {
                    vpnManager.toggleVpn(runtime, tabManager.tabs)
                }
            )

            Divider(color = Color(0xFF2C2C2E), thickness = 0.5.dp)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenVpnLocations() }
                    .padding(horizontal = 16.dp, vertical = 13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = vpnManager.selectedCountry.flag,
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = vpnManager.selectedCountry.name,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = vpnManager.selectedCountry.city,
                            fontSize = 11.5.sp,
                            color = Color(0xFF8E8E93)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val ping = vpnManager.getPingForCountry(vpnManager.selectedCountry.code)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (vpnManager.isEnabled) Color(0xFF1A3320) else Color(0xFF2C2C2E)
                    ) {
                        Text(
                            text = if (vpnManager.isEnabled) "$ping ms" else "10 Locations",
                            color = if (vpnManager.isEnabled) Color(0xFF34C759) else Color(0xFF8E8E93),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section 2: Cookie Shield (Segmented Pill Selector)
        SettingsSectionHeader(title = "COOKIE SHIELD")
        SettingsCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Cookie Policy",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(10.dp))

                // 3-Segment Pill Control
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF141416))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CookiePolicy.values().forEach { policy ->
                        val isSelected = preferencesRepo.cookiePolicy == policy
                        val label = when (policy) {
                            CookiePolicy.ACCEPT_NON_TRACKERS -> "Non-Trackers"
                            CookiePolicy.ACCEPT_FIRST_PARTY -> "1st-Party"
                            CookiePolicy.REJECT_ALL -> "Block All"
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF007AFF) else Color.Transparent)
                                .clickable {
                                    preferencesRepo.setCookiePolicyPreference(policy)
                                    try {
                                        runtime.settings.contentBlocking.setCookieBehavior(policy.geckoValue)
                                    } catch (e: Exception) {}
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFF8E8E93)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when (preferencesRepo.cookiePolicy) {
                        CookiePolicy.ACCEPT_NON_TRACKERS -> "Allows standard logins while blocking cross-site trackers."
                        CookiePolicy.ACCEPT_FIRST_PARTY -> "Strictly confines cookies to the visited domain."
                        CookiePolicy.REJECT_ALL -> "Highest privacy shield. Blocks all persistent cookies."
                    },
                    fontSize = 11.5.sp,
                    color = Color(0xFF8E8E93),
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section 3: Privacy & Search Protection
        SettingsSectionHeader(title = "PRIVACY & SEARCH SHIELD")
        SettingsCard {
            SettingsSwitchRow(
                title = "Ad & Tracker Shield",
                subtitle = "Block network ads, telemetry and miners",
                icon = Icons.Rounded.Shield,
                checked = preferencesRepo.adBlockingEnabled,
                onCheckedChange = {
                    preferencesRepo.setAdBlockingPreference(it)
                    tabManager.updateAdBlocking(it)
                }
            )
            Divider(color = Color(0xFF2C2C2E), thickness = 0.5.dp)
            SettingsSwitchRow(
                title = "Consent Banner Eradicator",
                subtitle = "Auto-dismiss GDPR and cookie popups",
                icon = Icons.Rounded.AutoFixHigh,
                checked = preferencesRepo.cosmeticBlockingEnabled,
                onCheckedChange = {
                    preferencesRepo.setCosmeticBlockingPreference(it)
                    tabManager.updateCosmeticBlocking(it)
                }
            )
            Divider(color = Color(0xFF2C2C2E), thickness = 0.5.dp)
            SettingsSwitchRow(
                title = "Block Google AI Overviews",
                subtitle = "Clean search results without AI clutter",
                icon = Icons.Rounded.Search,
                checked = preferencesRepo.blockAiOverviews,
                onCheckedChange = {
                    preferencesRepo.setBlockAiOverviewsPreference(it)
                }
            )
            Divider(color = Color(0xFF2C2C2E), thickness = 0.5.dp)
            SettingsSwitchRow(
                title = "HTTPS-Only Mode",
                subtitle = "Warn on unencrypted HTTP connections",
                icon = Icons.Rounded.Lock,
                checked = preferencesRepo.httpsOnlyMode,
                onCheckedChange = {
                    preferencesRepo.setHttpsOnlyModePreference(it)
                }
            )
            Divider(color = Color(0xFF2C2C2E), thickness = 0.5.dp)
            SettingsSwitchRow(
                title = "Dark Reader Dynamic Engine",
                subtitle = "High-contrast OLED pitch-black theme",
                icon = Icons.Rounded.DarkMode,
                checked = preferencesRepo.darkWebDefault,
                onCheckedChange = {
                    preferencesRepo.setDarkWebDefaultPreference(it)
                    tabManager.setDarkWebModeAll(it)
                }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section 4: Passwords & Autofill
        SettingsSectionHeader(title = "PASSWORDS & AUTOFILL")
        SettingsCard {
            SettingsSwitchRow(
                title = "Offer to Save Passwords",
                subtitle = "Hardware Keystore AES-256 vault",
                icon = Icons.Rounded.Lock,
                checked = preferencesRepo.savePasswordsEnabled,
                onCheckedChange = { preferencesRepo.setSavePasswordsPreference(it) }
            )
            Divider(color = Color(0xFF2C2C2E), thickness = 0.5.dp)
            SettingsSwitchRow(
                title = "Auto-fill Logins",
                subtitle = "Fill saved credentials on login forms",
                icon = Icons.Rounded.Key,
                checked = preferencesRepo.autofillEnabled,
                onCheckedChange = { preferencesRepo.setAutofillPreference(it) }
            )
            Divider(color = Color(0xFF2C2C2E), thickness = 0.5.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPasswords() }
                    .padding(horizontal = 16.dp, vertical = 13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Password,
                        contentDescription = null,
                        tint = Color(0xFF007AFF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Manage Saved Passwords",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${passwordRepo.logins.size} saved",
                        fontSize = 12.sp,
                        color = Color(0xFF8E8E93)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section 5: Startup & Search
        SettingsSectionHeader(title = "STARTUP & SEARCH")
        SettingsCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Startup Behavior",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Side-by-Side Startup Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StartupBehavior.values().forEach { behavior ->
                        val isSelected = preferencesRepo.startupBehavior == behavior
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF162536) else Color(0xFF141416))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color(0xFF007AFF) else Color(0xFF2C2C2E),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { preferencesRepo.setStartupBehaviorPreference(behavior) }
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (behavior == StartupBehavior.NEW_TAB) "Start Page" else "Resume",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else Color(0xFF8E8E93)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF007AFF),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (behavior == StartupBehavior.NEW_TAB) "Fresh home page" else "Restore all tabs",
                                    fontSize = 11.sp,
                                    color = Color(0xFF8E8E93),
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = Color(0xFF2C2C2E), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Default Search Engine",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SearchEngine.values().forEach { engine ->
                        val isSelected = preferencesRepo.searchEngine == engine
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFF162536) else Color(0xFF141416))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color(0xFF007AFF) else Color(0xFF2C2C2E),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { preferencesRepo.setSearchEnginePreference(engine) }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = engine.title,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFF8E8E93)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = Color(0xFF007AFF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section 6: Browsing Data
        SettingsSectionHeader(title = "BROWSING DATA")
        SettingsCard {
            SettingsSwitchRow(
                title = "Wipe on Exit",
                subtitle = "Clear history and cache on exit",
                icon = Icons.Rounded.AutoDelete,
                checked = preferencesRepo.wipeOnExit,
                onCheckedChange = { preferencesRepo.setWipeOnExitPreference(it) }
            )
            Divider(color = Color(0xFF2C2C2E), thickness = 0.5.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenHistory() }
                    .padding(horizontal = 16.dp, vertical = 13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = Color(0xFF007AFF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "View Browsing History",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF8E8E93),
                    modifier = Modifier.size(18.dp)
                )
            }
            Divider(color = Color(0xFF2C2C2E), thickness = 0.5.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenClearDialog() }
                    .padding(horizontal = 16.dp, vertical = 13.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = DangerRed,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Clear Browsing Data...",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DangerRed
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section 7: About
        SettingsSectionHeader(title = "ABOUT")
        SettingsCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Gecko AdFree Browser",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Version 1.0.0 (Release) • arm64-v8a",
                    fontSize = 11.5.sp,
                    color = Color(0xFF8E8E93)
                )
                Text(
                    text = "Mozilla GeckoView 122 + Dark Reader + Free VPN",
                    fontSize = 11.5.sp,
                    color = Color(0xFF8E8E93)
                )
                Text(
                    text = "GNU General Public License v3.0 (GPLv3)",
                    fontSize = 11.5.sp,
                    color = Color(0xFF8E8E93)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun VpnLocationsSubView(
    vpnManager: FreeVpnManager,
    onBack: () -> Unit,
    onSelectCountry: (VpnCountry) -> Unit
) {
    LaunchedEffect(Unit) {
        vpnManager.refreshPings()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Subview Navigation Header with Back and Refresh buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF2C2C2E), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Back to Settings",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "VPN Locations",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "10 Encrypted Free Relays",
                        fontSize = 11.5.sp,
                        color = Color(0xFF8E8E93)
                    )
                }
            }

            IconButton(
                onClick = { vpnManager.refreshPings() },
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(0xFF2C2C2E), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Refresh Ping",
                    tint = Color(0xFF8E8E93),
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Country List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(VpnCountry.COUNTRIES) { country ->
                val isSelected = country.code == vpnManager.selectedCountry.code
                val pingMs = vpnManager.getPingForCountry(country.code)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) Color(0xFF1C2A20) else Color(0xFF1C1C1E))
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Color(0xFF34C759) else Color(0xFF2C2C2E),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { onSelectCountry(country) }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = country.flag,
                            fontSize = 22.sp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = country.name,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = country.city,
                                color = Color(0xFF8E8E93),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (pingMs < 100) Color(0xFF1A3320) else Color(0xFF332B1A)
                        ) {
                            Text(
                                text = "$pingMs ms",
                                color = if (pingMs < 100) Color(0xFF34C759) else Color(0xFFFF9F0A),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        if (isSelected) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = Color(0xFF34C759),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF007AFF),
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1C1C1E))
            .border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(18.dp)),
        content = content
    )
}

@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) Color(0xFF007AFF) else Color(0xFF8E8E93),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = Color(0xFF8E8E93)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF007AFF),
                uncheckedTrackColor = Color(0xFF2C2C2E),
                uncheckedThumbColor = Color(0xFF8E8E93)
            )
        )
    }
}
