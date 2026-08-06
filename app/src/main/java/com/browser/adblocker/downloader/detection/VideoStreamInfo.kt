package com.browser.adblocker.downloader.detection

data class VideoStreamInfo(
    val url: String,
    val title: String,
    val format: String,
    val resolution: String = "Original Quality"
)
