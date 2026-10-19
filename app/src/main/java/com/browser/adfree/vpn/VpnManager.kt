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

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.browser.adfree.storage.BrowserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.InetSocketAddress

enum class VpnMode { TOR, PROXY }

sealed interface VpnStatus {
    data object Off : VpnStatus
    data class Connecting(val mode: VpnMode, val message: String, val percent: Int? = null) : VpnStatus
    data class Connected(
        val mode: VpnMode,
        val exitIp: String,
        val exitCountry: String,
        val latencyMs: Int?
    ) : VpnStatus
    data class Failed(val mode: VpnMode, val reason: String) : VpnStatus
}

/**
 * Routes the whole browser through Tor or a verified public SOCKS5 relay.
 * Gecko traffic goes through the aifree extension's proxy.onRequest hook
 * ([onProxyChanged]); native HTTP follows [NetworkRoute].
 */
class VpnManager(
    context: Context,
    private val preferencesRepo: BrowserPreferencesRepository
) {
    var status by mutableStateOf<VpnStatus>(VpnStatus.Off)
        private set


    /** Last selected mode and countries, persisted for the next connect. */
    val lastMode: VpnMode get() = runCatching { VpnMode.valueOf(preferencesRepo.vpnMode) }.getOrDefault(VpnMode.TOR)
    val proxyCountry: String get() = preferencesRepo.vpnCountryCode
    val torExitCountry: String get() = preferencesRepo.vpnTorExitCountry

    /** host/port are blank/0 when disabled. */
    var onProxyChanged: ((enabled: Boolean, host: String, port: Int) -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tor = TorController(context)
    private var connectJob: Job? = null

    fun connectTor(exitCountry: String) {
        preferencesRepo.setVpnPreference(VpnMode.TOR.name, proxyCountry, exitCountry)
        cancelPending()
        clearRoute()
        status = VpnStatus.Connecting(VpnMode.TOR, "Starting Tor", 0)
        tor.start(exitCountry, object : TorController.Listener {
            override fun onProgress(percent: Int, summary: String) {
                if (status is VpnStatus.Connecting) {
                    status = VpnStatus.Connecting(VpnMode.TOR, summary, percent)
                }
            }

            override fun onReady(socksPort: Int) {
                connectJob = scope.launch {
                    status = VpnStatus.Connecting(VpnMode.TOR, "Checking exit address", 100)
                    val exit = ProxyDirectory.torExitInfo(LOOPBACK, socksPort)
                    if (exit == null) {
                        tor.stop()
                        status = VpnStatus.Failed(VpnMode.TOR, "Tor connected but the test request failed. Retry, or pick \"Any country\".")
                        return@launch
                    }
                    applyRoute(LOOPBACK, socksPort)
                    status = VpnStatus.Connected(VpnMode.TOR, exit.first, exit.second, null)
                }
            }

            override fun onError(message: String) {
                tor.stop()
                clearRoute()
                status = VpnStatus.Failed(VpnMode.TOR, message)
            }
        })
    }

    fun connectProxy(countryCode: String) {
        preferencesRepo.setVpnPreference(VpnMode.PROXY.name, countryCode, torExitCountry)
        cancelPending()
        tor.stop()
        clearRoute()
        status = VpnStatus.Connecting(VpnMode.PROXY, "Finding a relay")
        connectJob = scope.launch {
            val relay = ProxyDirectory.findWorking(countryCode) { step ->
                scope.launch { status = VpnStatus.Connecting(VpnMode.PROXY, step) }
            }
            if (relay == null) {
                status = VpnStatus.Failed(VpnMode.PROXY, "No working free relay in this country right now. Try another country or Tor.")
                return@launch
            }
            applyRoute(relay.host, relay.port)
            status = VpnStatus.Connected(VpnMode.PROXY, relay.exitIp, relay.exitCountry, relay.latencyMs)
        }
    }

    fun disconnect() {
        cancelPending()
        tor.stop()
        clearRoute()
        status = VpnStatus.Off
    }

    /** Called when Gecko reports the relay dropped mid-session (proxy.onError). */
    fun reportRouteError(message: String) {
        val current = status as? VpnStatus.Connected ?: return
        Log.w(TAG, "Proxy error while connected: $message")
        if (current.mode == VpnMode.PROXY) {
            // Fail closed: keep routing to the dead relay rather than leaking, but tell the user.
            status = VpnStatus.Failed(VpnMode.PROXY, "The relay stopped responding. Pages will not load until you reconnect or disconnect.")
        }
    }

    /** Re-sends the active route; used when the extension (re)connects. */
    fun resendRoute() {
        val proxy = NetworkRoute.socks
        onProxyChanged?.invoke(proxy != null, proxy?.hostString ?: "", proxy?.port ?: 0)
    }

    fun shutdown() {
        disconnect()
        scope.cancel()
    }

    private fun cancelPending() {
        connectJob?.cancel()
        connectJob = null
    }

    private fun applyRoute(host: String, port: Int) {
        NetworkRoute.socks = InetSocketAddress(host, port)
        onProxyChanged?.invoke(true, host, port)
    }

    private fun clearRoute() {
        val wasRouted = NetworkRoute.socks != null
        NetworkRoute.socks = null
        if (wasRouted || status !is VpnStatus.Off) onProxyChanged?.invoke(false, "", 0)
    }

    companion object {
        private const val TAG = "VpnManager"
        private const val LOOPBACK = "127.0.0.1"
    }
}
