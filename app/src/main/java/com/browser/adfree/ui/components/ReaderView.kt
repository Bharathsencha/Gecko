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

import android.text.Html
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.browser.adfree.model.ArticleData
import com.browser.adfree.model.ReaderFont
import com.browser.adfree.model.ReaderTheme

@Composable
fun ReaderView(
    article: ArticleData,
    onCloseReader: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appIsDark = com.browser.adfree.ui.theme.LocalBrowserColors.current.isDark
    var currentTheme by remember { mutableStateOf(if (appIsDark) ReaderTheme.OLED_DARK else ReaderTheme.WHITE) }
    var currentFont by remember { mutableStateOf(ReaderFont.SERIF) }
    var fontSizeSp by remember { mutableFloatStateOf(18f) }
    var showFontMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(currentTheme.backgroundColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Reader Navigation & Customization Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(currentTheme.surfaceColor)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                // Back / Close Button
                IconButton(onClick = onCloseReader) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Exit Reader View",
                        tint = currentTheme.textColor
                    )
                }

                // Theme Selector Pills (Dark, Sepia, Slate, White)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReaderTheme.values().forEach { theme ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(theme.backgroundColor)
                                .border(
                                    width = if (currentTheme == theme) 3.dp else 1.5.dp,
                                    color = currentTheme.textColor,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable { currentTheme = theme }
                        )
                    }
                }

                // Font Size & Style Controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Decrease Font
                    IconButton(
                        onClick = { if (fontSizeSp > 13f) fontSizeSp -= 2f },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(
                            text = "A-",
                            color = currentTheme.textColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Increase Font
                    IconButton(
                        onClick = { if (fontSizeSp < 32f) fontSizeSp += 2f },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(
                            text = "A+",
                            color = currentTheme.textColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Font Family Menu
                    Box {
                        IconButton(
                            onClick = { showFontMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatSize,
                                contentDescription = "Font Style",
                                tint = currentTheme.textColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showFontMenu,
                            onDismissRequest = { showFontMenu = false },
                            modifier = Modifier.background(currentTheme.surfaceColor)
                        ) {
                            ReaderFont.values().forEach { font ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = font.label,
                                            fontFamily = font.fontFamily,
                                            color = currentTheme.textColor
                                        )
                                    },
                                    onClick = {
                                        currentFont = font
                                        showFontMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(2.dp).background(currentTheme.textColor))
            // Scrollable Article Body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                // Article Title
                Text(
                    text = article.title,
                    color = currentTheme.textColor,
                    fontSize = (fontSizeSp + 8).sp,
                    fontFamily = currentFont.fontFamily,
                    fontWeight = FontWeight.Bold,
                    lineHeight = (fontSizeSp + 14).sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Byline / Author
                if (!article.byline.isNullOrBlank()) {
                    Text(
                        text = "By ${article.byline}",
                        color = currentTheme.textColor.copy(alpha = 0.7f),
                        fontSize = (fontSizeSp - 3).sp,
                        fontFamily = currentFont.fontFamily,
                        modifier = Modifier.padding(bottom = 18.dp)
                    )
                }

                Divider(
                    color = currentTheme.textColor.copy(alpha = 0.15f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // Article Content HTML Rendered via native TextView with Spans & Image support
                AndroidView(
                    factory = { context ->
                        TextView(context).apply {
                            setTextColor(currentTheme.textColor.toArgb())
                            textSize = fontSizeSp
                            setLineSpacing(12f, 1.25f)
                            isClickable = true
                        }
                    },
                    update = { textView ->
                        textView.setTextColor(currentTheme.textColor.toArgb())
                        textView.textSize = fontSizeSp
                        val spanned = Html.fromHtml(
                            article.contentHtml,
                            Html.FROM_HTML_MODE_COMPACT
                        )
                        textView.text = spanned
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}
