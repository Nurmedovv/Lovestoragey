package com.lovestory.app.domain.repository

// PIN-код, настройки автолока и счётчики неудачных попыток.
// Контекст реализация получает в конструкторе (application context)
interface LockRepository {
    fun getPinCode(): String?
    fun savePinCode(pin: String)
    fun verifyPin(enteredPin: String): Boolean
    fun removePinCode()

    fun isAuthEnabled(): Boolean
    fun setAuthEnabled(enabled: Boolean)

    fun getLockTimeout(): Long
    fun setLockTimeout(timeoutMs: Long)

    fun getLastForegroundTime(): Long
    fun setLastForegroundTime(timeMs: Long)

    fun getFailedAttempts(): Int
    fun incrementFailedAttempts()
    fun resetFailedAttempts()

    fun getLockUntil(): Long
    fun setLockUntil(timeMs: Long)
    fun isLockedOut(): Boolean
    fun getLockoutDuration(): Long
}
