package com.onmeet.auth.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.onmeet.auth.config.GatewayProperties
import com.onmeet.auth.config.JwtProperties
import com.onmeet.auth.dto.TeamRequest
import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.Team
import com.onmeet.auth.entity.User
import com.onmeet.auth.security.AuthGatewayPreAuthFilter
import com.onmeet.auth.security.JwtAuthenticationFilter
import com.onmeet.auth.security.JwtTokenProvider
import com.onmeet.common.security.TeamSecurity
import com.onmeet.auth.service.TeamService
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(TeamController::class) // [Necessary Infrastructure] TeamController 슬라이스 테스트 설정
@Import(com.onmeet.auth.config.PropertiesConfig::class, TeamControllerTest.TestSecurityConfig::class) 
class TeamControllerTest {

    // [Necessary Infrastructure] 테스트용 보안 설정 (CSRF/Auth 무시 및 메서드 보안 테스트 준비)
    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    class TestSecurityConfig {
        @org.springframework.context.annotation.Bean
        fun filterChain(http: org.springframework.security.config.annotation.web.builders.HttpSecurity): org.springframework.security.web.SecurityFilterChain {
            http.csrf { it.disable() }
                .authorizeHttpRequests { it.anyRequest().permitAll() }
            return http.build()
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var teamService: TeamService

    @MockkBean
    private lateinit var jwtProperties: JwtProperties

    @MockkBean
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @MockkBean
    private lateinit var gatewayProperties: GatewayProperties

    @MockkBean(relaxed = true) // relaxed = true: lifecycle 메서드(init, destroy) 호출 시 MockKException 방지
    private lateinit var authGatewayPreAuthFilter: AuthGatewayPreAuthFilter

    @MockkBean(relaxed = true) // Spring Boot는 Filter 타입의 모든 빈을 자동으로 서블릿 컨테이너에 등록하므로 필수로 Mocking 필요
    private lateinit var jwtAuthenticationFilter: JwtAuthenticationFilter



    @MockkBean(name = "teamSecurity")
    private lateinit var teamSecurity: TeamSecurity

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @org.junit.jupiter.api.BeforeEach
    // [Necessary Infrastructure] 필터 체인 통과 모킹 - 테스트 환경 구축의 필수 로직
    fun setupFilters() {
        // 필터 체인 통과 모킹: doFilter가 호출될 때 아무것도 하지 않으면(relaxed 기본값) 
        // 컨트롤러까지 요청이 도달하지 못하므로, 명시적으로 FilterChain.doFilter를 호출하여 다음 단계로 진행시킴
        every { authGatewayPreAuthFilter.doFilter(any(), any(), any()) } answers {
            val chain = it.invocation.args[2] as jakarta.servlet.FilterChain
            chain.doFilter(it.invocation.args[0] as jakarta.servlet.ServletRequest, it.invocation.args[1] as jakarta.servlet.ServletResponse)
        }
        every { jwtAuthenticationFilter.doFilter(any(), any(), any()) } answers {
            val chain = it.invocation.args[2] as jakarta.servlet.FilterChain
            chain.doFilter(it.invocation.args[0] as jakarta.servlet.ServletRequest, it.invocation.args[1] as jakarta.servlet.ServletResponse)
        }
    }

    @Test
    @DisplayName("팀 생성 요청 시 Team ID를 반환한다")
    // [Essential] 비즈니스 요구사항 검증 (팀 생성)
    fun createTeamTest() {
        // given
        val company = Company(id = 1L, name = "Test Company")
        val manager = User(
            id = 2L, email = "manager@test.com", passwordHash = "hash", name = "Manager", company = company,
            roles = mutableSetOf(User.Role.USER, User.Role.MANAGER)
        )
        val team = Team(id = 100L, name = "New Team", company = company, leader = manager, status = Team.TeamStatus.ACTIVE)
        val request = TeamRequest("New Team", "Desc", "#FFF", listOf(2L), 2L)

        every { teamService.createTeam(any<User>(), any()) } returns team

        // when & then
        mockMvc.perform(
            post("/v1/teams")
                // .with(user(manager)): Spring Security 컨텍스트에 사용자 정보를 주입하여 @AuthenticationPrincipal이 동작하게 함
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(manager))
                .with(csrf()) // CSRF 실효성 검사 통과를 위해 필요
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(content().string("100"))

        verify(exactly = 1) { teamService.createTeam(manager, request) }
    }

    // Since addFilters=false ignores Method Security in some contexts, but Spring Method Security is a completely separate interceptor (MethodSecurityInterceptor), 
    // it IS normally evaluated unless disabled. However, if using mockMvc, @PreAuthorize usually requires valid SecurityContextHolder.
    // By passing .with(authentication(auth)), mockMvc populates SecurityContext for the duration of the request.
    @Test
    @DisplayName("팀을 승인하면 HTTP 200 OK를 반환한다")
    fun approveTeamTest() {
        // given
        val company = Company(id = 1L, name = "Test Company")
        val manager = User(
            id = 2L, email = "manager@test.com", passwordHash = "hash", name = "Manager", company = company,
            roles = mutableSetOf(User.Role.USER, User.Role.MANAGER)
        )

        every { teamService.approveTeam(100L, any()) } returns Unit
        every { teamSecurity.belongsToSameCompany(100L, manager) } returns true

        // when & then
        mockMvc.perform(
            post("/v1/teams/100/approve")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(manager))
                .with(csrf())
        )
            .andExpect(status().isOk)

        verify(exactly = 1) { teamService.approveTeam(100L, manager) }
    }

    @Test
    @DisplayName("팀을 해체하면 HTTP 200 OK를 반환한다")
    fun dissolveTeamTest() {
        // given
        val company = Company(id = 1L, name = "Test Company")
        val manager = User(
            id = 2L, email = "manager@test.com", passwordHash = "hash", name = "Manager", company = company,
            roles = mutableSetOf(User.Role.USER, User.Role.MANAGER)
        )

        every { teamService.dissolveTeam(100L, any()) } returns Unit
        every { teamSecurity.isLeaderOf(100L, manager) } returns true

        // when & then
        mockMvc.perform(
            delete("/v1/teams/100")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(manager))
                .with(csrf())
        )
            .andExpect(status().isOk)

        verify(exactly = 1) { teamService.dissolveTeam(100L, manager) }
    }

    @Test
    @DisplayName("팀을 반려하면 HTTP 200 OK를 반환한다")
    fun rejectTeamTest() {
        // given
        val company = Company(id = 1L, name = "Test Company")
        val manager = User(
            id = 2L, email = "manager@test.com", passwordHash = "hash", name = "Manager", company = company,
            roles = mutableSetOf(User.Role.USER, User.Role.MANAGER)
        )

        every { teamService.rejectTeam(100L, any()) } returns Unit
        every { teamSecurity.belongsToSameCompany(100L, manager) } returns true

        // when & then
        mockMvc.perform(
            post("/v1/teams/100/reject")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(manager))
                .with(csrf())
        )
            .andExpect(status().isOk)

        verify(exactly = 1) { teamService.rejectTeam(100L, manager) }
    }

    @Test
    @DisplayName("팀장을 임명하면 HTTP 200 OK를 반환한다")
    fun assignLeaderTest() {
        // given
        val company = Company(id = 1L, name = "Test Company")
        val manager = User(
            id = 2L, email = "manager@test.com", passwordHash = "hash", name = "Manager", company = company,
            roles = mutableSetOf(User.Role.USER, User.Role.MANAGER)
        )

        every { teamService.assignLeader(100L, any(), 3L) } returns Unit
        every { teamSecurity.belongsToSameCompany(100L, manager) } returns true

        // when & then
        mockMvc.perform(
            post("/v1/teams/100/leader/3")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(manager))
                .with(csrf())
        )
            .andExpect(status().isOk)

        verify(exactly = 1) { teamService.assignLeader(100L, manager, 3L) }
    }

    @Test
    @DisplayName("팀장을 위임하면 HTTP 200 OK를 반환한다")
    fun delegateLeaderTest() {
        // given
        val company = Company(id = 1L, name = "Test Company")
        val leader = User(
            id = 3L, email = "leader@test.com", passwordHash = "hash", name = "Leader", company = company,
            roles = mutableSetOf(User.Role.USER)
        )

        every { teamService.delegateLeader(100L, any(), 4L) } returns Unit
        every { teamSecurity.isLeaderOf(100L, leader) } returns true

        // when & then
        mockMvc.perform(
            post("/v1/teams/100/delegate/4")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(leader))
                .with(csrf())
        )
            .andExpect(status().isOk)

        verify(exactly = 1) { teamService.delegateLeader(100L, leader, 4L) }
    }
}
