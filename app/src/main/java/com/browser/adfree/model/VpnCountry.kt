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

/** Exit location for Tor or the free-proxy relay. [code] is ISO 3166-1 alpha-2. */
data class VpnCountry(
    val code: String,
    val name: String
) {
    companion object {
        val COUNTRIES = listOf(
            VpnCountry("US", "United States"),
            VpnCountry("CA", "Canada"),
            VpnCountry("GB", "United Kingdom"),
            VpnCountry("DE", "Germany"),
            VpnCountry("NL", "Netherlands"),
            VpnCountry("FR", "France"),
            VpnCountry("SE", "Sweden"),
            VpnCountry("CH", "Switzerland"),
            VpnCountry("JP", "Japan"),
            VpnCountry("SG", "Singapore"),
            VpnCountry("IN", "India"),
            VpnCountry("AU", "Australia"),
            VpnCountry("BR", "Brazil")
        )

        fun findByCode(code: String): VpnCountry? =
            COUNTRIES.find { it.code.equals(code, ignoreCase = true) }

        fun nameFor(code: String): String =
            findByCode(code)?.name ?: code.ifBlank { "Any country" }
    }
}
