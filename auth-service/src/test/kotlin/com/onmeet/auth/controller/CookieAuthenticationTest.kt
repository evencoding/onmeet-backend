package com.onmeet.auth.controller

import com.onmeet.auth.config.JwtProperties
import com.onmeet.auth.config.PropertiesConfig
import com.onmeet.auth.security.JwtAuthenticationFilter
import com.onmeet.auth.security.JwtTokenProvider
import com.onmeet.auth.service.AuthService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import jakarta.servlet.http.Cookie
import java.util.Collections

@WebMvcTest(AuthController::class)
@Import(CookieAuthenticationTest.TestSecurityConfig::class, JwtAuthenticationFilter::class, PropertiesConfig::class)
class CookieAuthenticationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate



    @Test
    // [Essential] 쿠키 기반 인증 필터 검증 - 세션/토큰 관리의 핵심 로직
    fun `resetProfileImage should authenticate via cookie and call service`() {
        // given
        val token = "valid_token_value"
        val userId = 123L
        val userEmail = "manager@example.com"
        val cookie = Cookie("accessToken", token).apply {
            path = "/"
            isHttpOnly = true
        }

        // Mock AuthService logic
        every { authService.resetUserProfileImage(userId, userEmail) } returns Unit

        // Mock Redis (Blacklist check)
        every { redisTemplate.hasKey("blacklist:$token") } returns false

        // Mock JWT Validation and Parsing
        every { jwtTokenProvider.validateToken(token) } returns true
        val authentication = UsernamePasswordAuthenticationToken(
            userEmail, 
            null, 
            Collections.singletonList(SimpleGrantedAuthority("ROLE_MANAGER"))
        )
        every { jwtTokenProvider.getAuthentication(token) } returns authentication

        // when
        mockMvc.perform(
            delete("/v1/users/$userId/profile-image")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNoContent)

        // then
        verify { authService.resetUserProfileImage(userId, userEmail) }
        verify { jwtTokenProvider.validateToken(token) }
        verify { jwtTokenProvider.getAuthentication(token) }
    }

    @TestConfiguration
    @EnableWebSecurity
    class TestSecurityConfig {
        @Bean
        fun authService(): AuthService = mockk(relaxed = true)

        @Bean
        fun jwtTokenProvider(): JwtTokenProvider = mockk(relaxed = true)

        @Bean
        fun redisTemplate(): StringRedisTemplate = mockk(relaxed = true)

        @Bean
        fun filterChain(http: HttpSecurity, jwtFilter: JwtAuthenticationFilter): SecurityFilterChain {
            http
                .csrf { it.disable() }
                .authorizeHttpRequests { it.anyRequest().permitAll() }
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
            return http.build()
        }
    }
}
