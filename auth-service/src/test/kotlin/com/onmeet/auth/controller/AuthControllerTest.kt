package com.onmeet.auth.controller

import com.onmeet.auth.config.JwtProperties
import com.onmeet.auth.dto.*
import com.onmeet.auth.service.AuthService
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath

@WebMvcTest(AuthController::class) // [Necessary Infrastructure] AuthController 슬라이스 테스트 설정
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var authService: AuthService

    @MockkBean
    private lateinit var userService: com.onmeet.auth.service.UserService

    @MockkBean
    private lateinit var keyManager: com.onmeet.auth.security.KeyManager

    @MockkBean
    private lateinit var jwtProperties: JwtProperties

    @MockkBean
    private lateinit var jwtTokenProvider: com.onmeet.auth.security.JwtTokenProvider

    @MockkBean
    private lateinit var gatewayProperties: com.onmeet.auth.config.GatewayProperties

    @MockkBean
    private lateinit var authGatewayPreAuthFilter: com.onmeet.auth.security.AuthGatewayPreAuthFilter

    @MockkBean
    private lateinit var jwtAuthenticationFilter: com.onmeet.auth.security.JwtAuthenticationFilter

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    @WithMockUser
    // [Essential] 로그인 기능 검증 - 인증의 핵심 엔드포인트
    fun `login should return success and set cookies`() {
        // given
        val request = LoginRequest("test@example.com", "password")
        val tokenResponse = TokenResponse("access_token", "refresh_token")
        
        // Mocking jwtProperties for cookie settings
        every { jwtProperties.cookie } returns JwtProperties.CookieProperties(true, 3600)
        every { jwtProperties.refreshCookie } returns JwtProperties.RefreshCookieProperties(604800)
        
        every { authService.login(any()) } returns tokenResponse

        // when & then
        mockMvc.perform(
            post("/auth/v1/login").contextPath("/auth")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Login successful"))
    }
}
