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

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.model.VpnCountry
import com.browser.adfree.ui.theme.*
import com.browser.adfree.vpn.VpnManager
import com.browser.adfree.vpn.VpnMode
import com.browser.adfree.vpn.VpnStatus

@Composable
internal fun VpnSettings(vpnManager: VpnManager, onBack: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(vpnManager.lastMode) }
    val status = vpnManager.status
    val activeMode = when (status) {
        is VpnStatus.Connected -> status.mode
        is VpnStatus.Connecting -> status.mode
        is VpnStatus.Failed -> status.mode
        VpnStatus.Off -> null
    }

    fun connect(country: String) {
        if (mode == VpnMode.TOR) vpnManager.connectTor(country) else vpnManager.connectProxy(country)
    }

    val selectedCountry = if (mode == VpnMode.TOR) vpnManager.torExitCountry else vpnManager.proxyCountry

    SettingsScaffold(title = "VPN", onBack = onBack) {
        // Status panel
        Column(
            modifier = Modifier
                .padding(start = 16.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
                .fillMaxWidth()
                .brutalBox(
                    fill = if (status is VpnStatus.Connected) AccentCyan else SurfaceDark,
                    border = BorderSubtle,
                    shadow = HardShadow
                )
                .padding(16.dp)
        ) {
            val onPanel = if (status is VpnStatus.Connected) OnAccent else TextPrimary
            val onPanelSecondary = if (status is VpnStatus.Connected) OnAccent.copy(alpha = 0.75f) else TextSecondary
            Text(
                text = when (status) {
                    VpnStatus.Off -> "Not connected"
                    is VpnStatus.Connecting -> "Connecting"
                    is VpnStatus.Connected -> "Connected"
                    is VpnStatus.Failed -> "Connection failed"
                },
                color = onPanel,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            when (status) {
                VpnStatus.Off -> Text("Your real IP address is visible to sites.", color = onPanelSecondary, fontSize = 13.sp)
                is VpnStatus.Connecting -> {
                    Text(status.message, color = onPanelSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    BrutalProgressLine(progress = status.percent?.let { it / 100f })
                }
                is VpnStatus.Connected -> {
                    Text(
                        "Via ${if (status.mode == VpnMode.TOR) "Tor" else "free relay"}, exit in ${VpnCountry.nameFor(status.exitCountry)}",
                        color = onPanelSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Exit IP ${status.exitIp}" + (status.latencyMs?.let { "  |  ${it} ms" } ?: ""),
                        color = onPanel,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                is VpnStatus.Failed -> Text(status.reason, color = onPanelSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(14.dp))
            when (status) {
                VpnStatus.Off -> BrutalButton(
                    text = if (mode == VpnMode.TOR) "Connect to Tor" else "Connect to free relay",
                    onClick = { connect(selectedCountry) },
                    modifier = Modifier.fillMaxWidth()
                )
                is VpnStatus.Connecting -> BrutalOutlineButton("Cancel", { vpnManager.disconnect() }, Modifier.fillMaxWidth())
                is VpnStatus.Connected -> BrutalOutlineButton("Disconnect", { vpnManager.disconnect() }, Modifier.fillMaxWidth())
                is VpnStatus.Failed -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BrutalButton("Retry", { connect(selectedCountry) }, Modifier.weight(1f))
                    BrutalOutlineButton("Turn off", { vpnManager.disconnect() }, Modifier.weight(1f))
                }
            }
        }

        BrutalSectionLabel("Network")
        SettingsGroup(shadow = CardShadows[5]) {
            ChoiceRow(
                title = "Tor",
                subtitle = "Three encrypted hops, strongest anonymity. Pages load slower and some sites show captchas.",
                selected = mode == VpnMode.TOR,
                onClick = { mode = VpnMode.TOR }
            )
            BrutalHairline(startIndent = 16.dp)
            ChoiceRow(
                title = "Free relay",
                subtitle = "Public SOCKS5 relay, found and tested when you connect. Faster, but its operator can see traffic to non-HTTPS sites.",
                selected = mode == VpnMode.PROXY,
                onClick = { mode = VpnMode.PROXY }
            )
        }

        BrutalSectionLabel(if (mode == VpnMode.TOR) "Exit country" else "Relay country")
        SettingsGroup(shadow = CardShadows[0]) {
            val options = buildList {
                if (mode == VpnMode.TOR) add(VpnCountry("", "Any country (fastest)"))
                addAll(VpnCountry.COUNTRIES)
            }
            options.forEachIndexed { index, country ->
                if (index > 0) BrutalHairline(startIndent = 16.dp)
                ChoiceRow(
                    title = country.name,
                    selected = selectedCountry.equals(country.code, ignoreCase = true) &&
                        (mode == VpnMode.TOR || country.code.isNotBlank()),
                    onClick = {
                        // Picking a country connects (or reconnects) right away.
                        connect(country.code)
                    },
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 28.dp)
                                .brutalBox(fill = SurfaceCard, border = BorderSubtle, borderWidth = 1.5.dp, shape = RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                country.code.ifBlank { "ANY" },
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                )
            }
        }
        if (activeMode != null && activeMode != mode) {
            Text(
                "Picking a country switches the connection to ${if (mode == VpnMode.TOR) "Tor" else "a free relay"}.",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
