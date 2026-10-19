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

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Container tabs: each container has its own cookies, storage and cache, so
 * sites in one container cannot recognise you from another. The user creates
 * and names them; tabs without a container share the default store.
 */
data class TabContainer(val id: String, val displayName: String, val color: Long) {
    companion object {
        /** Colours offered when creating or editing a container. */
        val PALETTE = listOf(0xFFFFD84DL, 0xFF8EC5FFL, 0xFFFFB067L, 0xFFFF9EC7L, 0xFF7FD8C8L, 0xFFB49CFFL, 0xFF9BE07AL, 0xFFFF6B4AL)

        var all by mutableStateOf<List<TabContainer>>(emptyList())
            private set

        private var prefs: SharedPreferences? = null

        fun init(context: Context) {
            prefs = context.getSharedPreferences("containers", Context.MODE_PRIVATE)
            all = load()
        }

        fun byId(id: String?): TabContainer? = if (id == null) null else all.firstOrNull { it.id == id }

        fun add(name: String, color: Long): TabContainer {
            val c = TabContainer("c-" + UUID.randomUUID().toString().take(8), name.trim().take(24), color)
            all = all + c
            save()
            return c
        }

        fun update(container: TabContainer) {
            all = all.map { if (it.id == container.id) container else it }
            save()
        }

        /** Removes the container from the list; the caller clears its stored data. */
        fun remove(id: String) {
            all = all.filterNot { it.id == id }
            save()
        }

        private fun load(): List<TabContainer> = try {
            val arr = JSONArray(prefs?.getString("list", "[]") ?: "[]")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TabContainer(o.getString("id"), o.getString("name"), o.getLong("color"))
            }
        } catch (e: Exception) {
            emptyList()
        }

        private fun save() {
            val arr = JSONArray()
            all.forEach { c -> arr.put(JSONObject().put("id", c.id).put("name", c.displayName).put("color", c.color)) }
            prefs?.edit()?.putString("list", arr.toString())?.apply()
        }
    }
}
