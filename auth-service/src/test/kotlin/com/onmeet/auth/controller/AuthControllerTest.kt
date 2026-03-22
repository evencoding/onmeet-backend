package com.onmeet.auth.controller

import com.onmeet.auth.config.JwtProperties
import com.onmeet.auth.dto.*
import com.onmeet.auth.security.KeyManager
import com.onmeet.auth.service.AuthService
import com.onmeet.auth.service.UserService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AuthControllerTest {

    private lateinit var mockMvc: MockMvc
    private val authService: AuthService = mockk()
    private val userService: UserService = mockk()
    private val jwtProperties: JwtProperties = mockk()
    private val keyManager: KeyManager = mockk()
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(AuthController(authService, userService, jwtProperties, keyManager))
            .build()
    }

    @Test
    fun `login should return success and set cookies`() {
        // given
        val request = LoginRequest("test@example.com", "password")
        val tokenResponse = TokenResponse("access_token", "refresh_token")

        every { jwtProperties.cookie } returns JwtProperties.CookieProperties(true, 3600)
        every { jwtProperties.refreshCookie } returns JwtProperties.RefreshCookieProperties(604800)
        every { authService.login(any()) } returns tokenResponse

        // when & then
        mockMvc.perform(
            post("/v1/login")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Login successful"))
    }
}
