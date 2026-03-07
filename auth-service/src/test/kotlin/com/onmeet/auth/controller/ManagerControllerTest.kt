package com.onmeet.auth.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.onmeet.auth.dto.InvitationRequest
import com.onmeet.auth.dto.UserResponseDto
import com.onmeet.auth.entity.Invitation
import com.onmeet.auth.service.AuthService
import com.onmeet.auth.service.UserService
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import com.onmeet.auth.entity.User
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.core.MethodParameter
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.bind.support.WebDataBinderFactory
import com.onmeet.auth.entity.Company
import org.junit.jupiter.api.BeforeEach
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDateTime

class ManagerControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var userService: UserService
    private lateinit var authService: AuthService
    private lateinit var teamService: com.onmeet.auth.service.TeamService
    private lateinit var invitationService: com.onmeet.auth.service.InvitationService
    private lateinit var jobTitleService: com.onmeet.auth.service.JobTitleService
    private val objectMapper = ObjectMapper().apply {
        registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())
    }

    @BeforeEach
    fun setup() {
        userService = io.mockk.mockk()
        authService = io.mockk.mockk()
        teamService = io.mockk.mockk()
        invitationService = io.mockk.mockk()
        jobTitleService = io.mockk.mockk()

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
            .setMessageConverters(org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper))
            .build()
    }
    @Test
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

    @Test
    fun `inviteMember should return invitation ids for multiple emails`() {
        // given
        val testCompany = Company(id = 1L, name = "Test Company")
        val emails = listOf("user1@company.com", "user2@company.com", "user3@company.com")
        val request = InvitationRequest(emails = emails)

        val invitation1 = Invitation(
            id = 101L,
            email = emails[0],
            code = "CODE1",
            role = User.Role.USER,
            company = testCompany,
            expiresAt = LocalDateTime.now().plusDays(7)
        )
        val invitation2 = Invitation(
            id = 102L,
            email = emails[1],
            code = "CODE2",
            role = User.Role.USER,
            company = testCompany,
            expiresAt = LocalDateTime.now().plusDays(7)
        )
        val invitation3 = Invitation(
            id = 103L,
            email = emails[2],
            code = "CODE3",
            role = User.Role.USER,
            company = testCompany,
            expiresAt = LocalDateTime.now().plusDays(7)
        )

        every { invitationService.createInvitation(eq(1L), eq(emails[0]), eq(User.Role.USER)) } returns invitation1
        every { invitationService.createInvitation(eq(1L), eq(emails[1]), eq(User.Role.USER)) } returns invitation2
        every { invitationService.createInvitation(eq(1L), eq(emails[2]), eq(User.Role.USER)) } returns invitation3

        // when & then
        mockMvc.perform(
            post("/auth/v1/manager/invite")
                .contextPath("/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[0]").value(101))
            .andExpect(jsonPath("$[1]").value(102))
            .andExpect(jsonPath("$[2]").value(103))
    }

    @Test
    fun `inviteMember should return single invitation id for single email`() {
        // given
        val testCompany = Company(id = 1L, name = "Test Company")
        val email = "user@company.com"
        val request = InvitationRequest(emails = listOf(email))

        val invitation = Invitation(
            id = 100L,
            email = email,
            code = "CODE",
            role = User.Role.USER,
            company = testCompany,
            expiresAt = LocalDateTime.now().plusDays(7)
        )

        every { invitationService.createInvitation(eq(1L), eq(email), eq(User.Role.USER)) } returns invitation

        // when & then
        mockMvc.perform(
            post("/auth/v1/manager/invite")
                .contextPath("/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[0]").value(100))
    }
}
