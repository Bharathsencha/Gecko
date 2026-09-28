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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.ui.theme.*

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
    onOpenTabs: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        // Page Loading Progress Bar at bottom of page
        AnimatedVisibility(
            visible = isLoading && progress in 1..99,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LinearProgressIndicator(
                progress = progress / 100f,
                color = AccentCyan,
                trackColor = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
            )
        }

        // Frosted Glass Floating Toolbar with Swipe Gesture detection
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(SurfaceElevated.copy(alpha = 0.94f))
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(BorderSubtle, AccentCyan.copy(alpha = 0.25f), BorderSubtle)
                    ),
                    RoundedCornerShape(26.dp)
                )
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragOffset < -60f) {
                                onSwipeLeft()
                            } else if (dragOffset > 60f) {
                                onSwipeRight()
                            }
                            dragOffset = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            dragOffset += dragAmount
                        }
                    )
                }
                .padding(horizontal = 6.dp)
        ) {
            // 1. Back Button
            IconButton(
                onClick = onBack,
                enabled = canGoBack,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = TextPrimary,
                    disabledContentColor = TextSecondary.copy(alpha = 0.3f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBackIosNew,
                    contentDescription = "Back",
                    modifier = Modifier.size(19.dp)
                )
            }

            // 2. Forward Button
            IconButton(
                onClick = onForward,
                enabled = canGoForward,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = TextPrimary,
                    disabledContentColor = TextSecondary.copy(alpha = 0.3f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForwardIos,
                    contentDescription = "Forward",
                    modifier = Modifier.size(19.dp)
                )
            }

            // 3. Share Action Button
            IconButton(
                onClick = onShare,
                colors = IconButtonDefaults.iconButtonColors(contentColor = TextPrimary)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    modifier = Modifier.size(20.dp)
                )
            }

            // 4. Bookmarks Action Button
            IconButton(
                onClick = onOpenBookmarks,
                colors = IconButtonDefaults.iconButtonColors(contentColor = TextPrimary)
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmarks,
                    contentDescription = "Bookmarks",
                    modifier = Modifier.size(20.dp)
                )
            }

            // 5. Downloads Manager Button
            Box(contentAlignment = Alignment.TopEnd) {
                IconButton(
                    onClick = onOpenDownloads,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = TextPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Downloads",
                        tint = if (activeDownloadsCount > 0) AccentCyan else TextPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                }

                if (activeDownloadsCount > 0) {
                    Box(
                        modifier = Modifier
                            .offset(x = (-4).dp, y = 4.dp)
                            .size(16.dp)
                            .background(AccentCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = activeDownloadsCount.toString(),
                            color = DarkBg,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 5. Tabs Overview Button
            IconButton(
                onClick = onOpenTabs,
                colors = IconButtonDefaults.iconButtonColors(contentColor = TextPrimary)
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "Tabs",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
