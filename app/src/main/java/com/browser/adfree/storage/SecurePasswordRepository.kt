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
        /** 2: usernames encrypted and every ciphertext bound to its entry id. */
        private const val FORMAT_VERSION = 2
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

    /** [aad] ties the ciphertext to its entry, so records cannot be swapped around in the file. */
    private fun encrypt(plainText: String, aad: String): Pair<String, String> {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        cipher.updateAAD(aad.toByteArray(StandardCharsets.UTF_8))
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

        val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

        return Pair(encryptedBase64, ivBase64)
    }

    private fun decrypt(encryptedBase64: String, ivBase64: String, aad: String?): String {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        if (aad != null) cipher.updateAAD(aad.toByteArray(StandardCharsets.UTF_8))
        return String(cipher.doFinal(Base64.decode(encryptedBase64, Base64.NO_WRAP)), StandardCharsets.UTF_8)
    }

    fun decryptPassword(savedLogin: SavedLogin): String {
        return try {
            try {
                decrypt(savedLogin.encryptedPasswordBase64, savedLogin.ivBase64, savedLogin.id)
            } catch (e: Exception) {
                // Saved before entries were bound to their id.
                decrypt(savedLogin.encryptedPasswordBase64, savedLogin.ivBase64, null)
            }
        } catch (e: Exception) {
            android.util.Log.w("GeckoApp", "Ignored ${e.javaClass.simpleName}")
            "• • • • • •"
        }
    }

    private fun loadLogins() {
        try {
            if (file.exists()) {
                val jsonStr = file.readText()
                val jsonArray = JSONArray(jsonStr)
                logins.clear()
                var migrated = false
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optString("id", java.util.UUID.randomUUID().toString())
                    var encPass = obj.getString("encryptedPasswordBase64")
                    var passIv = obj.getString("ivBase64")
                    val username = if (obj.has("u")) {
                        decrypt(obj.getString("u"), obj.getString("uiv"), "$id:user")
                    } else {
                        migrated = true
                        obj.getString("username")
                    }
                    if (obj.optInt("v", 1) < FORMAT_VERSION) {
                        migrated = true
                        val plain = try { decrypt(encPass, passIv, null) } catch (e: Exception) { null }
                        if (plain != null) encrypt(plain, id).let { encPass = it.first; passIv = it.second }
                    }
                    logins.add(
                        SavedLogin(
                            id = id,
                            domain = obj.getString("domain"),
                            username = username,
                            encryptedPasswordBase64 = encPass,
                            ivBase64 = passIv,
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
                if (migrated) persistLogins()
            }
        } catch (e: Exception) {
            android.util.Log.w("GeckoApp", "Ignored ${e.javaClass.simpleName}")
        }
    }

    fun saveLogin(domain: String, username: String, plainPassword: String) {
        val cleanDomain = domain.trim().lowercase()
        val cleanUser = username.trim()
        if (cleanDomain.isBlank() || plainPassword.isEmpty()) return

        // Replace existing login for this domain & username if present
        val existingIndex = logins.indexOfFirst {
            it.domain.equals(cleanDomain, ignoreCase = true) && it.username == cleanUser
        }
        val id = if (existingIndex >= 0) logins[existingIndex].id else java.util.UUID.randomUUID().toString()
        val (encPass, iv) = encrypt(plainPassword, id)

        val newLogin = SavedLogin(
            id = id,
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
                    val (encUser, userIv) = encrypt(login.username, "${login.id}:user")
                    put("u", encUser)
                    put("uiv", userIv)
                    put("encryptedPasswordBase64", login.encryptedPasswordBase64)
                    put("ivBase64", login.ivBase64)
                    put("v", FORMAT_VERSION)
                    put("updatedAt", login.updatedAt)
                }
                jsonArray.put(obj)
            }
            val json = jsonArray.toString()
            DiskWriter.save(file) { json }
        } catch (e: Exception) {
            android.util.Log.w("GeckoApp", "Ignored ${e.javaClass.simpleName}")
        }
    }
}
