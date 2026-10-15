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
import com.browser.adfree.model.TabModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SavedTab(
    val id: String,
    val url: String,
    val title: String,
    val isDesktopMode: Boolean = false,
    val zoomLevel: Int = 100,
    val container: String? = null
)

class TabSessionRepository(private val context: Context) {

    private val file = File(context.filesDir, "tabs_session.json")

    fun saveSession(tabs: List<TabModel>, activeIndex: Int) {
        try {
            val root = JSONObject()
            val tabsArray = JSONArray()

            for (tab in tabs) {
                val tabObj = JSONObject().apply {
                    put("id", tab.id)
                    put("url", tab.url)
                    put("title", tab.title)
                    put("isDesktopMode", tab.isDesktopMode)
                    put("zoomLevel", tab.zoomLevel)
                    tab.container?.let { put("container", it) }
                }
                tabsArray.put(tabObj)
            }

            root.put("tabs", tabsArray)
            root.put("activeIndex", activeIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)))

            val tempFile = File(context.filesDir, "tabs_session.json.tmp")
            tempFile.writeText(root.toString())
            tempFile.renameTo(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadSession(): Pair<List<SavedTab>, Int> {
        val result = mutableListOf<SavedTab>()
        var activeIndex = 0

        try {
            if (file.exists()) {
                val jsonStr = file.readText()
                val root = JSONObject(jsonStr)
                activeIndex = root.optInt("activeIndex", 0)

                val tabsArray = root.optJSONArray("tabs")
                if (tabsArray != null) {
                    for (i in 0 until tabsArray.length()) {
                        val obj = tabsArray.getJSONObject(i)
                        result.add(
                            SavedTab(
                                id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                                url = obj.optString("url", "about:home"),
                                title = obj.optString("title", "Start Page"),
                                isDesktopMode = obj.optBoolean("isDesktopMode", false),
                                zoomLevel = obj.optInt("zoomLevel", 100),
                                container = obj.optString("container").ifBlank { null }
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Pair(result, activeIndex)
    }

    fun clearSession() {
        try {
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
