package com.onmeet.auth.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.onmeet.common.security.JwtConstants
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import java.util.*

@Component
class JwtTokenProvider(
    private val keyManager: KeyManager,
    @org.springframework.beans.factory.annotation.Value("\${jwt.validity-in-ms}") private val validityInMs: Long,
    @org.springframework.beans.factory.annotation.Value("\${jwt.key-id}") private val keyId: String
) {

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(JwtTokenProvider::class.java)
    }

    fun generateToken(authentication: Authentication): String {
        val authorities = authentication.authorities.joinToString(",") { it.authority }

        val now = Date()
        val validity = Date(now.time + validityInMs)

        // Type cast principal to our User entity to get the ID
        val principal = authentication.principal as com.onmeet.auth.entity.User

        // Build Claims
        val claimsSet = JWTClaimsSet.Builder()
            .subject(authentication.name)
            .claim(JwtConstants.ROLE_CLAIM, authorities)
            .claim(JwtConstants.USER_ID_CLAIM, principal.id)
            .issueTime(now)
            .expirationTime(validity)
            .jwtID(UUID.randomUUID().toString())
            .build()

        return signJwt(claimsSet)
    }

    fun generateGuestToken(name: String, meetingId: String?): String {
        val now = Date()
        val validity = Date(now.time + 14400000) // 4 hours

        val claimsSet = JWTClaimsSet.Builder()
            .subject(name)
            .claim(JwtConstants.ROLE_CLAIM, "ROLE_GUEST")
            .claim(JwtConstants.USER_ID_CLAIM, 0L) // Guest ID 0
            .claim("meetingId", meetingId)
            .issueTime(now)
            .expirationTime(validity)
            .jwtID(UUID.randomUUID().toString())
            .build()

        return signJwt(claimsSet)
    }

    private fun signJwt(claimsSet: JWTClaimsSet): String {
        // Create Signed JWT
        val header = JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(keyId)
            .build()
        val signedJWT = SignedJWT(header, claimsSet)

        // Sign with Private Key
        val signer: JWSSigner = RSASSASigner(keyManager.privateKey)
        signedJWT.sign(signer)

        return signedJWT.serialize()
    }

    fun validateToken(token: String): Boolean {
        try {
            val signedJWT = SignedJWT.parse(token)
            val verifier = com.nimbusds.jose.crypto.RSASSAVerifier(keyManager.publicKey)
            
            if (!signedJWT.verify(verifier)) {
                log.warn("Token verification failed for token: ${token.take(10)}...")
                return false
            }
            
            val claims = signedJWT.jwtClaimsSet
            val now = Date()
            if (claims.expirationTime.before(now)) {
                log.debug("Token expired")
                return false
            }
            
            return true
        } catch (e: Exception) {
            log.error("Error validating token: ${e.message}", e)
            return false
        }
    }

    fun getAuthentication(token: String): Authentication {
        val signedJWT = SignedJWT.parse(token)
        val claims = signedJWT.jwtClaimsSet
        
        val username = claims.subject
        val authClaim = claims.getClaim(JwtConstants.ROLE_CLAIM)?.toString() ?: ""
        
        val authorities = if (authClaim.isBlank()) {
            emptyList()
        } else {
            authClaim.split(",").map { org.springframework.security.core.authority.SimpleGrantedAuthority(it) }
        }
        
        val userId = claims.getClaim(JwtConstants.USER_ID_CLAIM)?.toString() ?: username
        
        return org.springframework.security.authentication.UsernamePasswordAuthenticationToken(userId, token, authorities)
    }
}
