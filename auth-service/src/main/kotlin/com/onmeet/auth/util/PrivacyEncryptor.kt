package com.onmeet.auth.util

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
@Converter
class PrivacyEncryptor : AttributeConverter<String, String> {

    @Value("\${privacy.encryption.key}")
    private lateinit var secretKey: String

    companion object {
        private const val AES_ALGORITHM = "AES"
        private const val GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ECB_TRANSFORMATION = "AES/ECB/PKCS5Padding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
    }

    override fun convertToDatabaseColumn(attribute: String?): String? {
        if (attribute == null) return null
        val keySpec = SecretKeySpec(secretKey.toByteArray(), AES_ALGORITHM)
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(attribute.toByteArray())
        return Base64.getEncoder().encodeToString(iv + ciphertext)
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        if (dbData == null) return null
        val decoded = Base64.getDecoder().decode(dbData)
        // GCM format: IV(12) + ciphertext + auth_tag(16) — minimum 28 bytes for any plaintext
        return if (decoded.size >= GCM_IV_LENGTH + GCM_TAG_BITS / 8) {
            try {
                decryptGcm(decoded)
            } catch (e: Exception) {
                // Backward compat: old ECB data may collide in length — fall back to ECB
                decryptEcb(decoded)
            }
        } else {
            decryptEcb(decoded)
        }
    }

    private fun decryptGcm(decoded: ByteArray): String {
        val keySpec = SecretKeySpec(secretKey.toByteArray(), AES_ALGORITHM)
        val iv = decoded.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = decoded.copyOfRange(GCM_IV_LENGTH, decoded.size)
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext))
    }

    private fun decryptEcb(decoded: ByteArray): String {
        val keySpec = SecretKeySpec(secretKey.toByteArray(), AES_ALGORITHM)
        val cipher = Cipher.getInstance(ECB_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keySpec)
        return String(cipher.doFinal(decoded))
    }
}
