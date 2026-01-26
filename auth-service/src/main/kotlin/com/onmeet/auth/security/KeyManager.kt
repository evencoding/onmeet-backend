package com.onmeet.auth.security

import com.onmeet.auth.entity.ServerKey
import com.onmeet.auth.repository.ServerKeyRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
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
    private val serverKeyRepository: ServerKeyRepository
) {

    private lateinit var rsaKeyPair: KeyPair

    val publicKey: RSAPublicKey
        get() = rsaKeyPair.public as RSAPublicKey

    val privateKey: RSAPrivateKey
        get() = rsaKeyPair.private as RSAPrivateKey

    @PostConstruct
    fun init() {
        val existingKey = serverKeyRepository.findTopByOrderByCreatedAtDesc()
        if (existingKey.isPresent) {
            rsaKeyPair = loadKey(existingKey.get())
        } else {
            rsaKeyPair = generateAndSaveKey()
        }
    }

    private fun generateAndSaveKey(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val keyPair = keyPairGenerator.generateKeyPair()

        val pubKeyString = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val privKeyString = Base64.getEncoder().encodeToString(keyPair.private.encoded)

        serverKeyRepository.save(ServerKey(publicKey = pubKeyString, privateKey = privKeyString))
        
        return keyPair
    }

    private fun loadKey(serverKey: ServerKey): KeyPair {
        val keyFactory = KeyFactory.getInstance("RSA")

        val pubKeyBytes = Base64.getDecoder().decode(serverKey.publicKey)
        val pubKeySpec = X509EncodedKeySpec(pubKeyBytes)
        val publicKey = keyFactory.generatePublic(pubKeySpec)

        val privKeyBytes = Base64.getDecoder().decode(serverKey.privateKey)
        val privKeySpec = PKCS8EncodedKeySpec(privKeyBytes)
        val privateKey = keyFactory.generatePrivate(privKeySpec)

        return KeyPair(publicKey, privateKey)
    }
}
