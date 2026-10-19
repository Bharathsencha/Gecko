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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.ui.theme.*

/** Shown by HTTPS-Only mode before loading an unencrypted page. */
@Composable
fun InsecureHttpWarningDialog(
    url: String,
    onProceedAnyway: () -> Unit,
    onGoBack: () -> Unit
) {
    BrutalDialog(
        title = "This page is not encrypted",
        onDismiss = onGoBack,
        dismissOnOutside = false,
        confirmLabel = "Go back",
        onConfirm = onGoBack,
        dismissLabel = "Open anyway",
        onDismissAction = onProceedAnyway,
        header = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .brutalBox(fill = WarningAmber, border = BorderSubtle, shape = RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null, tint = InkBlack, modifier = Modifier.size(24.dp))
            }
        }
    ) {
        Text(
            "Anyone on your network can read and change what you send or receive on this page, including passwords.",
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 19.sp
        )
        Text(
            url,
            color = TextPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .brutalBox(fill = SurfaceCard, border = BorderSubtle, borderWidth = 1.5.dp, shape = RoundedCornerShape(4.dp))
                .padding(10.dp)
        )
    }
}
