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

import java.net.URLEncoder

/** Turns what was typed in the address bar into a URL or a search. */
object UrlInput {
    private const val HOME = "about:home"

    fun resolve(input: String, searchEngine: SearchEngine): String {
        var trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed == HOME) return HOME

        // If markdown link format was pasted e.g. [coveryourtracks.eff.org](https://coveryourtracks.eff.org/)
        val mdMatch = Regex("\\[.*?\\]\\((https?://.*?)\\)").find(trimmed)
        if (mdMatch != null) {
            trimmed = mdMatch.groupValues[1]
        }

        if (trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true)) {
            return UrlSanitizer.cleanUrl(trimmed)
        }

        if (trimmed.startsWith("content://", ignoreCase = true) ||
            trimmed.startsWith("about:", ignoreCase = true) ||
            trimmed.startsWith("javascript:", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true)) {
            return trimmed
        }

        // A bare address: example.com, example.com:8080/path, 192.168.1.1, localhost:3000
        if (!trimmed.contains(' ') && looksLikeHost(trimmed.substringBefore('/').substringBefore('?'))) {
            val scheme = if (isLocal(trimmed.substringBefore('/'))) "http" else "https"
            return UrlSanitizer.cleanUrl("$scheme://$trimmed")
        }

        return try {
            searchEngine.searchUrl + URLEncoder.encode(trimmed, "UTF-8")
        } catch (e: Exception) {
            searchEngine.searchUrl + trimmed.replace(" ", "+")
        }
    }

    private val IPV4 = Regex("^(\\d{1,3})(\\.\\d{1,3}){3}$")

    private fun looksLikeHost(hostPort: String): Boolean {
        val host = hostPort.substringBefore(':').lowercase()
        val port = hostPort.substringAfter(':', "")
        if (port.isNotEmpty() && (port.toIntOrNull() ?: -1) !in 1..65535) return false
        if (host == "localhost" || IPV4.matches(host)) return true
        if (!host.contains('.') || host.startsWith('.') || host.endsWith('.')) return false
        val tld = host.substringAfterLast('.')
        return tld.length in 2..24 && tld.all { it.isLetter() } &&
            host.split('.').all { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c == '-' } }
    }

    /** Local addresses rarely have certificates; open them over http. */
    private fun isLocal(hostPort: String): Boolean {
        val host = hostPort.substringBefore(':')
        return host == "localhost" || host.startsWith("127.") || host.startsWith("10.") ||
            host.startsWith("192.168.") || Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(host)
    }
}
