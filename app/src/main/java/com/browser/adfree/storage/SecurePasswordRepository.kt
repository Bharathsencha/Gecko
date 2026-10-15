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

package com.browser.adfree.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.mutableStateListOf
import com.browser.adfree.model.SavedLogin
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecurePasswordRepository(private val context: Context) {

    val logins = mutableStateListOf<SavedLogin>()
    private val file = File(context.filesDir, "secure_logins.json")

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "AdFreeMasterPasswordKey_v1"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }

    init {
        ensureMasterKey()
        loadLogins()
    }

    private fun ensureMasterKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("Keystore entry for $KEY_ALIAS not found")
        return entry.secretKey
    }

    fun encryptPassword(plainText: String): Pair<String, String> {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

        val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

        return Pair(encryptedBase64, ivBase64)
    }

    fun decryptPassword(savedLogin: SavedLogin): String {
        return try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val iv = Base64.decode(savedLogin.ivBase64, Base64.NO_WRAP)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

            val encryptedBytes = Base64.decode(savedLogin.encryptedPasswordBase64, Base64.NO_WRAP)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            "• • • • • •"
        }
    }

    private fun loadLogins() {
        try {
            if (file.exists()) {
                val jsonStr = file.readText()
                val jsonArray = JSONArray(jsonStr)
                logins.clear()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    logins.add(
                        SavedLogin(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            domain = obj.getString("domain"),
                            username = obj.getString("username"),
                            encryptedPasswordBase64 = obj.getString("encryptedPasswordBase64"),
                            ivBase64 = obj.getString("ivBase64"),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveLogin(domain: String, username: String, plainPassword: String) {
        val cleanDomain = domain.trim().lowercase()
        val cleanUser = username.trim()
        if (cleanDomain.isBlank() || plainPassword.isEmpty()) return

        val (encPass, iv) = encryptPassword(plainPassword)

        // Replace existing login for this domain & username if present
        val existingIndex = logins.indexOfFirst {
            it.domain.equals(cleanDomain, ignoreCase = true) && it.username == cleanUser
        }

        val newLogin = SavedLogin(
            id = if (existingIndex >= 0) logins[existingIndex].id else java.util.UUID.randomUUID().toString(),
            domain = cleanDomain,
            username = cleanUser,
            encryptedPasswordBase64 = encPass,
            ivBase64 = iv,
            updatedAt = System.currentTimeMillis()
        )

        if (existingIndex >= 0) {
            logins[existingIndex] = newLogin
        } else {
            logins.add(0, newLogin)
        }
        persistLogins()
    }

    fun deleteLogin(id: String) {
        val removed = logins.removeAll { it.id == id }
        if (removed) {
            persistLogins()
        }
    }

    private fun persistLogins() {
        try {
            val jsonArray = JSONArray()
            for (login in logins) {
                val obj = JSONObject().apply {
                    put("id", login.id)
                    put("domain", login.domain)
                    put("username", login.username)
                    put("encryptedPasswordBase64", login.encryptedPasswordBase64)
                    put("ivBase64", login.ivBase64)
                    put("updatedAt", login.updatedAt)
                }
                jsonArray.put(obj)
            }
            val tempFile = File(context.filesDir, "secure_logins.json.tmp")
            tempFile.writeText(jsonArray.toString())
            tempFile.renameTo(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
