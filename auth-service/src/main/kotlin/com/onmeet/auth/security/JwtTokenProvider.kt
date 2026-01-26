package com.onmeet.auth.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.stereotype.Component
import java.util.*

@Component
class JwtTokenProvider(
    private val keyManager: KeyManager
) {

    fun generateToken(authentication: Authentication): String {
        val authorities = authentication.authorities.joinToString(",") { it.authority }

        val now = Date()
        val validity = Date(now.time + 3600000) // 1 hour

        // Type cast principal to our User entity to get the ID
        val principal = authentication.principal as com.onmeet.auth.entity.User

        // Build Claims
        val claimsSet = JWTClaimsSet.Builder()
            .subject(authentication.name)
            .claim("auth", authorities)
            .claim("userId", principal.id) // Add sequence ID
            .issueTime(now)
            .expirationTime(validity)
            .jwtID(UUID.randomUUID().toString())
            .build()

        // Create Signed JWT
        val header = JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID("onmeet-auth-key")
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
            val verifier = com.nimbusds.jose.crypto.RSASSAVerifier(keyManager.publicKey as java.security.interfaces.RSAPublicKey)
            
            if (!signedJWT.verify(verifier)) {
                return false
            }
            
            val claims = signedJWT.jwtClaimsSet
            val now = Date()
            if (claims.expirationTime.before(now)) {
                return false
            }
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun getAuthentication(token: String): Authentication {
        val signedJWT = SignedJWT.parse(token)
        val claims = signedJWT.jwtClaimsSet
        
        val username = claims.subject
        val authClaim = claims.getClaim("auth") as String
        
        val authorities = if (authClaim.isBlank()) {
            emptyList()
        } else {
            authClaim.split(",").map { org.springframework.security.core.authority.SimpleGrantedAuthority(it) }
        }
        
        val principal = org.springframework.security.core.userdetails.User(username, "", authorities)
        
        return org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, token, authorities)
    }
}
