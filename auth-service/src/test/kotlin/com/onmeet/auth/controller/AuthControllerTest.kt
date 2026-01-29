package com.onmeet.auth.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.onmeet.auth.dto.CompanySignupRequest
import com.onmeet.auth.dto.LoginRequest
import com.onmeet.auth.dto.TokenResponse
import com.onmeet.auth.service.AuthService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(AuthController::class)
@AutoConfigureMockMvc(addFilters = false) // Disable security filters for simple controller testing
class AuthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockBean
    lateinit var authService: AuthService

    @MockBean
    lateinit var jwtTokenProvider: com.onmeet.auth.security.JwtTokenProvider

    val objectMapper = jacksonObjectMapper()

    @Test
    fun `signupCompany should return 200 and userId`() {
        val request = CompanySignupRequest("manager@test.com", "pass", "Manager", "Corp", "Team")
        `when`(authService.signupCompany(request)).thenReturn(100L)

        mockMvc.perform(post("/auth/signup/company")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk)
            .andExpect(content().string("100"))
    }

    @Test
    fun `login should return token response and cookie`() {
        val request = LoginRequest("user@test.com", "pass")
        val response = TokenResponse("access-token", "refresh-token")
        `when`(authService.login(request)).thenReturn(response)

        mockMvc.perform(post("/auth/login")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("access-token"))
            .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
            .andExpect(cookie().exists("accessToken"))
    }
}
