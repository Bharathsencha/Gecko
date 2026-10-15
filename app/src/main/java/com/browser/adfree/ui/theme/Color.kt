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

/**
 * Neo-brutalist palette: flat warm surfaces, a single ink colour for text,
 * borders and hard shadows, and solid pastel accents. No gradients, no blur.
 */
data class BrowserColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceCard: Color,
    /** Ink: 2dp outlines and dividers. */
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    /** Primary fill (user selectable). Content on it is always [onAccent]. */
    val accent: Color,
    /** Hard offset shadow behind raised boxes. */
    val shadow: Color,
    val onAccent: Color,
    val isDark: Boolean
)

val InkBlack = Color(0xFF111111)
val PaperLight = Color(0xFFF4F1EA)

val LightBrowserColors = BrowserColors(
    background = PaperLight,
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFDF7),
    surfaceCard = Color(0xFFEAE5D9),
    border = InkBlack,
    textPrimary = InkBlack,
    textSecondary = Color(0xFF57534B),
    accent = Color(0xFFFFD84D),
    shadow = InkBlack,
    onAccent = InkBlack,
    isDark = false
)

val DarkBrowserColors = BrowserColors(
    background = Color(0xFF111111),
    surface = Color(0xFF1A1A1A),
    surfaceElevated = Color(0xFF212121),
    surfaceCard = Color(0xFF2A2A2A),
    border = Color(0xFFEDE8DC),
    textPrimary = Color(0xFFF2EEE4),
    textSecondary = Color(0xFFA9A49A),
    accent = Color(0xFFFFD84D),
    shadow = Color(0xFFEDE8DC),
    onAccent = InkBlack,
    isDark = true
)

val LocalBrowserColors = staticCompositionLocalOf { LightBrowserColors }

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

val OnAccent: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalBrowserColors.current.onAccent

val HardShadow: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalBrowserColors.current.shadow

/** Solid fill marking private-browsing UI. Content on it is ink. */
val PrivatePurple = Color(0xFFB49CFF)

// Status colours, readable as text and icons on both paper and dark surfaces.
val DangerRed = Color(0xFFE5483F)
val ShieldGreen = Color(0xFF22A05A)
val FlameOrange = Color(0xFFF08C2E)
val WarningAmber = Color(0xFFE0A800)

