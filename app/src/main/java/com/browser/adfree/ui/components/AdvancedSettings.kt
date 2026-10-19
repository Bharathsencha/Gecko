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

package com.browser.adfree.ui.components

import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.storage.BrowserPreferencesRepository
import com.browser.adfree.ui.theme.*
import org.mozilla.geckoview.GeckoRuntime

@Composable
internal fun AdvancedSettings(preferencesRepo: BrowserPreferencesRepository, runtime: GeckoRuntime, onBack: () -> Unit) {
    SettingsScaffold(title = "Advanced", onBack = onBack) {
        Text(
            "Encrypted DNS hides the names of the sites you open from your mobile network or Wi-Fi. Pick who resolves them; the default works for most people.",
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
        )
        BrutalSectionLabel("Encrypted DNS")
        SettingsGroup(shadow = CardShadows[1]) {
            com.browser.adfree.storage.DnsProvider.values().forEachIndexed { index, provider ->
                if (index > 0) BrutalHairline(startIndent = 16.dp)
                ChoiceRow(
                    title = provider.displayName,
                    subtitle = provider.detail,
                    selected = preferencesRepo.dnsProvider == provider,
                    onClick = {
                        preferencesRepo.setDnsPreference(provider, preferencesRepo.dnsStrict)
                        applyDns(runtime, preferencesRepo)
                    }
                )
            }
            if (preferencesRepo.dnsProvider != com.browser.adfree.storage.DnsProvider.OFF) {
                BrutalHairline(startIndent = 16.dp)
                BrutalListRow(
                    title = "Encrypted DNS only",
                    subtitle = "Never fall back to your network's DNS. Some public Wi-Fi blocks this and pages will not load.",
                    checked = preferencesRepo.dnsStrict,
                    onCheckedChange = {
                        preferencesRepo.setDnsPreference(preferencesRepo.dnsProvider, it)
                        applyDns(runtime, preferencesRepo)
                    }
                )
            }
        }

    }
}

// ------------------------------------------------------------------ About

/** Applies the chosen DNS-over-HTTPS resolver to the running engine. */
fun applyDns(runtime: GeckoRuntime, prefs: BrowserPreferencesRepository) {
    val provider = prefs.dnsProvider
    try {
        if (provider == com.browser.adfree.storage.DnsProvider.OFF) {
            runtime.settings.setTrustedRecursiveResolverMode(org.mozilla.geckoview.GeckoRuntimeSettings.TRR_MODE_OFF)
        } else {
            runtime.settings.setTrustedRecursiveResolverUri(provider.url)
            runtime.settings.setTrustedRecursiveResolverMode(
                if (prefs.dnsStrict) org.mozilla.geckoview.GeckoRuntimeSettings.TRR_MODE_ONLY
                else org.mozilla.geckoview.GeckoRuntimeSettings.TRR_MODE_FIRST
            )
        }
    } catch (e: Exception) {
        Log.w("Settings", "Encrypted DNS not applied: ${e.message}")
    }
}
