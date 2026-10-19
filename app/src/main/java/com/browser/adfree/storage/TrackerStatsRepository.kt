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

package com.browser.adfree.storage

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate

/**
 * Counts blocked trackers and ads per day and per domain, for normal tabs
 * only, on this device. Recording is cheap and lock-protected; the UI copy is
 * published at most every two seconds and the file is written in the
 * background, so busy pages and scrolling are not slowed down.
 */
class TrackerStatsRepository(context: Context) {

    data class Snapshot(
        val today: Int = 0,
        val week: Int = 0,
        val month: Int = 0,
        /** Oldest first, 7 entries: day label to count. */
        val lastSevenDays: List<Pair<String, Int>> = emptyList(),
        val topDomains: List<Pair<String, Int>> = emptyList()
    )

    private val file = java.io.File(context.filesDir, "tracker_stats.json")
    private val lock = Any()
    private val days = LinkedHashMap<String, Int>()
    private val domains = HashMap<String, Int>()
    private val main = Handler(Looper.getMainLooper())
    private val io = CoroutineScope(Dispatchers.IO)
    private var publishScheduled = false

    var snapshot by mutableStateOf(Snapshot())
        private set

    init {
        io.launch {
            load()
            main.post { publish() }
        }
    }

    fun record(host: String?) {
        if (host.isNullOrBlank()) return
        val key = registrableDomain(host)
        val today = LocalDate.now().toString()
        synchronized(lock) {
            days[today] = (days[today] ?: 0) + 1
            domains[key] = (domains[key] ?: 0) + 1
        }
        main.post {
            if (!publishScheduled) {
                publishScheduled = true
                main.postDelayed({
                    publishScheduled = false
                    publish()
                    save()
                }, 2_000)
            }
        }
    }

    fun reset() {
        synchronized(lock) {
            days.clear()
            domains.clear()
        }
        publish()
        save()
    }

    private fun publish() {
        val now = LocalDate.now()
        synchronized(lock) {
            val last7 = (6 downTo 0).map { back ->
                val d = now.minusDays(back.toLong())
                d.dayOfWeek.name.take(1) to (days[d.toString()] ?: 0)
            }
            val month = (0 until 30).sumOf { days[now.minusDays(it.toLong()).toString()] ?: 0 }
            snapshot = Snapshot(
                today = days[now.toString()] ?: 0,
                week = last7.sumOf { it.second },
                month = month,
                lastSevenDays = last7,
                topDomains = domains.entries.sortedByDescending { it.value }.take(20).map { it.key to it.value }
            )
        }
    }

    private fun save() {
        val json = synchronized(lock) {
            // Keep 60 days and the 300 most blocked domains.
            val cutoff = LocalDate.now().minusDays(60).toString()
            days.keys.filter { it < cutoff }.forEach { days.remove(it) }
            if (domains.size > 300) {
                domains.entries.sortedBy { it.value }.take(domains.size - 300).forEach { domains.remove(it.key) }
            }
            JSONObject().apply {
                put("days", JSONObject(days as Map<*, *>))
                put("domains", JSONObject(domains as Map<*, *>))
            }.toString()
        }
        io.launch { runCatching { file.writeText(json) } }
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val o = JSONObject(file.readText())
            synchronized(lock) {
                o.optJSONObject("days")?.let { d -> d.keys().forEach { days[it] = d.optInt(it) } }
                o.optJSONObject("domains")?.let { d -> d.keys().forEach { domains[it] = d.optInt(it) } }
            }
        }
    }

    /** "ads.g.doubleclick.net" -> "doubleclick.net" (good enough for a report). */
    private fun registrableDomain(host: String): String {
        val parts = host.lowercase().removePrefix("www.").split('.')
        if (parts.size <= 2) return parts.joinToString(".")
        val twoLevelSuffix = parts[parts.size - 2] in setOf("co", "com", "net", "org", "gov", "ac", "edu") && parts.last().length == 2
        return parts.takeLast(if (twoLevelSuffix) 3 else 2).joinToString(".")
    }
}
