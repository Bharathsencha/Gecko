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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.browser.adfree.R
import coil.compose.AsyncImage
import com.browser.adfree.model.DesignPresets
import com.browser.adfree.model.FavoriteItem
import com.browser.adfree.model.WallDesign
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
    isPrivate: Boolean = false,
    onSwitchToPrivate: () -> Unit = {},
    onSwitchToNormal: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isEditMode by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showPrivateFacts by remember { mutableStateOf(false) }

    val wallpaper = preferencesRepo.homepageWallpaper
    val hasPhoto = !isPrivate &&
        wallpaper == HomepageWallpaper.CUSTOM &&
        preferencesRepo.customWallpaperUri.isNotBlank()
    val drawn = !isPrivate && wallpaper != HomepageWallpaper.CUSTOM
    val backdropText = when {
        isPrivate -> null
        hasPhoto -> Color.White
        else -> wallpaperTextColor(wallpaper, preferencesRepo.activeDesign)
    }
    val onBackdrop = backdropText != null

    Box(modifier = modifier.fillMaxSize().background(DarkBg)) {
        // The page shrinks when the keyboard opens; the background keeps the
        // tallest size it has had, so it stays put like an image wallpaper.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            var fullHeight by remember { mutableStateOf(0.dp) }
            if (maxHeight > fullHeight) fullHeight = maxHeight
            Box(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .requiredHeight(fullHeight)
            ) {
            if (drawn) {
                WallpaperBackground(wallpaper, Modifier.fillMaxSize(), preferencesRepo.activeDesign)
                if (wallpaperBrush(wallpaper) != null && preferencesRepo.wallpaperDim > 0f) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = preferencesRepo.wallpaperDim * 0.6f)))
                }
            } else if (hasPhoto) {
                val customFile = java.io.File(preferencesRepo.customWallpaperUri)
                AsyncImage(
                    model = if (customFile.exists()) customFile else Uri.parse(preferencesRepo.customWallpaperUri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (preferencesRepo.wallpaperBlur > 0f) Modifier.blur(preferencesRepo.wallpaperBlur.dp) else Modifier)
                )
                if (preferencesRepo.wallpaperDim > 0f) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = preferencesRepo.wallpaperDim)))
                }
            }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // Header: wordmark and private toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.gecko_logo),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (isPrivate) "Private" else "Gecko",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = backdropText ?: TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                PrivateModeToggle(
                    isPrivate = isPrivate,
                    onToggle = { if (isPrivate) onSwitchToNormal() else onSwitchToPrivate() }
                )
            }

            if (isPrivate) {
                PrivatePanel(expanded = showPrivateFacts, onToggleFacts = { showPrivateFacts = !showPrivateFacts })
            } else if (preferencesRepo.showFavoritesGrid) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Text(
                        "Favourites",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = backdropText ?: TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (favorites.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .brutalPressable(
                                    fill = if (isEditMode) AccentCyan else SurfaceDark,
                                    border = BorderSubtle,
                                    shadow = HardShadow,
                                    shadowOffset = 2.dp,
                                    onClick = { isEditMode = !isEditMode }
                                )
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                if (isEditMode) "Done" else "Edit",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isEditMode) OnAccent else TextPrimary
                            )
                        }
                    }
                }

                if (favorites.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 4.dp, bottom = 4.dp)
                            .brutalBox(fill = SurfaceDark, border = BorderSubtle, shadow = HardShadow)
                            .padding(16.dp)
                    ) {
                        Text("No favourites yet", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Pin the sites you open every day.", color = TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        BrutalButton("Add a favourite", { showAddSheet = true }, icon = Icons.Default.Add)
                    }
                } else {
                    val columns = preferencesRepo.favoritesGridColumns.coerceIn(3, 5)
                    val cells = favorites.toList()
                    val totalCells = cells.size + 1
                    val rows = (totalCells + columns - 1) / columns
                    for (rowIndex in 0 until rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (col in 0 until columns) {
                                val index = rowIndex * columns + col
                                Box(Modifier.weight(1f)) {
                                    when {
                                        index < cells.size -> {
                                            val fav = cells[index]
                                            FavoriteTile(
                                                fav = fav,
                                                isEditMode = isEditMode,
                                                labelColor = backdropText ?: TextPrimary,
                                                onClick = { if (!isEditMode) onNavigate(fav.url) },
                                                onLongClick = { isEditMode = true },
                                                onDelete = { onRemoveFavorite(fav) }
                                            )
                                        }
                                        index == cells.size -> AddTile(
                                            labelColor = backdropText ?: TextSecondary,
                                            onClick = { showAddSheet = true }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAddSheet) {
        AddFavoriteSheet(
            onAdd = { title, url -> onAddFavorite(title, url) },
            onDismissRequest = { showAddSheet = false }
        )
    }
}

@Composable
private fun PrivatePanel(expanded: Boolean, onToggleFacts: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 4.dp, bottom = 4.dp)
            .brutalBox(fill = PrivatePurple, border = BorderSubtle, shadow = HardShadow)
            .padding(16.dp)
    ) {
        Text("Private tab", color = InkBlack, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "History, cookies and site data from private tabs are kept in memory only and erased when the tabs close. " +
                "Sites and your network can still see your IP address unless the VPN is on.",
            color = InkBlack,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (expanded) "Hide details" else "What private tabs do not do",
            color = InkBlack,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable(onClick = onToggleFacts)
                .padding(vertical = 8.dp)
        )
        if (expanded) {
            Fact("They do not hide your IP address. Use the VPN in Settings for that.")
            Fact("They do not stop websites from recognising an account you sign in to.")
            Fact("Downloads and bookmarks made in a private tab stay on the device.")
        }
    }
}

@Composable
private fun Fact(text: String) {
    Row(Modifier.padding(top = 6.dp)) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .background(InkBlack)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, color = InkBlack, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteTile(
    fav: FavoriteItem,
    isEditMode: Boolean,
    labelColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(top = 6.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .padding(end = 3.dp, bottom = 3.dp)
                    .size(54.dp)
                    .brutalBox(fill = brandFill(fav), border = BorderSubtle, shadow = HardShadow, shadowOffset = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                if (fav.isBuiltIn) {
                    Icon(builtInIcon(fav.builtInType), contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                } else {
                    AsyncImage(
                        model = faviconUrl(fav.url),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(30.dp).clip(RoundedCornerShape(4.dp))
                    )
                }
            }
            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .offset(x = 8.dp, y = (-8).dp)
                        .size(26.dp)
                        .brutalBox(fill = DangerRed, border = BorderSubtle, shape = RoundedCornerShape(4.dp))
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Remove ${fav.title}", tint = InkBlack, modifier = Modifier.size(15.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = fav.title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AddTile(labelColor: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(end = 3.dp, bottom = 3.dp)
                .size(54.dp)
                .brutalBox(fill = SurfaceDark, border = BorderSubtle),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add favourite", tint = TextPrimary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text("Add", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = labelColor, textAlign = TextAlign.Center)
    }
}

private fun faviconUrl(url: String): String {
    val host = try { URI(url).host ?: url } catch (e: Exception) { url }
    return "https://www.google.com/s2/favicons?domain_url=" + URLEncoder.encode(host, "UTF-8") + "&sz=128"
}

private fun builtInIcon(type: String?): ImageVector = when (type) {
    "google" -> Icons.Default.Search
    "youtube" -> Icons.Default.PlayArrow
    "reddit" -> Icons.Default.Forum
    "wiki" -> Icons.Default.MenuBook
    "github" -> Icons.Default.Code
    else -> Icons.Default.Language
}

@Composable
private fun brandFill(fav: FavoriteItem): Color = when (fav.builtInType) {
    "google" -> Color(0xFF4285F4)
    "youtube" -> Color(0xFFE62117)
    "reddit" -> Color(0xFFFF4500)
    "wiki" -> Color(0xFF555555)
    "github" -> Color(0xFF24292E)
    else -> SurfaceDark
}

/**
 * Private browsing switch drawn as Firefox's mask: outline mask on a plain
 * tile in normal mode, inverted on a purple tile in private mode.
 */
@Composable
private fun PrivateModeToggle(isPrivate: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .brutalPressable(
                    fill = if (isPrivate) PrivatePurple else SurfaceDark,
                    border = BorderSubtle,
                    shadow = HardShadow,
                    shadowOffset = 3.dp,
                    onClick = onToggle
                )
                .semantics { contentDescription = if (isPrivate) "Leave private browsing" else "Private browsing" },
            contentAlignment = Alignment.Center
        ) {
            PrivateMask(
                maskColor = if (isPrivate) InkBlack else TextPrimary,
                eyeColor = if (isPrivate) PrivatePurple else SurfaceDark,
                modifier = Modifier.size(width = 28.dp, height = 18.dp)
            )
        }
    }
}

@Composable
fun PrivateMask(maskColor: Color, eyeColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val mask = Path().apply {
            moveTo(w * 0.50f, h * 0.24f)
            cubicTo(w * 0.36f, h * 0.02f, w * 0.06f, h * 0.00f, w * 0.01f, h * 0.30f)
            cubicTo(w * -0.02f, h * 0.62f, w * 0.10f, h * 1.00f, w * 0.30f, h * 0.98f)
            cubicTo(w * 0.42f, h * 0.97f, w * 0.45f, h * 0.80f, w * 0.50f, h * 0.80f)
            cubicTo(w * 0.55f, h * 0.80f, w * 0.58f, h * 0.97f, w * 0.70f, h * 0.98f)
            cubicTo(w * 0.90f, h * 1.00f, w * 1.02f, h * 0.62f, w * 0.99f, h * 0.30f)
            cubicTo(w * 0.94f, h * 0.00f, w * 0.64f, h * 0.02f, w * 0.50f, h * 0.24f)
            close()
        }
        drawPath(mask, maskColor)
        // Slanted almond eyes
        val eyeW = w * 0.24f
        val eyeH = h * 0.30f
        rotate(degrees = 12f, pivot = Offset(w * 0.28f, h * 0.52f)) {
            drawOval(eyeColor, topLeft = Offset(w * 0.28f - eyeW / 2, h * 0.52f - eyeH / 2), size = Size(eyeW, eyeH))
        }
        rotate(degrees = -12f, pivot = Offset(w * 0.72f, h * 0.52f)) {
            drawOval(eyeColor, topLeft = Offset(w * 0.72f - eyeW / 2, h * 0.52f - eyeH / 2), size = Size(eyeW, eyeH))
        }
    }
}

/**
 * Start-page gradient presets. Null for the plain theme background and for a
 * photo (drawn separately).
 */
fun wallpaperBrush(wallpaper: HomepageWallpaper): Brush? = when (wallpaper) {
    HomepageWallpaper.AURORA -> Brush.linearGradient(listOf(Color(0xFF031024), Color(0xFF0A2E38), Color(0xFF0B4A45), Color(0xFF020912)))
    HomepageWallpaper.NEBULA -> Brush.radialGradient(listOf(Color(0xFF3A0D5C), Color(0xFF14052B), Color(0xFF000000)))
    HomepageWallpaper.CYBER -> Brush.linearGradient(listOf(Color(0xFF0D0221), Color(0xFF0F084B), Color(0xFF26408B), Color(0xFF000000)))
    HomepageWallpaper.OLED -> Brush.linearGradient(listOf(Color(0xFF000000), Color(0xFF000000)))
    HomepageWallpaper.FOREST -> Brush.linearGradient(listOf(Color(0xFF061A14), Color(0xFF0D2818), Color(0xFF123824), Color(0xFF000000)))
    HomepageWallpaper.SUNSET -> Brush.linearGradient(listOf(Color(0xFF2D0B00), Color(0xFF5A1C00), Color(0xFF7A2E0A), Color(0xFF120300)))
    else -> null
}

private val PastelButter = Color(0xFFFFE99A)
private val PastelPeach = Color(0xFFFFC9A3)
private val PastelMint = Color(0xFFB8EBD7)
private val PastelLavender = Color(0xFFD9CCFF)

/** Block compositions and the user's own design, as shape lists. */
fun designFor(wallpaper: HomepageWallpaper, custom: WallDesign? = null): WallDesign? = when (wallpaper) {
    HomepageWallpaper.BLOCKS -> DesignPresets.BLOCKS
    HomepageWallpaper.STACK -> DesignPresets.STACK
    HomepageWallpaper.SHAPES -> DesignPresets.SHAPES
    HomepageWallpaper.MONDRIAN -> DesignPresets.MONDRIAN
    HomepageWallpaper.DESIGN -> custom ?: DesignPresets.STARTER
    else -> null
}

/** Text colour that reads on [wallpaper], or null to use the theme's own. */
fun wallpaperTextColor(wallpaper: HomepageWallpaper, custom: WallDesign? = null): Color? = designFor(wallpaper, custom)?.let { designTextColor(it) } ?: when (wallpaper) {
    HomepageWallpaper.BUTTER, HomepageWallpaper.PEACH, HomepageWallpaper.MINT, HomepageWallpaper.LAVENDER -> InkBlack
    HomepageWallpaper.AURORA, HomepageWallpaper.NEBULA, HomepageWallpaper.CYBER, HomepageWallpaper.OLED,
    HomepageWallpaper.FOREST, HomepageWallpaper.SUNSET, HomepageWallpaper.CUSTOM -> Color.White
    else -> null
}

/**
 * Draws a start-page background: flat patterns in the theme's ink, solid
 * pastels, or one of the gradient presets. Also used for the picker swatches.
 */
@Composable
fun WallpaperBackground(wallpaper: HomepageWallpaper, modifier: Modifier = Modifier, custom: WallDesign? = null) {
    designFor(wallpaper, custom)?.let { design ->
        DesignCanvas(design, modifier)
        return
    }
    val ink = BorderSubtle
    val paper = DarkBg
    val card = SurfaceCard
    val brush = wallpaperBrush(wallpaper)
    Canvas(modifier = modifier) {
        if (brush != null) {
            drawRect(brush)
            return@Canvas
        }
        when (wallpaper) {
            HomepageWallpaper.BUTTER -> drawRect(PastelButter)
            HomepageWallpaper.PEACH -> drawRect(PastelPeach)
            HomepageWallpaper.MINT -> drawRect(PastelMint)
            HomepageWallpaper.LAVENDER -> drawRect(PastelLavender)
            HomepageWallpaper.DOTS -> {
                drawRect(paper)
                val step = 22.dp.toPx()
                val r = 1.7.dp.toPx()
                var y = step / 2
                while (y < size.height) {
                    var x = step / 2
                    while (x < size.width) {
                        drawCircle(ink.copy(alpha = 0.22f), r, Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }
            HomepageWallpaper.GRID -> {
                // Graph: ruled grid with a flat bar chart rising from the bottom.
                drawRect(paper)
                val step = 18.dp.toPx()
                var i = 0
                var x = 0f
                while (x < size.width) {
                    val major = i % 5 == 0
                    drawLine(ink.copy(alpha = if (major) 0.22f else 0.07f), Offset(x, 0f), Offset(x, size.height), if (major) 1.5.dp.toPx() else 1f)
                    x += step; i++
                }
                i = 0
                var y = 0f
                while (y < size.height) {
                    val major = i % 5 == 0
                    drawLine(ink.copy(alpha = if (major) 0.22f else 0.07f), Offset(0f, y), Offset(size.width, y), if (major) 1.5.dp.toPx() else 1f)
                    y += step; i++
                }
                val bars = listOf(0.16f to 0xFF7FD8C8, 0.27f to 0xFFFFD84D, 0.21f to 0xFFFF9EC7, 0.36f to 0xFFB49CFF, 0.30f to 0xFFFFB067)
                val barW = size.width / (bars.size * 1.6f)
                val gap = (size.width - barW * bars.size) / (bars.size + 1)
                val stroke = Stroke(2.5.dp.toPx())
                bars.forEachIndexed { index, (height, color) ->
                    val left = gap + index * (barW + gap)
                    val top = size.height * (1f - height)
                    val sz = Size(barW, size.height * height + 4f)
                    drawRect(ink, Offset(left + 5.dp.toPx(), top + 5.dp.toPx()), sz)
                    drawRect(Color(color), Offset(left, top), sz)
                    drawRect(ink, Offset(left, top), sz, style = stroke)
                }
            }
            HomepageWallpaper.STRIPES -> {
                drawRect(paper)
                val gap = 28.dp.toPx()
                val width = 10.dp.toPx()
                var start = -size.height
                while (start < size.width) {
                    drawLine(ink.copy(alpha = 0.07f), Offset(start, size.height), Offset(start + size.height, 0f), width)
                    start += gap
                }
            }
            HomepageWallpaper.CHECKER -> {
                val cell = 36.dp.toPx()
                var row = 0
                var y = 0f
                while (y < size.height) {
                    var col = 0
                    var x = 0f
                    while (x < size.width) {
                        drawRect(if ((row + col) % 2 == 0) paper else card, Offset(x, y), Size(cell, cell))
                        x += cell; col++
                    }
                    y += cell; row++
                }
            }
            else -> drawRect(paper)
        }
    }
}
