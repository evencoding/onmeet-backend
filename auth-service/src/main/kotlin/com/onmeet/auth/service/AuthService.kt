package com.onmeet.auth.service

import com.onmeet.auth.dto.LoginRequest
import com.onmeet.auth.dto.SignupRequest
import com.onmeet.auth.dto.TokenResponse
import com.onmeet.auth.entity.User
import com.onmeet.auth.repository.UserRepository
import com.onmeet.auth.security.JwtTokenProvider
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authenticationManager: AuthenticationManager,
    private val jwtTokenProvider: JwtTokenProvider
) {

    @Transactional
    fun signup(request: SignupRequest): Long {
        if (userRepository.existsByEmail(request.email)) {
            throw org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Email already in use")
        }

        val user = User(
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password)
        )
        return userRepository.save(user).id!!
    }

    @Transactional
    fun login(request: LoginRequest): TokenResponse {
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.email, request.password)
        )
        
        // Generate Token
        val token = jwtTokenProvider.generateToken(authentication)
        return TokenResponse(token)
    }
}
