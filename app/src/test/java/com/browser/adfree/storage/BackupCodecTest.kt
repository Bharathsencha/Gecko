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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    @Test
    fun roundTripKeepsBookmarksAndSettings() {
        val backup = Backup(
            favorites = listOf(FavoriteItem(title = "Example", url = "https://example.com")),
            settings = mapOf("https_only_mode" to true, "favorites_grid_columns" to 3, "theme_mode" to "DARK")
        )
        val decoded = BackupCodec.decode(BackupCodec.encode(backup))
        assertNotNull(decoded)
        assertEquals("https://example.com", decoded!!.favorites.single().url)
        assertEquals(true, decoded.settings["https_only_mode"])
        assertEquals("DARK", decoded.settings["theme_mode"])
        assertEquals(3, (decoded.settings["favorites_grid_columns"] as Number).toInt())
    }

    @Test
    fun rejectsFilesFromOtherApps() {
        assertNull(BackupCodec.decode("""{"app":"other","version":1}"""))
        assertNull(BackupCodec.decode("not json"))
        assertNull(BackupCodec.decode("""{"app":"gecko-adfree","version":99}"""))
    }

    @Test
    fun dropsBookmarksThatAreNotWebAddresses() {
        val text = """{"app":"gecko-adfree","version":1,"favorites":[
            {"title":"bad","url":"javascript:alert(1)"},
            {"title":"file","url":"file:///data/data/x"},
            {"title":"ok","url":"https://ok.example"}],"settings":{}}"""
        val decoded = BackupCodec.decode(text)!!
        assertEquals(listOf("https://ok.example"), decoded.favorites.map { it.url })
    }

    @Test
    fun ignoresNestedSettingValues() {
        val text = """{"app":"gecko-adfree","version":1,"favorites":[],
            "settings":{"a":true,"b":{"x":1},"c":[1,2],"d":"text"}}"""
        val settings = BackupCodec.decode(text)!!.settings
        assertEquals(setOf("a", "d"), settings.keys)
        assertTrue(settings["a"] == true)
    }
}
