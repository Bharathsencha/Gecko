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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Finds a working public SOCKS5 relay for a country at connect time.
 * Public lists churn by the hour, so nothing is hardcoded: candidates are
 * fetched fresh and each one is proven by making a real request through it and
 * checking the exit address reported back.
 */
object ProxyDirectory {

    data class VerifiedProxy(
        val host: String,
        val port: Int,
        val exitIp: String,
        val exitCountry: String,
        val latencyMs: Int
    )

    private val directClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val cache = mutableMapOf<String, Pair<VerifiedProxy, Long>>()
    private const val CACHE_TTL_MS = 10 * 60 * 1000L
    private const val MAX_CANDIDATES = 60
    private const val PARALLEL_CHECKS = 16
    private const val ENOUGH_RELAYS = 4
    private const val GRACE_MS = 4_000L
    private const val SEARCH_TIMEOUT_MS = 45_000L

    suspend fun findWorking(countryCode: String, onProgress: (String) -> Unit): VerifiedProxy? {
        val code = countryCode.uppercase()
        cache[code]?.let { (proxy, at) ->
            if (System.currentTimeMillis() - at < CACHE_TTL_MS) {
                onProgress("Re-checking last relay")
                if (verify(proxy.host, proxy.port, code) != null) return proxy
                cache.remove(code)
            }
        }

        onProgress("Fetching relay list")
        val candidates = fetchCandidates(code)
        if (candidates.isEmpty()) return null

        onProgress("Testing ${candidates.size} relays")
        val found = withTimeoutOrNull(SEARCH_TIMEOUT_MS) { fastestVerified(candidates, code) }
        found?.let { cache[code] = it to System.currentTimeMillis() }
        return found
    }

    private suspend fun fetchCandidates(code: String): List<Pair<String, Int>> = withContext(Dispatchers.IO) {
        val sources = listOf(
            "https://api.proxyscrape.com/v4/free-proxy-list/get?request=display_proxies&protocol=socks5&country=${code.lowercase()}&proxy_format=ipport&format=text&timeout=6000",
            "https://raw.githubusercontent.com/proxifly/free-proxy-list/main/proxies/countries/$code/data.txt"
        )
        val seen = linkedSetOf<Pair<String, Int>>()
        for (url in sources) {
            val body = try {
                directClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
                }
            } catch (e: Exception) {
                ""
            }
            body.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { line ->
                    // proxifly lists every protocol with a scheme prefix; keep SOCKS5 only.
                    val bare = when {
                        line.startsWith("socks5://") -> line.removePrefix("socks5://")
                        line.contains("://") -> return@mapNotNull null
                        else -> line
                    }
                    val host = bare.substringBeforeLast(":")
                    val port = bare.substringAfterLast(":").toIntOrNull()
                    if (host.isNotBlank() && port != null && port in 1..65535) host to port else null
                }
                .forEach { seen.add(it) }
            if (seen.size >= MAX_CANDIDATES) break
        }
        seen.take(MAX_CANDIDATES)
    }

    /**
     * Tests candidates in parallel and returns the lowest-latency relay among
     * the first few that pass, giving stragglers a short grace period after
     * the first success. Taking the very first one often meant a slow relay.
     */
    private suspend fun fastestVerified(candidates: List<Pair<String, Int>>, code: String): VerifiedProxy? = coroutineScope {
        val found = java.util.concurrent.CopyOnWriteArrayList<VerifiedProxy>()
        val firstFound = CompletableDeferred<Unit>()
        val gate = Semaphore(PARALLEL_CHECKS)
        val jobs = candidates.map { (host, port) ->
            launch {
                gate.withPermit {
                    if (found.size >= ENOUGH_RELAYS) return@withPermit
                    verify(host, port, code)?.let {
                        found.add(it)
                        firstFound.complete(Unit)
                    }
                }
            }
        }
        val allDone = launch { jobs.forEach { it.join() } }
        // Wait for the first success (or for every check to finish).
        kotlinx.coroutines.selects.select<Unit> {
            firstFound.onAwait { }
            allDone.onJoin { }
        }
        if (found.isNotEmpty()) {
            withTimeoutOrNull(GRACE_MS) {
                while (found.size < ENOUGH_RELAYS && !allDone.isCompleted) kotlinx.coroutines.delay(100)
            }
        }
        jobs.forEach { it.cancel() }
        allDone.cancel()
        found.minByOrNull { it.latencyMs }
    }

    /** Returns the relay only if traffic really exits through it in [code]. */
    suspend fun verify(host: String, port: Int, code: String): VerifiedProxy? = withContext(Dispatchers.IO) {
        val client = directClient.newBuilder()
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(host, port)))
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(9, TimeUnit.SECONDS)
            .build()
        val started = System.currentTimeMillis()
        try {
            client.newCall(Request.Builder().url("https://ipinfo.io/json").build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val json = JSONObject(resp.body?.string().orEmpty())
                val ip = json.optString("ip")
                val country = json.optString("country")
                if (ip.isBlank() || !country.equals(code, ignoreCase = true)) return@withContext null
                VerifiedProxy(host, port, ip, country.uppercase(), (System.currentTimeMillis() - started).toInt())
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Exit address through Tor. ipinfo.io often refuses Tor exits, so the IP comes
     * from the Tor Project's own check service and only the country lookup of
     * that public exit IP goes direct (it reveals nothing about the user).
     */
    suspend fun torExitInfo(host: String, port: Int): Pair<String, String>? = withContext(Dispatchers.IO) {
        val viaTor = directClient.newBuilder()
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(host, port)))
            .callTimeout(40, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .build()
        repeat(3) { attempt ->
            try {
                val ip = viaTor.newCall(Request.Builder().url("https://check.torproject.org/api/ip").build()).execute().use { resp ->
                    if (!resp.isSuccessful) null else JSONObject(resp.body?.string().orEmpty()).optString("IP").ifBlank { null }
                }
                if (ip != null) {
                    val country = try {
                        directClient.newCall(Request.Builder().url("https://ipinfo.io/$ip/country").build()).execute().use {
                            if (it.isSuccessful) it.body?.string().orEmpty().trim().uppercase() else ""
                        }
                    } catch (e: Exception) {
                        ""
                    }
                    return@withContext ip to country
                }
            } catch (e: Exception) {
                if (attempt == 2) return@withContext null
            }
        }
        null
    }

    /** Exit address seen by the outside world through an arbitrary SOCKS port. */
    suspend fun exitInfo(host: String, port: Int): Pair<String, String>? = withContext(Dispatchers.IO) {
        val client = directClient.newBuilder()
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(host, port)))
            .callTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        try {
            client.newCall(Request.Builder().url("https://ipinfo.io/json").build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val json = JSONObject(resp.body?.string().orEmpty())
                json.optString("ip") to json.optString("country").uppercase()
            }
        } catch (e: Exception) {
            null
        }
    }
}
