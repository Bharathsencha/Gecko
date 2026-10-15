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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.ui.theme.*

data class PendingSaveLogin(
    val domain: String,
    val username: String,
    val password: String
)

/** Offered after a login form is submitted in a normal tab. */
@Composable
fun SavePasswordPromptDialog(
    pendingLogin: PendingSaveLogin,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    BrutalDialog(
        title = "Save password for ${pendingLogin.domain.removePrefix("www.")}?",
        onDismiss = onDismiss,
        confirmLabel = "Save",
        onConfirm = onSave,
        dismissLabel = "Not now"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .brutalBox(fill = SurfaceCard, border = BorderSubtle, borderWidth = 1.5.dp, shape = RoundedCornerShape(4.dp))
                .padding(12.dp)
        ) {
            Text(pendingLogin.username.ifBlank { "No username" }, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("•".repeat(pendingLogin.password.length.coerceIn(6, 14)), color = TextSecondary, fontSize = 15.sp)
        }
        Text(
            "Stored on this phone only, encrypted with a key kept in its secure hardware.",
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}
