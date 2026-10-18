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
import org.junit.Test

class UrlInputTest {
    private val ddg = SearchEngine.DUCKDUCKGO
    private fun resolve(s: String) = UrlInput.resolve(s, ddg)

    @Test fun blankGoesHome() {
        assertEquals("about:home", resolve("   "))
    }

    @Test fun fullUrlIsKept() {
        assertEquals("https://example.com/a?b=1", resolve("https://example.com/a?b=1"))
    }

    @Test fun bareDomainGetsHttps() {
        assertEquals("https://example.com", resolve("example.com"))
        assertEquals("https://news.ycombinator.com/item?id=1", resolve("news.ycombinator.com/item?id=1"))
    }

    @Test fun portAndLocalAddressesOpenDirectly() {
        assertEquals("https://example.com:8443/x", resolve("example.com:8443/x"))
        assertEquals("http://localhost:3000", resolve("localhost:3000"))
        assertEquals("http://192.168.1.1", resolve("192.168.1.1"))
    }

    @Test fun wordsAreSearched() {
        assertEquals(ddg.searchUrl + "gecko+browser", resolve("gecko browser"))
        assertEquals(ddg.searchUrl + "kotlin", resolve("kotlin"))
        assertEquals(ddg.searchUrl + "version+1.2", resolve("version 1.2"))
    }

    @Test fun numbersAndBadPortsAreSearched() {
        assertEquals(ddg.searchUrl + "3.14", resolve("3.14"))
        assertEquals(ddg.searchUrl + "example.com%3A99999", resolve("example.com:99999"))
    }

    @Test fun pastedMarkdownLinkOpensTheLink() {
        assertEquals("https://eff.org/", resolve("[EFF](https://eff.org/)"))
    }

    @Test fun trackingParamsAreRemovedFromTypedUrls() {
        assertEquals("https://example.com/p?id=7", resolve("https://example.com/p?utm_source=x&id=7"))
    }

    @Test fun specialSchemesPassThrough() {
        assertEquals("about:config", resolve("about:config"))
        assertEquals("content://media/1", resolve("content://media/1"))
    }
}
