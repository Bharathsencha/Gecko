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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.ui.theme.*
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFavoriteSheet(
    onAdd: (title: String, url: String) -> Unit,
    onDismissRequest: () -> Unit
) {
    var titleInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var titleEdited by remember { mutableStateOf(false) }

    val host = remember(urlInput) {
        val trimmed = urlInput.trim()
        if (!trimmed.contains(".")) "" else try {
            URI(if (trimmed.contains("://")) trimmed else "https://$trimmed").host?.removePrefix("www.") ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    val canAdd = host.isNotBlank()

    fun submit() {
        if (!canAdd) return
        onAdd(titleInput.ifBlank { host }, urlInput.trim())
        onDismissRequest()
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Text("Add favourite", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(16.dp))

            BrutalTextField(
                value = urlInput,
                onValueChange = {
                    urlInput = it
                    if (!titleEdited) {
                        val name = it.trim().removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore(".")
                        titleInput = name.replaceFirstChar { c -> c.uppercase() }
                    }
                },
                label = "Address",
                placeholder = "example.com",
                keyboardType = KeyboardType.Uri
            )
            Spacer(Modifier.height(14.dp))
            BrutalTextField(
                value = titleInput,
                onValueChange = {
                    titleInput = it
                    titleEdited = true
                },
                label = "Name",
                placeholder = "Shown under the tile",
                imeAction = ImeAction.Done,
                onImeAction = { submit() }
            )

            if (canAdd) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SiteFavicon(urlInput.trim().let { if (it.contains("://")) it else "https://$it" }, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(titleInput.ifBlank { host }, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(host, color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            BrutalButton(
                text = "Add",
                icon = Icons.Default.Add,
                onClick = { submit() },
                enabled = canAdd,
                modifier = Modifier.fillMaxWidth().padding(end = 3.dp)
            )
        }
    }
}
