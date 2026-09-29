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

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.browser.adfree.storage.AppAccentColor
import com.browser.adfree.storage.AppThemeMode
import com.browser.adfree.storage.BrowserPreferencesRepository
import com.browser.adfree.storage.HomepageWallpaper
import com.browser.adfree.ui.theme.*
import java.io.File

@Composable
fun CustomizationSuiteSubView(
    preferencesRepo: BrowserPreferencesRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Photo & GIF picker launcher for custom start page wallpaper (.gif, .png, .jpg, .jpeg, .webp)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = saveCustomWallpaper(context, uri)
            if (!savedPath.isNullOrBlank()) {
                preferencesRepo.setHomepageWallpaperPreference(HomepageWallpaper.CUSTOM, savedPath)
            }
        }
    }

    val currentAccent = Color(preferencesRepo.accentColor.hex)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Compact & Scaled Subview Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(32.dp)
                    .background(SurfaceElevated, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Back to Settings",
                    tint = TextPrimary,
                    modifier = Modifier.size(17.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "Personalization & Appearance",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Themes, wallpapers & dynamic accents",
                    fontSize = 11.5.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Section 1: Theme & Dark Flavors
        SettingsSectionHeader(title = "THEME & DARK FLAVORS")
        SettingsCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "App Color Theme",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Theme Mode Grid
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (mode in AppThemeMode.values()) {
                        val isSelected = preferencesRepo.themeMode == mode
                        val (modeBg, modeDesc, modeIcon) = when (mode) {
                            AppThemeMode.LIGHT -> Triple(Color(0xFFF2F2F7), "Crisp daylight theme with dark text", Icons.Rounded.LightMode)
                            AppThemeMode.DARK_OLED -> Triple(Color(0xFF000000), "Pitch black #000000 • Maximum battery savings", Icons.Rounded.DarkMode)
                            AppThemeMode.DARK_MIDNIGHT -> Triple(Color(0xFF070B14), "Deep navy cosmos glow", Icons.Rounded.Nightlight)
                            AppThemeMode.DARK_CHARCOAL -> Triple(Color(0xFF141416), "Modern matte charcoal dark", Icons.Rounded.Contrast)
                            AppThemeMode.SYSTEM -> Triple(Color(0xFF2C2C2E), "Matches device system theme", Icons.Rounded.SettingsBrightness)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) currentAccent.copy(alpha = 0.15f) else SurfaceCard)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    brush = if (isSelected) {
                                        Brush.horizontalGradient(listOf(currentAccent, currentAccent.copy(alpha = 0.5f), currentAccent))
                                    } else {
                                        Brush.linearGradient(listOf(BorderSubtle, BorderSubtle))
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { preferencesRepo.setThemeModePreference(mode) }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(modeBg)
                                        .border(1.dp, BorderSubtle, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = modeIcon,
                                        contentDescription = null,
                                        tint = if (mode == AppThemeMode.LIGHT) Color(0xFF1C1C1E) else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = mode.displayName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = modeDesc,
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = currentAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section 2: Dynamic Accent Colors
        SettingsSectionHeader(title = "ACCENT COLOR PALETTE")
        SettingsCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "System Accent Highlight",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "Colors buttons, progress indicators, active tab indicators and highlights",
                    fontSize = 11.5.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AppAccentColor.values()) { accent ->
                        val isSelected = preferencesRepo.accentColor == accent
                        val color = Color(accent.hex)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { preferencesRepo.setAccentColorPreference(accent) }
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        brush = if (isSelected) {
                                            Brush.sweepGradient(listOf(color, Color.White, color))
                                        } else {
                                            Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                        },
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = if (accent == AppAccentColor.YELLOW || accent == AppAccentColor.MONOCHROME) Color.Black else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = accent.displayName.substringBefore(" "),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) currentAccent else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section 3: Homepage Wallpapers & Visual FX (.gif, .png, .jpg, .webp)
        SettingsSectionHeader(title = "START PAGE WALLPAPER & FX")
        SettingsCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Start Page Background",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "Supports preset gradients and animated GIFs, PNG, JPG, and WebP",
                    fontSize = 11.5.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Wallpaper Presets (2 columns grid)
                val wallpapers = HomepageWallpaper.values()
                val chunkedWallpapers = wallpapers.toList().chunked(2)

                chunkedWallpapers.forEach { rowWallpapers ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowWallpapers.forEach { wp ->
                            val isSelected = preferencesRepo.homepageWallpaper == wp

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SurfaceElevated)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        brush = if (isSelected) {
                                            Brush.horizontalGradient(listOf(currentAccent, currentAccent.copy(alpha = 0.5f), currentAccent))
                                        } else {
                                            Brush.linearGradient(listOf(BorderSubtle, BorderSubtle))
                                        },
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        if (wp == HomepageWallpaper.CUSTOM) {
                                            if (preferencesRepo.customWallpaperUri.isBlank()) {
                                                photoPickerLauncher.launch("image/*")
                                            } else {
                                                preferencesRepo.setHomepageWallpaperPreference(HomepageWallpaper.CUSTOM, preferencesRepo.customWallpaperUri)
                                            }
                                        } else {
                                            preferencesRepo.setHomepageWallpaperPreference(wp)
                                        }
                                    }
                            ) {
                                // Background preview
                                if (wp == HomepageWallpaper.CUSTOM && preferencesRepo.customWallpaperUri.isNotBlank()) {
                                    val customFile = File(preferencesRepo.customWallpaperUri)
                                    if (customFile.exists()) {
                                        AsyncImage(
                                            model = customFile,
                                            contentDescription = "Custom Preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.35f))
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(getWallpaperPreviewBrush(wp))
                                    )
                                }

                                // Overlay Text & Check
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Text(
                                        text = wp.displayName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = "Active",
                                            tint = currentAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // If Custom is selected, show Gallery / GIF upload button
                if (preferencesRepo.homepageWallpaper == HomepageWallpaper.CUSTOM) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceElevated,
                            contentColor = TextPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            tint = currentAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (preferencesRepo.customWallpaperUri.isBlank()) "Choose GIF, PNG, JPG, WebP" else "Change Custom Wallpaper",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = BorderSubtle, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Wallpaper Blur Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Wallpaper Blur",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Text(
                        text = "${preferencesRepo.wallpaperBlur.toInt()} dp",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentAccent
                    )
                }

                Slider(
                    value = preferencesRepo.wallpaperBlur,
                    onValueChange = { preferencesRepo.setWallpaperBlurPreference(it) },
                    valueRange = 0f..24f,
                    colors = SliderDefaults.colors(
                        thumbColor = currentAccent,
                        activeTrackColor = currentAccent,
                        inactiveTrackColor = BorderSubtle
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Wallpaper Dimming Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dimming Overlay",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Text(
                        text = "${(preferencesRepo.wallpaperDim * 100).toInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentAccent
                    )
                }

                Slider(
                    value = preferencesRepo.wallpaperDim,
                    onValueChange = { preferencesRepo.setWallpaperDimPreference(it) },
                    valueRange = 0f..0.8f,
                    colors = SliderDefaults.colors(
                        thumbColor = currentAccent,
                        activeTrackColor = currentAccent,
                        inactiveTrackColor = BorderSubtle
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section 4: Start Page Widgets & Layout
        SettingsSectionHeader(title = "START PAGE WIDGETS & LAYOUT")
        SettingsCard {
            SettingsSwitchRow(
                title = "Greeting & Clock Header",
                subtitle = "Displays time-of-day greeting on home page",
                icon = Icons.Rounded.Schedule,
                checked = preferencesRepo.showGreetingHeader,
                onCheckedChange = { preferencesRepo.setShowGreetingHeaderPreference(it) }
            )
            Divider(color = BorderSubtle, thickness = 0.5.dp)
            SettingsSwitchRow(
                title = "Privacy & Shields Stats Widget",
                subtitle = "Live blocked ads & trackers banner",
                icon = Icons.Rounded.Security,
                checked = preferencesRepo.showPrivacyStatsWidget,
                onCheckedChange = { preferencesRepo.setShowPrivacyStatsPreference(it) }
            )
            Divider(color = BorderSubtle, thickness = 0.5.dp)
            SettingsSwitchRow(
                title = "Favorites Speed Dial Grid",
                subtitle = "Quick access shortcut tiles",
                icon = Icons.Rounded.Bookmarks,
                checked = preferencesRepo.showFavoritesGrid,
                onCheckedChange = { preferencesRepo.setShowFavoritesGridPreference(it) }
            )

            if (preferencesRepo.showFavoritesGrid) {
                Divider(color = BorderSubtle, thickness = 0.5.dp)
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = "Favorites Grid Columns",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCard)
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(3, 4, 5).forEach { cols ->
                            val isSelected = preferencesRepo.favoritesGridColumns == cols
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) currentAccent else Color.Transparent)
                                    .clickable { preferencesRepo.setFavoritesGridColumnsPreference(cols) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$cols Columns",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) (if (preferencesRepo.accentColor == AppAccentColor.YELLOW || preferencesRepo.accentColor == AppAccentColor.MONOCHROME) Color.Black else Color.White) else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section 5: Layout & Address Bar Position
        SettingsSectionHeader(title = "NAVIGATION & CONTROLS")
        SettingsCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Address Bar Position",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceCard)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(false to "Top Bar", true to "Bottom Bar (Safari)").forEach { (isBottom, label) ->
                        val isSelected = preferencesRepo.bottomAddressBar == isBottom
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) currentAccent else Color.Transparent)
                                .clickable { preferencesRepo.setBottomAddressBarPreference(isBottom) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) (if (preferencesRepo.accentColor == AppAccentColor.YELLOW || preferencesRepo.accentColor == AppAccentColor.MONOCHROME) Color.Black else Color.White) else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

fun saveCustomWallpaper(context: Context, uri: Uri): String? {
    return try {
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val ext = when {
            mimeType.contains("gif") -> "gif"
            mimeType.contains("png") -> "png"
            mimeType.contains("webp") -> "webp"
            else -> "jpg"
        }
        val destinationFile = File(context.filesDir, "custom_wallpaper.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            destinationFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        destinationFile.absolutePath
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun getWallpaperPreviewBrush(wp: HomepageWallpaper): Brush {
    val isDark = LocalBrowserColors.current.isDark
    return when (wp) {
        HomepageWallpaper.DEFAULT -> if (isDark) {
            Brush.radialGradient(listOf(Color(0xFF1B2A4A), Color(0xFF0D1525), Color(0xFF000000)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF2F2F7), Color(0xFFE5E5EA)))
        }
        HomepageWallpaper.AURORA -> Brush.linearGradient(listOf(Color(0xFF031024), Color(0xFF0A2E38), Color(0xFF041E24), Color(0xFF020912)))
        HomepageWallpaper.NEBULA -> Brush.radialGradient(listOf(Color(0xFF2A0845), Color(0xFF14052B), Color(0xFF000000)))
        HomepageWallpaper.CYBER -> Brush.linearGradient(listOf(Color(0xFF0D0221), Color(0xFF0F084B), Color(0xFF26408B), Color(0xFF000000)))
        HomepageWallpaper.OLED -> Brush.linearGradient(listOf(Color(0xFF000000), Color(0xFF000000)))
        HomepageWallpaper.FOREST -> Brush.linearGradient(listOf(Color(0xFF061A14), Color(0xFF0D2818), Color(0xFF040F0A), Color(0xFF000000)))
        HomepageWallpaper.SUNSET -> Brush.linearGradient(listOf(Color(0xFF2D0B00), Color(0xFF4A1500), Color(0xFF1A0500), Color(0xFF000000)))
        HomepageWallpaper.CUSTOM -> Brush.linearGradient(listOf(SurfaceElevated, DarkBg))
    }
}
