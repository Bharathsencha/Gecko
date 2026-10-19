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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.model.DesignPalette
import com.browser.adfree.model.ShapeKind
import com.browser.adfree.model.WallDesign
import com.browser.adfree.model.WallShape
import com.browser.adfree.ui.theme.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ------------------------------------------------------------------ Rendering

/** Text colour that reads on a design's background, or null for the theme's. */
fun designTextColor(design: WallDesign): Color? =
    design.background?.let { if (Color(it).luminance() > 0.45f) InkBlack else Color.White }

/** Draws [design] filling the canvas. Theme background and ink are used when the design leaves them unset. */
@Composable
fun DesignCanvas(design: WallDesign, modifier: Modifier = Modifier) {
    val themeBg = DarkBg
    val themeInk = BorderSubtle
    Canvas(modifier) { drawDesign(design, themeBg, themeInk) }
}

fun DrawScope.drawDesign(design: WallDesign, themeBg: Color, themeInk: Color) {
    val ink = if (design.background == null) themeInk else InkBlack
    drawRect(design.background?.let { Color(it) } ?: themeBg)
    val outline = Stroke(width = (size.minDimension * 0.007f).coerceIn(2f, 8f))
    val shadow = size.minDimension * 0.014f
    design.shapes.forEach { s -> drawShape(s, ink, outline, shadow) }
}

private fun DrawScope.drawShape(s: WallShape, ink: Color, outline: Stroke, shadowPx: Float) {
    val left = s.x * size.width
    val top = s.y * size.height
    val w = s.w * size.width
    val h = s.h * size.height
    val center = Offset(left + w / 2, top + h / 2)
    rotate(s.rotation, center) {
        if (s.shadow) translate(shadowPx, shadowPx) { drawKind(s.kind, left, top, w, h, ink, null) }
        drawKind(s.kind, left, top, w, h, Color(s.color), null)
        if (s.outlined) drawKind(s.kind, left, top, w, h, ink, outline)
    }
}

private fun DrawScope.drawKind(kind: ShapeKind, l: Float, t: Float, w: Float, h: Float, color: Color, stroke: Stroke?) {
    val style = stroke ?: androidx.compose.ui.graphics.drawscope.Fill
    when (kind) {
        ShapeKind.RECT -> drawRect(color, Offset(l, t), Size(w, h), style = style)
        ShapeKind.CIRCLE -> drawOval(color, Offset(l, t), Size(w, h), style = style)
        ShapeKind.TRIANGLE -> drawPath(Path().apply { moveTo(l + w / 2, t); lineTo(l + w, t + h); lineTo(l, t + h); close() }, color, style = style)
        ShapeKind.HALF -> drawArc(color, 180f, 180f, useCenter = true, topLeft = Offset(l, t), size = Size(w, h * 2), style = style)
    }
}

// ------------------------------------------------------------------ Editor

/**
 * Full-page start-page designer. Tap the canvas to select a shape, drag to
 * move it, drag the corner square to resize; the bar below edits colour,
 * rotation, layering, outline and shadow. Nothing is saved until "Use".
 */
@Composable
fun WallpaperDesigner(
    initial: WallDesign,
    initialName: String,
    onSave: (name: String, design: WallDesign) -> Unit,
    onDelete: (() -> Unit)?,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var askName by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val shapes = remember { mutableStateListOf<WallShape>().apply { addAll(initial.shapes) } }
    var background by remember { mutableStateOf(initial.background) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val history = remember { mutableStateListOf<Pair<List<WallShape>, Long?>>() }
    val selected = shapes.firstOrNull { it.id == selectedId }
    val themeBg = DarkBg
    val themeInk = BorderSubtle

    fun snapshot() {
        history.add(shapes.toList() to background)
        if (history.size > 40) history.removeAt(0)
    }
    fun update(id: String, change: (WallShape) -> WallShape) {
        val i = shapes.indexOfFirst { it.id == id }
        if (i >= 0) shapes[i] = change(shapes[i])
    }
    fun add(kind: ShapeKind) {
        snapshot()
        val color = DesignPalette.ALL[shapes.size % 8]
        val aspect = if (kind == ShapeKind.RECT) 0.25f else 0.47f
        val shape = WallShape(kind = kind, x = 0.3f, y = 0.4f, w = 0.4f, h = 0.4f * aspect * (if (kind == ShapeKind.HALF) 0.5f else 1f), color = color)
        shapes.add(shape)
        selectedId = shape.id
    }

    Column(Modifier.fillMaxSize().background(DarkBg)) {
        BrutalPageBar(title = name, onBack = onBack, onTitleClick = { askName = true }) {
            if (onDelete != null) {
                BrutalIconButton(Icons.Default.DeleteOutline, "Delete design", { confirmDelete = true }, tint = DangerRed)
            }
            BrutalIconButton(Icons.Default.Undo, "Undo", {
                history.removeLastOrNull()?.let { (s, bg) ->
                    shapes.clear(); shapes.addAll(s); background = bg
                    if (shapes.none { it.id == selectedId }) selectedId = null
                }
            }, enabled = history.isNotEmpty())
            Spacer(Modifier.width(4.dp))
            BrutalButton(
                text = "Use",
                icon = Icons.Default.Check,
                onClick = { askName = true },
                modifier = Modifier.padding(end = 12.dp)
            )
        }

        // Canvas with the phone's own aspect ratio
        val config = LocalConfiguration.current
        val aspect = config.screenWidthDp.toFloat() / config.screenHeightDp.toFloat()
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(aspect)
                    .padding(end = 5.dp, bottom = 5.dp)
                    .brutalBox(fill = Color.Transparent, border = BorderSubtle, shadow = HardShadow, shadowOffset = 5.dp)
                    .clip(BrutalShape)
            ) {
                var dragMode by remember { mutableStateOf(0) } // 0 none, 1 move, 2 resize
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { p ->
                                val hit = shapes.lastOrNull { hitTest(it, p, size.width.toFloat(), size.height.toFloat()) }
                                selectedId = hit?.id
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { p ->
                                    val wPx = size.width.toFloat(); val hPx = size.height.toFloat()
                                    val sel = shapes.firstOrNull { it.id == selectedId }
                                    dragMode = when {
                                        sel != null && handleRect(sel, wPx, hPx).inflate(28f).contains(p) -> 2
                                        else -> {
                                            val hit = shapes.lastOrNull { hitTest(it, p, wPx, hPx) }
                                            if (hit != null) { selectedId = hit.id; 1 } else 0
                                        }
                                    }
                                    if (dragMode != 0) snapshot()
                                },
                                onDragEnd = { dragMode = 0 },
                                onDrag = { change, delta ->
                                    change.consume()
                                    val id = selectedId ?: return@detectDragGestures
                                    val dx = delta.x / size.width
                                    val dy = delta.y / size.height
                                    when (dragMode) {
                                        1 -> update(id) { it.copy(x = it.x + dx, y = it.y + dy) }
                                        2 -> update(id) { it.copy(w = (it.w + dx).coerceAtLeast(0.03f), h = (it.h + dy).coerceAtLeast(0.015f)) }
                                    }
                                }
                            )
                        }
                ) {
                    drawDesign(WallDesign(background, shapes.toList()), themeBg, themeInk)
                    selected?.let { s ->
                        val r = Rect(s.x * size.width, s.y * size.height, (s.x + s.w) * size.width, (s.y + s.h) * size.height)
                        val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                        rotate(s.rotation, r.center) {
                            drawRect(Color(0xFF3D7BFF), r.topLeft, r.size, style = Stroke(3f, pathEffect = dash))
                        }
                        val hr = handleRect(s, size.width, size.height)
                        drawRect(Color.White, hr.topLeft, hr.size)
                        drawRect(InkBlack, hr.topLeft, hr.size, style = Stroke(4f))
                    }
                }
            }
        }

        // Tools
        Column(
            Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .navigationBarsPadding()
        ) {
            BrutalRule()
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                ShapeKind.values().forEach { kind ->
                    ShapeButton(kind) { add(kind) }
                }
            }
            BrutalHairline()
            if (selected != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    BrutalIconButton(Icons.Default.RotateLeft, "Rotate left", { snapshot(); update(selected.id) { it.copy(rotation = it.rotation - 15f) } })
                    BrutalIconButton(Icons.Default.RotateRight, "Rotate right", { snapshot(); update(selected.id) { it.copy(rotation = it.rotation + 15f) } })
                    BrutalIconButton(Icons.Default.ArrowUpward, "Bring forward", {
                        val i = shapes.indexOfFirst { it.id == selected.id }
                        if (i in 0 until shapes.lastIndex) { snapshot(); shapes.add(i + 1, shapes.removeAt(i)) }
                    })
                    BrutalIconButton(Icons.Default.ArrowDownward, "Send back", {
                        val i = shapes.indexOfFirst { it.id == selected.id }
                        if (i > 0) { snapshot(); shapes.add(i - 1, shapes.removeAt(i)) }
                    })
                    BrutalIconButton(Icons.Default.ContentCopy, "Duplicate", {
                        snapshot()
                        val copy = selected.copy(id = java.util.UUID.randomUUID().toString(), x = selected.x + 0.04f, y = selected.y + 0.03f)
                        shapes.add(copy); selectedId = copy.id
                    })
                    BrutalIconButton(Icons.Default.DeleteOutline, "Delete", { snapshot(); shapes.removeAll { it.id == selected.id }; selectedId = null }, tint = DangerRed)
                    ToggleChip("Outline", selected.outlined) { snapshot(); update(selected.id) { it.copy(outlined = !it.outlined) } }
                    Spacer(Modifier.width(8.dp))
                    ToggleChip("Shadow", selected.shadow) { snapshot(); update(selected.id) { it.copy(shadow = !it.shadow) } }
                }
                ColorRow(DesignPalette.ALL, selected.color) { c -> snapshot(); update(selected.id) { it.copy(color = c) } }
            } else {
                Text(
                    "Background",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    ToggleChip("Theme", background == null) { snapshot(); background = null }
                    DesignPalette.BACKGROUNDS.forEach { c ->
                        Swatch(c, background == c) { snapshot(); background = c }
                    }
                }
                Text(
                    "Tap a shape to edit it. Drag to move, drag the white corner to resize.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                )
            }
        }
    }

    if (askName) {
        BrutalDialog(
            title = "Name this design",
            onDismiss = { askName = false },
            confirmLabel = "Save and use",
            onConfirm = {
                askName = false
                onSave(name.trim().ifBlank { initialName }, WallDesign(background, shapes.toList()))
            },
            dismissLabel = "Cancel"
        ) {
            BrutalTextField(
                value = name,
                onValueChange = { name = it.take(32) },
                label = "Name",
                imeAction = androidx.compose.ui.text.input.ImeAction.Done
            )
        }
    }
    if (confirmDelete && onDelete != null) {
        BrutalDialog(
            title = "Delete \"$name\"?",
            onDismiss = { confirmDelete = false },
            confirmLabel = "Delete",
            confirmFill = DangerRed,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            dismissLabel = "Cancel"
        ) {
            Text("The background switches back to Blocks if this design is in use.", color = TextSecondary, fontSize = 14.sp)
        }
    }
}

private fun handleRect(s: WallShape, wPx: Float, hPx: Float): Rect {
    val cx = (s.x + s.w) * wPx
    val cy = (s.y + s.h) * hPx
    return Rect(cx - 18f, cy - 18f, cx + 18f, cy + 18f)
}

/** Hit test in the shape's own (unrotated) frame. */
private fun hitTest(s: WallShape, p: Offset, wPx: Float, hPx: Float): Boolean {
    val l = s.x * wPx; val t = s.y * hPx; val w = s.w * wPx; val h = s.h * hPx
    val c = Offset(l + w / 2, t + h / 2)
    val rad = Math.toRadians(-s.rotation.toDouble())
    val dx = p.x - c.x; val dy = p.y - c.y
    val rx = (dx * cos(rad) - dy * sin(rad)).toFloat()
    val ry = (dx * sin(rad) + dy * cos(rad)).toFloat()
    return abs(rx) <= w / 2 + 12f && abs(ry) <= h / 2 + 12f
}

@Composable
private fun ShapeButton(kind: ShapeKind, onClick: () -> Unit) {
    val ink = BorderSubtle
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .brutalPressable(fill = SurfaceElevated, border = BorderSubtle, shadow = HardShadow, shadowOffset = 3.dp, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            val fill = Color(DesignPalette.YELLOW)
            Canvas(Modifier.size(28.dp, if (kind == ShapeKind.RECT) 18.dp else 24.dp)) {
                val s = Stroke(2.dp.toPx())
                drawKind(kind, 0f, 0f, size.width, if (kind == ShapeKind.HALF) size.height / 2 else size.height, fill, null)
                drawKind(kind, 0f, 0f, size.width, if (kind == ShapeKind.HALF) size.height / 2 else size.height, ink, s)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            when (kind) { ShapeKind.RECT -> "Block"; ShapeKind.CIRCLE -> "Circle"; ShapeKind.TRIANGLE -> "Triangle"; ShapeKind.HALF -> "Arch" },
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ColorRow(colors: List<Long>, selected: Long, onPick: (Long) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp)
    ) {
        colors.forEach { c -> Swatch(c, c == selected) { onPick(c) } }
    }
}

@Composable
private fun Swatch(color: Long, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .brutalPressable(
                fill = Color(color),
                border = BorderSubtle,
                shadow = HardShadow,
                shadowOffset = if (selected) 0.dp else 3.dp,
                role = Role.RadioButton,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = if (Color(color).luminance() > 0.45f) InkBlack else Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ToggleChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .brutalPressable(
                fill = if (on) AccentCyan else SurfaceDark,
                border = BorderSubtle,
                shadow = HardShadow,
                shadowOffset = if (on) 0.dp else 2.dp,
                role = Role.Switch,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (on) OnAccent else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
