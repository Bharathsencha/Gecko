package com.browser.adblocker.downloader.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.concurrent.thread

object VideoDownloader {
    private val client = OkHttpClient()

    interface DownloadProgressListener {
        fun onProgress(percent: Int, currentSegment: Int, totalSegments: Int)
        fun onComplete(filePath: String)
        fun onError(error: String)
    }

    fun downloadVideo(
        context: Context,
        url: String,
        title: String,
        userAgent: String,
        cookie: String,
        listener: DownloadProgressListener? = null
    ) {
        val cleanTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        
        if (url.contains(".m3u8")) {
            // HLS Stream - download custom HLS segments
            downloadHlsStream(context, url, "$cleanTitle.ts", userAgent, cookie, listener)
        } else {
            // Standard File (MP4, WEBM etc) - Use DownloadManager for background efficiency
            try {
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val uri = Uri.parse(url)
                val request = DownloadManager.Request(uri)
                    .setTitle(title)
                    .setDescription("Downloading Video...")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$cleanTitle.mp4")
                
                if (userAgent.isNotEmpty()) {
                    request.addRequestHeader("User-Agent", userAgent)
                }
                if (cookie.isNotEmpty()) {
                    request.addRequestHeader("Cookie", cookie)
                }

                downloadManager.enqueue(request)
                
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Download started for $title", Toast.LENGTH_SHORT).show()
                }
                
                val path = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "$cleanTitle.mp4"
                ).absolutePath
                listener?.onComplete(path)
            } catch (e: Exception) {
                e.printStackTrace()
                listener?.onError(e.message ?: "Failed to queue download")
            }
        }
    }

    private fun downloadHlsStream(
        context: Context,
        playlistUrl: String,
        fileName: String,
        userAgent: String,
        cookie: String,
        listener: DownloadProgressListener?
    ) {
        thread {
            try {
                // 1. Fetch playlist
                val requestBuilder = Request.Builder().url(playlistUrl)
                if (userAgent.isNotEmpty()) {
                    requestBuilder.addHeader("User-Agent", userAgent)
                }
                if (cookie.isNotEmpty()) {
                    requestBuilder.addHeader("Cookie", cookie)
                }

                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    throw IOException("Failed to load playlist: " + response.code)
                }
                
                val content = response.body?.string() ?: throw IOException("Empty playlist body")
                val lines = content.split("\n").map { it.trim() }
                
                val segmentUrls = mutableListOf<String>()
                for (line in lines) {
                    if (line.isNotEmpty() && !line.startsWith("#")) {
                        val absoluteUrl = M3u8Parser.resolveUrl(playlistUrl, line)
                        segmentUrls.add(absoluteUrl)
                    }
                }

                if (segmentUrls.isEmpty()) {
                    throw IOException("No TS segments found in playlist")
                }

                // 2. Prepare output file
                val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val outputFile = File(downloadsDir, fileName)
                val fos = FileOutputStream(outputFile)

                // 3. Download segment by segment
                val total = segmentUrls.size
                var downloadedCount = 0
                
                for ((index, segmentUrl) in segmentUrls.withIndex()) {
                    val segRequest = Request.Builder().url(segmentUrl)
                    if (userAgent.isNotEmpty()) {
                        segRequest.addHeader("User-Agent", userAgent)
                    }
                    if (cookie.isNotEmpty()) {
                        segRequest.addHeader("Cookie", cookie)
                    }

                    val segResponse = client.newCall(segRequest.build()).execute()
                    if (segResponse.isSuccessful) {
                        segResponse.body?.byteStream()?.use { input ->
                            input.copyTo(fos)
                        }
                        downloadedCount++
                        val progress = (downloadedCount * 100) / total
                        Handler(Looper.getMainLooper()).post {
                            listener?.onProgress(progress, downloadedCount, total)
                        }
                    } else {
                        System.err.println("Failed to download segment $index: ${segResponse.code}")
                    }
                }
                
                fos.close()
                
                Handler(Looper.getMainLooper()).post {
                    listener?.onComplete(outputFile.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    listener?.onError(e.message ?: "HLS Download failed")
                }
            }
        }
    }
}
