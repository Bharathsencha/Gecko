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

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VpnKey
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
    onOpenVpnSelector: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = SurfaceDark,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Browser Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            // Section 1: Free In-Browser VPN
            SettingsSectionHeader(title = "FREE IN-BROWSER VPN")
            SettingsCard {
                SettingsSwitchRow(
                    title = "In-Browser Encrypted Relay",
                    subtitle = if (vpnManager.isEnabled) "Routing via ${vpnManager.selectedCountry.name} (${vpnManager.selectedCountry.city})" else "Auto-disconnects when browser closes",
                    icon = Icons.Rounded.Public,
                    checked = vpnManager.isEnabled,
                    onCheckedChange = {
                        vpnManager.toggleVpn(runtime, tabManager.tabs)
                        Toast.makeText(
                            context,
                            if (it) "VPN Connected: ${vpnManager.selectedCountry.name}" else "VPN Disconnected",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )

                Divider(color = BorderSubtle, thickness = 0.5.dp)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenVpnSelector() }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = vpnManager.selectedCountry.flag,
                            fontSize = 22.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Relay Location",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "${vpnManager.selectedCountry.name} • ${vpnManager.selectedCountry.city}",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (vpnManager.isEnabled) Color(0xFF1A3320) else Color(0xFF2C2C2E)
                        ) {
                            Text(
                                text = if (vpnManager.isEnabled) "${vpnManager.livePingMs} ms" else "10 Locations",
                                color = if (vpnManager.isEnabled) Color(0xFF34C759) else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2: Cookie Shield & Privacy
            SettingsSectionHeader(title = "COOKIE SHIELD & PRIVACY")
            SettingsCard {
                CookiePolicy.values().forEachIndexed { index, policy ->
                    val isSelected = preferencesRepo.cookiePolicy == policy

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                preferencesRepo.setCookiePolicyPreference(policy)
                                Toast.makeText(context, "Cookie Policy Updated", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                        ) {
                            Text(
                                text = policy.title,
                                fontSize = 15.sp,
                                color = TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = policy.description,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = AccentCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (index < CookiePolicy.values().size - 1) {
                        Divider(color = BorderSubtle, thickness = 0.5.dp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 3: Privacy & Content Shield
            SettingsSectionHeader(title = "CONTENT & SEARCH SHIELD")
            SettingsCard {
                SettingsSwitchRow(
                    title = "Strict Ad & Tracker Shield",
                    subtitle = "Blocks intrusive ads, telemetry & popups",
                    icon = Icons.Rounded.Shield,
                    checked = preferencesRepo.adBlockingEnabled,
                    onCheckedChange = {
                        preferencesRepo.setAdBlockingPreference(it)
                        tabManager.updateAdBlocking(it)
                        Toast.makeText(context, "Content Shield " + (if (it) "Enabled" else "Disabled"), Toast.LENGTH_SHORT).show()
                    }
                )
                Divider(color = BorderSubtle, thickness = 0.5.dp)
                SettingsSwitchRow(
                    title = "Cosmetic Banner Eradicator",
                    subtitle = "Suppresses GDPR consent modals & blank ad slots",
                    icon = Icons.Default.AutoFixHigh,
                    checked = preferencesRepo.cosmeticBlockingEnabled,
                    onCheckedChange = {
                        preferencesRepo.setCosmeticBlockingPreference(it)
                        tabManager.updateCosmeticBlocking(it)
                    }
                )
                Divider(color = BorderSubtle, thickness = 0.5.dp)
                SettingsSwitchRow(
                    title = "Block Google AI Overviews",
                    subtitle = "Eliminates generative AI clutter on Google searches",
                    icon = Icons.Default.Search,
                    checked = preferencesRepo.blockAiOverviews,
                    onCheckedChange = {
                        preferencesRepo.setBlockAiOverviewsPreference(it)
                    }
                )
                Divider(color = BorderSubtle, thickness = 0.5.dp)
                SettingsSwitchRow(
                    title = "HTTPS-Only Mode",
                    subtitle = "Enforces HTTPS default & warns on insecure HTTP",
                    icon = Icons.Rounded.Lock,
                    checked = preferencesRepo.httpsOnlyMode,
                    onCheckedChange = {
                        preferencesRepo.setHttpsOnlyModePreference(it)
                    }
                )
                Divider(color = BorderSubtle, thickness = 0.5.dp)
                SettingsSwitchRow(
                    title = "Dark Reader Dynamic Engine",
                    subtitle = "OLED pitch-black contrast with protected media",
                    icon = Icons.Default.DarkMode,
                    checked = preferencesRepo.darkWebDefault,
                    onCheckedChange = {
                        preferencesRepo.setDarkWebDefaultPreference(it)
                        tabManager.setDarkWebModeAll(it)
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 4: Passwords & Autofill
            SettingsSectionHeader(title = "PASSWORDS & AUTOFILL")
            SettingsCard {
                SettingsSwitchRow(
                    title = "Offer to Save Passwords",
                    subtitle = "Hardware Keystore AES-256-GCM encryption",
                    icon = Icons.Default.Lock,
                    checked = preferencesRepo.savePasswordsEnabled,
                    onCheckedChange = { preferencesRepo.setSavePasswordsPreference(it) }
                )
                Divider(color = BorderSubtle, thickness = 0.5.dp)
                SettingsSwitchRow(
                    title = "Auto-fill Logins",
                    subtitle = "Automatically fill credentials on login pages",
                    icon = Icons.Default.Key,
                    checked = preferencesRepo.autofillEnabled,
                    onCheckedChange = { preferencesRepo.setAutofillPreference(it) }
                )
                Divider(color = BorderSubtle, thickness = 0.5.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPasswords() }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Password,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Manage Saved Passwords",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "${passwordRepo.logins.size} saved accounts",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 5: Startup & Search
            SettingsSectionHeader(title = "STARTUP & SEARCH ENGINE")
            SettingsCard {
                Text(
                    text = "Startup Behavior",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
                StartupBehavior.values().forEachIndexed { index, behavior ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { preferencesRepo.setStartupBehaviorPreference(behavior) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                        ) {
                            Text(
                                text = behavior.title,
                                fontSize = 15.sp,
                                color = TextPrimary,
                                fontWeight = if (preferencesRepo.startupBehavior == behavior) FontWeight.Bold else FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = behavior.description,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }

                        if (preferencesRepo.startupBehavior == behavior) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = AccentCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (index < StartupBehavior.values().size - 1) {
                        Divider(color = BorderSubtle, thickness = 0.5.dp)
                    }
                }

                Divider(color = BorderSubtle, thickness = 1.dp)

                Text(
                    text = "Default Search Engine",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
                SearchEngine.values().forEachIndexed { index, engine ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { preferencesRepo.setSearchEnginePreference(engine) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = engine.title,
                            fontSize = 15.sp,
                            color = TextPrimary,
                            fontWeight = if (preferencesRepo.searchEngine == engine) FontWeight.Bold else FontWeight.Normal
                        )
                        if (preferencesRepo.searchEngine == engine) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = AccentCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (index < SearchEngine.values().size - 1) {
                        Divider(color = BorderSubtle, thickness = 0.5.dp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 6: History & Data Wipe
            SettingsSectionHeader(title = "BROWSING HISTORY & DATA")
            SettingsCard {
                SettingsSwitchRow(
                    title = "Wipe on Exit",
                    subtitle = "Automatically clears history and cache on browser exit",
                    icon = Icons.Default.AutoDelete,
                    checked = preferencesRepo.wipeOnExit,
                    onCheckedChange = { preferencesRepo.setWipeOnExitPreference(it) }
                )
                Divider(color = BorderSubtle, thickness = 0.5.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenHistory() }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "View Browsing History",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Divider(color = BorderSubtle, thickness = 0.5.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showClearDialog = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = DangerRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Clear Browsing Data...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DangerRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 7: About
            SettingsSectionHeader(title = "ABOUT")
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "AdFree Gecko Browser",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Version 1.0.0 (Release) • arm64-v8a",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "Engine: Mozilla GeckoView 122 + Dark Reader + Free In-Browser VPN",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
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
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = AccentCyan,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceElevated)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
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
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) AccentCyan else TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DarkBg,
                checkedTrackColor = AccentCyan,
                uncheckedTrackColor = SurfaceCard
            )
        )
    }
}
