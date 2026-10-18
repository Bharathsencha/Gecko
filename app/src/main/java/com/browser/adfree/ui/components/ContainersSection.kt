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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.ui.theme.*
import org.mozilla.geckoview.GeckoRuntime

/** User-created containers: add, rename, recolour, clear data, delete. */
@Composable
internal fun ContainersSection(runtime: GeckoRuntime) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<com.browser.adfree.model.TabContainer?>(null) }
    var creating by remember { mutableStateOf(false) }
    val containers = com.browser.adfree.model.TabContainer.all

    SettingsGroup(shadow = CardShadows[3]) {
        Text(
            "A container keeps its own cookies and site data, so sites in one cannot recognise you in another. Make one for work, shopping or anything else, then pick it when you open a new tab.",
            color = TextSecondary,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(16.dp)
        )
        containers.forEach { container ->
            BrutalHairline(startIndent = 16.dp)
            BrutalListRow(
                title = container.displayName,
                subtitle = "Tap to rename, recolour or delete",
                icon = Icons.Default.Layers,
                iconFill = Color(container.color),
                onClick = { editing = container },
                trailing = {
                    Text(
                        "Clear",
                        color = DangerRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .brutalTap {
                                runtime.storageController.clearDataForSessionContext(container.id)
                                android.widget.Toast.makeText(context, "${container.displayName} data cleared", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    )
                }
            )
        }
        BrutalHairline(startIndent = 16.dp)
        BrutalListRow(
            title = "New container",
            icon = Icons.Default.Add,
            onClick = { creating = true },
            trailing = { Chevron() }
        )
    }

    if (creating || editing != null) {
        val current = editing
        var name by remember(current) { mutableStateOf(current?.displayName ?: "") }
        var color by remember(current) { mutableStateOf(current?.color ?: com.browser.adfree.model.TabContainer.PALETTE[com.browser.adfree.model.TabContainer.all.size % com.browser.adfree.model.TabContainer.PALETTE.size]) }
        val close = { creating = false; editing = null }
        BrutalDialog(
            title = if (current == null) "New container" else "Edit container",
            onDismiss = close,
            confirmLabel = "Save",
            onConfirm = {
                val clean = name.trim()
                if (clean.isNotEmpty()) {
                    if (current == null) com.browser.adfree.model.TabContainer.add(clean, color)
                    else com.browser.adfree.model.TabContainer.update(current.copy(displayName = clean.take(24), color = color))
                    close()
                }
            },
            dismissLabel = if (current == null) "Cancel" else "Delete",
            onDismissAction = if (current == null) close else {
                {
                    runtime.storageController.clearDataForSessionContext(current.id)
                    com.browser.adfree.model.TabContainer.remove(current.id)
                    close()
                }
            }
        ) {
            BrutalTextField(value = name, onValueChange = { name = it.take(24) }, label = "Name", placeholder = "Work, Shopping, Gaming...")
            Spacer(Modifier.height(14.dp))
            Text("Colour", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.browser.adfree.model.TabContainer.PALETTE.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .brutalPressable(
                                fill = Color(c),
                                border = BorderSubtle,
                                shadow = HardShadow,
                                shadowOffset = if (c == color) 0.dp else 2.dp,
                                role = Role.RadioButton,
                                onClick = { color = c }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (c == color) androidx.compose.material3.Icon(Icons.Default.Check, contentDescription = null, tint = InkBlack, modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (current != null) {
                Spacer(Modifier.height(10.dp))
                Text("Deleting also erases this container's cookies and site data. Open tabs in it keep working until closed.", color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}
