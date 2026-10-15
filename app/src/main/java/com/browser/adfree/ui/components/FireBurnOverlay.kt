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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.browser.adfree.ui.theme.InkBlack
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private val FlameRed = Color(0xFFFF5A3C)
private val FlameOrange = Color(0xFFFF9A3C)
private val FlameYellow = Color(0xFFFFD84D)

/**
 * Burn-it-all animation for the clear button, in the app's flat style:
 * three flame walls with ink outlines roll up from the bottom, swallow the
 * screen, [onCovered] runs while nothing underneath is visible (that is when
 * the data is wiped), then the fire burns off the top and reveals a clean page.
 */
@Composable
fun FireBurnOverlay(
    playing: Boolean,
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
