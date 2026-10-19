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

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.browser.adfree.storage.Backup
import com.browser.adfree.storage.BackupCodec
import com.browser.adfree.storage.BrowserPreferencesRepository
import com.browser.adfree.storage.FavoritesRepository
import com.browser.adfree.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_BACKUP_BYTES = 4 * 1024 * 1024

/**
 * Export and import of bookmarks and settings as one JSON file the user keeps.
 * Passwords, history, cookies and the VPN route are never included.
 */
@Composable
fun BackupSection(preferencesRepo: BrowserPreferencesRepository, favoritesRepo: FavoritesRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val json = BackupCodec.encode(Backup(favoritesRepo.favorites.toList(), preferencesRepo.exportSettings()))
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) } != null
                    } catch (e: Exception) {
                        false
                    }
                }
                toast(if (ok) "Backup saved" else "Could not write the backup file")
            }
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val backup = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val bytes = stream.readBytes()
                            if (bytes.size > MAX_BACKUP_BYTES) null else BackupCodec.decode(String(bytes))
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                if (backup == null) {
                    toast("That file is not a backup from this browser")
                } else {
                    val added = favoritesRepo.importFavorites(backup.favorites)
                    val applied = preferencesRepo.importSettings(backup.settings)
                    toast("Restored $added bookmarks and $applied settings. Restart the browser to apply the settings")
                }
            }
        }
    }

    BrutalSectionLabel("Backup")
    SettingsGroup(shadow = CardShadows[2]) {
        BrutalListRow(
            title = "Export bookmarks and settings",
            subtitle = "Saves a file you keep. Passwords and history are not included",
            icon = Icons.Default.FileUpload,
            onClick = { exporter.launch("gecko-backup.json") },
            trailing = { Chevron() }
        )
        BrutalHairline(startIndent = 64.dp)
        BrutalListRow(
            title = "Import from a backup",
            subtitle = "Adds missing bookmarks and replaces settings",
            icon = Icons.Default.FileDownload,
            onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) },
            trailing = { Chevron() }
        )
    }
}
