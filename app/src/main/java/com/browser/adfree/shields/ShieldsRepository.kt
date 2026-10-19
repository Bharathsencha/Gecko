/*
 * Copyright (C) 2024-2026 Gibbly Gobbly <gibblygobbly@proton.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.browser.adfree.shields

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONObject

data class SiteShieldConfig(
    val shieldsEnabled: Boolean = true,
    val darkWebEnabled: Boolean = false,
    val backgroundMediaEnabled: Boolean = true,
    val desktopModeEnabled: Boolean = false,
    /** Off stops the site's scripts. */
    val javascriptEnabled: Boolean = true,
    /** Keep this site's cookies when "Keep cookies only for trusted sites" clears the rest. */
    val trusted: Boolean = false,
    /** Container this site always opens in, or null. */
    val container: String? = null
)

class ShieldsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("shields_per_site_prefs", Context.MODE_PRIVATE)

    val siteConfigs = mutableStateMapOf<String, SiteShieldConfig>()

    init {
        loadAll()
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host ?: ""
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            ""
        }.lowercase()
    }

    fun getConfigForUrl(url: String): SiteShieldConfig {
        val domain = extractDomain(url)
        if (domain.isBlank() || domain == "about" || domain.startsWith("about:")) {
            return SiteShieldConfig()
        }
        return siteConfigs[domain] ?: SiteShieldConfig()
    }

    fun setShieldsForUrl(
        url: String,
        shieldsEnabled: Boolean? = null,
        darkWebEnabled: Boolean? = null,
        backgroundMediaEnabled: Boolean? = null,
        desktopModeEnabled: Boolean? = null,
        javascriptEnabled: Boolean? = null,
        trusted: Boolean? = null,
        /** "" removes the assignment. */
        container: String? = null
    ) {
        val domain = extractDomain(url)
        if (domain.isBlank()) return

        val current = getConfigForUrl(url)
        val updated = current.copy(
            shieldsEnabled = shieldsEnabled ?: current.shieldsEnabled,
            darkWebEnabled = darkWebEnabled ?: current.darkWebEnabled,
            backgroundMediaEnabled = backgroundMediaEnabled ?: current.backgroundMediaEnabled,
            desktopModeEnabled = desktopModeEnabled ?: current.desktopModeEnabled,
            javascriptEnabled = javascriptEnabled ?: current.javascriptEnabled,
            trusted = trusted ?: current.trusted,
            container = when (container) { null -> current.container; "" -> null; else -> container }
        )

        siteConfigs[domain] = updated
        persistDomain(domain, updated)
    }

    /** Domain as stored here: host without "www.", lowercase. */
    fun domainOf(url: String): String = extractDomain(url)

    fun trustedDomains(): Set<String> = siteConfigs.filterValues { it.trusted }.keys

    /** Container assigned to [url]'s site or a parent domain of it. */
    fun containerFor(url: String): String? {
        var d = extractDomain(url)
        while (d.contains('.')) {
            siteConfigs[d]?.container?.let { return it }
            d = d.substringAfter('.')
        }
        return null
    }

    private fun persistDomain(domain: String, config: SiteShieldConfig) {
        val json = JSONObject().apply {
            put("shieldsEnabled", config.shieldsEnabled)
            put("darkWebEnabled", config.darkWebEnabled)
            put("backgroundMediaEnabled", config.backgroundMediaEnabled)
            put("desktopModeEnabled", config.desktopModeEnabled)
            put("javascriptEnabled", config.javascriptEnabled)
            put("trusted", config.trusted)
            config.container?.let { put("container", it) }
        }
        prefs.edit().putString(domain, json.toString()).apply()
    }

    private fun loadAll() {
        for ((domain, rawValue) in prefs.all) {
            if (rawValue is String) {
                try {
                    val json = JSONObject(rawValue)
                    siteConfigs[domain] = SiteShieldConfig(
                        shieldsEnabled = json.optBoolean("shieldsEnabled", true),
                        darkWebEnabled = json.optBoolean("darkWebEnabled", false),
                        backgroundMediaEnabled = json.optBoolean("backgroundMediaEnabled", true),
                        desktopModeEnabled = json.optBoolean("desktopModeEnabled", false),
                        javascriptEnabled = json.optBoolean("javascriptEnabled", true),
                        trusted = json.optBoolean("trusted", false),
                        container = json.optString("container").ifBlank { null }
                    )
                } catch (e: Exception) {}
            }
        }
    }
}
