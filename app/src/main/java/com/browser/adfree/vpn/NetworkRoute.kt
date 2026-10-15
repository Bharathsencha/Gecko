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

package com.browser.adfree.vpn

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * Process-wide route for native HTTP (downloads, extractors). Set by
 * [VpnManager] so app-side requests leave through the same relay as the pages.
 */
object NetworkRoute : ProxySelector() {

    @Volatile
    var socks: InetSocketAddress? = null
        internal set

    /** "socks5h://host:port" for yt-dlp (remote DNS), null when direct. */
    val ytDlpProxy: String?
        get() = socks?.let { "socks5h://${it.hostString}:${it.port}" }

    override fun select(uri: URI?): List<Proxy> {
        val target = socks ?: return listOf(Proxy.NO_PROXY)
        val host = uri?.host
        if (host == "localhost" || host == "127.0.0.1") return listOf(Proxy.NO_PROXY)
        return listOf(Proxy(Proxy.Type.SOCKS, target))
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        // Fail closed: no fallback to a direct connection.
    }
}
