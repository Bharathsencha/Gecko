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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.BuildConfig
import com.browser.adfree.ui.theme.*

internal data class Credit(val name: String, val role: String, val license: String, val url: String)

internal val CREDITS = listOf(
    Credit("Mozilla GeckoView", "Web engine", "MPL 2.0", "https://mozilla.github.io/geckoview/"),
    Credit("uBlock Origin", "Ad and tracker blocking. Modified: one site exemption in js/start.js, dashboard flags and editor removed", "GPL 3.0", "https://github.com/gorhill/uBlock"),
    Credit("Tor", "Anonymous network for the VPN", "BSD 3-Clause", "https://www.torproject.org/"),
    Credit("tor-android", "Tor packaging for Android (Guardian Project)", "BSD 3-Clause", "https://github.com/guardianproject/tor-android"),
    Credit("jtorctl", "Tor control protocol", "BSD 3-Clause", "https://github.com/guardianproject/jtorctl"),
    Credit("yt-dlp", "Video and audio extraction", "Unlicense", "https://github.com/yt-dlp/yt-dlp"),
    Credit("youtubedl-android", "yt-dlp runtime for Android", "GPL 3.0", "https://github.com/JunkFood02/youtubedl-android"),
    Credit("NewPipe Extractor", "Stream extraction", "GPL 3.0", "https://github.com/TeamNewPipe/NewPipeExtractor"),
    Credit("OkHttp", "Native HTTP client", "Apache 2.0", "https://square.github.io/okhttp/"),
    Credit("Coil", "Image loading", "Apache 2.0", "https://coil-kt.github.io/coil/"),
    Credit("SnakeYAML", "Engine configuration parsing", "Apache 2.0", "https://bitbucket.org/snakeyaml/snakeyaml"),
    Credit("Jetpack Compose and AndroidX", "User interface", "Apache 2.0", "https://developer.android.com/jetpack"),
    Credit("Material Icons", "Icons", "Apache 2.0", "https://fonts.google.com/icons"),
    Credit("Space Grotesk", "Typeface (Florian Karsten)", "SIL OFL 1.1", "https://github.com/floriankarsten/space-grotesk")
)

/** Projects whose ideas or approach Gecko follows, without copying their code. */
internal val INSPIRATIONS = listOf(
    Credit("DuckDuckGo Fire Button", "Idea of a one-tap clear button with a fire animation", "Idea", "https://duckduckgo.com/duckduckgo-help-pages/privacy/fire-button/"),
    Credit("Firefox private browsing", "Mask icon for private mode", "Idea", "https://support.mozilla.org/kb/private-browsing-firefox-android"),
    Credit("Brave Shields", "Per-site protection panel", "Idea", "https://brave.com/shields/"),
    Credit("Cat-Catch", "Approach for detecting media on pages", "Idea", "https://github.com/xifangczy/cat-catch"),
    Credit("Mozilla Readability", "Approach for reader view", "Idea", "https://github.com/mozilla/readability"),
    Credit("Tor Browser", "Uniform anti-fingerprinting settings", "Idea", "https://support.torproject.org/glossary/fingerprinting/"),
    Credit("EasyList, AdGuard, Firebog, d3ward", "Sources for the built-in blocklist", "Lists", "https://easylist.to/")
)

@Composable
internal fun AboutSettings(onOpenUrl: (String) -> Unit, onOpenPrivacy: () -> Unit, onOpenTerms: () -> Unit, onBack: () -> Unit) {
    SettingsScaffold(title = "About", onBack = onBack) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.browser.adfree.R.drawable.gecko_logo),
                contentDescription = null,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Gecko", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Version ${BuildConfig.VERSION_NAME}", color = TextSecondary, fontSize = 13.sp)
            }
        }
        Text(
            "Gecko is free software: you can redistribute it and modify it under the terms of the " +
                "GNU General Public License, version 3 or later. It comes with no warranty.",
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(16.dp)
        )
        SettingsGroup(shadow = CardShadows[1]) {
            BrutalListRow(
                title = "Source code",
                subtitle = "github.com/Bharathsencha/Gecko",
                onClick = { onOpenUrl("https://github.com/Bharathsencha/Gecko") },
                trailing = { Chevron() }
            )
            BrutalHairline(startIndent = 16.dp)
            BrutalListRow(
                title = "Privacy policy",
                subtitle = "What Gecko stores and what it sends",
                onClick = onOpenPrivacy,
                trailing = { Chevron() }
            )
            BrutalHairline(startIndent = 16.dp)
            BrutalListRow(
                title = "Terms of use",
                onClick = onOpenTerms,
                trailing = { Chevron() }
            )
            BrutalHairline(startIndent = 16.dp)
            BrutalListRow(
                title = "GNU General Public License v3",
                onClick = { onOpenUrl("https://www.gnu.org/licenses/gpl-3.0.html") },
                trailing = { Chevron() }
            )
        }

        BrutalSectionLabel("Built with")
        SettingsGroup(shadow = CardShadows[2]) {
            CREDITS.forEachIndexed { index, credit ->
                if (index > 0) BrutalHairline(startIndent = 16.dp)
                BrutalListRow(
                    title = credit.name,
                    subtitle = credit.role,
                    onClick = { onOpenUrl(credit.url) },
                    trailing = { ValueChip(credit.license) }
                )
            }
        }
        BrutalSectionLabel("Ideas from")
        SettingsGroup(shadow = CardShadows[2]) {
            INSPIRATIONS.forEachIndexed { index, credit ->
                if (index > 0) BrutalHairline(startIndent = 16.dp)
                BrutalListRow(
                    title = credit.name,
                    subtitle = credit.role,
                    onClick = { onOpenUrl(credit.url) },
                    trailing = { ValueChip(credit.license) }
                )
            }
        }
        Text(
            "Gecko is not affiliated with Mozilla, the Tor Project or any project listed here. " +
                "Firefox, Tor, Brave and DuckDuckGo are trademarks of their owners.",
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(16.dp)
        )
    }
}

// ------------------------------------------------------------------ Appearance
