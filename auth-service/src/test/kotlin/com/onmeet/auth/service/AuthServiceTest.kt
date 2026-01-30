package com.onmeet.auth.service

import com.onmeet.auth.dto.LoginRequest
import com.onmeet.auth.dto.SignupRequest
import com.onmeet.auth.dto.TokenResponse
import com.onmeet.auth.entity.User
import com.onmeet.auth.exception.EmailAlreadyExistsException
import com.onmeet.auth.repository.UserRepository
import com.onmeet.auth.security.JwtTokenProvider
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.password.PasswordEncoder

@ExtendWith(MockKExtension::class)
class AuthServiceTest {

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var passwordEncoder: PasswordEncoder

    @MockK
    lateinit var authenticationManager: AuthenticationManager

    @MockK
    lateinit var jwtTokenProvider: JwtTokenProvider

    @InjectMockKs
    lateinit var authService: AuthService

    @Test
    fun `signup should save new user and return id`() {
        // Given
        val request = SignupRequest("test@example.com", "password")
        val encodedPassword = "encodedPassword"
        val savedUser = User(id = 1L, email = request.email, passwordHash = encodedPassword)

        every { userRepository.existsByEmail(request.email) } returns false
        every { passwordEncoder.encode(request.password) } returns encodedPassword
        every { userRepository.save(any()) } returns savedUser

        // When
        val userId = authService.signup(request)

        // Then
        assertEquals(1L, userId)
        verify { userRepository.save(match { it.email == request.email && it.passwordHash == encodedPassword }) }
    }

    @Test
    fun `signup should throw exception if email exists`() {
        // Given
        val request = SignupRequest("existing@example.com", "password")
        every { userRepository.existsByEmail(request.email) } returns true

        // When & Then
        val exception = assertThrows(EmailAlreadyExistsException::class.java) {
            authService.signup(request)
        }
        assertEquals("Email already in use", exception.message)
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `login should authenticate and return token`() {
        // Given
        val request = LoginRequest("test@example.com", "password")
        val authentication = io.mockk.mockk<Authentication>()
        val token = "generated.jwt.token"

        every { authenticationManager.authenticate(any()) } returns authentication
        every { jwtTokenProvider.generateToken(authentication) } returns token

        // When
        val response = authService.login(request)

        // Then
        assertEquals(token, response.accessToken)
        verify { 
            authenticationManager.authenticate(match { 
                it is UsernamePasswordAuthenticationToken && 
                it.principal == request.email && 
                it.credentials == request.password 
            }) 
        }
    }
}
