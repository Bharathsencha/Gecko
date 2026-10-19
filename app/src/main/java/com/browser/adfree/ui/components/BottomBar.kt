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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.ui.theme.*

/**
 * Docked toolbar spanning the full width, sitting directly on the navigation
 * bar. A horizontal swipe on it switches tabs.
 */
@Composable
fun BottomBar(
    progress: Int,
    isLoading: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    activeDownloadsCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onShare: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark)
    ) {
        if (isLoading && progress in 1..99) {
            BrutalProgressLine(progress = progress / 100f)
        } else {
            BrutalRule()
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(56.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragOffset < -60f) onSwipeLeft() else if (dragOffset > 60f) onSwipeRight()
                            dragOffset = 0f
                        },
                        onHorizontalDrag = { _, dragAmount -> dragOffset += dragAmount }
                    )
                }
        ) {
            BrutalIconButton(Icons.Default.ArrowBack, "Back", onBack, enabled = canGoBack)
            BrutalIconButton(Icons.Default.ArrowForward, "Forward", onForward, enabled = canGoForward)
            BrutalIconButton(Icons.Default.Share, "Share", onShare)
            BrutalIconButton(Icons.Default.Bookmarks, "Bookmarks", onOpenBookmarks)

            Box(contentAlignment = Alignment.TopEnd) {
                BrutalIconButton(
                    icon = Icons.Default.FileDownload,
                    contentDescription = if (activeDownloadsCount > 0) "Downloads, $activeDownloadsCount active" else "Downloads",
                    onClick = onOpenDownloads,
                    boxed = activeDownloadsCount > 0,
                    fill = AccentCyan,
                    tint = if (activeDownloadsCount > 0) OnAccent else TextPrimary
                )
                if (activeDownloadsCount > 0) {
                    Box(
                        modifier = Modifier
                            .offset(x = (-2).dp, y = 2.dp)
                            .widthIn(min = 18.dp)
                            .height(18.dp)
                            .brutalBox(fill = SurfaceDark, border = BorderSubtle, borderWidth = 1.5.dp, shape = RoundedCornerShape(3.dp))
                            .padding(horizontal = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = activeDownloadsCount.toString(),
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            BrutalIconButton(Icons.Default.Settings, "Settings", onOpenSettings)
        }
    }
}
