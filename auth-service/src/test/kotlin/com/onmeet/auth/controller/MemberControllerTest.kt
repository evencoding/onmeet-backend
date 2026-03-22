package com.onmeet.auth.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.onmeet.auth.dto.UserProfileUpdateRequest
import com.onmeet.auth.dto.UserResponseDto
import com.onmeet.auth.entity.User
import com.onmeet.auth.entity.Company
import com.onmeet.auth.service.AuthService
import com.onmeet.auth.service.UserService
import com.onmeet.auth.service.TeamService
import com.onmeet.auth.service.JobTitleService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.core.MethodParameter
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.bind.support.WebDataBinderFactory

class MemberControllerTest {

    private lateinit var mockMvc: MockMvc
    private val userService: UserService = mockk()
    private val authService: AuthService = mockk()
    private val teamService: TeamService = mockk()
    private val jobTitleService: JobTitleService = mockk()
    private val objectMapper = ObjectMapper()

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
            get("/v1/member/me")
                .principal(org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test@example.com", null))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("test@example.com"))
            .andExpect(jsonPath("$.name").value("Test User"))
    }

    @Test
    fun `updateProfile should return updated user info`() {
        // given
        val request = UserProfileUpdateRequest(name = "Updated Name", employeeId = null, jobTitleId = null)
        val requestPart = org.springframework.mock.web.MockMultipartFile(
            "request",
            "",
            "application/json",
            objectMapper.writeValueAsBytes(request)
        )

        val userResponse = UserResponseDto(
            id = 1, email = "test@example.com", name = "Updated Name",
            employeeId = null, roles = setOf("ROLE_USER"), status = "ACTIVE",
            company = null, jobTitle = null, teams = emptyList(), profileImageId = null
        )
        every { userService.updateUserProfile(any(), any(), any(), any()) } returns userResponse

        // when & then
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/v1/member/me")
                .file(requestPart)
                .with { it.method = "PATCH"; it }
                .principal(org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test@example.com", null))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Updated Name"))
    }
}
