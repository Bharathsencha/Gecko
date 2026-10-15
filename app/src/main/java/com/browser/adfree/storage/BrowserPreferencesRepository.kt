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

    var videoDownloaderEnabled by mutableStateOf(prefs.getBoolean("video_downloader_enabled", true))
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
