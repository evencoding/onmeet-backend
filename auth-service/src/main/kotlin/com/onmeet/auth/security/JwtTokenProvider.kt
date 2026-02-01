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
import org.springframework.beans.factory.annotation.Value
import com.onmeet.auth.entity.User
import java.util.*
import com.onmeet.auth.config.JwtProperties
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetailsService

@Component
class JwtTokenProvider(
    private val keyManager: KeyManager,
    private val jwtProperties: JwtProperties,
    private val userDetailsService: UserDetailsService
) {

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(JwtTokenProvider::class.java)
        private const val GUEST_TOKEN_VALIDITY_MS = 14400000L // 4 hours
    }

    fun generateToken(authentication: Authentication): String {
        val authorities = authentication.authorities.joinToString(",") { it.authority }

        val user = authentication.principal as User
        val now = Date()
        val expiryDate = Date(now.time + jwtProperties.validityInMs)

        val claimsSet = JWTClaimsSet.Builder()
            .subject(user.username)
            .claim(JwtConstants.USER_ID_CLAIM, user.id)
            .claim(JwtConstants.CLAIM_AUTHORITIES, authorities)
            .issueTime(now)
            .expirationTime(expiryDate)
            .build()

        val signedJWT = SignedJWT(
            JWSHeader(JWSAlgorithm.RS256),
            claimsSet
        )

        signedJWT.sign(RSASSASigner(keyManager.privateKey))

        return signedJWT.serialize()
    }

    fun generateGuestToken(authName: String, roles: List<String>, meetingId: String? = null): String {
        val authorities = roles.joinToString(",")
        val now = Date()
        val expiryDate = Date(now.time + GUEST_TOKEN_VALIDITY_MS)

        val claimsSetBuilder = JWTClaimsSet.Builder()
            .subject(authName)
            .claim(JwtConstants.CLAIM_AUTHORITIES, authorities)

        meetingId?.let {
            claimsSetBuilder.claim(JwtConstants.MEETING_ID_CLAIM, it)
        }

        val claimsSet = claimsSetBuilder
            .issueTime(now)
            .expirationTime(expiryDate)
            .build()

        val signedJWT = SignedJWT(
            JWSHeader(JWSAlgorithm.RS256),
            claimsSet
        )

        signedJWT.sign(RSASSASigner(keyManager.privateKey))

        return signedJWT.serialize()
    }

    fun validateToken(token: String): Boolean {
        return try {
            val signedJWT = SignedJWT.parse(token)
            val now = Date()
            val expirationTime = signedJWT.jwtClaimsSet.expirationTime
            
            if (expirationTime != null && now.after(expirationTime)) {
                log.warn("JWT token is expired")
                return false
            }
            true
        } catch (e: Exception) {
            log.error("Invalid JWT token: {}", e.message)
            false
        }
    }

    fun getUsernameFromToken(token: String): String {
        val signedJWT = SignedJWT.parse(token)
        return signedJWT.jwtClaimsSet.subject
    }

    fun getAuthentication(token: String): Authentication {
        val userDetails = userDetailsService.loadUserByUsername(getUsernameFromToken(token))
        return UsernamePasswordAuthenticationToken(userDetails, "", userDetails.authorities)
    }

    fun getAuthoritiesFromToken(token: String): String? {
        val signedJWT = SignedJWT.parse(token)
        return signedJWT.jwtClaimsSet.getStringClaim(JwtConstants.CLAIM_AUTHORITIES)
    }
}
