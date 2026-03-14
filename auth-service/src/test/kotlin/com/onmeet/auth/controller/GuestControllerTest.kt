package com.onmeet.auth.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.onmeet.auth.config.JwtProperties
import com.onmeet.auth.dto.GuestInviteRequestDto
import com.onmeet.auth.dto.GuestJoinResultDto
import com.onmeet.auth.service.GuestService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class GuestControllerTest {

    private lateinit var mockMvc: MockMvc
    private val guestService: GuestService = mockk()
    private val jwtProperties: JwtProperties = mockk()
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(GuestController(guestService, jwtProperties, "http://localhost:3000"))
            .build()
    }

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
