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

/** 4-digit PIN entry for the password vault: create (with confirmation) or verify. */
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

    val title = when {
        mode == PinMode.VERIFY -> "Enter your PIN"
        mode == PinMode.CHANGE -> "Enter your current PIN"
        firstPin == null -> "Create a 4-digit PIN"
        else -> "Confirm your PIN"
    }

    fun submit() {
        if (pin.length != 4) {
            message = "Enter all 4 digits."
            return
        }
        when (mode) {
            PinMode.VERIFY, PinMode.CHANGE -> when (val r = vaultLock.checkPin(pin)) {
                VaultLock.PinResult.Ok -> onUnlocked()
                is VaultLock.PinResult.Wrong -> { message = "Wrong PIN. ${r.attemptsLeft} tries left before a lockout."; pin = "" }
                is VaultLock.PinResult.LockedOut -> { message = "Too many wrong tries. Try again in ${r.seconds} s."; pin = "" }
            }
            PinMode.CREATE -> {
                val first = firstPin
                if (first == null) {
                    firstPin = pin; pin = ""; message = null
                } else if (first == pin) {
                    vaultLock.setPin(pin)
                    vaultLock.method = VaultLock.Method.PIN
                    onUnlocked()
                } else {
                    firstPin = null; pin = ""; message = "The PINs did not match. Start again."
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
            if (mode == PinMode.CREATE) "Used to open saved passwords when fingerprint or a screen lock is not set up." else "Saved passwords are locked.",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(14.dp))
        // Four boxes mirror the hidden field below them.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(4) { i ->
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .brutalBox(fill = if (i < pin.length) AccentCyan else SurfaceCard, border = BorderSubtle, shape = RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (i < pin.length) Box(Modifier.size(12.dp).brutalBox(fill = InkBlack, border = InkBlack, shape = RoundedCornerShape(2.dp)))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        BrutalTextField(
            value = pin,
            onValueChange = { v -> pin = v.filter { it.isDigit() }.take(4); message = null },
            label = "PIN",
            isPassword = true,
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
            onImeAction = { submit() }
        )
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = DangerRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
