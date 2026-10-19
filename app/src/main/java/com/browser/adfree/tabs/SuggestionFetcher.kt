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

package com.browser.adfree.tabs

import com.browser.adfree.model.SearchEngine
import com.browser.adfree.model.SearchSuggestions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebResponse
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * Asks the search engine what people type after [query]. The request goes through
 * the engine's network stack, so it follows the VPN route and encrypted DNS, and it
 * carries no cookies. Callers only use it when the user turned suggestions on.
 */
object SuggestionFetcher {
    private const val MAX_BYTES = 64 * 1024

    suspend fun fetch(runtime: GeckoRuntime, engine: SearchEngine, query: String): List<String> {
        val request = WebRequest.Builder(SearchSuggestions.requestUrl(engine, query)).build()
        val response = suspendCancellableCoroutine<WebResponse?> { cont ->
            GeckoWebExecutor(runtime).fetch(request, GeckoWebExecutor.FETCH_FLAGS_ANONYMOUS)
                .accept({ cont.resume(it) }, { cont.resume(null) })
        } ?: return emptyList()
        if (response.statusCode != 200) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val out = ByteArrayOutputStream()
                response.body?.use { stream ->
                    val chunk = ByteArray(4096)
                    while (out.size() < MAX_BYTES) {
                        val read = stream.read(chunk)
                        if (read < 0) break
                        out.write(chunk, 0, read)
                    }
                }
                SearchSuggestions.parse(out.toString(Charsets.UTF_8.name()), query)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
