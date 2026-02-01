package com.onmeet.auth.security

import com.onmeet.auth.entity.ServerKey
import com.onmeet.auth.repository.jpa.ServerKeyRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import com.onmeet.auth.config.AuthProperties

@Component
class KeyManager(
    private val serverKeyRepository: ServerKeyRepository,
    private val authProperties: AuthProperties
) {
    companion object {
        private const val PBKDF2_ITERATIONS = 600000
        private const val SALT_LENGTH = 16
        private const val IV_LENGTH = 12
        private const val GCM_AUTH_TAG_LENGTH = 128
    }
    
    private val log = LoggerFactory.getLogger(KeyManager::class.java)
    private lateinit var rsaKeyPair: KeyPair
    private val secureRandom = java.security.SecureRandom()

    val publicKey: RSAPublicKey
        get() = rsaKeyPair.public as RSAPublicKey

    val privateKey: RSAPrivateKey
        get() = rsaKeyPair.private as RSAPrivateKey

    @PostConstruct
    fun init() {
        val existingKey = serverKeyRepository.findTopByOrderByCreatedAtDesc()
        if (existingKey.isPresent) {
            try {
                rsaKeyPair = loadKey(existingKey.get())
            } catch (e: Exception) {
                log.error("CRITICAL: Failed to load existing server key (possibly encryption mismatch). Aborting startup to prevent accidental token invalidation.", e)
                throw IllegalStateException("Failed to load server RSA key. Check auth.encryption-key configuration.", e)
            }
        } else {
            rsaKeyPair = generateAndSaveKey()
        }
    }

    private fun generateAndSaveKey(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val keyPair = keyPairGenerator.generateKeyPair()

        val pubKeyString = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val privKeyBytes = keyPair.private.encoded
        
        val encryptedPrivKey = encrypt(privKeyBytes)

        serverKeyRepository.save(ServerKey(publicKey = pubKeyString, encryptedPrivateKey = encryptedPrivKey))
        
        return keyPair
    }

    private fun loadKey(serverKey: ServerKey): KeyPair {
        val keyFactory = KeyFactory.getInstance("RSA")

        val pubKeyBytes = Base64.getDecoder().decode(serverKey.publicKey)
        val pubKeySpec = X509EncodedKeySpec(pubKeyBytes)
        val publicKey = keyFactory.generatePublic(pubKeySpec)

        val decryptedPrivKeyBytes = decrypt(serverKey.encryptedPrivateKey)
        val privKeySpec = PKCS8EncodedKeySpec(decryptedPrivKeyBytes)
        val privateKey = keyFactory.generatePrivate(privKeySpec)

        return KeyPair(publicKey, privateKey)
    }

    private fun getSecretKey(salt: ByteArray): javax.crypto.SecretKey {
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        // Use provided random salt
        val spec = javax.crypto.spec.PBEKeySpec(authProperties.encryptionKey.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val tmp = factory.generateSecret(spec)
        return javax.crypto.spec.SecretKeySpec(tmp.encoded, "AES")
    }

    private fun encrypt(data: ByteArray): String {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)
        
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = getSecretKey(salt)
        val iv = ByteArray(IV_LENGTH) // GCM standard IV length
        secureRandom.nextBytes(iv)
        val spec = javax.crypto.spec.GCMParameterSpec(GCM_AUTH_TAG_LENGTH, iv)
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, spec)

        val cipherText = cipher.doFinal(data)
        // Format: Salt (16) + IV (12) + CipherText
        val combined = ByteArray(salt.size + iv.size + cipherText.size)
        System.arraycopy(salt, 0, combined, 0, salt.size)
        System.arraycopy(iv, 0, combined, salt.size, iv.size)
        System.arraycopy(cipherText, 0, combined, salt.size + iv.size, cipherText.size)

        return Base64.getEncoder().encodeToString(combined)
    }

    private fun decrypt(encryptedString: String): ByteArray {
        val decoded = Base64.getDecoder().decode(encryptedString)
        
        // Extract Salt
        val salt = ByteArray(SALT_LENGTH)
        System.arraycopy(decoded, 0, salt, 0, SALT_LENGTH)

        // Extract IV
        val iv = ByteArray(IV_LENGTH)
        System.arraycopy(decoded, SALT_LENGTH, iv, 0, IV_LENGTH)
        
        // Extract Ciphertext
        val cipherTextOffset = SALT_LENGTH + IV_LENGTH
        val cipherText = ByteArray(decoded.size - cipherTextOffset)
        System.arraycopy(decoded, cipherTextOffset, cipherText, 0, cipherText.size)

        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = getSecretKey(salt)
        val spec = javax.crypto.spec.GCMParameterSpec(GCM_AUTH_TAG_LENGTH, iv)
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, spec)

        return cipher.doFinal(cipherText)
    }
}
