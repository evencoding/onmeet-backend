package com.onmeet.auth.util

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@Component
@Converter
class PrivacyEncryptor : AttributeConverter<String, String> {

    @Value("\${privacy.encryption.key}")
    private lateinit var secretKey: String

    private val algorithm = "AES"

    override fun convertToDatabaseColumn(attribute: String?): String? {
        if (attribute == null) return null
        val key = SecretKeySpec(secretKey.toByteArray(), algorithm)
        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return Base64.getEncoder().encodeToString(cipher.doFinal(attribute.toByteArray()))
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        if (dbData == null) return null
        val key = SecretKeySpec(secretKey.toByteArray(), algorithm)
        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.DECRYPT_MODE, key)
        return String(cipher.doFinal(Base64.getDecoder().decode(dbData)))
    }
}
