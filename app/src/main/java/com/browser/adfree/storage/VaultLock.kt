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
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Lock in front of the password vault.
 *
 * Preferred: the phone's own fingerprint/face or screen lock. Fallback: a
 * 4-digit app PIN. A 4-digit PIN has only 10,000 values, so a slow hash
 * (Argon2id, PBKDF2) could still be brute-forced once copied off the phone;
 * instead the verifier is an HMAC under a non-exportable Android Keystore key,
 * which can only be computed on this device, and wrong attempts lock out with
 * growing delays.
 */
class VaultLock(context: Context) {

    enum class Method { SYSTEM, PIN }

    sealed interface PinResult {
        data object Ok : PinResult
        data class Wrong(val attemptsLeft: Int) : PinResult
        data class LockedOut(val seconds: Long) : PinResult
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("vault_lock", Context.MODE_PRIVATE)

    private val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** Fingerprint/face or a screen lock is set up on the phone. */
    val systemAvailable: Boolean
        get() = BiometricManager.from(appContext).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    val hasPin: Boolean get() = prefs.contains(KEY_VERIFIER)

    var method: Method
        get() = runCatching { Method.valueOf(prefs.getString(KEY_METHOD, null) ?: "") }.getOrNull()
            ?: if (systemAvailable) Method.SYSTEM else Method.PIN
        set(value) = prefs.edit().putString(KEY_METHOD, value.name).apply()

    /** Shows the system prompt (fingerprint, falling back to the phone's screen lock). */
    fun promptSystem(activity: FragmentActivity, onSuccess: () -> Unit, onFail: (String) -> Unit) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_CANCELED) {
                    onFail(errString.toString())
                }
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock passwords")
            .setSubtitle("Confirm it is you to see saved passwords")
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }

    fun setPin(pin: String) {
        require(pin.length == 4 && pin.all { it.isDigit() })
        prefs.edit()
            .putString(KEY_VERIFIER, verifier(pin))
            .putInt(KEY_FAILS, 0)
            .putLong(KEY_LOCK_UNTIL, 0)
            .apply()
    }

    fun clearPin() {
        prefs.edit().remove(KEY_VERIFIER).remove(KEY_FAILS).remove(KEY_LOCK_UNTIL).apply()
    }

    fun checkPin(pin: String): PinResult {
        val now = System.currentTimeMillis()
        val lockedUntil = prefs.getLong(KEY_LOCK_UNTIL, 0)
        if (lockedUntil > now) return PinResult.LockedOut((lockedUntil - now + 999) / 1000)
        val stored = prefs.getString(KEY_VERIFIER, null) ?: return PinResult.Wrong(0)
        val ok = MessageDigest.isEqual(stored.toByteArray(), verifier(pin).toByteArray())
        if (ok) {
            prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCK_UNTIL, 0).apply()
            return PinResult.Ok
        }
        val fails = prefs.getInt(KEY_FAILS, 0) + 1
        val edit = prefs.edit().putInt(KEY_FAILS, fails)
        // 5 free tries, then 30 s, 60 s, 120 s ... up to an hour per further mistake.
        return if (fails >= FREE_TRIES) {
            val seconds = (30L shl (fails - FREE_TRIES).coerceAtMost(7)).coerceAtMost(3600L)
            edit.putLong(KEY_LOCK_UNTIL, now + seconds * 1000).apply()
            PinResult.LockedOut(seconds)
        } else {
            edit.apply()
            PinResult.Wrong(FREE_TRIES - fails)
        }
    }

    private fun verifier(pin: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey())
        return Base64.encodeToString(mac.doFinal(pin.toByteArray()), Base64.NO_WRAP)
    }

    private fun hmacKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN).build())
        return gen.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "gecko_vault_pin_hmac"
        private const val KEY_VERIFIER = "pin_verifier"
        private const val KEY_FAILS = "pin_fails"
        private const val KEY_LOCK_UNTIL = "pin_locked_until"
        private const val KEY_METHOD = "unlock_method"
        private const val FREE_TRIES = 5
    }
}
