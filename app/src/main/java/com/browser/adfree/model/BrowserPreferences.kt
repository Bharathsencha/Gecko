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

enum class SearchEngine(
    val title: String,
    val searchUrl: String,
    /** OpenSearch suggestion endpoint; the typed text is appended, URL-encoded. */
    val suggestUrl: String
) {
    GOOGLE("Google", "https://www.google.com/search?q=", "https://suggestqueries.google.com/complete/search?client=firefox&q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=", "https://duckduckgo.com/ac/?type=list&q="),
    BRAVE("Brave Search", "https://search.brave.com/search?q=", "https://search.brave.com/api/suggest?q="),
    BING("Bing", "https://www.bing.com/search?q=", "https://api.bing.com/osjson.aspx?query="),
    ECOSIA("Ecosia", "https://www.ecosia.org/search?q=", "https://ac.ecosia.org/autocomplete?type=list&q=")
}

enum class StartupBehavior(
    val title: String,
    val description: String
) {
    NEW_TAB("Start Page", "Opens directly to a fresh Start Page while keeping background tabs intact"),
    CONTINUE_SESSION("Continue where you left off", "Restores all tabs and resumes your active webpage")
}

enum class TimeframeWipe(
    val title: String,
    val durationMs: Long
) {
    ALL_TIME("All Time", Long.MAX_VALUE),
    LAST_10_MINS("Last 10 mins", 10 * 60 * 1000L),
    LAST_1_HOUR("Last 1 hour", 60 * 60 * 1000L),
    LAST_3_HOURS("Last 3 hours", 3 * 60 * 60 * 1000L),
    LAST_1_WEEK("Last 1 week", 7 * 24 * 60 * 60 * 1000L),
    LAST_1_MONTH("Last 1 month", 30 * 24 * 60 * 60 * 1000L)
}

enum class CookiePolicy(
    val title: String,
    val description: String,
    val geckoValue: Int
) {
    ACCEPT_NON_TRACKERS(
        "Block 3rd-Party Trackers",
        "Allows standard site logins while blocking cross-site tracking cookies and telemetry.",
        4 // ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
    ),
    ACCEPT_FIRST_PARTY(
        "Block All 3rd-Party Cookies",
        "Strictly confines cookies to the visited domain. Blocks third-party cross-site embeds.",
        1 // ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY
    ),
    REJECT_ALL(
        "Block All Cookies",
        "Highest privacy shield. Disables all persistent cookie storage (may affect site logins).",
        2 // ContentBlocking.CookieBehavior.REJECT_ALL
    )
}

