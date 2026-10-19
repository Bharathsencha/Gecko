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

package com.browser.adfree.model

import org.json.JSONArray

/** Reads the suggestion lists search engines return in the OpenSearch format: ["query", ["a", "b"]]. */
object SearchSuggestions {
    private const val MAX = 6

    fun requestUrl(engine: SearchEngine, query: String): String =
        engine.suggestUrl + java.net.URLEncoder.encode(query.trim(), "UTF-8")

    fun parse(body: String, query: String): List<String> = try {
        val list = JSONArray(body).optJSONArray(1) ?: JSONArray()
        val typed = query.trim()
        buildList {
            for (i in 0 until list.length()) {
                val item = list.optString(i).trim()
                if (item.isNotEmpty() && !item.equals(typed, ignoreCase = true) && item.length <= 200) add(item)
            }
        }.distinct().take(MAX)
    } catch (e: Exception) {
        emptyList()
    }
}
