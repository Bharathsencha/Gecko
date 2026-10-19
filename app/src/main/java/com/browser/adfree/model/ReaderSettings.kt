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

package com.browser.adfree.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

enum class ReaderTheme(
    val title: String,
    val backgroundColor: Color,
    val textColor: Color,
    val surfaceColor: Color
) {
    OLED_DARK(
        title = "Dark",
        backgroundColor = Color(0xFF000000),
        textColor = Color(0xFFE5E5EA),
        surfaceColor = Color(0xFF1C1C1E)
    ),
    SEPIA(
        title = "Sepia",
        backgroundColor = Color(0xFFFBF0D9),
        textColor = Color(0xFF5F4B32),
        surfaceColor = Color(0xFFEFE4CC)
    ),
    SLATE(
        title = "Slate",
        backgroundColor = Color(0xFF2C2C2E),
        textColor = Color(0xFFF2F2F7),
        surfaceColor = Color(0xFF3A3A3C)
    ),
    WHITE(
        title = "White",
        backgroundColor = Color(0xFFFFFFFF),
        textColor = Color(0xFF1C1C1E),
        surfaceColor = Color(0xFFF2F2F7)
    )
}

enum class ReaderFont(val label: String, val fontFamily: FontFamily) {
    SYSTEM("System Sans", FontFamily.SansSerif),
    SERIF("Classic Serif", FontFamily.Serif),
    MONO("Monospace", FontFamily.Monospace)
}

data class ArticleData(
    val title: String,
    val byline: String?,
    val contentHtml: String,
    val isArticle: Boolean = true
)
