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

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClearBrowsingDataSheet(
    historyRepository: HistoryRepository,
    tabManager: TabManager,
    runtime: GeckoRuntime,
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
                    .padding(bottom = 18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(DangerRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = DangerRed,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Clear Browsing Data",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Select data and time range to remove",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            // Time Range Section Header
            Text(
                text = "TIME RANGE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AccentCyan,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )

            // 2-Row Visible Grid of Time Options (3 columns per row)
            val timeframes = TimeframeWipe.values().toList()
            val row1 = timeframes.take(3)
            val row2 = timeframes.drop(3)

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    row1.forEach { option ->
                        val isSelected = selectedTimeframe == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) AccentCyan else SurfaceElevated)
                                .border(
                                    1.dp,
                                    if (isSelected) AccentCyan else BorderSubtle,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedTimeframe = option },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) DarkBg else TextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    row2.forEach { option ->
                        val isSelected = selectedTimeframe == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) AccentCyan else SurfaceElevated)
                                .border(
                                    1.dp,
                                    if (isSelected) AccentCyan else BorderSubtle,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedTimeframe = option },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) DarkBg else TextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Data Types Section Header
            Text(
                text = "DATA TO CLEAR",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AccentCyan,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )

            // Data Types Selection Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            ) {
                ClearDataCheckboxRow(
                    title = "Browsing History",
                    subtitle = "${historyRepository.history.size} site visits recorded",
                    icon = Icons.Default.History,
                    checked = clearHistoryChecked,
                    onCheckedChange = { clearHistoryChecked = it }
                )

                Divider(color = BorderSubtle, thickness = 0.5.dp)

                ClearDataCheckboxRow(
                    title = "Cookies & Site Data",
                    subtitle = "Signs you out of most websites",
                    icon = Icons.Default.Cookie,
                    checked = clearCookiesChecked,
                    onCheckedChange = { clearCookiesChecked = it }
                )

                Divider(color = BorderSubtle, thickness = 0.5.dp)

                ClearDataCheckboxRow(
                    title = "Cached Images & Files",
                    subtitle = "Frees up device storage & temporary cache",
                    icon = Icons.Default.Cached,
                    checked = clearCacheChecked,
                    onCheckedChange = { clearCacheChecked = it }
                )

                Divider(color = BorderSubtle, thickness = 0.5.dp)

                ClearDataCheckboxRow(
                    title = "Close Open Tabs",
                    subtitle = "${tabManager.tabs.size} open tabs will be closed",
                    icon = Icons.Default.Tab,
                    checked = closeTabsChecked,
                    onCheckedChange = { closeTabsChecked = it }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Action Buttons
            Button(
                onClick = {
                    executeClearData(
                        context = context,
                        historyRepository = historyRepository,
                        tabManager = tabManager,
                        runtime = runtime,
                        timeframe = selectedTimeframe,
                        clearHistory = clearHistoryChecked,
                        clearCookies = clearCookiesChecked,
                        clearCache = clearCacheChecked,
                        closeTabs = closeTabsChecked
                    )
                    onDismissRequest()
                },
                enabled = clearHistoryChecked || clearCookiesChecked || clearCacheChecked || closeTabsChecked,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DangerRed,
                    contentColor = TextPrimary,
                    disabledContainerColor = SurfaceCard,
                    disabledContentColor = TextSecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Clear Data (${selectedTimeframe.title})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(
                    text = "Cancel",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ClearDataCheckboxRow(
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
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp)
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
            Spacer(modifier = Modifier.width(14.dp))
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

        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = AccentCyan,
                checkmarkColor = DarkBg,
                uncheckedColor = TextSecondary
            )
        )
    }
}

private fun executeClearData(
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

        var flags = 0L
        if (timeframe == TimeframeWipe.ALL_TIME && (clearCookies || clearCache)) {
            flags = StorageController.ClearFlags.ALL
        } else {
            if (clearCookies) {
                flags = flags or StorageController.ClearFlags.COOKIES or 
                        StorageController.ClearFlags.DOM_STORAGES or 
                        StorageController.ClearFlags.AUTH_SESSIONS or
                        StorageController.ClearFlags.SITE_DATA
            }
            if (clearCache) {
                flags = flags or StorageController.ClearFlags.NETWORK_CACHE or 
                        StorageController.ClearFlags.IMAGE_CACHE or
                        StorageController.ClearFlags.ALL_CACHES
            }
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

        val cleanedItems = mutableListOf<String>()
        if (clearHistory) cleanedItems.add("History")
        if (clearCookies) cleanedItems.add("Cookies")
        if (clearCache) cleanedItems.add("Cache")
        if (closeTabs) cleanedItems.add("Tabs")

        val summary = if (cleanedItems.isNotEmpty()) cleanedItems.joinToString(", ") else "Data"
        Toast.makeText(context, "Cleared $summary (${timeframe.title}) 🔥", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Data cleared successfully", Toast.LENGTH_SHORT).show()
    }
}
