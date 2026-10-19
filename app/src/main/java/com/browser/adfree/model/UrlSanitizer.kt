/*
 * Copyright (C) 2024-2026 Gibbly Gobbly <gibblygobbly@proton.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.browser.adfree.model

import java.net.URI

/** Removes AMP wrappers and tracking parameters. Plain string work, so it is unit-tested. */
object UrlSanitizer {

    private val GLOBAL_TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "utm_id", "utm_cid", "utm_reader", "utm_name", "utm_referrer",
        "fbclid", "gclid", "gbraid", "wbraid", "igshid", "twclid",
        "msclkid", "mc_eid", "mc_cid", "_hsenc", "_hsmi",
        "ref_src", "ref_url", "yclid", "_ga", "_openstat"
    )

    /**
     * De-AMP: Detects Google AMP and AMPProject URLs and extracts the canonical direct URL.
     */
    fun deAmp(url: String): String {
        try {
            val uri = URI(url)
            val host = uri.host?.lowercase() ?: return url
            val path = uri.path ?: return url
            val querySuffix = if (!uri.rawQuery.isNullOrEmpty()) "?${uri.rawQuery}" else ""

            // Case 1: https://www.google.com/amp/s/publisher.com/path
            if (host.contains("google.") && path.startsWith("/amp/")) {
                val cleanPath = path.removePrefix("/amp/")
                if (cleanPath.startsWith("s/")) {
                    val actualTarget = cleanPath.removePrefix("s/")
                    val firstSegment = actualTarget.substringBefore('/')
                    if (firstSegment.contains('.')) {
                        return "https://$actualTarget$querySuffix"
                    }
                } else {
                    val firstSegment = cleanPath.substringBefore('/')
                    if (firstSegment.contains('.')) {
                        return "http://$cleanPath$querySuffix"
                    }
                }
            }

            // Case 2: https://publisher-com.cdn.ampproject.org/c/s/publisher.com/path
            if (host.endsWith(".ampproject.org")) {
                val segments = path.split('/').filter { it.isNotEmpty() }
                val cIndex = segments.indexOf("c")
                if (cIndex != -1 && cIndex < segments.size - 1) {
                    val isHttps = segments.getOrNull(cIndex + 1) == "s"
                    val pubIndex = if (isHttps) cIndex + 2 else cIndex + 1
                    if (pubIndex < segments.size) {
                        val subPath = segments.subList(pubIndex, segments.size).joinToString("/")
                        val scheme = if (isHttps) "https://" else "http://"
                        return "$scheme$subPath$querySuffix"
                    }
                }
            }
        } catch (e: Exception) {}
        return url
    }

    /**
     * Strips tracking query parameters. The parameters that stay are kept
     * byte for byte, so nothing the site needs gets re-encoded.
     */
    fun stripTrackingParams(url: String): String {
        val q = url.indexOf('?')
        if (q < 0) return url
        val hashAt = url.indexOf('#', q).let { if (it < 0) url.length else it }
        val base = url.substring(0, q)
        val query = url.substring(q + 1, hashAt)
        val fragment = url.substring(hashAt)
        if (query.isEmpty()) return url

        val host = runCatching { URI(base).host?.lowercase() }.getOrNull().orEmpty()
        val isYouTube = host.contains("youtube.com") || host.contains("youtu.be")
        val targetParams = if (isYouTube) GLOBAL_TRACKING_PARAMS + "si" else GLOBAL_TRACKING_PARAMS

        val parts = query.split('&')
        val kept = parts.filter { it.isNotEmpty() && it.substringBefore('=').lowercase() !in targetParams }
        if (kept.size == parts.size) return url
        return base + (if (kept.isEmpty()) "" else "?" + kept.joinToString("&")) + fragment
    }

    /**
     * Fully sanitizes a URL: De-AMPs + strips telemetry query parameters.
     */
    fun cleanUrl(url: String): String {
        val deAmped = deAmp(url)
        return stripTrackingParams(deAmped)
    }
}
