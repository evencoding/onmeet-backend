package com.onmeet.auth.controller

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.onmeet.auth.security.KeyManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class JwkController(
    private val keyManager: KeyManager,
    @Value("\${jwt.key-id}") private val keyId: String
) {

    @GetMapping("/.well-known/jwks.json")
    fun keys(): Map<String, Any> {
        val rsaKey = RSAKey.Builder(keyManager.publicKey)
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .keyID(keyId)
            .build()

        return JWKSet(rsaKey.toPublicJWK()).toJSONObject()
    }
}
