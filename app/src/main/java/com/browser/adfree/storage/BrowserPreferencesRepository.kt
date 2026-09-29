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

    var videoDownloaderEnabled by mutableStateOf(prefs.getBoolean("video_downloader_enabled", true))
        private set

    var preferAv1Codec by mutableStateOf(prefs.getBoolean("prefer_av1_codec", false))
        private set

    var showWebmFormats by mutableStateOf(prefs.getBoolean("show_webm_formats", false))
        private set

    // Customization & Appearance
    var themeMode by mutableStateOf(
        try {
            AppThemeMode.valueOf(prefs.getString("theme_mode", AppThemeMode.DARK_OLED.name) ?: AppThemeMode.DARK_OLED.name)
        } catch (e: Exception) {
            AppThemeMode.DARK_OLED
        }
    )
        private set

    var accentColor by mutableStateOf(
        try {
            AppAccentColor.valueOf(prefs.getString("accent_color", AppAccentColor.CYAN.name) ?: AppAccentColor.CYAN.name)
        } catch (e: Exception) {
            AppAccentColor.CYAN
        }
    )
        private set

    var homepageWallpaper by mutableStateOf(
        try {
            HomepageWallpaper.valueOf(prefs.getString("homepage_wallpaper", HomepageWallpaper.DEFAULT.name) ?: HomepageWallpaper.DEFAULT.name)
        } catch (e: Exception) {
            HomepageWallpaper.DEFAULT
        }
    )
        private set

    var customWallpaperUri by mutableStateOf(prefs.getString("custom_wallpaper_uri", "") ?: "")
        private set

    var wallpaperBlur by mutableStateOf(prefs.getFloat("wallpaper_blur", 0f))
        private set

    var wallpaperDim by mutableStateOf(prefs.getFloat("wallpaper_dim", 0.3f))
        private set

    var showPrivacyStatsWidget by mutableStateOf(prefs.getBoolean("show_privacy_stats", true))
        private set

    var showFavoritesGrid by mutableStateOf(prefs.getBoolean("show_favorites_grid", true))
        private set

    var favoritesGridColumns by mutableStateOf(prefs.getInt("favorites_grid_columns", 4))
        private set

    var showGreetingHeader by mutableStateOf(prefs.getBoolean("show_greeting_header", true))
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

    fun setShowPrivacyStatsPreference(show: Boolean) {
        showPrivacyStatsWidget = show
        prefs.edit().putBoolean("show_privacy_stats", show).apply()
    }

    fun setShowFavoritesGridPreference(show: Boolean) {
        showFavoritesGrid = show
        prefs.edit().putBoolean("show_favorites_grid", show).apply()
    }

    fun setFavoritesGridColumnsPreference(columns: Int) {
        favoritesGridColumns = columns
        prefs.edit().putInt("favorites_grid_columns", columns).apply()
    }

    fun setShowGreetingHeaderPreference(show: Boolean) {
        showGreetingHeader = show
        prefs.edit().putBoolean("show_greeting_header", show).apply()
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
    DARK_OLED("OLED Black"),
    DARK_MIDNIGHT("Midnight Navy"),
    DARK_CHARCOAL("Charcoal"),
    SYSTEM("System")
}

enum class AppAccentColor(val displayName: String, val hex: Long) {
    CYAN("Neon Cyan", 0xFF00D8F6),
    PURPLE("Electric Purple", 0xFFA855F7),
    GREEN("Emerald Green", 0xFF10B981),
    RED("Ruby Crimson", 0xFFF43F5E),
    ORANGE("Sunset Amber", 0xFFF97316),
    YELLOW("Cyber Gold", 0xFFFACC15),
    MONOCHROME("Monochrome", 0xFFE2E8F0)
}

enum class HomepageWallpaper(val displayName: String) {
    DEFAULT("Glass Dark"),
    AURORA("Deep Aurora"),
    NEBULA("Cosmic Nebula"),
    CYBER("Cyber Grid"),
    OLED("Pitch Black"),
    FOREST("Mystic Forest"),
    SUNSET("Warm Sunset"),
    CUSTOM("Custom Photo")
}
