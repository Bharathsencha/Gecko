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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class DownloadStatus {
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

class DownloadItem(
    val id: Long,
    fileName: String,
    val url: String,
    mimeType: String,
    destinationPath: String,
    initialProgress: Int = 0,
    initialStatus: DownloadStatus = DownloadStatus.DOWNLOADING,
    val timestamp: Long = System.currentTimeMillis(),
    val audioUrl: String? = null,
    val userAgent: String? = null,
    val referer: String? = null,
    val extraHeaders: Map<String, String> = emptyMap(),
    /** Page the media came from; lets YouTube streams fall back to yt-dlp. */
    val sourcePageUrl: String? = null,
    /** True when the id is a real android.app.DownloadManager id. */
    val isSystemDownload: Boolean = false,
    /** Which internal pipeline owns this item, so pause/resume re-enters correctly. */
    val pipeline: DownloadPipeline = DownloadPipeline.STREAM
) {
    /** Can change once, when an audio download is remuxed into its real format. */
    var fileName by mutableStateOf(fileName)
    var mimeType by mutableStateOf(mimeType)
    var destinationPath by mutableStateOf(destinationPath)

    var progress by mutableIntStateOf(initialProgress)
    var status by mutableStateOf(initialStatus)
    var currentBytes by mutableLongStateOf(0L)
    var totalBytes by mutableLongStateOf(-1L)

    /**
     * Human readable failure reason. Persisted so the download list can explain
     * a failure long after the toast that announced it has disappeared.
     */
    var errorMessage by mutableStateOf<String?>(null)
}

enum class DownloadPipeline {
    /** Direct http(s) byte stream. */
    STREAM,

    /** HLS (.m3u8) segment assembly. */
    HLS,

    /** Separate video + audio streams remuxed into one file. */
    MUXED,

    /** Delegated to android.app.DownloadManager. */
    SYSTEM
}
