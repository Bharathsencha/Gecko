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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabPolicyTest {
    @Test
    fun blankPrivateTabIsDiscardedButBrowsedOneIsKept() {
        assertTrue(TabPolicy.isBlankPrivate(isPrivate = true, url = "about:home"))
        assertFalse(TabPolicy.isBlankPrivate(isPrivate = true, url = "https://example.com"))
        assertFalse(TabPolicy.isBlankPrivate(isPrivate = false, url = "about:home"))
    }

    @Test
    fun staleOnlyAfterTheLimit() {
        val day = 24L * 60 * 60 * 1000
        assertFalse(TabPolicy.isStale(lastActiveMs = 0, nowMs = 6 * day, days = 7))
        assertTrue(TabPolicy.isStale(lastActiveMs = 0, nowMs = 8 * day, days = 7))
    }

    @Test
    fun zeroDaysNeverCloses() {
        assertFalse(TabPolicy.isStale(lastActiveMs = 0, nowMs = Long.MAX_VALUE / 2, days = 0))
    }

    @Test
    fun onlyRealNormalPagesAreReopenable() {
        assertTrue(TabPolicy.isReopenable(false, "https://example.com"))
        assertFalse(TabPolicy.isReopenable(true, "https://example.com"))
        assertFalse(TabPolicy.isReopenable(false, "about:home"))
        assertFalse(TabPolicy.isReopenable(false, ""))
    }
}

class ClosedTabStackTest {
    private fun tab(url: String) = ClosedTab(url, url)

    @Test
    fun reopensNewestFirst() {
        val stack = ClosedTabStack()
        stack.push(tab("a"))
        stack.push(tab("b"))
        assertEquals("b", stack.pop()?.url)
        assertEquals("a", stack.pop()?.url)
        assertNull(stack.pop())
    }

    @Test
    fun keepsOnlyTheLimit() {
        val stack = ClosedTabStack(limit = 3)
        listOf("a", "b", "c", "d").forEach { stack.push(tab(it)) }
        assertEquals(3, stack.size)
        stack.pop(); stack.pop()
        assertEquals("b", stack.pop()?.url)
    }

    @Test
    fun sameAddressIsListedOnce() {
        val stack = ClosedTabStack()
        stack.push(tab("a"))
        stack.push(tab("b"))
        stack.push(tab("a"))
        assertEquals(2, stack.size)
        assertEquals("a", stack.pop()?.url)
    }

    @Test
    fun clearForgetsEverything() {
        val stack = ClosedTabStack()
        stack.push(tab("a"))
        stack.clear()
        assertEquals(0, stack.size)
        assertNull(stack.pop())
    }
}
