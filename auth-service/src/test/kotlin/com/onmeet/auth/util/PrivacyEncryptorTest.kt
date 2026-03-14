package com.onmeet.auth.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PrivacyEncryptorTest {

    private lateinit var encryptor: PrivacyEncryptor

    // AES requires exactly 16, 24, or 32 bytes
    private val testKey = "1234567890123456" // 16-byte key

    @BeforeEach
    fun setUp() {
        encryptor = PrivacyEncryptor()
        val field = PrivacyEncryptor::class.java.getDeclaredField("secretKey")
        field.isAccessible = true
        field.set(encryptor, testKey)
    }

    @Test
    fun `GCM round-trip should encrypt and then decrypt to original value`() {
        // given
        val plaintext = "sensitive@email.com"

        // when
        val encrypted = encryptor.convertToDatabaseColumn(plaintext)
        val decrypted = encryptor.convertToEntityAttribute(encrypted)

        // then
        assertNotNull(encrypted)
        assertNotEquals(plaintext, encrypted)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypt should produce different ciphertext for same plaintext due to random IV`() {
        // given
        val plaintext = "test_value"

        // when
        val encrypted1 = encryptor.convertToDatabaseColumn(plaintext)
        val encrypted2 = encryptor.convertToDatabaseColumn(plaintext)

        // then - different IVs produce different ciphertext
        assertNotEquals(encrypted1, encrypted2)
        // but both decrypt to same plaintext
        assertEquals(plaintext, encryptor.convertToEntityAttribute(encrypted1))
        assertEquals(plaintext, encryptor.convertToEntityAttribute(encrypted2))
    }

    @Test
    fun `convertToDatabaseColumn should return null for null input`() {
        // when
        val result = encryptor.convertToDatabaseColumn(null)

        // then
        assertNull(result)
    }

    @Test
    fun `convertToEntityAttribute should return null for null input`() {
        // when
        val result = encryptor.convertToEntityAttribute(null)

        // then
        assertNull(result)
    }

    @Test
    fun `round-trip should handle empty string`() {
        // given
        val plaintext = ""

        // when
        val encrypted = encryptor.convertToDatabaseColumn(plaintext)
        val decrypted = encryptor.convertToEntityAttribute(encrypted)

        // then
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `round-trip should handle unicode characters`() {
        // given
        val plaintext = "홍길동@회사.com"

        // when
        val encrypted = encryptor.convertToDatabaseColumn(plaintext)
        val decrypted = encryptor.convertToEntityAttribute(encrypted)

        // then
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `round-trip should handle long strings`() {
        // given
        val plaintext = "a".repeat(1000)

        // when
        val encrypted = encryptor.convertToDatabaseColumn(plaintext)
        val decrypted = encryptor.convertToEntityAttribute(encrypted)

        // then
        assertEquals(plaintext, decrypted)
    }
}
