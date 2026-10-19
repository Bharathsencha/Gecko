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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import com.browser.adfree.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.browser.adfree.storage.AppAccentColor
import com.browser.adfree.storage.AppThemeMode

private val BrutalShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp)
)

/**
 * Space Grotesk (SIL OFL 1.1, assets/licenses): a geometric grotesque with
 * squared terminals that suits the flat, ink-outlined look. One variable font
 * file covers every weight.
 */
@OptIn(ExperimentalTextApi::class)
val SpaceGrotesk = FontFamily(
    listOf(FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { w ->
        Font(
            resId = R.font.space_grotesk,
            weight = w,
            variationSettings = FontVariation.Settings(FontVariation.weight(w.weight))
        )
    }
)

private val BrutalTypography = Typography().run {
    fun TextStyle.sg() = copy(fontFamily = SpaceGrotesk)
    copy(
        displayLarge = displayLarge.sg(),
        displayMedium = displayMedium.sg(),
        displaySmall = displaySmall.sg(),
        headlineLarge = headlineLarge.sg(),
        headlineMedium = headlineMedium.sg(),
        headlineSmall = headlineSmall.sg().copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.sg().copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.sg().copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.sg(),
        bodyLarge = bodyLarge.sg().copy(fontSize = 15.sp),
        bodyMedium = bodyMedium.sg().copy(fontSize = 14.sp),
        bodySmall = bodySmall.sg(),
        labelLarge = labelLarge.sg().copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.sg().copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontFamily = SpaceGrotesk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
    )
}

@Composable
fun BrowserTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    accentColor: AppAccentColor = AppAccentColor.YELLOW,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val accent = Color(accentColor.hex)
    val base = if (isDark) DarkBrowserColors else LightBrowserColors
    val browserColors = base.copy(accent = accent)

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = accent,
            onPrimary = browserColors.onAccent,
            primaryContainer = accent,
            onPrimaryContainer = browserColors.onAccent,
            secondary = accent,
            onSecondary = browserColors.onAccent,
            background = browserColors.background,
            onBackground = browserColors.textPrimary,
            surface = browserColors.surface,
            onSurface = browserColors.textPrimary,
            surfaceVariant = browserColors.surfaceCard,
            onSurfaceVariant = browserColors.textSecondary,
            outline = browserColors.border,
            outlineVariant = browserColors.border.copy(alpha = 0.25f),
            error = DangerRed
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = browserColors.onAccent,
            primaryContainer = accent,
            onPrimaryContainer = browserColors.onAccent,
            secondary = accent,
            onSecondary = browserColors.onAccent,
            background = browserColors.background,
            onBackground = browserColors.textPrimary,
            surface = browserColors.surface,
            onSurface = browserColors.textPrimary,
            surfaceVariant = browserColors.surfaceCard,
            onSurfaceVariant = browserColors.textSecondary,
            outline = browserColors.border,
            outlineVariant = browserColors.border.copy(alpha = 0.2f),
            error = DangerRed
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    val selectionColors = TextSelectionColors(
        handleColor = browserColors.border,
        backgroundColor = accent.copy(alpha = 0.6f)
    )

    CompositionLocalProvider(
        LocalBrowserColors provides browserColors,
        LocalTextSelectionColors provides selectionColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = BrutalShapes,
            typography = BrutalTypography,
            content = content
        )
    }
}
