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
import com.browser.adfree.model.FavoriteItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class FavoritesRepository(private val context: Context) {
    val favorites = mutableStateListOf<FavoriteItem>()
    private val file = File(context.filesDir, "favorites.json")

    init {
        loadFavorites()
    }

    private fun loadFavorites() {
        try {
            if (file.exists()) {
                val jsonStr = file.readText()
                val jsonArray = JSONArray(jsonStr)
                favorites.clear()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val rawType = if (obj.has("builtInType") && !obj.isNull("builtInType")) {
                        obj.getString("builtInType")
                    } else null
                    val cleanType = if (rawType == "null" || rawType.isNullOrBlank()) null else rawType

                    favorites.add(
                        FavoriteItem(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            title = obj.getString("title"),
                            url = obj.getString("url"),
                            isBuiltIn = obj.optBoolean("isBuiltIn", false),
                            builtInType = cleanType
                        )
                    )
                }
            } else {
                populateDefaults()
                saveFavorites()
            }
        } catch (e: Exception) {
            android.util.Log.w("GeckoApp", "Ignored ${e.javaClass.simpleName}")
            populateDefaults()
            saveFavorites()
        }
    }

    private fun populateDefaults() {
        favorites.clear()
        favorites.addAll(
            listOf(
                FavoriteItem(title = "Google", url = "https://www.google.com", isBuiltIn = true, builtInType = "google"),
                FavoriteItem(title = "YouTube", url = "https://m.youtube.com", isBuiltIn = true, builtInType = "youtube"),
                FavoriteItem(title = "Reddit", url = "https://www.reddit.com", isBuiltIn = true, builtInType = "reddit"),
                FavoriteItem(title = "Wikipedia", url = "https://en.m.wikipedia.org", isBuiltIn = true, builtInType = "wiki"),
                FavoriteItem(title = "GitHub", url = "https://github.com", isBuiltIn = true, builtInType = "github")
            )
        )
    }

    fun addFavorite(title: String, url: String) {
        val trimmed = url.trim()
        val cleanUrl = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else trimmed

        val cleanTitle = if (title.isBlank()) {
            try {
                val host = java.net.URI(cleanUrl).host ?: cleanUrl
                if (host.startsWith("www.")) host.substring(4) else host
            } catch (e: Exception) {
                cleanUrl
            }
        } else title.trim()

        favorites.add(
            FavoriteItem(
                title = cleanTitle,
                url = cleanUrl,
                isBuiltIn = false,
                builtInType = null
            )
        )
        saveFavorites()
    }

    /** Adds bookmarks from a backup that are not already saved; returns how many were new. */
    fun importFavorites(items: List<FavoriteItem>): Int {
        val known = favorites.map { it.url }.toMutableSet()
        val fresh = items.filter { known.add(it.url) }
        favorites.addAll(fresh)
        if (fresh.isNotEmpty()) saveFavorites()
        return fresh.size
    }

    fun removeFavorite(item: FavoriteItem) {
        favorites.remove(item)
        saveFavorites()
    }

    fun saveFavorites() {
        try {
            val jsonArray = JSONArray()
            for (fav in favorites) {
                val obj = JSONObject()
                obj.put("id", fav.id)
                obj.put("title", fav.title)
                obj.put("url", fav.url)
                obj.put("isBuiltIn", fav.isBuiltIn)
                if (fav.builtInType != null) {
                    obj.put("builtInType", fav.builtInType)
                }
                jsonArray.put(obj)
            }
            val json = jsonArray.toString()
            DiskWriter.save(file) { json }
        } catch (e: Exception) {
            android.util.Log.w("GeckoApp", "Ignored ${e.javaClass.simpleName}")
        }
    }
}
