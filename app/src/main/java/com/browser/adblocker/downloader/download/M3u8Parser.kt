package com.browser.adblocker.downloader.download

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

data class M3u8Stream(val url: String, val resolution: String)

object M3u8Parser {
    private val client = OkHttpClient()

    fun parse(masterUrl: String, userAgent: String = "", cookie: String = ""): List<M3u8Stream> {
        val streams = mutableListOf<M3u8Stream>()
        val requestBuilder = Request.Builder().url(masterUrl)
        if (userAgent.isNotEmpty()) {
            requestBuilder.addHeader("User-Agent", userAgent)
        }
        if (cookie.isNotEmpty()) {
            requestBuilder.addHeader("Cookie", cookie)
        }

        try {
            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) return emptyList()
            
            val content = response.body?.string() ?: return emptyList()
            val lines = content.split("\n").map { it.trim() }
            
            var currentResolution = ""
            
            for (line in lines) {
                if (line.startsWith("#EXT-X-STREAM-INF:")) {
                    // Extract resolution
                    val resolutionRegex = Regex("RESOLUTION=(\\d+x\\d+)")
                    val match = resolutionRegex.find(line)
                    if (match != null) {
                        currentResolution = match.groupValues[1]
                    }
                } else if (line.isNotEmpty() && !line.startsWith("#")) {
                    // Resolve URL to absolute
                    val absoluteUrl = resolveUrl(masterUrl, line)
                    val label = if (currentResolution.isNotEmpty()) currentResolution else "Original Quality"
                    streams.add(M3u8Stream(absoluteUrl, label))
                    currentResolution = "" // reset
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return streams
    }

    fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        return try {
            val baseUri = URI(baseUrl)
            baseUri.resolve(relativeUrl).toString()
        } catch (e: Exception) {
            relativeUrl
        }
    }
}
