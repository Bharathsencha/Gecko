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

package com.browser.adfree.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.browser.adfree.model.CookiePolicy
import com.browser.adfree.model.SearchEngine
import com.browser.adfree.model.StartupBehavior

class BrowserPreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("browser_preferences", Context.MODE_PRIVATE)

    var searchEngine by mutableStateOf(
        try {
            SearchEngine.valueOf(prefs.getString("search_engine", SearchEngine.GOOGLE.name) ?: SearchEngine.GOOGLE.name)
        } catch (e: Exception) {
            SearchEngine.GOOGLE
        }
    )
        private set

    var startupBehavior by mutableStateOf(
        try {
            StartupBehavior.valueOf(prefs.getString("startup_behavior", StartupBehavior.NEW_TAB.name) ?: StartupBehavior.NEW_TAB.name)
        } catch (e: Exception) {
            StartupBehavior.NEW_TAB
        }
    )
        private set

    var adBlockingEnabled by mutableStateOf(prefs.getBoolean("ad_blocking", true))
        private set

    var cosmeticBlockingEnabled by mutableStateOf(prefs.getBoolean("cosmetic_blocking", true))
        private set

    var darkWebDefault by mutableStateOf(prefs.getBoolean("dark_web_default", false))
        private set

    var wipeOnExit by mutableStateOf(prefs.getBoolean("wipe_on_exit", false))
        private set

    var savePasswordsEnabled by mutableStateOf(prefs.getBoolean("save_passwords", true))
        private set

    var autofillEnabled by mutableStateOf(prefs.getBoolean("autofill_enabled", true))
        private set

    var cookiePolicy by mutableStateOf(
        try {
            CookiePolicy.valueOf(prefs.getString("cookie_policy", CookiePolicy.ACCEPT_NON_TRACKERS.name) ?: CookiePolicy.ACCEPT_NON_TRACKERS.name)
        } catch (e: Exception) {
            CookiePolicy.ACCEPT_NON_TRACKERS
        }
    )
        private set

    var blockAiOverviews by mutableStateOf(prefs.getBoolean("block_ai_overviews", true))
        private set

    var httpsOnlyMode by mutableStateOf(prefs.getBoolean("https_only_mode", true))
        private set

    var vpnEnabled by mutableStateOf(prefs.getBoolean("vpn_enabled", false))
        private set

    var vpnCountryCode by mutableStateOf(prefs.getString("vpn_country_code", "US") ?: "US")
        private set

    fun setSearchEnginePreference(engine: SearchEngine) {
        searchEngine = engine
        prefs.edit().putString("search_engine", engine.name).apply()
    }

    fun setStartupBehaviorPreference(behavior: StartupBehavior) {
        startupBehavior = behavior
        prefs.edit().putString("startup_behavior", behavior.name).apply()
    }

    fun setAdBlockingPreference(enabled: Boolean) {
        adBlockingEnabled = enabled
        prefs.edit().putBoolean("ad_blocking", enabled).apply()
    }

    fun setCosmeticBlockingPreference(enabled: Boolean) {
        cosmeticBlockingEnabled = enabled
        prefs.edit().putBoolean("cosmetic_blocking", enabled).apply()
    }

    fun setDarkWebDefaultPreference(enabled: Boolean) {
        darkWebDefault = enabled
        prefs.edit().putBoolean("dark_web_default", enabled).apply()
    }

    fun setWipeOnExitPreference(enabled: Boolean) {
        wipeOnExit = enabled
        prefs.edit().putBoolean("wipe_on_exit", enabled).apply()
    }

    fun setSavePasswordsPreference(enabled: Boolean) {
        savePasswordsEnabled = enabled
        prefs.edit().putBoolean("save_passwords", enabled).apply()
    }

    fun setAutofillPreference(enabled: Boolean) {
        autofillEnabled = enabled
        prefs.edit().putBoolean("autofill_enabled", enabled).apply()
    }

    fun setCookiePolicyPreference(policy: CookiePolicy) {
        cookiePolicy = policy
        prefs.edit().putString("cookie_policy", policy.name).apply()
    }

    fun setBlockAiOverviewsPreference(enabled: Boolean) {
        blockAiOverviews = enabled
        prefs.edit().putBoolean("block_ai_overviews", enabled).apply()
    }

    fun setHttpsOnlyModePreference(enabled: Boolean) {
        httpsOnlyMode = enabled
        prefs.edit().putBoolean("https_only_mode", enabled).apply()
    }

    fun setVpnPreference(enabled: Boolean, countryCode: String) {
        vpnEnabled = enabled
        vpnCountryCode = countryCode
        prefs.edit()
            .putBoolean("vpn_enabled", enabled)
            .putString("vpn_country_code", countryCode)
            .apply()
    }
}
