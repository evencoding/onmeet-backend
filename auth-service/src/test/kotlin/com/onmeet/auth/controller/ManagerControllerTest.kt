package com.onmeet.auth.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.onmeet.auth.config.JwtProperties
import com.onmeet.auth.dto.UserResponseDto
import com.onmeet.auth.service.AuthService
import com.onmeet.auth.service.UserService
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import com.onmeet.auth.entity.User
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.core.MethodParameter
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.bind.support.WebDataBinderFactory
import com.onmeet.auth.entity.Company
import org.junit.jupiter.api.BeforeEach
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@WebMvcTest(ManagerController::class)
@AutoConfigureMockMvc(addFilters = false)class ManagerControllerTest {
    private lateinit var mockMvc: MockMvc
    @MockkBean
    private lateinit var userService: UserService

    @MockkBean
    private lateinit var authService: AuthService

    @MockkBean
    private lateinit var teamService: com.onmeet.auth.service.TeamService

    @MockkBean
    private lateinit var invitationService: com.onmeet.auth.service.InvitationService

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
            .standaloneSetup(ManagerController(userService, authService, teamService, invitationService, jobTitleService))
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
                        id = 2L, email = "manager@test.com", passwordHash = "hash", name = "Manager",
                        company = Company(id = 1L, name = "Test Company"),
                        roles = mutableSetOf(User.Role.USER, User.Role.MANAGER)
                    )
                }
            })
            .build()
    }
    @Test
    @WithMockUser(roles = ["MANAGER"])
    fun `deactivateUser should return success`() {
        // given
        val userId = 1L
        val userResponse = UserResponseDto(
            id = userId, email = "test@example.com", name = "Test User",
            employeeId = null, roles = setOf("ROLE_USER"), status = "ACTIVE",
            company = null, jobTitle = null, teams = emptyList(), profileImageId = null
        )
        every { userService.deactivateUser(eq(userId), any()) } returns userResponse

        // when & then
        mockMvc.perform(
            put("/auth/v1/manager/employees/$userId/deactivate")
                .contextPath("/auth")
        )
            .andExpect(status().isOk)    }

    @Test
    @WithMockUser(roles = ["MANAGER"])
    fun `resetProfileImage should return no content`() {
        // given
        val userId = 1L
        every { authService.resetUserProfileImage(eq(userId), any()) } just runs

        // when & then
        mockMvc.perform(
            delete("/auth/v1/manager/employees/$userId/profile-image")
                .contextPath("/auth")
                .principal(org.springframework.security.authentication.UsernamePasswordAuthenticationToken("manager@test.com", null))
        )
            .andExpect(status().isNoContent)    }
}
