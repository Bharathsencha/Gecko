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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.ui.theme.*
import org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement

/** What the user can do with a long-pressed element. */
enum class ContextAction(val label: String, val icon: ImageVector) {
    OPEN_TAB("Open in new tab", Icons.Default.Tab),
    OPEN_PRIVATE("Open in private tab", Icons.Default.OpenInNew),
    COPY_LINK("Copy link", Icons.Default.ContentCopy),
    SHARE_LINK("Share link", Icons.Default.Share),
    DOWNLOAD_LINK("Download link", Icons.Default.Download),
    OPEN_IMAGE("Open image in new tab", Icons.Default.Image),
    DOWNLOAD_MEDIA("Save to Downloads", Icons.Default.Download),
    COPY_MEDIA("Copy address", Icons.Default.ContentCopy),
    SHARE_MEDIA("Share address", Icons.Default.Share)
}

/**
 * Long-press menu: link actions for links, save/open/copy for images, video and
 * audio. Private tabs only offer private tabs for opening.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextMenuSheet(
    element: ContextElement,
    isPrivate: Boolean,
    onAction: (ContextAction) -> Unit,
    onDismiss: () -> Unit
) {
    val hasLink = !element.linkUri.isNullOrBlank()
    val hasMedia = !element.srcUri.isNullOrBlank() && element.type != ContextElement.TYPE_NONE
    val mediaWord = when (element.type) {
        ContextElement.TYPE_IMAGE -> "image"
        ContextElement.TYPE_VIDEO -> "video"
        ContextElement.TYPE_AUDIO -> "audio"
        else -> "file"
    }
    val actions = buildList {
        if (hasLink) {
            if (!isPrivate) add(ContextAction.OPEN_TAB)
            add(ContextAction.OPEN_PRIVATE)
            add(ContextAction.COPY_LINK)
            add(ContextAction.SHARE_LINK)
            if (!hasMedia) add(ContextAction.DOWNLOAD_LINK)
        }
        if (hasMedia) {
            if (element.type == ContextElement.TYPE_IMAGE) add(ContextAction.OPEN_IMAGE)
            add(ContextAction.DOWNLOAD_MEDIA)
            add(ContextAction.COPY_MEDIA)
            add(ContextAction.SHARE_MEDIA)
        }
    }
    val heading = element.linkText?.takeIf { it.isNotBlank() }
        ?: element.title?.takeIf { it.isNotBlank() }
        ?: element.altText?.takeIf { it.isNotBlank() }
    val address = element.linkUri ?: element.srcUri.orEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceDark,
        contentColor = TextPrimary,
        tonalElevation = 0.dp,
        dragHandle = { BrutalSheetHandle() },
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                Icon(if (hasLink) Icons.Default.Link else Icons.Default.Image, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    if (heading != null) {
                        Text(heading, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    } else if (hasMedia && !hasLink) {
                        Text(mediaWord.replaceFirstChar { it.uppercase() }, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(address, color = TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            BrutalRule()
            actions.forEach { action ->
                val label = when (action) {
                    ContextAction.DOWNLOAD_MEDIA -> "Save $mediaWord"
                    ContextAction.COPY_MEDIA -> "Copy $mediaWord address"
                    ContextAction.SHARE_MEDIA -> "Share $mediaWord"
                    else -> action.label
                }
                BrutalListRow(
                    title = label,
                    icon = action.icon,
                    onClick = { onAction(action); onDismiss() }
                )
            }
        }
    }
}
