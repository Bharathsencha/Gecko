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
import androidx.compose.runtime.mutableStateListOf
import com.browser.adfree.model.HistoryItem
import com.browser.adfree.model.TimeframeWipe
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class HistoryRepository(private val context: Context) {

    val history = mutableStateListOf<HistoryItem>()
    private val file = File(context.filesDir, "history.json")

    init {
        loadHistory()
    }

    private fun loadHistory() {
        try {
            if (file.exists()) {
                val jsonStr = file.readText()
                val jsonArray = JSONArray(jsonStr)
                history.clear()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    history.add(
                        HistoryItem(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            title = obj.getString("title"),
                            url = obj.getString("url"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addHistory(title: String, url: String) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank() || trimmedUrl.startsWith("about:") || trimmedUrl.startsWith("javascript:")) {
            return
        }

        val cleanTitle = if (title.isBlank() || title == "about:blank") {
            try {
                val host = java.net.URI(trimmedUrl).host ?: trimmedUrl
                if (host.startsWith("www.")) host.substring(4) else host
            } catch (e: Exception) {
                trimmedUrl
            }
        } else title

        // Remove identical URL if visited within the last 15 seconds to prevent spam
        val now = System.currentTimeMillis()
        history.removeAll { it.url == trimmedUrl && (now - it.timestamp) < 15_000 }

        history.add(
            0,
            HistoryItem(
                title = cleanTitle,
                url = trimmedUrl,
                timestamp = now
            )
        )

        // Cap at 1000 items
        if (history.size > 1000) {
            history.removeRange(1000, history.size)
        }

        saveHistory()
    }

    fun deleteHistoryItem(id: String) {
        val removed = history.removeAll { it.id == id }
        if (removed) {
            saveHistory()
        }
    }

    fun clearHistoryByTimeframe(timeframe: TimeframeWipe) {
        if (timeframe == TimeframeWipe.ALL_TIME) {
            history.clear()
        } else {
            val cutoff = System.currentTimeMillis() - timeframe.durationMs
            history.removeAll { it.timestamp >= cutoff }
        }
        saveHistory()
    }

    fun clearAll() {
        history.clear()
        saveHistory()
    }

    private fun saveHistory() {
        try {
            val jsonArray = JSONArray()
            for (item in history) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("url", item.url)
                    put("timestamp", item.timestamp)
                }
                jsonArray.put(obj)
            }
            val tempFile = File(context.filesDir, "history.json.tmp")
            tempFile.writeText(jsonArray.toString())
            tempFile.renameTo(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
