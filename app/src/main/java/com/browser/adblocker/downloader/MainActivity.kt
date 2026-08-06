package com.browser.adblocker.downloader

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.browser.adblocker.downloader.detection.VideoStreamInfo
import com.browser.adblocker.downloader.download.M3u8Parser
import com.browser.adblocker.downloader.download.M3u8Stream
import com.browser.adblocker.downloader.download.VideoDownloader
import com.browser.adblocker.downloader.ui.BrowserScreen
import com.browser.adblocker.downloader.ui.components.DownloadBottomSheet
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private lateinit var runtime: GeckoRuntime
    private lateinit var session: GeckoSession

    private val urlInput = mutableStateOf("https://google.com")
    private val adBlockCount = mutableIntStateOf(0)
    private val detectedVideos = mutableStateListOf<VideoStreamInfo>()
    private val hlsStreams = mutableStateListOf<M3u8Stream>()
    private val loadingProgress = mutableIntStateOf(0)
    
    private val canGoBack = mutableStateOf(false)
    private val canGoForward = mutableStateOf(false)
    
    private val showBottomSheet = mutableStateOf(false)
    private val isDownloading = mutableStateOf(false)
    private val downloadProgress = mutableIntStateOf(0)
    private val downloadSegmentText = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize GeckoView Runtime and Session
        runtime = GeckoRuntime.create(this)
        session = GeckoSession()

        // 2. Load AdBlocker WebExtension from Assets
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/adblock-extension/",
            "adblock-downloader@example.com"
        ).accept(
            { extension ->
                if (extension != null) {
                    Log.i("GeckoView", "Successfully loaded WebExtension")
                    
                    // Register native messaging port
                    extension.setMessageDelegate(object : WebExtension.MessageDelegate {
                        override fun onMessage(
                            nativeApp: String,
                            message: Any,
                            sender: WebExtension.MessageSender
                        ): GeckoResult<Any>? {
                            if (nativeApp == "browser-app") {
                                handleExtensionMessage(message)
                            }
                            return null
                        }
                    }, "browser-app")
                }
            },
            { error ->
                Log.e("GeckoView", "Failed to register WebExtension", error)
            }
        )

        // 3. Configure Progress and Navigation Delegates
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                runOnUiThread {
                    detectedVideos.clear()
                    hlsStreams.clear()
                    adBlockCount.intValue = 0
                    urlInput.value = url
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                runOnUiThread {
                    loadingProgress.intValue = progress
                }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {}

            override fun onSecurityChange(
                session: GeckoSession,
                securityInfo: GeckoSession.ProgressDelegate.SecurityInformation
            ) {}
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                runOnUiThread {
                    this@MainActivity.canGoBack.value = canGoBack
                }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                runOnUiThread {
                    this@MainActivity.canGoForward.value = canGoForward
                }
            }
        }

        // 4. Open and Start Session
        session.open(runtime)
        session.loadUri(urlInput.value)

        // 5. Build Compose Interface
        setContent {
            var urlText by remember { urlInput }
            val blockedCount by remember { adBlockCount }
            val videosCount = detectedVideos.size
            val progress by remember { loadingProgress }
            val backEnabled by remember { canGoBack }
            val forwardEnabled by remember { canGoForward }
            
            var openSheet by remember { showBottomSheet }
            val downloading by remember { isDownloading }
            val currentProgress by remember { downloadProgress }
            val segmentText by remember { downloadSegmentText }

            BrowserScreen(
                session = session,
                urlInput = urlText,
                onUrlInputChanged = { urlText = it },
                onSearch = {
                    var target = urlText.trim()
                    if (target.isNotEmpty()) {
                        if (!target.startsWith("http://") && !target.startsWith("https://")) {
                            target = "https://www.google.com/search?q=" + target.replace(" ", "+")
                        }
                        session.loadUri(target)
                    }
                },
                adBlockCount = blockedCount,
                detectedVideosCount = videosCount,
                loadingProgress = progress,
                canGoBack = backEnabled,
                canGoForward = forwardEnabled,
                onBackClick = { if (backEnabled) session.goBack() },
                onForwardClick = { if (forwardEnabled) session.goForward() },
                onHomeClick = { session.loadUri("https://google.com") },
                onDownloadClick = { handleDownloadFabClick() }
            )

            if (openSheet) {
                DownloadBottomSheet(
                    videoTitle = detectedVideos.firstOrNull()?.title ?: "Web Video",
                    detectedStreams = detectedVideos,
                    hlsStreams = hlsStreams,
                    isDownloading = downloading,
                    downloadProgress = currentProgress,
                    downloadSegmentText = segmentText,
                    onDismissRequest = {
                        if (!downloading) openSheet = false
                    },
                    onDownloadSelected = { url, quality ->
                        triggerDownload(url, quality)
                    }
                )
            }
        }
    }

    private fun handleExtensionMessage(message: Any) {
        try {
            val json = JSONObject(message.toString())
            val action = json.optString("action")
            
            if (action == "blocked_ad") {
                val count = json.optInt("count", 0)
                runOnUiThread {
                    adBlockCount.intValue = count
                }
            } else if (action == "video_detected") {
                val url = json.optString("url")
                val title = json.optString("title", "Video Stream")
                val format = json.optString("format", "MP4")

                val video = VideoStreamInfo(url = url, title = title, format = format)
                runOnUiThread {
                    if (detectedVideos.none { it.url == url }) {
                        detectedVideos.add(video)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleDownloadFabClick() {
        val firstVideo = detectedVideos.firstOrNull() ?: return
        if (firstVideo.url.contains(".m3u8")) {
            // Parse multi-resolution playlist in background thread
            thread {
                val parsed = M3u8Parser.parse(
                    firstVideo.url,
                    userAgent = "Mozilla/5.0 (Android; Mobile; rv:122.0) Gecko/122.0 Firefox/122.0"
                )
                runOnUiThread {
                    hlsStreams.clear()
                    hlsStreams.addAll(parsed)
                    showBottomSheet.value = true
                }
            }
        } else {
            hlsStreams.clear()
            showBottomSheet.value = true
        }
    }

    private fun triggerDownload(url: String, quality: String) {
        val firstVideo = detectedVideos.firstOrNull()
        val title = firstVideo?.title ?: "Video Stream"
        val userAgent = "Mozilla/5.0 (Android; Mobile; rv:122.0) Gecko/122.0 Firefox/122.0"
        
        if (url.contains(".m3u8")) {
            isDownloading.value = true
            downloadProgress.intValue = 0
            downloadSegmentText.value = "Connecting..."
            
            VideoDownloader.downloadVideo(
                context = this,
                url = url,
                title = title,
                userAgent = userAgent,
                cookie = "",
                listener = object : VideoDownloader.DownloadProgressListener {
                    override fun onProgress(percent: Int, currentSegment: Int, totalSegments: Int) {
                        runOnUiThread {
                            downloadProgress.intValue = percent
                            downloadSegmentText.value = "segment $currentSegment/$totalSegments"
                        }
                    }

                    override fun onComplete(filePath: String) {
                        runOnUiThread {
                            isDownloading.value = false
                            showBottomSheet.value = false
                            Toast.makeText(
                                this@MainActivity,
                                "Video saved successfully to:\n$filePath",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    override fun onError(error: String) {
                        runOnUiThread {
                            isDownloading.value = false
                            Toast.makeText(
                                this@MainActivity,
                                "Download failed: $error",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )
        } else {
            // Standard background direct download
            VideoDownloader.downloadVideo(
                context = this,
                url = url,
                title = "$title ($quality)",
                userAgent = userAgent,
                cookie = ""
            )
            showBottomSheet.value = false
        }
    }

    override fun onDestroy() {
        session.close()
        runtime.shutdown()
        super.onDestroy()
    }
}
