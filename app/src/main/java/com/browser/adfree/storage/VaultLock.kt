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
import android.os.SystemClock
import android.provider.Settings
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
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * Lock in front of the password vault.
 *
 * Preferred: the phone's own fingerprint/face or screen lock. Fallback: an app
 * passcode of 6 letters or digits. The passcode goes through Argon2id (memory
 * hard, per-install salt) and the result is then signed with a non-exportable
 * Android Keystore HMAC key, so a copy of the app's files cannot be used to
 * test guesses off the phone, and even a leaked Keystore key still leaves a
 * slow hash in the way. Wrong attempts lock out with growing delays; the
 * lockout state is signed and measured on the monotonic clock, so editing the
 * stored counters or the phone's date does not reset it.
 *
 * Passcodes set by older versions were 4 digits with a plain Keystore HMAC;
 * they still verify, and the caller then asks for a new passcode.
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

    /** The saved passcode is a 4-digit one from an older version and should be replaced. */
    val isLegacyPin: Boolean get() = hasPin && !prefs.contains(KEY_SALT)

    /** Characters the current passcode needs to enter. */
    val passcodeLength: Int get() = if (isLegacyPin) LEGACY_LENGTH else PASSCODE_LENGTH

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
        require(pin.length == PASSCODE_LENGTH && pin.all { it.isAsciiLetterOrDigit() })
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_VERIFIER, sign(argon2id(pin, salt)))
            .apply()
        saveState(LockState(0, 0, 0, bootCount()))
    }

    fun clearPin() {
        prefs.edit().remove(KEY_VERIFIER).remove(KEY_SALT).remove(KEY_STATE).remove(KEY_STATE_MAC)
            .remove(KEY_FAILS).remove(KEY_LOCK_UNTIL).apply()
    }

    /** Slow (Argon2id); call off the main thread. */
    fun checkPin(pin: String): PinResult {
        var state = loadState()
        val remaining = state.remainingSeconds()
        if (remaining > 0) return PinResult.LockedOut(remaining)
        val stored = prefs.getString(KEY_VERIFIER, null) ?: return PinResult.Wrong(0)
        val computed = if (isLegacyPin) sign(pin.toByteArray()) else {
            val salt = Base64.decode(prefs.getString(KEY_SALT, null), Base64.NO_WRAP)
            sign(argon2id(pin, salt))
        }
        if (MessageDigest.isEqual(stored.toByteArray(), computed.toByteArray())) {
            saveState(LockState(0, 0, 0, bootCount()))
            return PinResult.Ok
        }
        val fails = state.fails + 1
        // 5 free tries, then 30 s, 60 s, 120 s ... up to an hour per further mistake.
        return if (fails >= FREE_TRIES) {
            val seconds = (30L shl (fails - FREE_TRIES).coerceAtMost(7)).coerceAtMost(MAX_LOCK_SECONDS)
            saveState(lockedFor(fails, seconds))
            PinResult.LockedOut(seconds)
        } else {
            saveState(LockState(fails, 0, 0, bootCount()))
            PinResult.Wrong(FREE_TRIES - fails)
        }
    }

    private fun Char.isAsciiLetterOrDigit() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    private fun argon2id(pin: String, salt: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(ARGON_MEMORY_KB)
            .withIterations(ARGON_ITERATIONS)
            .withParallelism(ARGON_PARALLELISM)
            .build()
        val out = ByteArray(ARGON_OUT_BYTES)
        Argon2BytesGenerator().apply { init(params) }.generateBytes(pin.toByteArray(Charsets.UTF_8), out)
        return out
    }

    private fun sign(data: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey())
        return Base64.encodeToString(mac.doFinal(data), Base64.NO_WRAP)
    }

    /**
     * Failed-attempt state. [untilElapsed] is on the monotonic clock and only valid for the boot
     * it was written in ([boot]); after a reboot the wall-clock [untilWall] applies instead.
     */
    private data class LockState(val fails: Int, val untilElapsed: Long, val untilWall: Long, val boot: Int)

    private fun LockState.remainingSeconds(): Long {
        val leftMs = if (boot == bootCount()) untilElapsed - SystemClock.elapsedRealtime() else untilWall - System.currentTimeMillis()
        return if (leftMs > 0) (leftMs + 999) / 1000 else 0
    }

    private fun lockedFor(fails: Int, seconds: Long) = LockState(
        fails,
        SystemClock.elapsedRealtime() + seconds * 1000,
        System.currentTimeMillis() + seconds * 1000,
        bootCount()
    )

    private fun bootCount(): Int = Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT, 0)

    private fun saveState(state: LockState) {
        val raw = "${state.fails}|${state.untilElapsed}|${state.untilWall}|${state.boot}"
        prefs.edit().putString(KEY_STATE, raw).putString(KEY_STATE_MAC, sign(raw.toByteArray()))
            .remove(KEY_FAILS).remove(KEY_LOCK_UNTIL).apply()
    }

    private fun loadState(): LockState {
        val raw = prefs.getString(KEY_STATE, null)
        val mac = prefs.getString(KEY_STATE_MAC, null)
        if (raw != null && mac != null && MessageDigest.isEqual(mac.toByteArray(), sign(raw.toByteArray()).toByteArray())) {
            val p = raw.split('|')
            if (p.size == 4) return LockState(p[0].toInt(), p[1].toLong(), p[2].toLong(), p[3].toInt())
        }
        // Passcode from an older version: counters were plain integers.
        if (raw == null && prefs.contains(KEY_VERIFIER)) {
            val until = prefs.getLong(KEY_LOCK_UNTIL, 0)
            return LockState(prefs.getInt(KEY_FAILS, 0), 0, until, bootCount().minus(1))
        }
        // State missing or edited while a passcode exists: treat as tampering and lock for the maximum.
        return if (prefs.contains(KEY_VERIFIER)) lockedFor(FREE_TRIES, MAX_LOCK_SECONDS) else LockState(0, 0, 0, bootCount())
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
        private const val KEY_SALT = "pin_salt"
        private const val KEY_STATE = "pin_state"
        private const val KEY_STATE_MAC = "pin_state_mac"
        private const val KEY_FAILS = "pin_fails"
        private const val KEY_LOCK_UNTIL = "pin_locked_until"
        private const val KEY_METHOD = "unlock_method"
        private const val FREE_TRIES = 5
        private const val MAX_LOCK_SECONDS = 3600L
        const val PASSCODE_LENGTH = 6
        private const val LEGACY_LENGTH = 4
        private const val SALT_BYTES = 16
        private const val ARGON_MEMORY_KB = 32 * 1024
        private const val ARGON_ITERATIONS = 3
        private const val ARGON_PARALLELISM = 1
        private const val ARGON_OUT_BYTES = 32
    }
}
