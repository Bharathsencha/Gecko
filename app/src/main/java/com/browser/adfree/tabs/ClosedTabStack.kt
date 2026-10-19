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

package com.browser.adfree.tabs

/** What it takes to bring a closed tab back. */
data class ClosedTab(
    val url: String,
    val title: String,
    val container: String? = null,
    val group: String? = null
)

/**
 * The most recently closed normal tabs, newest first. Kept in memory only, so
 * nothing about a closed page is written to disk, and never fed private tabs.
 */
class ClosedTabStack(private val limit: Int = 10) {
    private val items = ArrayDeque<ClosedTab>()

    val size: Int get() = items.size

    fun push(tab: ClosedTab) {
        items.removeAll { it.url == tab.url }
        items.addFirst(tab)
        while (items.size > limit) items.removeLast()
    }

    fun pop(): ClosedTab? = items.removeFirstOrNull()

    fun clear() = items.clear()
}
