package com.onmeet.auth.security

import com.onmeet.auth.entity.ServerKey
import com.onmeet.auth.repository.ServerKeyRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.beans.factory.annotation.Value
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

@Component
class KeyManager(
    private val serverKeyRepository: ServerKeyRepository,
    @Value("\${auth.encryption-key}") private val encryptionKey: String
) {
    private val log = LoggerFactory.getLogger(KeyManager::class.java)
    private lateinit var rsaKeyPair: KeyPair

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
                // If decryption fails, generating a new key will make previously encrypted private keys unrecoverable.
                // Consider a more robust key rotation/migration strategy or fail startup if decryption fails.
                log.warn("Failed to load existing key (possibly encryption mismatch). Generating new key.", e)
                rsaKeyPair = generateAndSaveKey()
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
        val spec = javax.crypto.spec.PBEKeySpec(encryptionKey.toCharArray(), salt, 65536, 256)
        val tmp = factory.generateSecret(spec)
        return javax.crypto.spec.SecretKeySpec(tmp.encoded, "AES")
    }

    private fun encrypt(data: ByteArray): String {
        val salt = ByteArray(16)
        java.security.SecureRandom().nextBytes(salt)
        
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = getSecretKey(salt)
        val iv = ByteArray(12) // GCM standard IV length
        java.security.SecureRandom().nextBytes(iv)
        val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
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
        val salt = ByteArray(16)
        System.arraycopy(decoded, 0, salt, 0, 16)

        // Extract IV
        val iv = ByteArray(12)
        System.arraycopy(decoded, 16, iv, 0, 12)
        
        // Extract Ciphertext
        val cipherText = ByteArray(decoded.size - 28) // 16 + 12
        System.arraycopy(decoded, 28, cipherText, 0, cipherText.size)

        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = getSecretKey(salt)
        val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, spec)

        return cipher.doFinal(cipherText)
    }
}
