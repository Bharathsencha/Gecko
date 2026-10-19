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
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNamingTest {
    @Test fun contentDispositionWins() {
        assertEquals("report.pdf", DownloadNaming.extractFileName("https://x.example/dl?id=1", "attachment; filename=\"report.pdf\"", "application/pdf"))
        assertEquals("résumé.pdf", DownloadNaming.extractFileName("https://x.example/dl", "attachment; filename*=UTF-8''résumé.pdf", null))
    }

    @Test fun lastPathSegmentIsUsedAndDecoded() {
        assertEquals("my file.zip", DownloadNaming.extractFileName("https://x.example/files/my%20file.zip?token=abc", null, null))
    }

    @Test fun generatedNameWhenNothingUsable() {
        val name = DownloadNaming.extractFileName("https://x.example/", null, null)
        assertTrue(name, name.startsWith("download_") && name.endsWith(".bin"))
    }

    @Test fun unsafeCharactersAreReplaced() {
        assertEquals("a_b_c_.txt", DownloadNaming.sanitizeFileName("a/b:c?.txt"))
        assertEquals("one two", DownloadNaming.sanitizeFileName("  one \n two "))
    }

    @Test fun extensionAddedOnlyWhenMissing() {
        assertEquals("clip.mp4", DownloadNaming.withExtension("clip", VideoAudioMuxer.Container.MP4))
        assertEquals("clip.webm", DownloadNaming.withExtension("clip.webm", VideoAudioMuxer.Container.MP4))
        assertEquals("v1.2.webm", DownloadNaming.withExtension("v1.2", VideoAudioMuxer.Container.WEBM))
    }

    @Test fun containerFollowsMediaType() {
        assertEquals(VideoAudioMuxer.Container.WEBM, DownloadNaming.containerFor("video/webm", null, false))
        assertEquals(VideoAudioMuxer.Container.WEBM, DownloadNaming.containerFor(null, "mkv", false))
        assertEquals(VideoAudioMuxer.Container.MP4, DownloadNaming.containerFor("application/x-mpegURL", null, false))
        assertEquals(VideoAudioMuxer.Container.MP4, DownloadNaming.containerFor("audio/mp4", null, true))
    }
}
