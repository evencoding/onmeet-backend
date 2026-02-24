package com.onmeet.auth.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.onmeet.auth.config.JwtProperties
import com.onmeet.auth.dto.UserProfileUpdateRequest
import com.onmeet.auth.dto.UserResponseDto
import com.onmeet.auth.entity.User
import com.onmeet.auth.service.AuthService
import com.onmeet.auth.service.UserService
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.core.MethodParameter
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.bind.support.WebDataBinderFactory
import com.onmeet.auth.entity.Company
import org.junit.jupiter.api.BeforeEach
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@WebMvcTest(MemberController::class)
@AutoConfigureMockMvc(addFilters = false)class MemberControllerTest {
    private lateinit var mockMvc: MockMvc
    @MockkBean
    private lateinit var userService: UserService

    @MockkBean
    private lateinit var authService: AuthService

    @MockkBean
    private lateinit var teamService: com.onmeet.auth.service.TeamService

    @MockkBean
    private lateinit var jobTitleService: com.onmeet.auth.service.JobTitleService

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

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(MemberController(userService, authService, teamService, jobTitleService))
            .setCustomArgumentResolvers(object : HandlerMethodArgumentResolver {
                override fun supportsParameter(parameter: MethodParameter): Boolean {
                    return parameter.parameterType == User::class.java
                }

                override fun resolveArgument(
                    parameter: MethodParameter,
                    mavContainer: ModelAndViewContainer?,
                    webRequest: NativeWebRequest,
                    binderFactory: WebDataBinderFactory?
                ): Any? {
                    return User(
                        id = 1, email = "test@example.com", name = "Test User", passwordHash = "hash",
                        company = Company(id = 1L, name = "Test Company")
                    )
                }
            })
            .build()
    }
    @Test
    @WithMockUser
    fun `getMyInfo should return current user info`() {
        // given
        val userResponse = UserResponseDto(
            id = 1, email = "test@example.com", name = "Test User",
            employeeId = null, roles = setOf("ROLE_USER"), status = "ACTIVE",
            company = null, jobTitle = null, teams = emptyList(), profileImageId = null
        )
        every { userService.getMyInfo(any()) } returns userResponse

        // when & then
        mockMvc.perform(
            get("/auth/v1/member/me")
                .contextPath("/auth")
                .principal(org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test@example.com", null))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("test@example.com"))
            .andExpect(jsonPath("$.name").value("Test User"))
    }

    @Test
    @WithMockUser
    fun `updateProfile should return updated user info`() {
        // given
        val request = UserProfileUpdateRequest(name = "Updated Name", employeeId = null, jobTitleId = null)
        val userResponse = UserResponseDto(
            id = 1, email = "test@example.com", name = "Updated Name",
            employeeId = null, roles = setOf("ROLE_USER"), status = "ACTIVE",
            company = null, jobTitle = null, teams = emptyList(), profileImageId = null
        )
        every { userService.updateUserProfile(any(), any(), any()) } returns userResponse

        // when & then
        mockMvc.perform(
            patch("/auth/v1/member/me")
                .contextPath("/auth")
                .principal(org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test@example.com", null))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Updated Name"))
    }
}
