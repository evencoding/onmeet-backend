package com.onmeet.auth.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.onmeet.auth.config.JwtProperties
import com.onmeet.auth.dto.GuestInviteRequestDto
import com.onmeet.auth.dto.GuestJoinResultDto
import com.onmeet.auth.service.GuestService
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(GuestController::class)
@AutoConfigureMockMvc(addFilters = false)
class GuestControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var guestService: GuestService

    @MockkBean
    private lateinit var jwtProperties: JwtProperties

    @MockkBean
    private lateinit var keyManager: com.onmeet.auth.security.KeyManager

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
    fun `inviteGuest should return 200 OK`() {
        val request = GuestInviteRequestDto("guest@example.com", "room123", "Test Room")
        
        every { guestService.inviteGuest(any(), "host@example.com") } returns Unit

        mockMvc.perform(
            post("/v1/guests/invite")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .principal(org.springframework.security.authentication.UsernamePasswordAuthenticationToken("host@example.com", null))
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `joinMeeting should set cookies and redirect`() {
        val uuid = "test-uuid"
        val resultDto = GuestJoinResultDto("room123", "Guest_guest_test-u", "access", "refresh")

        every { jwtProperties.cookie } returns JwtProperties.CookieProperties(secure = false, maxAge = 3600)
        every { jwtProperties.refreshCookie } returns JwtProperties.RefreshCookieProperties(maxAge = 86400)
        every { guestService.joinMeeting(uuid) } returns resultDto

        mockMvc.perform(get("/v1/guests/join/$uuid"))
            .andExpect(status().isFound)
            .andExpect(cookie().exists("accessToken"))
            .andExpect(cookie().exists("refreshToken"))
            .andExpect(header().string("Location", "http://localhost:3000/rooms/room123"))
    }
}
