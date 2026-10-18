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

import android.webkit.MimeTypeMap

/** File names and output containers for downloads. No I/O, so it is unit-tested. */
internal object DownloadNaming {

    val MEDIA_EXTENSIONS = setOf("mp4", "webm", "mkv", "m4a", "mp3", "aac", "ogg", "opus", "ts", "3gp", "m4v", "flv")

    /**
     * Chooses the output container from the media type, never from the file
     * name, so a WebM/VP9 stream is not forced into an MP4 container that
     * MediaMuxer cannot write.
     */
    fun containerFor(mimeType: String?, format: String?, isAudioOnly: Boolean): VideoAudioMuxer.Container {
        val mime = mimeType?.lowercase().orEmpty()
        val fmt = format?.lowercase().orEmpty()
        return when {
            mime.contains("webm") || fmt == "webm" -> VideoAudioMuxer.Container.WEBM
            mime.contains("matroska") || fmt == "mkv" -> VideoAudioMuxer.Container.WEBM
            mime.contains("mp2t") || mime.contains("mpegurl") || fmt == "m3u8" -> VideoAudioMuxer.Container.MP4
            mime.contains("mpeg") && mime.contains("audio") -> VideoAudioMuxer.Container.MP4
            mime.contains("mpeg") -> VideoAudioMuxer.Container.MP4
            isAudioOnly -> VideoAudioMuxer.Container.MP4
            else -> VideoAudioMuxer.Container.MP4
        }
    }

    /** Appends [container]'s extension unless the name already ends in a media one. */
    fun withExtension(fileName: String, container: VideoAudioMuxer.Container): String {
        val extension = fileName.substringAfterLast(".", "")
        if (extension.lowercase() in MEDIA_EXTENSIONS) return fileName
        return "$fileName.${container.extension}"
    }

    /** Name from Content-Disposition, then the URL's last path segment, then a generated one. */
    fun extractFileName(url: String, contentDisposition: String?, mimeType: String?): String {
        if (!contentDisposition.isNullOrBlank()) {
            val regex = Regex("filename\\*?=(?:UTF-8'')?[\"']?([^\"';\\n]+)[\"']?", RegexOption.IGNORE_CASE)
            val match = regex.find(contentDisposition)
            if (match != null) {
                val extracted = match.groupValues[1].trim()
                if (extracted.isNotEmpty()) return sanitizeFileName(extracted)
            }
        }
        try {
            val lastSegment = java.net.URI(url).path?.substringAfterLast('/')?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            if (!lastSegment.isNullOrBlank() && lastSegment.contains(".") && lastSegment.length > 3) {
                return sanitizeFileName(lastSegment)
            }
        } catch (e: Exception) {
            // Not a parseable URL; fall through to a generated name.
        }
        val ext = mimeType?.let { MimeTypeMap.getSingleton()?.getExtensionFromMimeType(it) } ?: "bin"
        return "download_${System.currentTimeMillis()}.$ext"
    }

    fun sanitizeFileName(name: String): String = name
        .replace(Regex("\\s+"), " ")
        .replace(Regex("[\\\\/:*?\"<>|•\u0000-\u001f]"), "_")
        .trim()
        .ifBlank { "download_${System.currentTimeMillis()}" }
}
