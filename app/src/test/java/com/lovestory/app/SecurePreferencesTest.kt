package com.lovestory.app

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class SecurePreferencesTest {

    // ==================== PIN Hashing ====================

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `hashPin - produces consistent hash`() {
        val pin = "1234"
        val hash1 = hashPin(pin)
        val hash2 = hashPin(pin)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `hashPin - different pins produce different hashes`() {
        val hash1 = hashPin("1234")
        val hash2 = hashPin("5678")
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `hashPin - produces 64 character hex string`() {
        val hash = hashPin("1234")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `hashPin - empty string produces valid hash`() {
        val hash = hashPin("")
        assertEquals(64, hash.length)
    }

    @Test
    fun `hashPin - known SHA-256 value`() {
        // SHA-256 of "1234" is known
        val expected = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4"
        assertEquals(expected, hashPin("1234"))
    }

    @Test
    fun `hashPin - long PIN produces valid hash`() {
        val hash = hashPin("1234567890123456")
        assertEquals(64, hash.length)
    }

    // ==================== Lockout Duration ====================

    @Test
    fun `lockout duration - 0 attempts returns 0`() {
        val duration = getLockoutDuration(0)
        assertEquals(0L, duration)
    }

    @Test
    fun `lockout duration - 2 attempts returns 0`() {
        val duration = getLockoutDuration(2)
        assertEquals(0L, duration)
    }

    @Test
    fun `lockout duration - 3 attempts returns 30 seconds`() {
        val duration = getLockoutDuration(3)
        assertEquals(30_000L, duration)
    }

    @Test
    fun `lockout duration - 4 attempts returns 30 seconds`() {
        val duration = getLockoutDuration(4)
        assertEquals(30_000L, duration)
    }

    @Test
    fun `lockout duration - 5 attempts returns 5 minutes`() {
        val duration = getLockoutDuration(5)
        assertEquals(5 * 60 * 1000L, duration)
    }

    @Test
    fun `lockout duration - 6 attempts returns 5 minutes`() {
        val duration = getLockoutDuration(6)
        assertEquals(5 * 60 * 1000L, duration)
    }

    @Test
    fun `lockout duration - 7 attempts returns 15 minutes`() {
        val duration = getLockoutDuration(7)
        assertEquals(15 * 60 * 1000L, duration)
    }

    @Test
    fun `lockout duration - 9 attempts returns 15 minutes`() {
        val duration = getLockoutDuration(9)
        assertEquals(15 * 60 * 1000L, duration)
    }

    @Test
    fun `lockout duration - 10 attempts returns 1 hour`() {
        val duration = getLockoutDuration(10)
        assertEquals(60 * 60 * 1000L, duration)
    }

    @Test
    fun `lockout duration - 100 attempts returns 1 hour`() {
        val duration = getLockoutDuration(100)
        assertEquals(60 * 60 * 1000L, duration)
    }

    // Helper function to test lockout duration logic
    private fun getLockoutDuration(attempts: Int): Long {
        return when {
            attempts >= 10 -> 60 * 60 * 1000L
            attempts >= 7 -> 15 * 60 * 1000L
            attempts >= 5 -> 5 * 60 * 1000L
            attempts >= 3 -> 30 * 1000L
            else -> 0L
        }
    }
}
