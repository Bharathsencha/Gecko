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

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

private data class FlameParticle(
    val initialXRatio: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val drift: Float,
    val delayOffset: Float
)

@Composable
fun FireAnimationOverlay(
    isPlaying: Boolean,
    onWipeTrigger: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isPlaying) return

    val haptic = LocalHapticFeedback.current
    val progress = remember { Animatable(0f) }
    var wipeTriggered by remember { mutableStateOf(false) }

    // Seed flame particles
    val particles = remember {
        val colors = listOf(
            Color(0xFFFF3B30),
            Color(0xFFFF9500),
            Color(0xFFFFCC00),
            Color(0xFFFF2D55),
            Color(0xFFFFA07A),
            Color(0xFFFFF3A3)
        )
        List(85) {
            FlameParticle(
                initialXRatio = Random.nextFloat(),
                speed = 0.6f + Random.nextFloat() * 0.9f,
                size = 12f + Random.nextFloat() * 32f,
                color = colors[Random.nextInt(colors.size)],
                drift = (Random.nextFloat() - 0.5f) * 120f,
                delayOffset = Random.nextFloat() * 0.35f
            )
        }
    }

    LaunchedEffect(isPlaying) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        progress.snapTo(0f)
        wipeTriggered = false

        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
        )

        delay(100)
        onFinished()
    }

    // Trigger data wipe halfway through the flame burst
    LaunchedEffect(progress.value) {
        if (progress.value > 0.45f && !wipeTriggered) {
            wipeTriggered = true
            onWipeTrigger()
        }
    }

    val currentProg = progress.value

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = (sin(currentProg * Math.PI).toFloat() * 0.82f).coerceIn(0f, 0.85f))),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // 1. Rising Flame Base Wave
            val waveHeight = (height * 1.25f) * currentProg
            val flamePath = Path().apply {
                moveTo(0f, height)
                lineTo(0f, height - waveHeight * 0.85f)
                
                // Segmented bezier fire wave
                val steps = 8
                val stepW = width / steps
                for (i in 1..steps) {
                    val x = i * stepW
                    val prevX = (i - 1) * stepW
                    val waveVariance = sin((i + currentProg * 10f) * 1.5).toFloat() * (35f + currentProg * 50f)
                    val cpY = height - waveHeight + waveVariance
                    val targetY = height - waveHeight * 0.85f + sin((i * 2f + currentProg * 8f)).toFloat() * 20f
                    quadraticBezierTo(prevX + stepW / 2f, cpY, x, targetY)
                }
                lineTo(width, height)
                close()
            }

            val flameBrush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFFF7C2).copy(alpha = (1f - currentProg * 0.4f).coerceIn(0f, 1f)),
                    Color(0xFFFFCC00).copy(alpha = 0.9f),
                    Color(0xFFFF9500).copy(alpha = 0.85f),
                    Color(0xFFFF3B30).copy(alpha = 0.8f),
                    Color(0xFF8B0000).copy(alpha = 0.75f)
                ),
                startY = (height - waveHeight).coerceAtLeast(0f),
                endY = height
            )

            drawPath(flamePath, flameBrush)

            // 2. Dynamic Flame Particles / Embers
            particles.forEach { p ->
                val particleProg = ((currentProg - p.delayOffset) / (1f - p.delayOffset)).coerceIn(0f, 1f)
                if (particleProg > 0f) {
                    val pY = height - (particleProg * height * p.speed * 1.3f)
                    val pX = (p.initialXRatio * width) + (sin(particleProg * 12f + p.delayOffset * 10f) * p.drift)
                    val pAlpha = (1f - particleProg) * (1f - (currentProg * 0.6f))
                    val pRadius = p.size * (1f - particleProg * 0.45f)

                    if (pY in -100f..height && pAlpha > 0.05f) {
                        drawCircle(
                            color = p.color.copy(alpha = pAlpha.coerceIn(0f, 1f)),
                            radius = pRadius.coerceAtLeast(2f),
                            center = Offset(pX, pY)
                        )
                    }
                }
            }
        }

        // Center Burning Text & Flame Icon
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            val iconAlpha = (sin(currentProg * Math.PI).toFloat() * 1.2f).coerceIn(0f, 1f)
            val iconScale = 1f + (currentProg * 0.35f)

            Icon(
                imageVector = Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = Color(0xFFFF9500).copy(alpha = iconAlpha),
                modifier = Modifier
                    .size((64 * iconScale).dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Clearing & Burning Tabs...",
                color = Color.White.copy(alpha = iconAlpha),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Zero traces left",
                color = Color(0xFFFFCC00).copy(alpha = (iconAlpha * 0.85f)),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
