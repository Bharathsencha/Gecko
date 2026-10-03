/*
 * Copyright (C) 2024-2026 Gibbly Gobbly <gibblygobbly@proton.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.browser.adfree.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.browser.adfree.MainActivity
import java.util.Locale

class DownloadForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        // Immediately promote to foreground in onCreate to satisfy Android 14 requirements
        val initialNotification = buildNotification("Downloading media", -1, 0L, -1L, 1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopForegroundService()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Downloading media"
        val progress = intent?.getIntExtra(EXTRA_PROGRESS, -1) ?: -1
        val downloadedBytes = intent?.getLongExtra(EXTRA_DOWNLOADED_BYTES, 0L) ?: 0L
        val totalBytes = intent?.getLongExtra(EXTRA_TOTAL_BYTES, -1L) ?: -1L
        val activeCount = intent?.getIntExtra(EXTRA_ACTIVE_COUNT, 1) ?: 1

        val notification = buildNotification(title, progress, downloadedBytes, totalBytes, activeCount)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_NOT_STICKY
    }

    private fun buildNotification(
        title: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        activeCount: Int
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sizeInfo = if (totalBytes > 0) {
            "${VideoQualityParser.formatFileSize(downloadedBytes)} / ${VideoQualityParser.formatFileSize(totalBytes)}"
        } else if (downloadedBytes > 0) {
            VideoQualityParser.formatFileSize(downloadedBytes)
        } else ""

        val contentText = if (activeCount > 1) {
            "$activeCount active downloads • ${if (progress >= 0) "$progress%" else sizeInfo}"
        } else {
            if (progress >= 0 && sizeInfo.isNotBlank()) "$progress% • $sizeInfo" else if (progress >= 0) "$progress%" else sizeInfo.ifBlank { "Downloading..." }
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (activeCount > 1) "Downloading Media" else title)
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (progress in 0..100) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AdFreeBrowser::DownloadWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(2 * 60 * 60 * 1000L) // 2 hours max safety timeout
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (e: Exception) {}
    }

    private fun stopForegroundService() {
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Downloads"
            val descriptionText = "Shows active background download progress"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "browser_downloads_channel"
        const val NOTIFICATION_ID = 4001

        const val ACTION_START_OR_UPDATE = "com.browser.adfree.download.ACTION_START_OR_UPDATE"
        const val ACTION_STOP = "com.browser.adfree.download.ACTION_STOP"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_DOWNLOADED_BYTES = "extra_downloaded_bytes"
        const val EXTRA_TOTAL_BYTES = "extra_total_bytes"
        const val EXTRA_ACTIVE_COUNT = "extra_active_count"

        fun updateProgress(
            context: Context,
            title: String,
            progress: Int,
            downloadedBytes: Long = 0L,
            totalBytes: Long = -1L,
            activeCount: Int = 1
        ) {
            try {
                val intent = Intent(context, DownloadForegroundService::class.java).apply {
                    action = ACTION_START_OR_UPDATE
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_PROGRESS, progress)
                    putExtra(EXTRA_DOWNLOADED_BYTES, downloadedBytes)
                    putExtra(EXTRA_TOTAL_BYTES, totalBytes)
                    putExtra(EXTRA_ACTIVE_COUNT, activeCount)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {}
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, DownloadForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w("DownloadService", "Failed to stop foreground service: ${e.message}")
            }
        }
    }
}
