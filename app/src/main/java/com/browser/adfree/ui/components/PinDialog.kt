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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.storage.VaultLock
import com.browser.adfree.ui.theme.*

/** CHANGE verifies the current PIN; the caller then switches to CREATE. */
enum class PinMode { CREATE, VERIFY, CHANGE }

/** Passcode entry for the password vault (6 letters or digits): create (with confirmation) or verify. */
@Composable
fun PinDialog(
    mode: PinMode,
    vaultLock: VaultLock,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // An older 4-digit passcode is still checked as 4 digits; a new one is always 6 characters.
    val length = if (mode == PinMode.CREATE) VaultLock.PASSCODE_LENGTH else vaultLock.passcodeLength
    val digitsOnly = mode != PinMode.CREATE && vaultLock.isLegacyPin

    val title = when {
        mode == PinMode.VERIFY -> "Enter your passcode"
        mode == PinMode.CHANGE -> "Enter your current passcode"
        firstPin == null -> "Create a $length-character passcode"
        else -> "Confirm your passcode"
    }

    fun submit() {
        if (busy) return
        if (pin.length != length) {
            message = "Enter all $length characters."
            return
        }
        val entered = pin
        when (mode) {
            PinMode.VERIFY, PinMode.CHANGE -> {
                busy = true
                scope.launch {
                    // Argon2id is deliberately slow and memory hungry.
                    val r = withContext(Dispatchers.IO) { vaultLock.checkPin(entered) }
                    busy = false
                    when (r) {
                        VaultLock.PinResult.Ok -> onUnlocked()
                        is VaultLock.PinResult.Wrong -> { message = "Wrong passcode. ${r.attemptsLeft} tries left before a lockout."; pin = "" }
                        is VaultLock.PinResult.LockedOut -> { message = "Too many wrong tries. Try again in ${r.seconds} s."; pin = "" }
                    }
                }
            }
            PinMode.CREATE -> {
                val first = firstPin
                if (first == null) {
                    firstPin = entered; pin = ""; message = null
                } else if (first == entered) {
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            vaultLock.setPin(entered)
                            vaultLock.method = VaultLock.Method.PIN
                        }
                        busy = false
                        onUnlocked()
                    }
                } else {
                    firstPin = null; pin = ""; message = "The passcodes did not match. Start again."
                }
            }
        }
    }

    BrutalDialog(
        title = title,
        onDismiss = onDismiss,
        confirmLabel = if (mode == PinMode.CREATE && firstPin == null) "Next" else "Unlock",
        onConfirm = { submit() },
        dismissLabel = "Cancel"
    ) {
        Text(
            if (mode == PinMode.CREATE) "Letters and numbers. Used to open saved passwords when fingerprint or a screen lock is not set up." else "Saved passwords are locked.",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(14.dp))
        // The four boxes are the input: tapping one opens the keyboard and digits fill them in turn.
        val focusRequester = remember { FocusRequester() }
        var focused by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        BasicTextField(
            value = pin,
            onValueChange = { v ->
                if (busy) return@BasicTextField
                pin = v.filter { if (digitsOnly) it in '0'..'9' else it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' }.take(length)
                message = null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (digitsOnly) KeyboardType.NumberPassword else KeyboardType.Password,
                imeAction = ImeAction.Done,
                autoCorrect = false
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = "Passcode, ${pin.length} of $length characters entered" },
            decorationBox = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    repeat(length) { i ->
                        val active = focused && i == pin.length.coerceAtMost(length - 1)
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .widthIn(max = 48.dp)
                                .aspectRatio(1f)
                                .brutalBox(
                                    fill = if (i < pin.length) AccentCyan else SurfaceCard,
                                    border = if (active) TextPrimary else BorderSubtle,
                                    borderWidth = if (active) 3.dp else 2.dp,
                                    shape = RoundedCornerShape(5.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (i < pin.length) Box(Modifier.size(12.dp).brutalBox(fill = InkBlack, border = InkBlack, shape = RoundedCornerShape(2.dp)))
                        }
                    }
                }
            }
        )
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = DangerRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
