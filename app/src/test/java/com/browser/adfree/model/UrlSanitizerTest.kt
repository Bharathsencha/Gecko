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

class UrlSanitizerTest {
    @Test fun stripsTrackingParamsAndKeepsOthersUnchanged() {
        assertEquals(
            "https://shop.example/p?id=7&q=a%20b",
            UrlSanitizer.stripTrackingParams("https://shop.example/p?utm_source=news&id=7&fbclid=XYZ&q=a%20b")
        )
    }

    @Test fun dropsTheQuestionMarkWhenNothingIsLeft() {
        assertEquals("https://a.example/p", UrlSanitizer.stripTrackingParams("https://a.example/p?utm_medium=x&gclid=1"))
    }

    @Test fun keepsTheFragment() {
        assertEquals("https://a.example/p?x=1#top", UrlSanitizer.stripTrackingParams("https://a.example/p?x=1&utm_term=t#top"))
    }

    @Test fun youtubeShareParamIsRemovedOnlyOnYoutube() {
        assertEquals("https://youtu.be/abc", UrlSanitizer.stripTrackingParams("https://youtu.be/abc?si=123"))
        assertEquals("https://example.com/?si=123", UrlSanitizer.stripTrackingParams("https://example.com/?si=123"))
    }

    @Test fun untouchedWhenNoQuery() {
        assertEquals("https://example.com/a", UrlSanitizer.stripTrackingParams("https://example.com/a"))
    }

    @Test fun deAmpGoogleWrapper() {
        assertEquals("https://publisher.com/story", UrlSanitizer.deAmp("https://www.google.com/amp/s/publisher.com/story"))
    }

    @Test fun deAmpAmpProjectCache() {
        assertEquals("https://publisher.com/a/b", UrlSanitizer.deAmp("https://publisher-com.cdn.ampproject.org/c/s/publisher.com/a/b"))
    }

    @Test fun normalUrlsAreNotDeAmped() {
        assertEquals("https://example.com/amp/x", UrlSanitizer.deAmp("https://example.com/amp/x"))
    }
}
