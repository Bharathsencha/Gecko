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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.browser.adfree.model.CookiePolicy
import com.browser.adfree.model.SearchEngine
import com.browser.adfree.model.StartupBehavior
import com.browser.adfree.model.TabModel
import com.browser.adfree.model.VpnCountry
import com.browser.adfree.storage.AppAccentColor
import com.browser.adfree.storage.AppThemeMode
import com.browser.adfree.storage.BrowserPreferencesRepository
import com.browser.adfree.storage.HistoryRepository
import com.browser.adfree.storage.SecurePasswordRepository
import com.browser.adfree.tabs.TabManager
import com.browser.adfree.ui.theme.*
import com.browser.adfree.vpn.FreeVpnManager
import org.mozilla.geckoview.GeckoRuntime

enum class SettingsScreen {
    MAIN,
    VPN_LOCATIONS,
    CUSTOMIZATION
}

fun isAppDefaultBrowser(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
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
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                launcher.launch(intent)
                return
            } catch (e: Exception) {
                Log.e("Settings", "RoleManager error", e)
            }
        }
    }
    try {
        val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        launcher.launch(intent)
    } catch (e: Exception) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            launcher.launch(intent)
        } catch (e2: Exception) {}
    }
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
    activeTab: TabModel? = null,
    onZoomChange: (Int) -> Unit = {},
    onToggleDarkWeb: () -> Unit = {},
    onToggleReader: () -> Unit = {},
    onToggleDesktop: () -> Unit = {},
    onOpenPasswords: () -> Unit,
    onOpenHistory: () -> Unit,
    onDismissRequest: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(SettingsScreen.MAIN) }
    var showClearDialog by remember { mutableStateOf(false) }

    // Intercept back button when in subviews
    BackHandler(enabled = currentScreen != SettingsScreen.MAIN) {
        currentScreen = SettingsScreen.MAIN
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = DarkBg,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(BorderSubtle, RoundedCornerShape(2.dp))
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                if (targetState != SettingsScreen.MAIN) {
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
                        activeTab = activeTab,
                        onZoomChange = onZoomChange,
                        onToggleDarkWeb = onToggleDarkWeb,
                        onToggleReader = onToggleReader,
                        onToggleDesktop = onToggleDesktop,
                        onOpenVpnLocations = { currentScreen = SettingsScreen.VPN_LOCATIONS },
                        onOpenCustomization = { currentScreen = SettingsScreen.CUSTOMIZATION },
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
                SettingsScreen.CUSTOMIZATION -> {
                    CustomizationSuiteSubView(
                        preferencesRepo = preferencesRepo,
                        onBack = { currentScreen = SettingsScreen.MAIN }
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        ClearBrowsingDataSheet(
            historyRepository = historyRepo,
            tabManager = tabManager,
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
    activeTab: TabModel?,
    onZoomChange: (Int) -> Unit,
    onToggleDarkWeb: () -> Unit,
    onToggleReader: () -> Unit,
    onToggleDesktop: () -> Unit,
    onOpenVpnLocations: () -> Unit,
    onOpenCustomization: () -> Unit,
    onOpenPasswords: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenClearDialog: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var isDefault by remember { mutableStateOf(isAppDefaultBrowser(context)) }
    val currentAccent = Color(preferencesRepo.accentColor.hex)

    val defaultBrowserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isDefault = isAppDefaultBrowser(context)
    }

    // Refresh default status on return from Android Settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefault = isAppDefaultBrowser(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Sticky/Safe Subview Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Browser Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(32.dp)
                    .background(SurfaceElevated, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = TextSecondary,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Active Web Page Tools (when viewing an active website)
        if (activeTab != null && activeTab.url.isNotBlank() && activeTab.url != "about:home") {
            SettingsSectionHeader(title = "ACTIVE PAGE TOOLS")
            SettingsCard {
                // Zoom controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.ZoomIn,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Page Zoom",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCard)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = { if (activeTab.zoomLevel > 50) onZoomChange(activeTab.zoomLevel - 10) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }

                        Text(
                            text = "${activeTab.zoomLevel}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        IconButton(
                            onClick = { if (activeTab.zoomLevel < 200) onZoomChange(activeTab.zoomLevel + 10) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }

                Divider(color = BorderSubtle, thickness = 0.5.dp)

                SettingsSwitchRow(
                    title = "Force Dark Web",
                    subtitle = "Convert bright web page to high-contrast dark theme",
                    icon = Icons.Rounded.DarkMode,
                    checked = activeTab.isDarkWeb,
                    onCheckedChange = { onToggleDarkWeb() }
                )

                Divider(color = BorderSubtle, thickness = 0.5.dp)

                SettingsSwitchRow(
                    title = "Desktop Site",
                    subtitle = "Request full desktop layout",
                    icon = Icons.Rounded.DesktopWindows,
                    checked = activeTab.isDesktopMode,
                    onCheckedChange = { onToggleDesktop() }
                )

                Divider(color = BorderSubtle, thickness = 0.5.dp)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onClose()
                            onToggleReader()
                        }
                        .padding(horizontal = 16.dp, vertical = 13.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.MenuBook,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (activeTab.isReaderMode) "Exit Reader View" else "Reader View",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Distraction-free article reader mode",
                                fontSize = 11.5.sp,
                                color = TextSecondary
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }


        Spacer(modifier = Modifier.height(16.dp))

        // Default Browser Action Card
        if (!isDefault) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(currentAccent.copy(alpha = 0.12f))
                    .border(1.dp, currentAccent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    .clickable { requestSetDefaultBrowser(context, defaultBrowserLauncher) }
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(currentAccent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Language,
                                contentDescription = null,
                                tint = currentAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Set as Default Browser",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Open all links ad-free & protected",
                                fontSize = 11.5.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = currentAccent
                    ) {
                        Text(
                            text = "Set Default",
                            color = if (preferencesRepo.accentColor == AppAccentColor.YELLOW || preferencesRepo.accentColor == AppAccentColor.MONOCHROME) Color.Black else Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section: Appearance & Customization
        SettingsSectionHeader(title = "APPEARANCE & THEME")
        SettingsCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "App Theme Mode",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                // 3-Segment Quick Mode Selector: Light | Dark | System
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceCard)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isLight = preferencesRepo.themeMode == AppThemeMode.LIGHT
                    val isSystem = preferencesRepo.themeMode == AppThemeMode.SYSTEM
                    val isDark = !isLight && !isSystem

                    for ((label, isSelected, targetMode) in listOf(
                        Triple("Light", isLight, AppThemeMode.LIGHT),
                        Triple("Dark", isDark, AppThemeMode.DARK_OLED),
                        Triple("System", isSystem, AppThemeMode.SYSTEM)
                    )) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) currentAccent else Color.Transparent)
                                .clickable {
                                    if (targetMode == AppThemeMode.DARK_OLED) {
                                        if (preferencesRepo.themeMode != AppThemeMode.DARK_OLED &&
                                            preferencesRepo.themeMode != AppThemeMode.DARK_MIDNIGHT &&
                                            preferencesRepo.themeMode != AppThemeMode.DARK_CHARCOAL
                                        ) {
                                            preferencesRepo.setThemeModePreference(AppThemeMode.DARK_OLED)
                                        }
                                    } else {
                                        preferencesRepo.setThemeModePreference(targetMode)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) (if (preferencesRepo.accentColor == AppAccentColor.YELLOW || preferencesRepo.accentColor == AppAccentColor.MONOCHROME) Color.Black else Color.White) else TextSecondary
                            )
                        }
                    }
                }
            }

            Divider(color = BorderSubtle, thickness = 0.5.dp)

            // Personalization Subview Link
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenCustomization() }
                    .padding(horizontal = 16.dp, vertical = 13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(currentAccent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = currentAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Personalization & Theme",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${preferencesRepo.themeMode.displayName} • ${preferencesRepo.accentColor.displayName}",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(currentAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

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

            Divider(color = BorderSubtle, thickness = 0.5.dp)

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
                            color = TextPrimary
                        )
                        Text(
                            text = vpnManager.selectedCountry.city,
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val ping = vpnManager.getPingForCountry(vpnManager.selectedCountry.code)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (vpnManager.isEnabled) ShieldGreen.copy(alpha = 0.15f) else SurfaceCard
                    ) {
                        Text(
                            text = if (vpnManager.isEnabled) "$ping ms" else "10 Locations",
                            color = if (vpnManager.isEnabled) ShieldGreen else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = TextSecondary,
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
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                // 3-Segment Pill Control
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceCard)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (policy in CookiePolicy.values()) {
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
                                .background(if (isSelected) currentAccent else Color.Transparent)
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
                                color = if (isSelected) (if (preferencesRepo.accentColor == AppAccentColor.YELLOW || preferencesRepo.accentColor == AppAccentColor.MONOCHROME) Color.Black else Color.White) else TextSecondary
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
                    color = TextSecondary,
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
            Divider(color = BorderSubtle, thickness = 0.5.dp)
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
            Divider(color = BorderSubtle, thickness = 0.5.dp)
            SettingsSwitchRow(
                title = "Block Google AI Overviews",
                subtitle = "Clean search results without AI clutter",
                icon = Icons.Rounded.Search,
                checked = preferencesRepo.blockAiOverviews,
                onCheckedChange = {
                    preferencesRepo.setBlockAiOverviewsPreference(it)
                }
            )
            Divider(color = BorderSubtle, thickness = 0.5.dp)
            SettingsSwitchRow(
                title = "HTTPS-Only Mode",
                subtitle = "Warn on unencrypted HTTP connections",
                icon = Icons.Rounded.Lock,
                checked = preferencesRepo.httpsOnlyMode,
                onCheckedChange = {
                    preferencesRepo.setHttpsOnlyModePreference(it)
                }
            )
            Divider(color = BorderSubtle, thickness = 0.5.dp)
            SettingsSwitchRow(
                title = "Night Mode for Web",
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
            Divider(color = BorderSubtle, thickness = 0.5.dp)
            SettingsSwitchRow(
                title = "Auto-fill Logins",
                subtitle = "Fill saved credentials on login forms",
                icon = Icons.Rounded.Key,
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
                    .padding(horizontal = 16.dp, vertical = 13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Password,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Manage Saved Passwords",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${passwordRepo.logins.size} saved",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section: Media & Video Downloader
        SettingsSectionHeader(title = "MEDIA & DOWNLOADER")
        SettingsCard {
            SettingsSwitchRow(
                title = "Video Downloader (Sniffer)",
                subtitle = "Detect and offer downloads for videos playing on web pages",
                icon = Icons.Rounded.Download,
                checked = preferencesRepo.videoDownloaderEnabled,
                onCheckedChange = {
                    preferencesRepo.setVideoDownloaderEnabledPreference(it)
                }
            )
            Divider(color = BorderSubtle, thickness = 0.5.dp)
            SettingsSwitchRow(
                title = "Prioritize AV1 Video Format",
                subtitle = "Offer high-efficiency AV1 streams when provided by platforms",
                icon = Icons.Rounded.HighQuality,
                checked = preferencesRepo.preferAv1Codec,
                onCheckedChange = {
                    preferencesRepo.setPreferAv1CodecPreference(it)
                }
            )
            Divider(color = BorderSubtle, thickness = 0.5.dp)
            SettingsSwitchRow(
                title = "Show WebM Formats",
                subtitle = "Show WebM container streams in download sheet (Disabled by default for universal MP4 compatibility)",
                icon = Icons.Rounded.VideoLibrary,
                checked = preferencesRepo.showWebmFormats,
                onCheckedChange = {
                    preferencesRepo.setShowWebmFormatsPreference(it)
                }
            )
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
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Side-by-Side Startup Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (behavior in StartupBehavior.values()) {
                        val isSelected = preferencesRepo.startupBehavior == behavior
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) currentAccent.copy(alpha = 0.15f) else SurfaceCard)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) currentAccent else BorderSubtle,
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
                                        color = if (isSelected) TextPrimary else TextSecondary
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = currentAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (behavior == StartupBehavior.NEW_TAB) "Fresh home page" else "Restore all tabs",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = BorderSubtle, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Default Search Engine",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (engine in SearchEngine.values()) {
                        val isSelected = preferencesRepo.searchEngine == engine
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) currentAccent.copy(alpha = 0.15f) else SurfaceCard)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) currentAccent else BorderSubtle,
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
                                color = if (isSelected) TextPrimary else TextSecondary
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = currentAccent,
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
            Divider(color = BorderSubtle, thickness = 0.5.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenClearDialog() }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = DangerRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Clear Browsing Data",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Clear history, cookies, cache and open sessions",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section 7: About
        SettingsSectionHeader(title = "ABOUT")
        SettingsCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Gecko",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Version 1.0.0 (Release)",
                    fontSize = 11.5.sp,
                    color = TextSecondary
                )
                Text(
                    text = "Fast, private and customizable web browser",
                    fontSize = 11.5.sp,
                    color = TextSecondary
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
            .statusBarsPadding()
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
                        .background(SurfaceElevated, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Back to Settings",
                        tint = TextPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "VPN Locations",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "10 Encrypted Free Relays",
                        fontSize = 11.5.sp,
                        color = TextSecondary
                    )
                }
            }

            IconButton(
                onClick = { vpnManager.refreshPings() },
                modifier = Modifier
                    .size(32.dp)
                    .background(SurfaceElevated, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Refresh Ping",
                    tint = TextSecondary,
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
                        .background(if (isSelected) AccentCyan.copy(alpha = 0.15f) else SurfaceElevated)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) AccentCyan else BorderSubtle,
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
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = country.city,
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (pingMs < 100) ShieldGreen.copy(alpha = 0.15f) else SurfaceCard
                        ) {
                            Text(
                                text = "$pingMs ms",
                                color = if (pingMs < 100) ShieldGreen else FlameOrange,
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
                                tint = AccentCyan,
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
        color = AccentCyan,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceElevated)
            .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp)),
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
                tint = if (checked) AccentCyan else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = TextSecondary
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentCyan,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = SurfaceCard,
                uncheckedBorderColor = BorderSubtle
            )
        )
    }
}
