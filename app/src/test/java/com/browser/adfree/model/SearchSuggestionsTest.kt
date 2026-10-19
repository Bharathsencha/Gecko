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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSuggestionsTest {
    @Test
    fun parsesOpenSearchListsAndDropsTheTypedText() {
        val body = """["rust",["rust","rust lang","rust game","rust lang"]]"""
        assertEquals(listOf("rust lang", "rust game"), SearchSuggestions.parse(body, "Rust"))
    }

    @Test
    fun garbageGivesNoSuggestions() {
        assertTrue(SearchSuggestions.parse("<html>", "x").isEmpty())
        assertTrue(SearchSuggestions.parse("""["x"]""", "x").isEmpty())
    }

    @Test
    fun encodesTheQuery() {
        val url = SearchSuggestions.requestUrl(SearchEngine.DUCKDUCKGO, "a b&c")
        assertTrue(url.endsWith("a+b%26c"))
    }

    @Test
    fun addressesAreNotSearchPhrases() {
        assertFalse(UrlInput.isSearchPhrase("example.com"))
        assertFalse(UrlInput.isSearchPhrase("https://example.com/a b"))
        assertFalse(UrlInput.isSearchPhrase("about:home"))
        assertFalse(UrlInput.isSearchPhrase(""))
        assertTrue(UrlInput.isSearchPhrase("weather tomorrow"))
        assertTrue(UrlInput.isSearchPhrase("kotlin"))
    }
}
