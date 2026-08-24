package com.lovestory.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import com.lovestory.app.domain.repository.AppPrefs
import com.lovestory.app.domain.repository.LockRepository

// контекст приходит из AppContainer (application context) —
// все хранилища app-scoped, activity-контекст не нужен
class SecurePreferences(private val context: Context) : LockRepository {

    companion object {
        private const val TAG = "SecurePreferences"
        private const val SECURE_PREFS_NAME = "secure_app_settings"
        private const val LEGACY_PREFS_NAME = AppPrefs.PREFS_NAME
    }

    @Volatile
    private var securePrefsInstance: SharedPreferences? = null

    private fun getSecurePrefs(): SharedPreferences {
        return securePrefsInstance ?: synchronized(this) {
            securePrefsInstance ?: run {
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                EncryptedSharedPreferences.create(
                    SECURE_PREFS_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ).also { securePrefsInstance = it }
            }
        }
    }

    private fun getPlainPrefs(): SharedPreferences {
        return context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun hashPinLegacy(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun getOrCreateSalt(): ByteArray {
        val secure = getSecurePrefs()
        val existing = secure.getString("pin_salt", null)
        if (existing != null) {
            return android.util.Base64.decode(existing, android.util.Base64.DEFAULT)
        }
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        secure.edit().putString("pin_salt", android.util.Base64.encodeToString(salt, android.util.Base64.DEFAULT)).apply()
        return salt
    }

    private fun hashPinV2(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 100_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return hash.joinToString("") { "%02x".format(it) }
    }

    override fun getPinCode(): String? {
        val secure = getSecurePrefs()
        val pin = secure.getString("pin_code", null)
        if (pin != null) return pin

        val legacy = getPlainPrefs()
        val legacyPin = legacy.getString("pin_code", null)
        if (legacyPin != null) {
            try {
                secure.edit().putString("pin_code", hashPinLegacy(legacyPin)).apply()
                legacy.edit().remove("pin_code").apply()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Ошибка миграции PIN", e)
            }
            return hashPinLegacy(legacyPin)
        }
        return null
    }

    override fun savePinCode(pin: String) {
        val salt = getOrCreateSalt()
        getSecurePrefs().edit().putString("pin_code", hashPinV2(pin, salt)).apply()
        getPlainPrefs().edit().remove("pin_code").apply()
    }

    override fun verifyPin(enteredPin: String): Boolean {
        val savedHash = getPinCode() ?: return false
        val secure = getSecurePrefs()
        val salt = secure.getString("pin_salt", null)
        if (salt != null) {
            val saltBytes = android.util.Base64.decode(salt, android.util.Base64.DEFAULT)
            if (hashPinV2(enteredPin, saltBytes) == savedHash) return true
        }
        if (hashPinLegacy(enteredPin) == savedHash) {
            val saltBytes = getOrCreateSalt()
            secure.edit().putString("pin_code", hashPinV2(enteredPin, saltBytes)).apply()
            return true
        }
        return false
    }

    override fun removePinCode() {
        getSecurePrefs().edit().remove("pin_code").remove("pin_salt").apply()
        getPlainPrefs().edit().remove("pin_code").apply()
    }

    override fun isAuthEnabled(): Boolean {
        val secure = getSecurePrefs()
        if (secure.contains("auth_enabled")) return secure.getBoolean("auth_enabled", false)
        val legacy = getPlainPrefs()
        val legacyValue = legacy.getBoolean("auth_enabled", false)
        try {
            secure.edit().putBoolean("auth_enabled", legacyValue).apply()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Ошибка миграции auth_enabled", e)
        }
        return legacyValue
    }

    override fun setAuthEnabled(enabled: Boolean) {
        getSecurePrefs().edit().putBoolean("auth_enabled", enabled).apply()
    }

    override fun getLockTimeout(): Long {
        return getSecurePrefs().getLong("lock_timeout_ms", 60_000L)
    }

    override fun setLockTimeout(timeoutMs: Long) {
        getSecurePrefs().edit().putLong("lock_timeout_ms", timeoutMs).apply()
    }

    override fun getLastForegroundTime(): Long {
        return getPlainPrefs().getLong("last_foreground_time", 0L)
    }

    override fun setLastForegroundTime(timeMs: Long) {
        getPlainPrefs().edit().putLong("last_foreground_time", timeMs).apply()
    }

    override fun getFailedAttempts(): Int {
        return getSecurePrefs().getInt("failed_attempts", 0)
    }

    @Synchronized
    override fun incrementFailedAttempts() {
        val current = getFailedAttempts()
        getSecurePrefs().edit().putInt("failed_attempts", current + 1).apply()
    }

    override fun resetFailedAttempts() {
        getSecurePrefs().edit().putInt("failed_attempts", 0).apply()
    }

    override fun getLockUntil(): Long {
        return getSecurePrefs().getLong("lock_until", 0L)
    }

    override fun setLockUntil(timeMs: Long) {
        getSecurePrefs().edit().putLong("lock_until", timeMs).apply()
    }

    override fun isLockedOut(): Boolean {
        val lockUntil = getLockUntil()
        return lockUntil > System.currentTimeMillis()
    }

    override fun getLockoutDuration(): Long {
        val attempts = getFailedAttempts()
        return when {
            attempts >= 10 -> 60 * 60 * 1000L
            attempts >= 7 -> 15 * 60 * 1000L
            attempts >= 5 -> 5 * 60 * 1000L
            attempts >= 3 -> 30 * 1000L
            else -> 0L
        }
    }
}
