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

import androidx.compose.runtime.getValue

import androidx.compose.material.icons.filled.LocalFireDepartment

import androidx.compose.material.icons.filled.History

import androidx.compose.material.icons.filled.Tab

import androidx.compose.material.icons.filled.Storage

import androidx.compose.material.icons.filled.Cookie

import androidx.compose.material.icons.filled.Check

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.selection.toggleable

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.browser.adfree.model.TimeframeWipe
import com.browser.adfree.storage.HistoryRepository
import com.browser.adfree.tabs.TabManager
import com.browser.adfree.ui.theme.*
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.StorageController

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ClearBrowsingDataSheet(
    historyRepository: HistoryRepository,
    tabManager: TabManager,
    runtime: GeckoRuntime,
    /** Receives the wipe so the caller can run it while the fire covers the screen. */
    onBurn: ((wipe: () -> Unit) -> Unit)? = null,
    clearStyle: com.browser.adfree.storage.ClearStyle = com.browser.adfree.storage.ClearStyle.FIRE,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var selectedTimeframe by remember { mutableStateOf(TimeframeWipe.ALL_TIME) }

    var clearHistoryChecked by remember { mutableStateOf(true) }
    var clearCookiesChecked by remember { mutableStateOf(true) }
    var clearCacheChecked by remember { mutableStateOf(true) }
    var closeTabsChecked by remember { mutableStateOf(true) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = SurfaceDark,
        contentColor = TextPrimary,
        tonalElevation = 0.dp,
        dragHandle = { BrutalSheetHandle() },
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Clear browsing data",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            BrutalSectionLabel("Time range")
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(start = 16.dp, end = 19.dp)
            ) {
                TimeframeWipe.values().forEach { option ->
                    val selected = selectedTimeframe == option
                    Box(
                        modifier = Modifier
                            .brutalPressable(
                                fill = if (selected) AccentCyan else SurfaceDark,
                                border = BorderSubtle,
                                shadow = HardShadow,
                                shadowOffset = if (selected) 0.dp else 2.dp,
                                role = androidx.compose.ui.semantics.Role.RadioButton,
                                onClick = { selectedTimeframe = option }
                            )
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text(option.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (selected) OnAccent else TextPrimary)
                    }
                }
            }

            BrutalSectionLabel("What to clear")
            BrutalRule()
            ClearDataCheckboxRow(
                title = "History",
                subtitle = "${historyRepository.history.size} visits" + if (selectedTimeframe != TimeframeWipe.ALL_TIME) ", only ${selectedTimeframe.title.lowercase()}" else "",
                icon = Icons.Default.History,
                checked = clearHistoryChecked,
                onCheckedChange = { clearHistoryChecked = it }
            )
            BrutalHairline(startIndent = 64.dp)
            ClearDataCheckboxRow(
                title = "Cookies and site data",
                subtitle = "Signs you out of most sites. Always clears all time",
                icon = Icons.Default.Cookie,
                checked = clearCookiesChecked,
                onCheckedChange = { clearCookiesChecked = it }
            )
            BrutalHairline(startIndent = 64.dp)
            ClearDataCheckboxRow(
                title = "Cached images and files",
                subtitle = "Frees storage. Always clears all time",
                icon = Icons.Default.Storage,
                checked = clearCacheChecked,
                onCheckedChange = { clearCacheChecked = it }
            )
            BrutalHairline(startIndent = 64.dp)
            ClearDataCheckboxRow(
                title = "Close tabs",
                subtitle = "${tabManager.tabs.size} open",
                icon = Icons.Default.Tab,
                checked = closeTabsChecked,
                onCheckedChange = { closeTabsChecked = it }
            )
            BrutalRule()

            val anything = clearHistoryChecked || clearCookiesChecked || clearCacheChecked || closeTabsChecked
            BrutalButton(
                text = "Clear now",
                icon = clearStyleIcon(clearStyle),
                fill = clearStyleFill(clearStyle),
                contentColor = InkBlack,
                enabled = anything,
                onClick = {
                    val timeframe = selectedTimeframe
                    val history = clearHistoryChecked
                    val cookies = clearCookiesChecked
                    val cache = clearCacheChecked
                    val tabs = closeTabsChecked
                    val wipe = {
                        executeClearData(
                            context = context,
                            historyRepository = historyRepository,
                            tabManager = tabManager,
                            runtime = runtime,
                            timeframe = timeframe,
                            clearHistory = history,
                            clearCookies = cookies,
                            clearCache = cache,
                            closeTabs = tabs
                        )
                    }
                    onDismissRequest()
                    if (onBurn != null) onBurn(wipe) else wipe()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 19.dp, top = 20.dp, bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun ClearDataCheckboxRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val view = androidx.compose.ui.platform.LocalView.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .toggleable(
                value = checked,
                role = androidx.compose.ui.semantics.Role.Checkbox,
                onValueChange = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    onCheckedChange(it)
                }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        BrutalIconTile(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, fontSize = 12.5.sp, color = TextSecondary)
        }
        val fill by androidx.compose.animation.animateColorAsState(if (checked) AccentCyan else SurfaceDark, label = "checkFill")
        Box(
            modifier = Modifier
                .size(24.dp)
                .brutalBox(fill = fill, border = BorderSubtle, shape = RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Icon(Icons.Default.Check, contentDescription = null, tint = OnAccent, modifier = Modifier.size(17.dp))
        }
    }
}

fun executeClearData(
    context: Context,
    historyRepository: HistoryRepository,
    tabManager: TabManager,
    runtime: GeckoRuntime,
    timeframe: TimeframeWipe,
    clearHistory: Boolean,
    clearCookies: Boolean,
    clearCache: Boolean,
    closeTabs: Boolean
) {
    try {
        if (clearHistory) {
            if (timeframe == TimeframeWipe.ALL_TIME) {
                historyRepository.clearAll()
            } else {
                historyRepository.clearHistoryByTimeframe(timeframe)
            }
        }

        // Only what was ticked. Gecko cannot scope cookies or cache to a time
        // range, so those always clear everything (the UI says so).
        var flags = 0L
        if (clearCookies) {
            flags = flags or StorageController.ClearFlags.COOKIES or
                    StorageController.ClearFlags.DOM_STORAGES or
                    StorageController.ClearFlags.AUTH_SESSIONS or
                    StorageController.ClearFlags.SITE_DATA
        }
        if (clearCache) {
            flags = flags or StorageController.ClearFlags.ALL_CACHES
        }

        if (flags != 0L) {
            runtime.storageController.clearData(flags).accept(
                { android.util.Log.i("AdFreeBrowser", "GeckoView storage cleared.") },
                { e -> android.util.Log.e("AdFreeBrowser", "Error clearing GeckoView storage", e) }
            )
        }

        if (clearCache) {
            try {
                context.cacheDir.deleteRecursively()
                context.codeCacheDir.deleteRecursively()
                context.externalCacheDir?.deleteRecursively()
            } catch (e: Exception) {}
        }

        if (closeTabs) {
            tabManager.closeAllTabs(runtime)
            tabManager.sessionRepository.clearSession()
        }
    } catch (e: Exception) {
        android.util.Log.w("GeckoApp", "Ignored ${e.javaClass.simpleName}")
    }
}
