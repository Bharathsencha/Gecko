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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.storage.PermissionAsk
import com.browser.adfree.storage.SitePermissions
import com.browser.adfree.ui.theme.*
import org.mozilla.geckoview.GeckoRuntime

/** Asked once per site; the answer is kept until changed in the shield panel or Settings. */
@Composable
fun PermissionPrompt(ask: PermissionAsk, onAnswered: () -> Unit) {
    val what = ask.permissions.map { it.ask }.let { parts ->
        if (parts.size == 2 && ask.permissions.containsAll(listOf(com.browser.adfree.storage.SitePermission.CAMERA, com.browser.adfree.storage.SitePermission.MICROPHONE)))
            "use your camera and microphone"
        else parts.joinToString(" and ")
    }
    BrutalDialog(
        title = "${ask.host.ifBlank { "This site" }} wants to $what",
        onDismiss = {},
        dismissOnOutside = false,
        confirmLabel = "Allow",
        onConfirm = { ask.onAnswer(true); onAnswered() },
        dismissLabel = "Block",
        onDismissAction = { ask.onAnswer(false); onAnswered() },
        header = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ask.permissions.forEach { BrutalIconTile(permissionIcon(it), fill = AccentCyan, tint = InkBlack) }
            }
        }
    ) {
        Text(
            if (ask.isPrivate) "Remembered until you close your private tabs."
            else "Gecko remembers your answer for this site. Change it from the shield in the address bar or in Settings.",
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 19.sp
        )
    }
}

/** Settings > Site permissions: every site that was given an answer. */
@Composable
fun SitePermissionsPage(runtime: GeckoRuntime, onBack: () -> Unit) {
    val sites = SitePermissions.all.entries.sortedBy { it.key }
    var confirmClear by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        BrutalPageBar(title = "Site permissions", onBack = onBack) {
            if (sites.isNotEmpty()) {
                BrutalIconButton(Icons.Default.Delete, "Reset all", { confirmClear = true })
            }
        }
        if (sites.isEmpty()) {
            BrutalEmptyState(
                title = "No site permissions",
                message = "When a site asks for your location, camera, microphone or notifications, your answer shows up here."
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                items(sites, key = { it.key }) { (host, perms) ->
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp)
                        ) {
                            SiteFavicon("https://$host", size = 28.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(host, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            BrutalIconButton(Icons.Default.Delete, "Forget $host", { SitePermissions.forget(host, runtime) }, iconSize = 20.dp)
                        }
                        perms.entries.sortedBy { it.key.ordinal }.forEach { (p, allowed) ->
                            PermissionRow(p, allowed) { SitePermissions.set(host, p, it, runtime = runtime) }
                        }
                        Spacer(Modifier.height(8.dp))
                        BrutalHairline()
                    }
                }
            }
        }
    }
    if (confirmClear) {
        BrutalDialog(
            title = "Reset all site permissions?",
            onDismiss = { confirmClear = false },
            confirmLabel = "Reset",
            onConfirm = { SitePermissions.forgetAll(runtime); confirmClear = false },
            dismissLabel = "Cancel"
        ) {
            Text("Every site will ask again the next time it needs a permission.", color = TextSecondary, fontSize = 14.sp, lineHeight = 19.sp)
        }
    }
}
