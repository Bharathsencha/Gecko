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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.R
import com.browser.adfree.ui.theme.*

/** First-run offer to open links from other apps in Gecko. */
@Composable
fun DefaultBrowserPromptDialog(
    onSetDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    BrutalDialog(
        title = "Open links in Gecko?",
        onDismiss = onDismiss,
        confirmLabel = "Set as default",
        onConfirm = onSetDefault,
        dismissLabel = "Not now",
        stackButtons = true,
        header = {
            Image(painterResource(R.drawable.gecko_logo), contentDescription = null, modifier = Modifier.size(64.dp))
        }
    ) {
        Text(
            "Links you tap in other apps will open here, with ads and trackers blocked.",
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 19.sp
        )
    }
}
