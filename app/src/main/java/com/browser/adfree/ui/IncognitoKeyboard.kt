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

package com.browser.adfree.ui

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import org.mozilla.geckoview.GeckoView

/**
 * Incognito keyboard everywhere: IME_FLAG_NO_PERSONALIZED_LEARNING asks the
 * keyboard not to learn words, store history or send what is typed for
 * suggestions. Applied to every Compose text field and to web page inputs.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun IncognitoKeyboard(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            val privateRequest = PlatformTextInputMethodRequest { outAttributes ->
                request.createInputConnection(outAttributes).also {
                    outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                }
            }
            nextHandler.startInputMethod(privateRequest)
        },
        content = content
    )
}

/** GeckoView that requests the incognito keyboard for normal tabs too (Gecko only does it for private ones). */
class IncognitoGeckoView(context: Context) : GeckoView(context) {
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs)
        if (enabled) outAttrs.imeOptions = outAttrs.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        return connection
    }

    companion object {
        /** Mirrors the setting; private tabs get the flag from Gecko regardless. */
        @Volatile
        var enabled: Boolean = true
    }
}
