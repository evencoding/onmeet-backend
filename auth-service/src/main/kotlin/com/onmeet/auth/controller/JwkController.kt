package com.onmeet.auth.controller

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.onmeet.auth.config.JwtProperties
import com.onmeet.auth.security.KeyManager
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Auth - JWK", description = "JWT 검증을 위한 공개키(JWK) 제공 API")
class JwkController(
    private val keyManager: KeyManager,
    private val jwtProperties: JwtProperties
) {

    @Operation(summary = "JWK Set 조회", description = "OAuth2 Resource Server에서 토큰 서명을 검증하기 위한 공개키 목록을 반환합니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "JWK Set 반환 성공")
    ])
    @GetMapping("/.well-known/jwks.json")
    fun keys(): Map<String, Any> {
        val rsaKey = RSAKey.Builder(keyManager.publicKey)
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .keyID(jwtProperties.keyId)
            .build()

        return JWKSet(rsaKey.toPublicJWK()).toJSONObject()
    }
}
