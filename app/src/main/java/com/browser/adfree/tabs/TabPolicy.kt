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

/** Decisions about when a tab is worth keeping, kept free of engine types so they can be tested. */
object TabPolicy {
    private const val HOME_URL = TabManager.HOME_URL
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** A private tab still on the start page holds nothing, so leaving it closes it. */
    fun isBlankPrivate(isPrivate: Boolean, url: String): Boolean = isPrivate && url == HOME_URL

    /** True when a tab untouched since [lastActiveMs] has passed the [days] limit; 0 days means never. */
    fun isStale(lastActiveMs: Long, nowMs: Long, days: Int): Boolean =
        days > 0 && nowMs - lastActiveMs > days * DAY_MS

    /** Only pages with an address are worth reopening. */
    fun isReopenable(isPrivate: Boolean, url: String): Boolean =
        !isPrivate && url.isNotBlank() && url != HOME_URL && url != "about:blank"
}
