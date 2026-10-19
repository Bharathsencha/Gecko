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

package com.browser.adfree.download

import org.junit.Assert.assertEquals
import org.junit.Test

class YtDlpSelectorTest {
    private fun url(itag: Int, mime: String) =
        "https://rr1---sn.googlevideo.com/videoplayback?itag=$itag&mime=" + java.net.URLEncoder.encode(mime, "UTF-8") + "&clen=1"

    @Test fun itagIsRead() {
        assertEquals("700", YtDlpDownloader.itagOf(url(700, "video/mp4")))
    }

    @Test fun audioFallsBackToSameContainer() {
        assertEquals("600/ba[ext=webm]/ba", YtDlpDownloader.selectorFor(url(600, "audio/webm"), "600", null))
        assertEquals("140/ba[ext=m4a]/ba", YtDlpDownloader.selectorFor(url(140, "audio/mp4"), "140", null))
    }

    @Test fun videoFallsBackToSameHeightThenLower() {
        assertEquals(
            "700/bv*[height=1440][ext=mp4]/bv*[height=1440]/bv*[height<=1440][ext=mp4]/bv*[height<=1440]",
            YtDlpDownloader.selectorFor(url(700, "video/mp4"), "700", 1440)
        )
    }

    @Test fun videoWithoutHeightTakesBestOfContainer() {
        assertEquals("271/bv*[ext=webm]/bv*", YtDlpDownloader.selectorFor(url(271, "video/webm"), "271", null))
    }
}
