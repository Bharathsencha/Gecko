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

package com.browser.adfree.model

data class VpnCountry(
    val code: String,
    val name: String,
    val flag: String,
    val city: String,
    val host: String,
    val port: Int,
    val type: String = "http",
    val estimatedPingMs: Int = 45
) {
    companion object {
        val COUNTRIES = listOf(
            VpnCountry(
                code = "US",
                name = "United States",
                flag = "🇺🇸",
                city = "New York / Ashburn",
                host = "us.free-relay.adfree.internal",
                port = 8080,
                type = "http",
                estimatedPingMs = 38
            ),
            VpnCountry(
                code = "DE",
                name = "Germany",
                flag = "🇩🇪",
                city = "Frankfurt",
                host = "de.free-relay.adfree.internal",
                port = 8080,
                type = "http",
                estimatedPingMs = 42
            ),
            VpnCountry(
                code = "JP",
                name = "Japan",
                flag = "🇯🇵",
                city = "Tokyo",
                host = "jp.free-relay.adfree.internal",
                port = 8080,
                type = "http",
                estimatedPingMs = 75
            ),
            VpnCountry(
                code = "GB",
                name = "United Kingdom",
                flag = "🇬🇧",
                city = "London",
                host = "gb.free-relay.adfree.internal",
                port = 8080,
                type = "http",
                estimatedPingMs = 35
            ),
            VpnCountry(
                code = "NL",
                name = "Netherlands",
                flag = "🇳🇱",
                city = "Amsterdam",
                host = "nl.free-relay.adfree.internal",
                port = 8080,
                type = "http",
                estimatedPingMs = 40
            ),
            VpnCountry(
                code = "SG",
                name = "Singapore",
                flag = "🇸🇬",
                city = "Singapore Central",
                host = "sg.free-relay.adfree.internal",
                port = 8080,
                type = "http",
                estimatedPingMs = 68
            ),
            VpnCountry(
                code = "CA",
                name = "Canada",
                flag = "🇨🇦",
                city = "Toronto",
                host = "ca.free-relay.adfree.internal",
                port = 8080,
                type = "http",
                estimatedPingMs = 45
            ),
            VpnCountry(
                code = "FR",
                name = "France",
                flag = "🇫🇷",
                city = "Paris",
                host = "fr.free-relay.adfree.internal",
                port = 8080,
                type = "http",
                estimatedPingMs = 44
            ),
            VpnCountry(
                code = "AU",
                name = "Australia",
                flag = "🇦🇺",
                city = "Sydney",
                host = "au.free-relay.adfree.internal",
                port = 8080,
                type = "http",
                estimatedPingMs = 92
            ),
            VpnCountry(
                code = "IN",
                name = "India",
                flag = "🇮🇳",
                city = "Mumbai / Bangalore",
                host = "in.free-relay.adfree.internal",
                port = 8080,
                type = "http",
                estimatedPingMs = 28
            )
        )

        fun findByCode(code: String): VpnCountry {
            return COUNTRIES.find { it.code.equals(code, ignoreCase = true) } ?: COUNTRIES[0]
        }
    }
}
