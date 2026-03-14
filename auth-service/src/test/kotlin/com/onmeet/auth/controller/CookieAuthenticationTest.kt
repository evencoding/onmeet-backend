package com.onmeet.auth.controller

import com.onmeet.auth.security.JwtAuthenticationFilter
import com.onmeet.auth.security.JwtTokenProvider
import com.onmeet.auth.service.AuthService
import com.onmeet.auth.service.UserService
import com.onmeet.auth.service.TeamService
import com.onmeet.auth.service.InvitationService
import com.onmeet.auth.service.JobTitleService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import jakarta.servlet.http.Cookie
import java.util.Collections

class CookieAuthenticationTest {

    private val authService: AuthService = mockk(relaxed = true)
    private val userService: UserService = mockk(relaxed = true)
    private val teamService: TeamService = mockk(relaxed = true)
    private val invitationService: InvitationService = mockk(relaxed = true)
    private val jobTitleService: JobTitleService = mockk(relaxed = true)
    private val jwtTokenProvider: JwtTokenProvider = mockk(relaxed = true)
    private val redisTemplate: StringRedisTemplate = mockk(relaxed = true)

    @Test
    fun `JwtAuthenticationFilter should extract token from cookie and set SecurityContext`() {
        // given
        val token = "valid_token_value"
        val userEmail = "manager@example.com"
        val jwtFilter = JwtAuthenticationFilter(jwtTokenProvider, redisTemplate)

        every { redisTemplate.hasKey("blacklist:$token") } returns false
        every { jwtTokenProvider.validateToken(token) } returns true
        val authentication = UsernamePasswordAuthenticationToken(
            userEmail, null,
            Collections.singletonList(SimpleGrantedAuthority("ROLE_MANAGER"))
        )
        every { jwtTokenProvider.getAuthentication(token) } returns authentication

        val request = MockHttpServletRequest().apply {
            setCookies(Cookie("accessToken", token))
        }
        val response = MockHttpServletResponse()
        val filterChain = MockFilterChain()

        // when
        jwtFilter.doFilter(request, response, filterChain)

        // then
        val contextAuth = SecurityContextHolder.getContext().authentication
        assertNotNull(contextAuth)
        assertEquals(userEmail, contextAuth.name)
        verify { jwtTokenProvider.validateToken(token) }
        verify { jwtTokenProvider.getAuthentication(token) }

        // cleanup
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `resetProfileImage should call service when authenticated via principal`() {
        // given
        val userId = 123L
        val userEmail = "manager@example.com"
        val authentication = UsernamePasswordAuthenticationToken(
            userEmail, null,
            Collections.singletonList(SimpleGrantedAuthority("ROLE_MANAGER"))
        )

        val mockMvc = MockMvcBuilders
            .standaloneSetup(ManagerController(userService, authService, teamService, invitationService, jobTitleService))
            .build()

        every { authService.resetUserProfileImage(userId, userEmail) } returns Unit

        // when & then
        mockMvc.perform(
            delete("/v1/manager/employees/$userId/profile-image")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNoContent)

        verify { authService.resetUserProfileImage(userId, userEmail) }
    }
}
