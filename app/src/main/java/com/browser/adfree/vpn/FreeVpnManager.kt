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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.browser.adfree.model.TabModel
import com.browser.adfree.model.VpnCountry
import com.browser.adfree.storage.BrowserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime

class FreeVpnManager(
    private val context: Context,
    private val preferencesRepo: BrowserPreferencesRepository
) {
    var isEnabled by mutableStateOf(preferencesRepo.vpnEnabled)
        private set

    var selectedCountry by mutableStateOf(VpnCountry.findByCode(preferencesRepo.vpnCountryCode))
        private set

    var statusText by mutableStateOf(if (preferencesRepo.vpnEnabled) "Secured & Connected" else "Disconnected")
        private set

    var statusIcon by mutableStateOf(if (preferencesRepo.vpnEnabled) "🟢" else "🔴")
        private set

    val livePings = mutableStateMapOf<String, Int>()

    var onConfigChanged: ((enabled: Boolean, host: String, port: Int, type: String) -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        // Initialize default pings and trigger background live probe
        VpnCountry.COUNTRIES.forEach { country ->
            livePings[country.code] = country.estimatedPingMs
        }
        refreshPings()
    }

    fun refreshPings() {
        scope.launch {
            val results = VpnPingManager.probeAllCountries(VpnCountry.COUNTRIES)
            results.forEach { (code, ping) ->
                livePings[code] = ping
            }
        }
    }

    fun getPingForCountry(code: String): Int {
        return livePings[code] ?: selectedCountry.estimatedPingMs
    }

    fun toggleVpn(runtime: GeckoRuntime?, tabs: List<TabModel>) {
        if (isEnabled) {
            disconnect(runtime, tabs)
        } else {
            verifyAndConnect(selectedCountry, runtime, tabs)
        }
    }

    fun verifyAndConnect(country: VpnCountry, runtime: GeckoRuntime?, tabs: List<TabModel>) {
        selectedCountry = country
        statusText = "Verifying…"
        statusIcon = "🟡"
        scope.launch {
            val reachable = VpnPingManager.testConnectivity(country.host, country.port)
            if (reachable) {
                isEnabled = true
                statusText = "Secured (${country.city}) ✓"
                statusIcon = "🟢"
                preferencesRepo.setVpnPreference(enabled = true, countryCode = country.code)
                notifyConfigChange(enabled = true, country = country, tabs = tabs)
                refreshPings()
            } else {
                // Try to find a live fallback proxy before giving up
                VpnPingManager.discoverLiveProxies(VpnCountry.COUNTRIES)
                val fallbackReachable = VpnPingManager.testConnectivity(country.host, country.port)
                if (fallbackReachable) {
                    isEnabled = true
                    statusText = "Secured (${country.city}) ✓"
                    statusIcon = "🟢"
                    preferencesRepo.setVpnPreference(enabled = true, countryCode = country.code)
                    notifyConfigChange(enabled = true, country = country, tabs = tabs)
                    refreshPings()
                } else {
                    // Server truly unreachable — do NOT change proxy state
                    isEnabled = false
                    statusText = "Server offline — try another"
                    statusIcon = "🔴"
                }
            }
        }
    }

    fun connect(country: VpnCountry, runtime: GeckoRuntime?, tabs: List<TabModel>) {
        verifyAndConnect(country, runtime, tabs)
    }

    fun disconnect(runtime: GeckoRuntime?, tabs: List<TabModel>) {
        isEnabled = false
        statusText = "Disconnected"
        statusIcon = "🔴"
        preferencesRepo.setVpnPreference(enabled = false, countryCode = selectedCountry.code)
        notifyConfigChange(enabled = false, country = selectedCountry, tabs = tabs)
    }

    fun selectCountry(country: VpnCountry, runtime: GeckoRuntime?, tabs: List<TabModel>) {
        selectedCountry = country
        preferencesRepo.setVpnPreference(enabled = isEnabled, countryCode = country.code)
        if (isEnabled) {
            verifyAndConnect(country, runtime, tabs)
        }
    }

    private fun notifyConfigChange(enabled: Boolean, country: VpnCountry, tabs: List<TabModel>) {
        onConfigChanged?.invoke(enabled, if (enabled) country.host else "", if (enabled) country.port else 0, country.type)

        val payload = JSONObject().apply {
            put("action", "set_vpn_config")
            put("enabled", enabled)
            put("host", if (enabled) country.host else "")
            put("port", if (enabled) country.port else 0)
            put("type", country.type)
        }

        for (tab in tabs) {
            try {
                val js = "if (typeof browser !== 'undefined' && browser.runtime && browser.runtime.sendMessage) { browser.runtime.sendMessage($payload).catch(()=>{}); }"
                tab.session.loadUri("javascript:(function(){$js})();")
            } catch (e: Exception) {}
        }
    }

    /**
     * Mandatory cleanup to ensure zero persistent proxy leaks on app background / exit.
     */
    fun forceDisconnectOnExit(tabs: List<TabModel>) {
        isEnabled = false
        statusText = "Disconnected"
        preferencesRepo.setVpnPreference(enabled = false, countryCode = selectedCountry.code)
        notifyConfigChange(enabled = false, country = selectedCountry, tabs = tabs)
    }
}
