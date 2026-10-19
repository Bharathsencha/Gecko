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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mozilla.geckoview.GeckoSession.PermissionDelegate

class SitePermissionTest {
    @Test fun everyEnginePermissionMapsBack() {
        SitePermission.entries.forEach { p ->
            SitePermission.toGecko(p).forEach { type -> assertEquals(p, SitePermission.fromGecko(type)) }
        }
    }

    @Test fun autoplayAndTrackingAreNotAsked() {
        assertNull(SitePermission.fromGecko(PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE))
        assertNull(SitePermission.fromGecko(PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE))
        assertNull(SitePermission.fromGecko(PermissionDelegate.PERMISSION_TRACKING))
    }

    @Test fun cameraAndMicrophoneAreHandledOutsideContentPermissions() {
        assertEquals(emptyList<Int>(), SitePermission.toGecko(SitePermission.CAMERA))
        assertEquals(emptyList<Int>(), SitePermission.toGecko(SitePermission.MICROPHONE))
    }
}
