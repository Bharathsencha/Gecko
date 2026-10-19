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
            SearchEngine.valueOf(prefs.getString("search_engine", SearchEngine.DUCKDUCKGO.name) ?: SearchEngine.DUCKDUCKGO.name)
        } catch (e: Exception) {
            SearchEngine.DUCKDUCKGO
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

    /** Encrypted DNS (DNS over HTTPS) provider; OFF uses the network's DNS. */
    var dnsProvider by mutableStateOf(
        runCatching { DnsProvider.valueOf(prefs.getString("dns_provider", DnsProvider.QUAD9.name) ?: "") }.getOrDefault(DnsProvider.QUAD9)
    )
        private set

    /** Strict: never fall back to unencrypted DNS (pages fail if the provider is blocked). */
    var dnsStrict by mutableStateOf(prefs.getBoolean("dns_strict", false))
        private set

    fun setDnsPreference(provider: DnsProvider, strict: Boolean) {
        dnsProvider = provider
        dnsStrict = strict
        prefs.edit().putString("dns_provider", provider.name).putBoolean("dns_strict", strict).apply()
    }

    /** Tracker statistics card on the start page. */
    var showTrackerStats by mutableStateOf(prefs.getBoolean("show_tracker_stats", true))
        private set

    fun setShowTrackerStatsPreference(show: Boolean) {
        showTrackerStats = show
        prefs.edit().putBoolean("show_tracker_stats", show).apply()
    }

    /** Icon and animation of the clear-data button. */
    var clearStyle by mutableStateOf(
        runCatching { ClearStyle.valueOf(prefs.getString("clear_style", ClearStyle.FIRE.name) ?: "") }.getOrDefault(ClearStyle.FIRE)
    )
        private set

    fun setClearStylePreference(style: ClearStyle) {
        clearStyle = style
        prefs.edit().putString("clear_style", style.name).apply()
    }

    /** Block screenshots, screen recording and the recent-apps preview in normal tabs. */
    var blockScreenshots by mutableStateOf(prefs.getBoolean("block_screenshots", false))
        private set

    fun setBlockScreenshotsPreference(enabled: Boolean) {
        blockScreenshots = enabled
        prefs.edit().putBoolean("block_screenshots", enabled).apply()
    }

    /** The same for private tabs; on unless the user turns it off. */
    var blockScreenshotsPrivate by mutableStateOf(prefs.getBoolean("block_screenshots_private", true))
        private set

    fun setBlockScreenshotsPrivatePreference(enabled: Boolean) {
        blockScreenshotsPrivate = enabled
        prefs.edit().putBoolean("block_screenshots_private", enabled).apply()
    }

    /** Clearing history also resets the tracker report. */
    var resetStatsWithHistory by mutableStateOf(prefs.getBoolean("reset_stats_with_history", true))
        private set

    fun setResetStatsWithHistoryPreference(enabled: Boolean) {
        resetStatsWithHistory = enabled
        prefs.edit().putBoolean("reset_stats_with_history", enabled).apply()
    }

    /** Ask keyboards not to learn or store typing (always on in private tabs). */
    var incognitoKeyboard by mutableStateOf(prefs.getBoolean("incognito_keyboard", true))
        private set

    fun setIncognitoKeyboardPreference(enabled: Boolean) {
        incognitoKeyboard = enabled
        prefs.edit().putBoolean("incognito_keyboard", enabled).apply()
    }

    /** Last used VPN mode ("TOR" or "PROXY"); the connection itself is never auto-restored. */
    var vpnMode by mutableStateOf(prefs.getString("vpn_mode", "TOR") ?: "TOR")
        private set

    /** Country for free-proxy mode. */
    var vpnCountryCode by mutableStateOf(prefs.getString("vpn_country_code", "US") ?: "US")
        private set

    /** Tor exit country, blank for any. */
    var vpnTorExitCountry by mutableStateOf(prefs.getString("vpn_tor_exit", "") ?: "")
        private set

    var videoDownloaderEnabled by mutableStateOf(prefs.getBoolean("video_downloader_enabled", false))
        private set

    var preferAv1Codec by mutableStateOf(prefs.getBoolean("prefer_av1_codec", false))
        private set

    var showWebmFormats by mutableStateOf(prefs.getBoolean("show_webm_formats", false))
        private set

    // Customization & Appearance
    var themeMode by mutableStateOf(
        try {
            when (val stored = prefs.getString("theme_mode", AppThemeMode.SYSTEM.name)) {
                // Older builds had several dark variants; they all map to the one dark theme.
                "DARK_OLED", "DARK_MIDNIGHT", "DARK_CHARCOAL" -> AppThemeMode.DARK
                null -> AppThemeMode.SYSTEM
                else -> AppThemeMode.valueOf(stored)
            }
        } catch (e: Exception) {
            AppThemeMode.SYSTEM
        }
    )
        private set

    var accentColor by mutableStateOf(
        try {
            AppAccentColor.valueOf(prefs.getString("accent_color", AppAccentColor.YELLOW.name) ?: AppAccentColor.YELLOW.name)
        } catch (e: Exception) {
            AppAccentColor.YELLOW
        }
    )
        private set

    var homepageWallpaper by mutableStateOf(
        try {
            HomepageWallpaper.valueOf(prefs.getString("homepage_wallpaper", HomepageWallpaper.BLOCKS.name) ?: HomepageWallpaper.BLOCKS.name)
        } catch (e: Exception) {
            HomepageWallpaper.BLOCKS
        }
    )
        private set

    var customWallpaperUri by mutableStateOf(prefs.getString("custom_wallpaper_uri", "") ?: "")
        private set

    /** Start-page designs the user made and named in the designer. */
    var customDesigns by mutableStateOf(loadDesigns())
        private set

    /** Which saved design is shown when the background is DESIGN. */
    var selectedDesignId by mutableStateOf(prefs.getString("selected_design_id", "") ?: "")
        private set

    val activeDesign: com.browser.adfree.model.WallDesign?
        get() = customDesigns.firstOrNull { it.id == selectedDesignId }?.design ?: customDesigns.firstOrNull()?.design

    private fun loadDesigns(): List<com.browser.adfree.model.SavedDesign> {
        val list = com.browser.adfree.model.SavedDesign.listFromJson(prefs.getString("custom_designs", null))
        if (list.isNotEmpty()) return list
        // One unnamed design from an earlier build becomes the first saved one.
        val legacy = com.browser.adfree.model.WallDesign.fromJson(prefs.getString("custom_design", null)) ?: return emptyList()
        return listOf(com.browser.adfree.model.SavedDesign(java.util.UUID.randomUUID().toString(), "My design", legacy))
    }

    private fun persistDesigns() {
        prefs.edit()
            .putString("custom_designs", com.browser.adfree.model.SavedDesign.listToJson(customDesigns))
            .putString("selected_design_id", selectedDesignId)
            .remove("custom_design")
            .apply()
    }

    /** Creates ([id] null) or replaces a design, selects it and makes it the background. */
    fun saveDesign(id: String?, name: String, design: com.browser.adfree.model.WallDesign) {
        val designId = id ?: java.util.UUID.randomUUID().toString()
        val entry = com.browser.adfree.model.SavedDesign(designId, name, design)
        customDesigns = if (customDesigns.any { it.id == designId }) customDesigns.map { if (it.id == designId) entry else it } else customDesigns + entry
        selectDesign(designId)
    }

    fun selectDesign(id: String) {
        selectedDesignId = id
        persistDesigns()
        setHomepageWallpaperPreference(HomepageWallpaper.DESIGN)
    }

    fun deleteDesign(id: String) {
        customDesigns = customDesigns.filterNot { it.id == id }
        if (selectedDesignId == id) {
            selectedDesignId = ""
            if (homepageWallpaper == HomepageWallpaper.DESIGN) setHomepageWallpaperPreference(HomepageWallpaper.BLOCKS)
        }
        persistDesigns()
    }

    var wallpaperBlur by mutableStateOf(prefs.getFloat("wallpaper_blur", 0f))
        private set

    var wallpaperDim by mutableStateOf(prefs.getFloat("wallpaper_dim", 0.3f))
        private set

    var showFavoritesGrid by mutableStateOf(prefs.getBoolean("show_favorites_grid", true))
        private set

    var favoritesGridColumns by mutableStateOf(prefs.getInt("favorites_grid_columns", 4))
        private set

    var bottomAddressBar by mutableStateOf(prefs.getBoolean("bottom_address_bar", false))
        private set

    /** Ask the search engine for suggestions while typing. Off by default: it sends what you type. */
    var searchSuggestions by mutableStateOf(prefs.getBoolean("search_suggestions", false))
        private set

    fun setSearchSuggestionsPreference(enabled: Boolean) {
        searchSuggestions = enabled
        prefs.edit().putBoolean("search_suggestions", enabled).apply()
    }

    /** Days of inactivity after which a normal tab is closed; 0 keeps tabs until closed by hand. */
    var autoCloseTabsDays by mutableStateOf(prefs.getInt("auto_close_tabs_days", 0))
        private set

    fun setAutoCloseTabsDaysPreference(days: Int) {
        autoCloseTabsDays = days
        prefs.edit().putInt("auto_close_tabs_days", days).apply()
    }

    /** Links opened from other apps go to a private tab. */
    var openExternalInPrivate by mutableStateOf(prefs.getBoolean("open_external_private", false))
        private set

    fun setOpenExternalInPrivatePreference(enabled: Boolean) {
        openExternalInPrivate = enabled
        prefs.edit().putBoolean("open_external_private", enabled).apply()
    }

    /** The settings a backup carries, as stored. Device-bound values (wallpaper file, VPN route) stay out. */
    fun exportSettings(): Map<String, Any> =
        prefs.all.filter { (key, value) -> value != null && BACKUP_KEYS.containsKey(key) }.mapValues { it.value!! }

    /**
     * Applies settings read from a backup. Only known keys with the right type are
     * written; the rest is ignored. Takes effect the next time the app starts.
     * Returns how many settings were applied.
     */
    fun importSettings(values: Map<String, Any>): Int {
        val edit = prefs.edit()
        var applied = 0
        for ((key, value) in values) {
            when (BACKUP_KEYS[key]) {
                Boolean::class -> (value as? Boolean)?.let { edit.putBoolean(key, it); applied++ }
                Int::class -> (value as? Number)?.let { edit.putInt(key, it.toInt()); applied++ }
                Float::class -> (value as? Number)?.let { edit.putFloat(key, it.toFloat()); applied++ }
                String::class -> (value as? String)?.takeIf { it.length <= 100_000 }?.let { edit.putString(key, it); applied++ }
                else -> {}
            }
        }
        edit.apply()
        return applied
    }

    var defaultBrowserPromptDismissed by mutableStateOf(prefs.getBoolean("default_browser_prompt_dismissed", false))
        private set

    fun setDefaultBrowserPromptDismissedPreference(dismissed: Boolean) {
        defaultBrowserPromptDismissed = dismissed
        prefs.edit().putBoolean("default_browser_prompt_dismissed", dismissed).apply()
    }

    fun setThemeModePreference(mode: AppThemeMode) {
        themeMode = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun setAccentColorPreference(color: AppAccentColor) {
        accentColor = color
        prefs.edit().putString("accent_color", color.name).apply()
    }

    fun setHomepageWallpaperPreference(wallpaper: HomepageWallpaper, customUri: String = "") {
        homepageWallpaper = wallpaper
        customWallpaperUri = customUri
        prefs.edit()
            .putString("homepage_wallpaper", wallpaper.name)
            .putString("custom_wallpaper_uri", customUri)
            .apply()
    }

    fun setWallpaperBlurPreference(blur: Float) {
        wallpaperBlur = blur
        prefs.edit().putFloat("wallpaper_blur", blur).apply()
    }

    fun setWallpaperDimPreference(dim: Float) {
        wallpaperDim = dim
        prefs.edit().putFloat("wallpaper_dim", dim).apply()
    }

    fun setShowFavoritesGridPreference(show: Boolean) {
        showFavoritesGrid = show
        prefs.edit().putBoolean("show_favorites_grid", show).apply()
    }

    fun setFavoritesGridColumnsPreference(columns: Int) {
        favoritesGridColumns = columns
        prefs.edit().putInt("favorites_grid_columns", columns).apply()
    }

    fun setBottomAddressBarPreference(bottom: Boolean) {
        bottomAddressBar = bottom
        prefs.edit().putBoolean("bottom_address_bar", bottom).apply()
    }

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

    fun setVpnPreference(mode: String, proxyCountry: String, torExitCountry: String) {
        vpnMode = mode
        vpnCountryCode = proxyCountry
        vpnTorExitCountry = torExitCountry
        prefs.edit()
            .putString("vpn_mode", mode)
            .putString("vpn_country_code", proxyCountry)
            .putString("vpn_tor_exit", torExitCountry)
            .apply()
    }

    fun setVideoDownloaderEnabledPreference(enabled: Boolean) {
        videoDownloaderEnabled = enabled
        prefs.edit().putBoolean("video_downloader_enabled", enabled).apply()
    }

    fun setPreferAv1CodecPreference(enabled: Boolean) {
        preferAv1Codec = enabled
        prefs.edit().putBoolean("prefer_av1_codec", enabled).apply()
    }

    fun setShowWebmFormatsPreference(show: Boolean) {
        showWebmFormats = show
        prefs.edit().putBoolean("show_webm_formats", show).apply()
    }
    private companion object {
        val BACKUP_KEYS: Map<String, kotlin.reflect.KClass<*>> = mapOf(
            "search_engine" to String::class,
            "startup_behavior" to String::class,
            "ad_blocking" to Boolean::class,
            "cosmetic_blocking" to Boolean::class,
            "dark_web_default" to Boolean::class,
            "wipe_on_exit" to Boolean::class,
            "save_passwords" to Boolean::class,
            "cookie_policy" to String::class,
            "block_ai_overviews" to Boolean::class,
            "https_only_mode" to Boolean::class,
            "dns_provider" to String::class,
            "dns_strict" to Boolean::class,
            "show_tracker_stats" to Boolean::class,
            "clear_style" to String::class,
            "block_screenshots" to Boolean::class,
            "block_screenshots_private" to Boolean::class,
            "reset_stats_with_history" to Boolean::class,
            "incognito_keyboard" to Boolean::class,
            "video_downloader_enabled" to Boolean::class,
            "show_webm_formats" to Boolean::class,
            "prefer_av1_codec" to Boolean::class,
            "theme_mode" to String::class,
            "accent_color" to String::class,
            "show_favorites_grid" to Boolean::class,
            "favorites_grid_columns" to Int::class,
            "bottom_address_bar" to Boolean::class,
            "search_suggestions" to Boolean::class,
            "auto_close_tabs_days" to Int::class,
            "open_external_private" to Boolean::class
        )
    }
}

enum class AppThemeMode(val displayName: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System")
}

/** Solid fill used for primary actions and active states. Text on top is always ink. */
enum class AppAccentColor(val displayName: String, val hex: Long) {
    YELLOW("Yellow", 0xFFFFD84D),
    ORANGE("Orange", 0xFFFFB067),
    PURPLE("Purple", 0xFFB49CFF),
    TEAL("Teal", 0xFF7FD8C8),
    PINK("Pink", 0xFFFF9EC7),
    BLUE("Blue", 0xFF8EC5FF)
}

enum class HomepageWallpaper(val displayName: String) {
    BLOCKS("Blocks"),
    STACK("Stack"),
    SHAPES("Shapes"),
    MONDRIAN("Mondrian"),
    DESIGN("My design"),
    DOTS("Dot grid"),
    GRID("Graph"),
    STRIPES("Stripes"),
    CHECKER("Checker"),
    BUTTER("Butter"),
    PEACH("Peach"),
    MINT("Mint"),
    LAVENDER("Lavender"),
    DEFAULT("Plain"),
    AURORA("Aurora"),
    NEBULA("Nebula"),
    CYBER("Cyber"),
    OLED("Pitch Black"),
    FOREST("Forest"),
    SUNSET("Sunset"),
    CUSTOM("Photo")
}

enum class ClearStyle(val displayName: String) {
    FIRE("Fire"),
    FLOOD("Flood"),
    SWEEP("Sweep"),
    SHRED("Shred"),
    PIXEL("Pixel")
}

/** DNS-over-HTTPS resolvers offered in Settings. */
enum class DnsProvider(val displayName: String, val detail: String, val url: String) {
    OFF("Off", "Use your network's DNS", ""),
    QUAD9("Quad9", "Swiss non-profit, blocks known malware domains", "https://dns.quad9.net/dns-query"),
    MULLVAD("Mullvad", "No logging, run by the VPN company", "https://dns.mullvad.net/dns-query"),
    MULLVAD_ADBLOCK("Mullvad ad blocking", "Mullvad, also blocks ad and tracker domains", "https://adblock.dns.mullvad.net/dns-query"),
    CLOUDFLARE("Cloudflare", "Fastest in most places, audited not to log", "https://mozilla.cloudflare-dns.com/dns-query"),
    ADGUARD("AdGuard", "Blocks ad and tracker domains", "https://dns.adguard-dns.com/dns-query")
}
