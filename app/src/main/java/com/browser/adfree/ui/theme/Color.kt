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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class BrowserColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceCard: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val isDark: Boolean
)

val LocalBrowserColors = staticCompositionLocalOf {
    BrowserColors(
        background = Color(0xFF000000),
        surface = Color(0xFF0C0C0E),
        surfaceElevated = Color(0xFF161618),
        surfaceCard = Color(0xFF1E1E22),
        border = Color(0xFF2C2C30),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFF8E8E93),
        accent = Color(0xFF00D8F6),
        isDark = true
    )
}

// Dynamic properties that automatically reflect the active theme & accent everywhere in Compose
val DarkBg: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalBrowserColors.current.background

val SurfaceDark: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalBrowserColors.current.surface

val SurfaceElevated: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalBrowserColors.current.surfaceElevated

val SurfaceCard: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalBrowserColors.current.surfaceCard

val BorderSubtle: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalBrowserColors.current.border

val TextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalBrowserColors.current.textPrimary

val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalBrowserColors.current.textSecondary

val AccentCyan: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalBrowserColors.current.accent

val AccentBlue: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalBrowserColors.current.accent

val IsDarkTheme: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalBrowserColors.current.isDark

val SurfaceSelected: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalBrowserColors.current.isDark) LocalBrowserColors.current.surfaceCard else Color(0xFFE2E2E8)

val DangerRed = Color(0xFFFF453A)
val ShieldGreen = Color(0xFF30D158)
val FlameOrange = Color(0xFFFF9500)

