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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoSession
import java.util.UUID

class TabModel(
    val id: String = UUID.randomUUID().toString(),
    initialTitle: String = "Start Page",
    initialUrl: String = "about:home",
    initialSession: GeckoSession
) {
    var session by mutableStateOf(initialSession)
    var title by mutableStateOf(initialTitle)
    var url by mutableStateOf(initialUrl)
    var progress by mutableIntStateOf(0)
    var isLoading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var adBlockCount by mutableIntStateOf(0)
    var isDesktopMode by mutableStateOf(false)

    // Safari Presentation features
    var zoomLevel by mutableIntStateOf(100)
    var isDarkWeb by mutableStateOf(false)
    var isReaderMode by mutableStateOf(false)
    var articleData by mutableStateOf<ArticleData?>(null)
}
