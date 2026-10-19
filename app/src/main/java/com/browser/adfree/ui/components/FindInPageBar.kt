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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.ui.theme.*

/**
 * Find in page, docked under the address bar so the keyboard never covers it.
 * [status] is the match count ("3/12"), "No matches", or empty before anything is typed.
 */
@Composable
fun FindInPageBar(
    query: String,
    onQueryChange: (String) -> Unit,
    status: String,
    hasMatches: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(modifier.fillMaxWidth().background(SurfaceDark)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .brutalBox(fill = SurfaceElevated, border = BorderSubtle, borderWidth = 1.5.dp)
                    .padding(horizontal = 10.dp)
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(TextPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrect = false),
                    keyboardActions = KeyboardActions(onSearch = { onNext() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Find in page", color = TextSecondary, fontSize = 14.sp, maxLines = 1)
                        inner()
                    }
                )
            }
            if (status.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    status,
                    color = if (query.isNotEmpty() && !hasMatches) DangerRed else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            BrutalIconButton(Icons.Default.KeyboardArrowUp, "Previous match", onPrevious, enabled = hasMatches)
            BrutalIconButton(Icons.Default.KeyboardArrowDown, "Next match", onNext, enabled = hasMatches)
            BrutalIconButton(Icons.Default.Close, "Close find in page", onClose)
        }
        BrutalRule()
    }
}
