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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.storage.TrackerStatsRepository
import com.browser.adfree.ui.theme.*

private val BarColors = listOf(Color(0xFFFFD84D), Color(0xFFB49CFF), Color(0xFF7FD8C8), Color(0xFFFF9EC7), Color(0xFFFFB067), Color(0xFF8EC5FF), Color(0xFF9BE07A))

/** Seven flat bars, one per day, tallest day full height. */
@Composable
fun WeekBars(days: List<Pair<String, Int>>, modifier: Modifier = Modifier, labels: Boolean = true, onInk: Color = TextSecondary) {
    val max = (days.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom, modifier = modifier) {
        days.forEachIndexed { i, (label, count) ->
            val target = if (count == 0) 0.04f else (count.toFloat() / max).coerceIn(0.06f, 1f)
            val h by animateFloatAsState(target, tween(500), label = "bar")
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(h)
                            .brutalBox(fill = BarColors[i % BarColors.size], border = InkBlack, borderWidth = 1.5.dp, shape = RoundedCornerShape(3.dp))
                    )
                }
                if (labels) {
                    Spacer(Modifier.height(4.dp))
                    Text(label, color = onInk, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Full page: totals, the last 7 days and the domains blocked most often. */
@Composable
fun TrackerReportPage(stats: TrackerStatsRepository, onBack: () -> Unit) {
    val snap = stats.snapshot
    var confirmReset by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(DarkBg)) {
        BrutalPageBar(title = "Tracker report", onBack = onBack) {
            BrutalIconButton(Icons.Default.RestartAlt, "Reset statistics", { confirmReset = true })
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(end = 4.dp)) {
                StatTile("Today", snap.today, Color(0xFFFFD84D), Modifier.weight(1f))
                StatTile("7 days", snap.week, Color(0xFF7FD8C8), Modifier.weight(1f))
                StatTile("30 days", snap.month, Color(0xFFB49CFF), Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(end = 5.dp, bottom = 5.dp)
                    .brutalBox(fill = SurfaceDark, border = BorderSubtle, shadow = HardShadow, shadowOffset = 5.dp)
                    .padding(16.dp)
            ) {
                Text("Last 7 days", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                WeekBars(snap.lastSevenDays, Modifier.fillMaxWidth().height(140.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("Blocked most often", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            if (snap.topDomains.isEmpty()) {
                Text("Nothing blocked yet. Browse a few sites and come back.", color = TextSecondary, fontSize = 13.5.sp)
            } else {
                val top = snap.topDomains.first().second.coerceAtLeast(1)
                snap.topDomains.forEachIndexed { i, (domain, count) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        Text("${i + 1}", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(domain, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("$count", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth().height(8.dp).background(SurfaceCard, RoundedCornerShape(2.dp))) {
                                Box(Modifier.fillMaxWidth(count.toFloat() / top).fillMaxHeight().background(BarColors[i % BarColors.size], RoundedCornerShape(2.dp)))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Counted on this phone from normal tabs only. Includes blocks by tracking protection and the built-in filter; uBlock Origin does not report its own.",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
    if (confirmReset) {
        BrutalDialog(
            title = "Reset statistics?",
            onDismiss = { confirmReset = false },
            confirmLabel = "Reset",
            confirmFill = DangerRed,
            onConfirm = {
                stats.reset()
                confirmReset = false
            },
            dismissLabel = "Cancel"
        ) {
            Text("Counts start again from zero.", color = TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, fill: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .brutalBox(fill = fill, border = BorderSubtle, shadow = HardShadow, shadowOffset = 3.dp)
            .padding(12.dp)
    ) {
        Text(formatCount(value), color = InkBlack, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = InkBlack, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

fun formatCount(n: Int): String = when {
    n >= 100_000 -> "${n / 1000}k"
    n >= 10_000 -> String.format("%.1fk", n / 1000f)
    else -> String.format("%,d", n)
}
