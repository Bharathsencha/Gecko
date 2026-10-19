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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.storage.AppAccentColor
import com.browser.adfree.storage.AppThemeMode
import com.browser.adfree.storage.BrowserPreferencesRepository
import com.browser.adfree.storage.HomepageWallpaper
import com.browser.adfree.ui.theme.*
import java.io.File

@Composable
internal fun AppearanceSettings(preferencesRepo: BrowserPreferencesRepository, onOpenDesigner: (String?) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val saved = saveCustomWallpaper(context, uri)
            if (!saved.isNullOrBlank()) preferencesRepo.setHomepageWallpaperPreference(HomepageWallpaper.CUSTOM, saved)
        }
    }

    SettingsScaffold(title = "Theme and layout", onBack = onBack) {
        BrutalSectionLabel("Theme")
        SettingsGroup(shadow = CardShadows[3]) {
            AppThemeMode.values().forEachIndexed { index, mode ->
                if (index > 0) BrutalHairline(startIndent = 16.dp)
                ChoiceRow(
                    title = mode.displayName,
                    subtitle = if (mode == AppThemeMode.SYSTEM) "Follow the phone's dark theme setting" else null,
                    selected = preferencesRepo.themeMode == mode,
                    onClick = { preferencesRepo.setThemeModePreference(mode) }
                )
            }
        }

        BrutalSectionLabel("Accent colour")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppAccentColor.values().forEach { accent ->
                val selected = preferencesRepo.accentColor == accent
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .brutalPressable(
                            fill = Color(accent.hex),
                            border = BorderSubtle,
                            shadow = HardShadow,
                            shadowOffset = if (selected) 0.dp else 3.dp,
                            role = Role.RadioButton,
                            onClick = { preferencesRepo.setAccentColorPreference(accent) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        androidx.compose.material3.Icon(Icons.Default.Check, contentDescription = accent.displayName, tint = InkBlack, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        BrutalSectionLabel("Address bar")
        SettingsGroup(shadow = CardShadows[4]) {
            ChoiceRow(
                title = "Top",
                selected = !preferencesRepo.bottomAddressBar,
                onClick = { preferencesRepo.setBottomAddressBarPreference(false) }
            )
            BrutalHairline(startIndent = 16.dp)
            ChoiceRow(
                title = "Bottom",
                subtitle = "Above the toolbar, within thumb reach",
                selected = preferencesRepo.bottomAddressBar,
                onClick = { preferencesRepo.setBottomAddressBarPreference(true) }
            )
        }

        BrutalSectionLabel("Start page")
        SettingsGroup(shadow = CardShadows[5]) {
            BrutalListRow(
                title = "Show favourites",
                icon = Icons.Default.Star,
                checked = preferencesRepo.showFavoritesGrid,
                onCheckedChange = { preferencesRepo.setShowFavoritesGridPreference(it) }
            )
            if (preferencesRepo.showFavoritesGrid) {
                BrutalHairline(startIndent = 64.dp)
                BrutalListRow(
                    title = "Favourites per row",
                    subtitle = "${preferencesRepo.favoritesGridColumns}",
                    icon = Icons.Default.GridView,
                    trailing = {
                        Row {
                            BrutalIconButton(Icons.Default.Remove, "Fewer", { preferencesRepo.setFavoritesGridColumnsPreference(preferencesRepo.favoritesGridColumns - 1) }, enabled = preferencesRepo.favoritesGridColumns > 3)
                            BrutalIconButton(Icons.Default.Add, "More", { preferencesRepo.setFavoritesGridColumnsPreference(preferencesRepo.favoritesGridColumns + 1) }, enabled = preferencesRepo.favoritesGridColumns < 5)
                        }
                    }
                )
            }
            BrutalHairline(startIndent = 64.dp)
            Text(
                "Background",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 10.dp)
            )
            WallpaperGrid(
                preferencesRepo = preferencesRepo,
                onPhoto = { photoPicker.launch("image/*") },
                onNewDesign = { onOpenDesigner(null) },
                onEditDesign = { id -> onOpenDesigner(id) }
            )
            val active = preferencesRepo.customDesigns.firstOrNull { it.id == preferencesRepo.selectedDesignId }
            if (preferencesRepo.homepageWallpaper == HomepageWallpaper.DESIGN && active != null) {
                BrutalHairline(startIndent = 64.dp)
                BrutalListRow(
                    title = "Edit \"${active.name}\"",
                    subtitle = "Change shapes and colours, rename or delete",
                    icon = Icons.Default.Palette,
                    onClick = { onOpenDesigner(active.id) },
                    trailing = { Chevron() }
                )
            }
            if (preferencesRepo.homepageWallpaper == HomepageWallpaper.CUSTOM || wallpaperBrush(preferencesRepo.homepageWallpaper) != null) {
                BrutalHairline(startIndent = 16.dp)
                LabeledSlider("Dim", preferencesRepo.wallpaperDim, 0f..0.8f) { preferencesRepo.setWallpaperDimPreference(it) }
                if (preferencesRepo.homepageWallpaper == HomepageWallpaper.CUSTOM) {
                    LabeledSlider("Blur", preferencesRepo.wallpaperBlur, 0f..25f) { preferencesRepo.setWallpaperBlurPreference(it) }
                }
            }
        }
    }
}

/**
 * Background tiles: block presets, the user's own designs and a "+" tile to
 * make another, then patterns, pastels, gradients and a photo.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun WallpaperGrid(
    preferencesRepo: BrowserPreferencesRepository,
    onPhoto: () -> Unit,
    onNewDesign: () -> Unit,
    onEditDesign: (String) -> Unit
) {
    val selected = preferencesRepo.homepageWallpaper
    // Kinds are kept apart (no headings): blocks and own designs, patterns,
    // flat colours, gradients, photo.
    val groups = listOf(
        listOf(HomepageWallpaper.BLOCKS, HomepageWallpaper.STACK, HomepageWallpaper.SHAPES, HomepageWallpaper.MONDRIAN, HomepageWallpaper.DESIGN),
        listOf(HomepageWallpaper.DOTS, HomepageWallpaper.GRID, HomepageWallpaper.STRIPES, HomepageWallpaper.CHECKER),
        listOf(HomepageWallpaper.DEFAULT, HomepageWallpaper.BUTTER, HomepageWallpaper.PEACH, HomepageWallpaper.MINT, HomepageWallpaper.LAVENDER),
        listOf(HomepageWallpaper.AURORA, HomepageWallpaper.NEBULA, HomepageWallpaper.CYBER, HomepageWallpaper.OLED, HomepageWallpaper.FOREST, HomepageWallpaper.SUNSET),
        listOf(HomepageWallpaper.CUSTOM)
    )
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 19.dp, bottom = 16.dp)
    ) {
        val tileWidth = (maxWidth - 12.dp * 3) / 4
        Column {
            groups.forEachIndexed { index, group ->
                if (index > 0) {
                    Spacer(Modifier.height(16.dp))
                    BrutalHairline()
                    Spacer(Modifier.height(16.dp))
                }
                androidx.compose.foundation.layout.FlowRow(
                    maxItemsInEachRow = 4,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    group.forEach { wp ->
                        if (wp == HomepageWallpaper.DESIGN) {
                            preferencesRepo.customDesigns.forEach { saved ->
                                WallpaperTile(
                                    width = tileWidth,
                                    label = saved.name,
                                    selected = selected == HomepageWallpaper.DESIGN && preferencesRepo.selectedDesignId == saved.id,
                                    onClick = { preferencesRepo.selectDesign(saved.id) },
                                    onLongClick = { onEditDesign(saved.id) }
                                ) { mod -> DesignCanvas(saved.design, mod) }
                            }
                            WallpaperTile(width = tileWidth, label = "New design", selected = false, onClick = onNewDesign) { _ ->
                                androidx.compose.material3.Icon(Icons.Default.Add, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(26.dp))
                            }
                        } else {
                            WallpaperTile(
                                width = tileWidth,
                                label = wp.displayName,
                                selected = wp == selected,
                                onClick = { if (wp == HomepageWallpaper.CUSTOM) onPhoto() else preferencesRepo.setHomepageWallpaperPreference(wp) }
                            ) { mod ->
                                if (wp == HomepageWallpaper.CUSTOM) {
                                    androidx.compose.material3.Icon(Icons.Default.Image, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(22.dp))
                                } else {
                                    WallpaperBackground(wallpaper = wp, modifier = mod)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun WallpaperTile(
    width: androidx.compose.ui.unit.Dp,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    preview: @Composable (Modifier) -> Unit
) {
    val view = androidx.compose.ui.platform.LocalView.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(width)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .brutalBox(fill = SurfaceCard, border = BorderSubtle, shadow = if (selected) null else HardShadow, shadowOffset = 3.dp)
                .combinedClickable(
                    role = Role.RadioButton,
                    onClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        onClick()
                    },
                    onLongClick = onLongClick?.let { l ->
                        {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            l()
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            preview(
                Modifier
                    .matchParentSize()
                    .padding(2.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            if (selected) {
                Box(
                    Modifier
                        .size(26.dp)
                        .brutalBox(fill = AccentCyan, border = BorderSubtle, shape = RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(Icons.Default.Check, contentDescription = null, tint = OnAccent, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = if (selected) TextPrimary else TextSecondary,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(56.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = BorderSubtle,
                activeTrackColor = BorderSubtle,
                inactiveTrackColor = SurfaceCard
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

// ------------------------------------------------------------------ Helpers

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
            destinationFile.outputStream().use { output -> input.copyTo(output) }
        }
        destinationFile.absolutePath
    } catch (e: Exception) {
        null
    }
}
