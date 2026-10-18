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

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.browser.adfree.ui.theme.InkBlack
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.cos
import kotlin.random.Random

private val FlameRed = Color(0xFFFF5A3C)
private val FlameOrange = Color(0xFFFF9A3C)
private val FlameYellow = Color(0xFFFFD84D)

/**
 * The one-tap "burn everything" clear button is an idea from DuckDuckGo's Fire
 * Button (https://duckduckgo.com/duckduckgo-help-pages/privacy/fire-button/).
 * All animations below are original drawings.
 *
 * Burn-it-all animation for the clear button, in the app's flat style:
 * three flame walls with ink outlines roll up from the bottom, swallow the
 * screen, [onCovered] runs while nothing underneath is visible (that is when
 * the data is wiped), then the fire burns off the top and reveals a clean page.
 */
@Composable
fun FireBurnOverlay(
    playing: Boolean,
    style: com.browser.adfree.storage.ClearStyle = com.browser.adfree.storage.ClearStyle.FIRE,
    onCovered: () -> Unit,
    onFinished: () -> Unit
) {
    if (!playing) return
    val view = LocalView.current
    val progress = remember { Animatable(0f) }
    var frameNanos by remember { mutableLongStateOf(0L) }
    val embers = remember {
        val rnd = Random(7)
        List(36) { Ember(x = rnd.nextFloat(), speed = 0.6f + rnd.nextFloat() * 0.8f, size = 4f + rnd.nextFloat() * 8f, delay = rnd.nextFloat() * 0.35f, drift = rnd.nextFloat() - 0.5f) }
    }

    LaunchedEffect(Unit) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        launch {
            // Flicker clock for the flame tongues.
            while (true) withFrameNanos { frameNanos = it }
        }
        progress.animateTo(COVER_AT, tween(durationMillis = 650, easing = LinearEasing))
        onCovered()
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        progress.animateTo(1f, tween(durationMillis = 750, easing = LinearEasing))
        onFinished()
    }

    val t = frameNanos / 1_000_000_000f
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            // Swallow touches while burning so nothing underneath gets tapped.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
    ) {
        val p = progress.value
        when (style) {
            com.browser.adfree.storage.ClearStyle.FIRE -> drawFire(p, t, embers)
            com.browser.adfree.storage.ClearStyle.FLOOD -> drawFlood(p, t)
            com.browser.adfree.storage.ClearStyle.SWEEP -> drawSweep(p)
            com.browser.adfree.storage.ClearStyle.SHRED -> drawShred(p)
            com.browser.adfree.storage.ClearStyle.PIXEL -> drawPixel(p)
        }
    }
}

private const val COVER_AT = 0.5f

private data class Ember(val x: Float, val speed: Float, val size: Float, val delay: Float, val drift: Float)

private fun Float.ease(): Float = (1f - (1f - this).pow(2.2f))

private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

/** Band whose upper edge is a row of flame tongues and lower edge a ragged base. */
private fun DrawScope.flameBand(top: Float, bottom: Float, amp: Float, freq: Float, t: Float): Path {
    val w = size.width
    val steps = 48
    val path = Path()
    for (i in 0..steps) {
        val x = w * i / steps
        val phase = x / w * freq * 2f * PI.toFloat()
        val tongue = abs(sin(phase + t * 3.1f)).pow(3f) * amp +
            abs(sin(phase * 2.3f - t * 4.7f)).pow(2f) * amp * 0.45f
        val y = top - tongue
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    for (i in steps downTo 0) {
        val x = w * i / steps
        val ragged = abs(sin(x / w * 9f + t * 2f)) * amp * 0.5f
        path.lineTo(x, bottom + ragged)
    }
    path.close()
    return path
}

private fun DrawScope.drawEmbers(embers: List<Ember>, p: Float, t: Float) {
    for (e in embers) {
        val local = ((p - e.delay) / 0.65f).coerceIn(0f, 1f)
        if (local <= 0f || local >= 1f) continue
        val y = size.height * (1.05f - local * 1.3f * e.speed)
        val x = size.width * e.x + sin(t * 3f + e.x * 10f) * 18f * e.drift
        val s = e.size.dp.toPx() * (1f - local * 0.6f)
        drawRect(FlameYellow, topLeft = Offset(x, y), size = Size(s, s))
        drawRect(InkBlack, topLeft = Offset(x, y), size = Size(s, s), style = Stroke(1.5.dp.toPx()))
    }
}

private fun DrawScope.drawFire(p: Float, t: Float, embers: List<Ember>) {
        val outline = Stroke(width = 3.dp.toPx())
    val amp = size.height * 0.07f
    // Each wall: bottom edge trails its top edge, so walls are bands that
    // sweep up and leave the screen. Red leads, yellow trails.
    val walls = listOf(
        Triple(FlameRed, 0.00f, 1.5f),
        Triple(FlameOrange, 0.08f, 2.1f),
        Triple(FlameYellow, 0.16f, 2.7f)
    )
    for ((color, lag, freq) in walls) {
        val local = ((p - lag) / (1f - lag)).coerceIn(0f, 1f)
        // 0..COVER: top edge rises from below the screen to above it.
        val top = lerp(size.height + amp, -amp * 2.5f, (local / COVER_AT).coerceAtMost(1f).ease())
        // After cover: the bottom edge follows the top off screen.
        val bottomProgress = ((local - COVER_AT) / (1f - COVER_AT)).coerceIn(0f, 1f).ease()
        val bottom = lerp(size.height + amp * 3, -amp * 3, bottomProgress)
        if (bottom <= top) continue
        val path = flameBand(top, bottom, amp, freq, t + lag * 10f)
        drawPath(path, color)
        drawPath(path, InkBlack, style = outline)
    }
    drawEmbers(embers, p, t)
}

private val Water1 = Color(0xFF8EC5FF)
private val Water2 = Color(0xFF5FA8F5)
private val Water3 = Color(0xFFBFE0FF)

/** Flood: water rises in waves until the screen is under, then keeps rising and leaves through the top. */
private fun DrawScope.drawFlood(p: Float, t: Float) {
    val outline = Stroke(3.dp.toPx())
    val amp = size.height * 0.03f
    val steps = 40
    listOf(Triple(Water1, 0f, 1.3f), Triple(Water2, 0.07f, 1.9f), Triple(Water3, 0.14f, 2.5f)).forEach { (color, lag, freq) ->
        val local = ((p - lag) / (1f - lag)).coerceIn(0f, 1f)
        val rise = (local / COVER_AT).coerceAtMost(1f).ease()
        val leave = ((local - COVER_AT) / (1f - COVER_AT)).coerceIn(0f, 1f).ease()
        val top = lerp(size.height + amp * 2, -amp * 2, rise) - leave * amp * 4
        // The tail of the water follows the surface up and out of the top.
        val bottom = lerp(size.height + amp * 3, -amp * 6, leave)
        if (bottom <= top || bottom < -amp * 2) return@forEach
        val phase = t * 4f
        val path = Path()
        for (i in 0..steps) {
            val x = size.width * i / steps
            val y = top + sin(x / size.width * freq * 2f * PI.toFloat() + phase) * amp
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        for (i in steps downTo 0) {
            val x = size.width * i / steps
            val y = bottom + sin(x / size.width * freq * 2f * PI.toFloat() - phase) * amp
            path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color)
        drawPath(path, InkBlack, style = outline)
    }
    // Square bubbles rise the whole way through.
    repeat(14) { i ->
        val x = size.width * ((i * 0.137f + 0.05f) % 1f)
        val speed = 0.8f + (i % 4) * 0.2f
        val y = size.height * (1.1f - p * speed * 1.6f) + i * 30f
        val s = (6 + i % 3 * 4).dp.toPx()
        drawRect(Color.White, Offset(x, y), Size(s, s))
        drawRect(InkBlack, Offset(x, y), Size(s, s), style = Stroke(1.5.dp.toPx()))
    }
}

/** Sweep: a big eraser block wipes left to right, leaving clean paper behind, then the paper slides away. */
private fun DrawScope.drawSweep(p: Float) {
    val outline = Stroke(3.dp.toPx())
    val blockW = size.width * 0.28f
    val cover = (p / COVER_AT).coerceAtMost(1f).ease()
    val leave = ((p - COVER_AT) / (1f - COVER_AT)).coerceIn(0f, 1f).ease()
    val lead = lerp(-blockW, size.width + blockW, cover)
    val trail = lerp(0f, size.width + blockW * 2, leave)
    val panelRight = lead.coerceAtMost(size.width)
    if (panelRight > trail) {
        drawRect(Color(0xFFF4F1EA), Offset(trail, 0f), Size(panelRight - trail, size.height))
        // diagonal sweep marks on the cleaned area
        var x = trail - size.height
        while (x < panelRight) {
            drawLine(InkBlack.copy(alpha = 0.06f), Offset(x, size.height), Offset(x + size.height, 0f), 12.dp.toPx())
            x += 48.dp.toPx()
        }
    }
    // the eraser
    val ex = if (p < COVER_AT) lead - blockW else trail - blockW
    if (ex < size.width && ex + blockW > 0) {
        val tilt = if (p < COVER_AT) -6f else 6f
        rotate(tilt, Offset(ex + blockW / 2, size.height / 2)) {
            val top = size.height * 0.08f
            val h = size.height * 0.84f
            drawRect(InkBlack, Offset(ex + 8.dp.toPx(), top + 8.dp.toPx()), Size(blockW, h))
            drawRect(Color(0xFFFF9EC7), Offset(ex, top), Size(blockW, h))
            drawRect(Color(0xFF8EC5FF), Offset(ex, top), Size(blockW, h * 0.35f))
            drawRect(InkBlack, Offset(ex, top), Size(blockW, h), style = outline)
            drawLine(InkBlack, Offset(ex, top + h * 0.35f), Offset(ex + blockW, top + h * 0.35f), 3.dp.toPx())
        }
    }
}

/** Shred: coloured strips drop in one after another, then fall through the bottom. */
private fun DrawScope.drawShred(p: Float) {
    val outline = Stroke(2.5.dp.toPx())
    val strips = 8
    val w = size.width / strips
    val colors = listOf(Color(0xFFFFD84D), Color(0xFFFFB067), Color(0xFFFF9EC7), Color(0xFFB49CFF), Color(0xFF8EC5FF), Color(0xFF7FD8C8))
    for (i in 0 until strips) {
        val delay = i * 0.035f
        val drop = (((p - delay) / (COVER_AT - 0.1f)).coerceIn(0f, 1f)).ease()
        val fall = (((p - COVER_AT - delay) / (1f - COVER_AT - 0.25f)).coerceIn(0f, 1f)).let { it * it }
        val top = lerp(-size.height, 0f, drop) + fall * size.height * 1.1f
        if (top >= size.height) continue
        val left = i * w
        val skew = sin((i + 1) * 1.7f) * 6f * fall
        rotate(skew, Offset(left + w / 2, top + size.height / 2)) {
            drawRect(colors[i % colors.size], Offset(left, top), Size(w + 1f, size.height))
            drawRect(InkBlack, Offset(left, top), Size(w + 1f, size.height), style = outline)
            // torn bottom edge
            val path = Path().apply {
                moveTo(left, top + size.height)
                var x = left
                var up = true
                while (x < left + w) {
                    x += w / 6
                    lineTo(x.coerceAtMost(left + w), top + size.height + if (up) 14f else 0f)
                    up = !up
                }
                lineTo(left + w, top + size.height)
                close()
            }
            drawPath(path, colors[i % colors.size])
        }
    }
}

/**
 * Pixel: the page is covered cell by cell with chunky 8-bit blocks that pop
 * in, then the blocks shrink away in the same random order.
 */
private fun DrawScope.drawPixel(p: Float) {
    val cols = 9
    val cell = size.width / cols
    val rows = (size.height / cell).toInt() + 1
    val colors = listOf(Color(0xFFFFD84D), Color(0xFFB49CFF), Color(0xFF7FD8C8), Color(0xFFFF9EC7), Color(0xFFFFB067), Color(0xFF8EC5FF))
    val stroke = Stroke(2.dp.toPx())
    val shadow = 4.dp.toPx()
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            // Stable pseudo-random order per cell
            val h = ((r * 73856093) xor (c * 19349663)).let { (it and 0x7fffffff) % 1000 } / 1000f
            val inAt = h * 0.32f
            val outAt = COVER_AT + 0.02f + h * 0.32f
            val pop = ((p - inAt) / 0.14f).coerceIn(0f, 1f)
            val unpop = ((p - outAt) / 0.14f).coerceIn(0f, 1f)
            if (pop <= 0f || unpop >= 1f) continue
            // Overshoot slightly as each block lands, then shrink back to nothing.
            val scale = if (unpop > 0f) (1f - unpop) * (1f + sin((1f - unpop) * PI.toFloat()) * 0.12f)
                else (pop + sin(pop * PI.toFloat()) * 0.18f).coerceAtMost(1.18f)
            val y = r * cell
            val sz = cell * scale
            val o = Offset(c * cell + (cell - sz) / 2, y + (cell - sz) / 2)
            drawRect(InkBlack, o + Offset(shadow, shadow), Size(sz, sz))
            drawRect(colors[((h * 97).toInt() + r + c) % colors.size], o, Size(sz, sz))
            drawRect(InkBlack, o, Size(sz, sz), style = stroke)
        }
    }
}

/** Icon for the clear button in each style. */
fun clearStyleIcon(style: com.browser.adfree.storage.ClearStyle): androidx.compose.ui.graphics.vector.ImageVector = when (style) {
    com.browser.adfree.storage.ClearStyle.FIRE -> androidx.compose.material.icons.Icons.Default.LocalFireDepartment
    com.browser.adfree.storage.ClearStyle.FLOOD -> androidx.compose.material.icons.Icons.Default.Water
    com.browser.adfree.storage.ClearStyle.SWEEP -> androidx.compose.material.icons.Icons.Default.CleaningServices
    com.browser.adfree.storage.ClearStyle.SHRED -> androidx.compose.material.icons.Icons.Default.ContentCut
    com.browser.adfree.storage.ClearStyle.PIXEL -> androidx.compose.material.icons.Icons.Default.GridView
}

/** Tile colour for the clear button in each style. */
fun clearStyleFill(style: com.browser.adfree.storage.ClearStyle): Color = when (style) {
    com.browser.adfree.storage.ClearStyle.FIRE -> Color(0xFFFFB067)
    com.browser.adfree.storage.ClearStyle.FLOOD -> Color(0xFF8EC5FF)
    com.browser.adfree.storage.ClearStyle.SWEEP -> Color(0xFFFF9EC7)
    com.browser.adfree.storage.ClearStyle.SHRED -> Color(0xFFFFD84D)
    com.browser.adfree.storage.ClearStyle.PIXEL -> Color(0xFFB49CFF)
}
