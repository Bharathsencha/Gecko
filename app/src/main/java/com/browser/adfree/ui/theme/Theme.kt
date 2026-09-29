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

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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

    val browserColors = when {
        !isEffectiveDark -> BrowserColors(
            background = Color(0xFFF2F2F7),
            surface = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xFFFFFFFF),
            surfaceCard = Color(0xFFE5E5EA),
            border = Color(0xFFD1D1D6),
            textPrimary = Color(0xFF000000),
            textSecondary = Color(0xFF6C6C70),
            accent = primaryAccent,
            isDark = false
        )
        themeMode == AppThemeMode.DARK_MIDNIGHT -> BrowserColors(
            background = Color(0xFF070B14),
            surface = Color(0xFF0D1527),
            surfaceElevated = Color(0xFF131F38),
            surfaceCard = Color(0xFF1A2A4C),
            border = Color(0xFF24375F),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFF94A3B8),
            accent = primaryAccent,
            isDark = true
        )
        themeMode == AppThemeMode.DARK_CHARCOAL -> BrowserColors(
            background = Color(0xFF121214),
            surface = Color(0xFF18181B),
            surfaceElevated = Color(0xFF222226),
            surfaceCard = Color(0xFF27272A),
            border = Color(0xFF3F3F46),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFFA1A1AA),
            accent = primaryAccent,
            isDark = true
        )
        else -> BrowserColors(
            background = Color(0xFF000000),
            surface = Color(0xFF0C0C0E),
            surfaceElevated = Color(0xFF161618),
            surfaceCard = Color(0xFF1E1E22),
            border = Color(0xFF2C2C30),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFF8E8E93),
            accent = primaryAccent,
            isDark = true
        )
    }

    val colorScheme = if (!isEffectiveDark) {
        lightColorScheme(
            primary = primaryAccent,
            secondary = primaryAccent,
            background = browserColors.background,
            surface = browserColors.surface,
            surfaceVariant = browserColors.surfaceElevated,
            onPrimary = Color.White,
            onBackground = browserColors.textPrimary,
            onSurface = browserColors.textPrimary
        )
    } else {
        darkColorScheme(
            primary = primaryAccent,
            secondary = primaryAccent,
            background = browserColors.background,
            surface = browserColors.surface,
            surfaceVariant = browserColors.surfaceElevated,
            onPrimary = Color.White,
            onBackground = browserColors.textPrimary,
            onSurface = browserColors.textPrimary
        )
    }

    // Invert system status bar & navigation bar icons according to dark/light theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isEffectiveDark
                insetsController.isAppearanceLightNavigationBars = !isEffectiveDark
            }
        }
    }

    val customTextSelectionColors = TextSelectionColors(
        handleColor = primaryAccent,
        backgroundColor = primaryAccent.copy(alpha = 0.35f)
    )

    CompositionLocalProvider(
        LocalBrowserColors provides browserColors,
        LocalTextSelectionColors provides customTextSelectionColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
