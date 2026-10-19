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

import com.browser.adfree.model.FavoriteItem
import org.json.JSONArray
import org.json.JSONObject

/** What a backup file holds: bookmarks and non-sensitive settings. Never passwords, history or cookies. */
data class Backup(val favorites: List<FavoriteItem>, val settings: Map<String, Any>)

object BackupCodec {
    const val VERSION = 1
    private const val MAX_FAVORITES = 5000

    fun encode(backup: Backup): String {
        val root = JSONObject()
        root.put("app", "gecko-adfree")
        root.put("version", VERSION)
        val favorites = JSONArray()
        backup.favorites.forEach { fav ->
            favorites.put(JSONObject().apply {
                put("title", fav.title)
                put("url", fav.url)
                put("isBuiltIn", fav.isBuiltIn)
                fav.builtInType?.let { put("builtInType", it) }
            })
        }
        root.put("favorites", favorites)
        val settings = JSONObject()
        backup.settings.forEach { (key, value) -> settings.put(key, value) }
        root.put("settings", settings)
        return root.toString(2)
    }

    /** Parses a backup, or returns null when the text is not one this app wrote. */
    fun decode(text: String): Backup? = try {
        val root = JSONObject(text)
        if (root.optString("app") != "gecko-adfree" || root.optInt("version", 0) !in 1..VERSION) {
            null
        } else {
            val favorites = mutableListOf<FavoriteItem>()
            val array = root.optJSONArray("favorites") ?: JSONArray()
            for (i in 0 until minOf(array.length(), MAX_FAVORITES)) {
                val obj = array.optJSONObject(i) ?: continue
                val url = obj.optString("url").trim()
                if (!(url.startsWith("https://") || url.startsWith("http://"))) continue
                favorites.add(
                    FavoriteItem(
                        title = obj.optString("title").ifBlank { url },
                        url = url,
                        isBuiltIn = obj.optBoolean("isBuiltIn", false),
                        builtInType = obj.optString("builtInType").ifBlank { null }
                    )
                )
            }
            val settings = mutableMapOf<String, Any>()
            val obj = root.optJSONObject("settings")
            obj?.keys()?.forEach { key ->
                when (val value = obj.get(key)) {
                    is Boolean, is Number, is String -> settings[key] = value
                    else -> {}
                }
            }
            Backup(favorites, settings)
        }
    } catch (e: Exception) {
        null
    }
}
