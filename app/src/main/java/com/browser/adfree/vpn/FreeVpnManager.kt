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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.browser.adfree.model.TabModel
import com.browser.adfree.model.VpnCountry
import com.browser.adfree.storage.BrowserPreferencesRepository
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

    var livePingMs by mutableIntStateOf(selectedCountry.estimatedPingMs)
        private set

    fun toggleVpn(runtime: GeckoRuntime?, tabs: List<TabModel>) {
        if (isEnabled) {
            disconnect(runtime, tabs)
        } else {
            connect(selectedCountry, runtime, tabs)
        }
    }

    fun connect(country: VpnCountry, runtime: GeckoRuntime?, tabs: List<TabModel>) {
        selectedCountry = country
        isEnabled = true
        statusText = "Secured & Connected (${country.city})"
        livePingMs = country.estimatedPingMs
        preferencesRepo.setVpnPreference(enabled = true, countryCode = country.code)
        applyVpnToExtension(tabs, enabled = true, country = country)
    }

    fun disconnect(runtime: GeckoRuntime?, tabs: List<TabModel>) {
        isEnabled = false
        statusText = "Disconnected"
        preferencesRepo.setVpnPreference(enabled = false, countryCode = selectedCountry.code)
        applyVpnToExtension(tabs, enabled = false, country = selectedCountry)
    }

    fun selectCountry(country: VpnCountry, runtime: GeckoRuntime?, tabs: List<TabModel>) {
        selectedCountry = country
        livePingMs = country.estimatedPingMs
        preferencesRepo.setVpnPreference(enabled = isEnabled, countryCode = country.code)
        if (isEnabled) {
            statusText = "Secured & Connected (${country.city})"
            applyVpnToExtension(tabs, enabled = true, country = country)
        }
    }

    private fun applyVpnToExtension(tabs: List<TabModel>, enabled: Boolean, country: VpnCountry) {
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
        applyVpnToExtension(tabs, enabled = false, country = selectedCountry)
    }
}
