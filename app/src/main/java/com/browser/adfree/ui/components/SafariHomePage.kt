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

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.browser.adfree.model.FavoriteItem
import com.browser.adfree.storage.BrowserPreferencesRepository
import com.browser.adfree.storage.HomepageWallpaper
import com.browser.adfree.ui.theme.*
import java.net.URI
import java.net.URLEncoder

@Composable
fun SafariHomePage(
    favorites: List<FavoriteItem>,
    preferencesRepo: BrowserPreferencesRepository,
    onNavigate: (String) -> Unit,
    onAddFavorite: (title: String, url: String) -> Unit,
    onRemoveFavorite: (FavoriteItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditMode by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    val currentAccent = Color(preferencesRepo.accentColor.hex)
    val isEffectiveDark = LocalBrowserColors.current.isDark

    val isWallpaperDark = when (preferencesRepo.homepageWallpaper) {
        HomepageWallpaper.DEFAULT -> isEffectiveDark
        else -> true // Presets (Aurora, Nebula, Cyber, Forest, Sunset, OLED) and custom photos are dark
    }

    val homeTextColor = if (isWallpaperDark) Color.White else TextPrimary
    val homeSecondaryTextColor = if (isWallpaperDark) Color.White.copy(alpha = 0.75f) else TextSecondary

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Wallpaper Background Layer (Supports animated GIFs, PNG, JPG, WebP & Presets)
        if (preferencesRepo.homepageWallpaper == HomepageWallpaper.CUSTOM && preferencesRepo.customWallpaperUri.isNotBlank()) {
            val customFile = java.io.File(preferencesRepo.customWallpaperUri)
            val imageModel = if (customFile.exists()) customFile else Uri.parse(preferencesRepo.customWallpaperUri)
            AsyncImage(
                model = imageModel,
                contentDescription = "Custom Wallpaper",
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (preferencesRepo.wallpaperBlur > 0f) Modifier.blur(preferencesRepo.wallpaperBlur.dp) else Modifier)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(getHomeWallpaperBrush(preferencesRepo.homepageWallpaper))
                    .then(if (preferencesRepo.wallpaperBlur > 0f) Modifier.blur(preferencesRepo.wallpaperBlur.dp) else Modifier)
            )
        }

        // 2. Wallpaper Dimming Scrim Layer
        if (preferencesRepo.wallpaperDim > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = preferencesRepo.wallpaperDim))
            )
        }

        // 3. Foreground Scrollable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Optional Greeting & Clock Header
            if (preferencesRepo.showGreetingHeader) {
                val currentGreeting = remember {
                    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    when {
                        hour in 5..11 -> "Good Morning"
                        hour in 12..16 -> "Good Afternoon"
                        hour in 17..21 -> "Good Evening"
                        else -> "Good Night"
                    }
                }
                Text(
                    text = currentGreeting,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = currentAccent,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Browser Brand
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(currentAccent, currentAccent.copy(alpha = 0.65f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = "Browser",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Gecko",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = homeTextColor
            )

            Text(
                text = "Ultra-Fast • Zero Ads • Complete Privacy",
                fontSize = 13.sp,
                color = homeSecondaryTextColor,
                textAlign = TextAlign.Center
            )

            // Privacy Report Card
            if (preferencesRepo.showPrivacyStatsWidget) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceElevated.copy(alpha = if (isWallpaperDark) 0.88f else 0.95f))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(SurfaceCard, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Privacy Shield",
                            tint = ShieldGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Privacy Protection Active",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Advanced ad blocking & strict anti-tracking active. Zero tracking cookies permitted.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Favorites Grid (if enabled)
            if (preferencesRepo.showFavoritesGrid) {
                Spacer(modifier = Modifier.height(28.dp))

                // Favorites Header with Edit/Done button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Text(
                        text = "Favorites",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = homeTextColor
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { isEditMode = !isEditMode },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (isEditMode) currentAccent else homeSecondaryTextColor
                            )
                        ) {
                            Text(
                                text = if (isEditMode) "Done" else "Edit",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Favorites Grid (Chunked based on user column choice 3, 4, 5)
                val columns = preferencesRepo.favoritesGridColumns.coerceIn(3, 5)
                val allItems = remember(favorites.toList()) { favorites.toList() }
                val totalCells = allItems.size + 1 // items + Add button
                val numRows = (totalCells + columns - 1) / columns

                for (rowIndex in 0 until numRows) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        for (colIndex in 0 until columns) {
                            val itemIndex = rowIndex * columns + colIndex
                            Box(modifier = Modifier.weight(1f)) {
                                if (itemIndex < allItems.size) {
                                    val fav = allItems[itemIndex]
                                    FavoriteCard(
                                        fav = fav,
                                        isEditMode = isEditMode,
                                        textColor = homeTextColor,
                                        onClick = {
                                            if (!isEditMode) onNavigate(fav.url)
                                        },
                                        onLongClick = { isEditMode = true },
                                        onDelete = { onRemoveFavorite(fav) }
                                    )
                                } else if (itemIndex == allItems.size) {
                                    // "+ Add" Card
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { showAddSheet = true }
                                            .padding(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(top = 6.dp, end = 6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(SurfaceElevated.copy(alpha = 0.88f))
                                                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Add Favorite",
                                                    tint = currentAccent,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Add",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextSecondary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    // Empty placeholder spacer to keep column alignment
                                    Spacer(modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Modern Safari Add Shortcut Bottom Sheet
    if (showAddSheet) {
        AddFavoriteSheet(
            onAdd = { title, url ->
                onAddFavorite(title, url)
            },
            onDismissRequest = { showAddSheet = false }
        )
    }
}

@Composable
private fun getHomeWallpaperBrush(wp: HomepageWallpaper): Brush {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoriteCard(
    fav: FavoriteItem,
    isEditMode: Boolean,
    textColor: Color = TextPrimary,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    val defaultBg = SurfaceElevated
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(4.dp)
    ) {
        // Icon Container with anchored delete badge
        Box(
            modifier = Modifier.padding(top = 6.dp, end = 6.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(getFavoriteBgColor(fav, defaultBg))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (fav.isBuiltIn) {
                    // Official Built-in Branded Vectors (Google, YouTube, Reddit, Wikipedia, GitHub)
                    Icon(
                        imageVector = getBuiltInIcon(fav.builtInType),
                        contentDescription = fav.title,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    // Real Dynamic Favicon via Google Favicons API
                    val faviconUrl = try {
                        val uri = URI(fav.url)
                        val host = uri.host ?: fav.url
                        "https://www.google.com/s2/favicons?domain_url=" + URLEncoder.encode(host, "UTF-8") + "&sz=128"
                    } catch (e: Exception) {
                        "https://www.google.com/s2/favicons?domain_url=" + URLEncoder.encode(fav.url, "UTF-8") + "&sz=128"
                    }

                    AsyncImage(
                        model = faviconUrl,
                        contentDescription = fav.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                }
            }

            // Delete (X) Badge in Edit Mode
            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .offset(x = 6.dp, y = (-6).dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(DangerRed)
                        .border(1.5.dp, DarkBg, CircleShape)
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = fav.title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

private fun getBuiltInIcon(type: String?): androidx.compose.ui.graphics.vector.ImageVector {
    return when (type) {
        "google" -> Icons.Default.Search
        "youtube" -> Icons.Default.PlayArrow
        "reddit" -> Icons.Default.Forum
        "wiki" -> Icons.Default.MenuBook
        "github" -> Icons.Default.Code
        else -> Icons.Default.Language
    }
}

private fun getFavoriteBgColor(fav: FavoriteItem, defaultBg: Color): Color {
    return when (fav.builtInType) {
        "google" -> Color(0xFF4285F4)
        "youtube" -> Color(0xFFFF0000)
        "reddit" -> Color(0xFFFF4500)
        "wiki" -> Color(0xFF555555)
        "github" -> Color(0xFF24292E)
        else -> defaultBg
    }
}
