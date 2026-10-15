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

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.adfree.model.SavedLogin
import com.browser.adfree.storage.SecurePasswordRepository
import com.browser.adfree.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PasswordsPage(
    passwordRepository: SecurePasswordRepository,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SavedLogin?>(null) }
    val logins = passwordRepository.logins
    val shown = logins.filter {
        query.isBlank() || it.domain.contains(query, ignoreCase = true) || it.username.contains(query, ignoreCase = true)
    }.sortedBy { it.domain }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .imePadding()
    ) {
        BrutalPageBar(title = "Passwords", onBack = onClose) {
            BrutalIconButton(Icons.Default.Add, "Add password", { showAdd = true })
        }
        if (logins.isEmpty()) {
            BrutalEmptyState(
                title = "No saved passwords",
                message = "When you sign in to a site in a normal tab, Gecko offers to save the login here. Passwords are encrypted with a key kept in the phone's secure hardware.",
                actionLabel = "Add a password",
                onAction = { showAdd = true }
            )
        } else {
            BrutalSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search sites and usernames",
                modifier = Modifier.padding(start = 16.dp, end = 19.dp, top = 12.dp, bottom = 16.dp)
            )
            BrutalRule()
            if (shown.isEmpty()) {
                BrutalEmptyState(title = "Nothing matches", message = "No site or username contains \"$query\".", actionLabel = "Clear search", onAction = { query = "" })
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(shown, key = { it.id }) { login ->
                        LoginRow(
                            login = login,
                            decrypt = { passwordRepository.decryptPassword(login) },
                            onDelete = { pendingDelete = login },
                            modifier = Modifier.animateItem()
                        )
                        BrutalHairline(startIndent = 62.dp)
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }

    if (showAdd) {
        AddLoginDialog(
            onSave = { domain, user, pass ->
                passwordRepository.saveLogin(domain, user, pass)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }
    pendingDelete?.let { login ->
        BrutalDialog(
            title = "Delete this password?",
            onDismiss = { pendingDelete = null },
            confirmLabel = "Delete",
            confirmFill = DangerRed,
            onConfirm = {
                passwordRepository.deleteLogin(login.id)
                pendingDelete = null
            },
            dismissLabel = "Cancel"
        ) {
            Text("${login.username.ifBlank { "No username" }} on ${login.domain}", color = TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun LoginRow(
    login: SavedLogin,
    decrypt: () -> String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf<String?>(null) }

    fun withPassword(action: (String) -> Unit) {
        scope.launch {
            // AES-GCM with a Keystore key: keep it off the main thread.
            val plain = withContext(Dispatchers.IO) { runCatching(decrypt).getOrNull() }
            if (plain == null) Toast.makeText(context, "Could not decrypt this password", Toast.LENGTH_SHORT).show()
            else action(plain)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .brutalTap(shape = RectangleShape, haptic = false, pressScale = 1f) {
                    expanded = !expanded
                    if (!expanded) revealed = null
                }
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
        ) {
            SiteFavicon("https://${login.domain}")
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(login.domain.removePrefix("www."), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(login.username.ifBlank { "No username" }, color = TextSecondary, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BrutalIconButton(Icons.Default.ContentCopy, "Copy password", {
                withPassword { copySensitive(context, it); Toast.makeText(context, "Password copied", Toast.LENGTH_SHORT).show() }
            }, iconSize = 19.dp)
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 62.dp, end = 4.dp, bottom = 10.dp)
            ) {
                Text(
                    text = revealed ?: "•".repeat(10),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .weight(1f)
                        .brutalBox(fill = SurfaceCard, border = BorderSubtle, borderWidth = 1.5.dp, shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
                BrutalIconButton(
                    if (revealed == null) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    if (revealed == null) "Show password" else "Hide password",
                    { if (revealed == null) withPassword { revealed = it } else revealed = null },
                    iconSize = 20.dp
                )
                BrutalIconButton(Icons.Default.DeleteOutline, "Delete", onDelete, tint = DangerRed, iconSize = 20.dp)
            }
        }
    }
}

private fun copySensitive(context: Context, text: String) {
    val clip = ClipData.newPlainText("password", text)
    // Hides the value from the clipboard preview and keyboard suggestions.
    val extras = PersistableBundle().apply {
        if (Build.VERSION.SDK_INT >= 33) putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        else putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
    clip.description.extras = extras
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
}

@Composable
private fun AddLoginDialog(onSave: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var domain by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val valid = domain.contains(".") && pass.isNotEmpty()
    BrutalDialog(
        title = "Add password",
        onDismiss = onDismiss,
        confirmLabel = "Save",
        onConfirm = {
            if (valid) {
                val host = domain.trim().removePrefix("https://").removePrefix("http://").substringBefore("/")
                onSave(host, user.trim(), pass)
            }
        },
        dismissLabel = "Cancel"
    ) {
        BrutalTextField(domain, { domain = it }, "Site", placeholder = "example.com", keyboardType = KeyboardType.Uri)
        Spacer(Modifier.height(12.dp))
        BrutalTextField(user, { user = it }, "Username", placeholder = "Email or name", keyboardType = KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        BrutalTextField(pass, { pass = it }, "Password", isPassword = true, imeAction = ImeAction.Done)
        if (!valid && (domain.isNotEmpty() || pass.isNotEmpty())) {
            Text("Enter a site address and a password.", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
