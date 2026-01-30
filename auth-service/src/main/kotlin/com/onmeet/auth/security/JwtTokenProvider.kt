package com.onmeet.auth.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import java.util.*

@Component
class JwtTokenProvider(
    private val keyManager: KeyManager,
    @org.springframework.beans.factory.annotation.Value("\${jwt.validity-in-ms}") private val validityInMs: Long,
    @org.springframework.beans.factory.annotation.Value("\${jwt.key-id}") private val keyId: String
) {

    private val logger = org.slf4j.LoggerFactory.getLogger(JwtTokenProvider::class.java)

    fun generateToken(authentication: Authentication): String {
        val authorities = authentication.authorities.joinToString(",") { it.authority }

        val now = Date()
        val validity = Date(now.time + validityInMs)

        // Type cast principal to our User entity to get the ID
        val principal = authentication.principal as com.onmeet.auth.entity.User

        // Build Claims
        val claimsSet = JWTClaimsSet.Builder()
            .subject(authentication.name)
            .claim("scope", authorities)
            .claim("userId", principal.id) // Add sequence ID
            .issueTime(now)
            .expirationTime(validity)
            .jwtID(UUID.randomUUID().toString())
            .build()

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
                logger.warn("Token verification failed for token: ${token.take(10)}...")
                return false
            }
            
            val claims = signedJWT.jwtClaimsSet
            val now = Date()
            if (claims.expirationTime.before(now)) {
                logger.debug("Token expired")
                return false
            }
            
            return true
        } catch (e: Exception) {
            logger.error("Error validating token: ${e.message}", e)
            return false
        }
    }

    fun getAuthentication(token: String): Authentication {
        val signedJWT = SignedJWT.parse(token)
        val claims = signedJWT.jwtClaimsSet
        
        val username = claims.subject
        val authClaim = claims.getClaim("scope")?.toString() ?: ""
        
        val authorities = if (authClaim.isBlank()) {
            emptyList()
        } else {
            authClaim.split(",").map { org.springframework.security.core.authority.SimpleGrantedAuthority(it) }
        }
        
        val userId = claims.getClaim("userId")?.toString() ?: username
        
        return org.springframework.security.authentication.UsernamePasswordAuthenticationToken(userId, token, authorities)
    }
}
