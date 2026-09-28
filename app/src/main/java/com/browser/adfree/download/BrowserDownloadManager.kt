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

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.FileProvider
import com.browser.adfree.model.DownloadItem
import com.browser.adfree.model.DownloadStatus
import java.io.File
import kotlin.concurrent.timer

class BrowserDownloadManager(private val context: Context) {
    val downloadsList = mutableStateListOf<DownloadItem>()
    private val systemDownloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        // Start periodic status poller for active downloads
        timer(name = "download-poller", daemon = true, period = 1000L, initialDelay = 1000L) {
            updateDownloadProgress()
        }
    }

    fun startDownload(
        url: String,
        contentDisposition: String? = null,
        mimeType: String? = null,
        userAgent: String? = null,
        referer: String? = null
    ): Long {
        try {
            val fileName = extractFileName(url, contentDisposition, mimeType)
            val uri = Uri.parse(url)
            val request = DownloadManager.Request(uri).apply {
                setTitle(fileName)
                setDescription("Downloading file...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                userAgent?.let { addRequestHeader("User-Agent", it) }
                referer?.let { addRequestHeader("Referer", it) }
                mimeType?.let { setMimeType(it) }
            }

            val downloadId = systemDownloadManager.enqueue(request)
            val destinationPath = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            ).absolutePath

            val item = DownloadItem(
                id = downloadId,
                fileName = fileName,
                url = url,
                mimeType = mimeType ?: "application/octet-stream",
                destinationPath = destinationPath,
                initialProgress = 0,
                initialStatus = DownloadStatus.DOWNLOADING
            )

            mainHandler.post {
                downloadsList.add(0, item)
                Toast.makeText(context, "Downloading $fileName", Toast.LENGTH_SHORT).show()
            }

            return downloadId
        } catch (e: Exception) {
            e.printStackTrace()
            mainHandler.post {
                Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
            return -1L
        }
    }

    private fun updateDownloadProgress() {
        val activeItems = downloadsList.filter { it.status == DownloadStatus.DOWNLOADING }
        if (activeItems.isEmpty()) return

        for (item in activeItems) {
            val query = DownloadManager.Query().setFilterById(item.id)
            var cursor: Cursor? = null
            try {
                cursor = systemDownloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalBytesIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                    if (statusIndex != -1) {
                        val status = cursor.getInt(statusIndex)
                        val downloaded = if (bytesDownloadedIndex != -1) cursor.getLong(bytesDownloadedIndex) else 0L
                        val total = if (totalBytesIndex != -1) cursor.getLong(totalBytesIndex) else -1L

                        mainHandler.post {
                            item.currentBytes = downloaded
                            item.totalBytes = total

                            when (status) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    item.progress = 100
                                    item.status = DownloadStatus.COMPLETED
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    item.status = DownloadStatus.FAILED
                                }
                                DownloadManager.STATUS_RUNNING -> {
                                    if (total > 0) {
                                        item.progress = ((downloaded * 100) / total).toInt()
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore query errors
            } finally {
                cursor?.close()
            }
        }
    }

    fun openDownloadedFile(item: DownloadItem) {
        try {
            val file = File(item.destinationPath)
            if (!file.exists()) {
                Toast.makeText(context, "File does not exist on disk", Toast.LENGTH_SHORT).show()
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val extension = file.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: item.mimeType

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "Open file with...").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun removeDownload(item: DownloadItem) {
        downloadsList.remove(item)
    }

    fun clearCompleted() {
        downloadsList.removeAll { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED }
    }

    private fun extractFileName(url: String, contentDisposition: String?, mimeType: String?): String {
        // Try content disposition
        if (!contentDisposition.isNullOrBlank()) {
            val regex = Regex("filename\\*?=(?:UTF-8'')?[\"']?([^\"';\\n]+)[\"']?", RegexOption.IGNORE_CASE)
            val match = regex.find(contentDisposition)
            if (match != null) {
                val extracted = match.groupValues[1].trim()
                if (extracted.isNotEmpty()) return sanitizeFileName(extracted)
            }
        }

        // Try extracting from URL path
        try {
            val uri = Uri.parse(url)
            val lastSegment = uri.lastPathSegment
            if (!lastSegment.isNullOrBlank() && lastSegment.contains(".")) {
                return sanitizeFileName(lastSegment)
            }
        } catch (e: Exception) {}

        // Fallback with timestamp and extension
        val ext = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "bin"
        return "download_${System.currentTimeMillis()}.$ext"
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }
}
