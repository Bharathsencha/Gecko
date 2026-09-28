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
    var host: String,
    var port: Int,
    var type: String = "socks",
    val estimatedPingMs: Int = 45
) {
    companion object {
        val COUNTRIES = listOf(
            VpnCountry(
                code = "US",
                name = "United States",
                flag = "🇺🇸",
                city = "New York / Ashburn",
                host = "107.150.41.226",
                port = 18080,
                type = "socks",
                estimatedPingMs = 38
            ),
            VpnCountry(
                code = "DE",
                name = "Germany",
                flag = "🇩🇪",
                city = "Frankfurt",
                host = "94.228.118.127",
                port = 1414,
                type = "socks",
                estimatedPingMs = 42
            ),
            VpnCountry(
                code = "JP",
                name = "Japan",
                flag = "🇯🇵",
                city = "Tokyo",
                host = "172.105.192.212",
                port = 9080,
                type = "socks",
                estimatedPingMs = 75
            ),
            VpnCountry(
                code = "GB",
                name = "United Kingdom",
                flag = "🇬🇧",
                city = "London",
                host = "144.24.111.128",
                port = 1088,
                type = "socks",
                estimatedPingMs = 35
            ),
            VpnCountry(
                code = "NL",
                name = "Netherlands",
                flag = "🇳🇱",
                city = "Amsterdam",
                host = "62.238.107.38",
                port = 9050,
                type = "socks",
                estimatedPingMs = 40
            ),
            VpnCountry(
                code = "SG",
                name = "Singapore",
                flag = "🇸🇬",
                city = "Singapore Central",
                host = "104.248.151.220",
                port = 57554,
                type = "socks",
                estimatedPingMs = 68
            ),
            VpnCountry(
                code = "CA",
                name = "Canada",
                flag = "🇨🇦",
                city = "Toronto",
                host = "142.54.228.193",
                port = 4145,
                type = "socks",
                estimatedPingMs = 45
            ),
            VpnCountry(
                code = "FR",
                name = "France",
                flag = "🇫🇷",
                city = "Paris",
                host = "51.79.11.180",
                port = 10001,
                type = "socks",
                estimatedPingMs = 44
            ),
            VpnCountry(
                code = "AU",
                name = "Australia",
                flag = "🇦🇺",
                city = "Sydney",
                host = "67.201.33.10",
                port = 25283,
                type = "socks",
                estimatedPingMs = 92
            ),
            VpnCountry(
                code = "IN",
                name = "India",
                flag = "🇮🇳",
                city = "Mumbai / Bangalore",
                host = "45.74.31.41",
                port = 4967,
                type = "socks",
                estimatedPingMs = 28
            )
        )

        fun findByCode(code: String): VpnCountry {
            return COUNTRIES.find { it.code.equals(code, ignoreCase = true) } ?: COUNTRIES[0]
        }
    }
}
