package com.onmeet.auth.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.onmeet.auth.dto.LoginRequest
import com.onmeet.auth.dto.LoginResponse
import com.onmeet.auth.dto.SignupRequest
import com.onmeet.auth.dto.TokenResponse
import com.onmeet.auth.service.AuthService
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import com.onmeet.auth.config.SecurityConfig

@WebMvcTest(
    controllers = [AuthController::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [SecurityConfig::class, com.onmeet.auth.security.JwtAuthenticationFilter::class])
    ],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, OAuth2ClientAutoConfiguration::class, OAuth2ResourceServerAutoConfiguration::class]
)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest(@Autowired val mockMvc: MockMvc) {

    @MockkBean
    lateinit var authService: AuthService

    val mapper = jacksonObjectMapper()



    @Test
    fun `signup should return user id`() {
        val request = SignupRequest("test@example.com", "password", "Test User")
        every { authService.signup(any()) } returns 1L

        mockMvc.perform(post("/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(request)))
            .andExpect(status().isOk)
            .andExpect(content().string("1"))

        verify { authService.signup(request) }
    }

    @Test
    fun `login should return cookie and empty body`() {
        val request = LoginRequest("test@example.com", "password")
        val tokenResponse = TokenResponse("jwt-token", "Bearer")

        every { authService.login(any()) } returns tokenResponse

        mockMvc.perform(post("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(request)))
            .andExpect(status().isOk)
            .andExpect(header().exists("Set-Cookie"))
            .andExpect(jsonPath("$.message").value("Login successful"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            // Verify token is NOT in body
            .andExpect(jsonPath("$.accessToken").doesNotExist())

        verify { authService.login(request) }
    }
}
