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

package com.browser.adfree.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.browser.adfree.storage.AppAccentColor
import com.browser.adfree.storage.AppThemeMode

@Composable
fun BrowserTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK_OLED,
    accentColor: AppAccentColor = AppAccentColor.CYAN,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isEffectiveDark = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemDark
        else -> true
    }

    val primaryAccent = Color(accentColor.hex)

    val colorScheme = when {
        !isEffectiveDark -> lightColorScheme(
            primary = primaryAccent,
            secondary = primaryAccent,
            background = Color(0xFFF2F2F7),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE5E5EA),
            onPrimary = Color.White,
            onBackground = Color(0xFF000000),
            onSurface = Color(0xFF000000)
        )
        themeMode == AppThemeMode.DARK_MIDNIGHT -> darkColorScheme(
            primary = primaryAccent,
            secondary = primaryAccent,
            background = Color(0xFF070B14),
            surface = Color(0xFF0D1527),
            surfaceVariant = Color(0xFF131F38),
            onPrimary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        )
        themeMode == AppThemeMode.DARK_CHARCOAL -> darkColorScheme(
            primary = primaryAccent,
            secondary = primaryAccent,
            background = Color(0xFF121214),
            surface = Color(0xFF18181B),
            surfaceVariant = Color(0xFF222226),
            onPrimary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        )
        else -> darkColorScheme(
            primary = primaryAccent,
            secondary = primaryAccent,
            background = Color(0xFF000000),
            surface = Color(0xFF0C0C0E),
            surfaceVariant = Color(0xFF161618),
            onPrimary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        )
    }

    val customTextSelectionColors = TextSelectionColors(
        handleColor = primaryAccent,
        backgroundColor = primaryAccent.copy(alpha = 0.35f)
    )

    MaterialTheme(
        colorScheme = colorScheme
    ) {
        CompositionLocalProvider(
            LocalTextSelectionColors provides customTextSelectionColors,
            content = content
        )
    }
}
