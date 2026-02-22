package com.onmeet.auth.service

import com.onmeet.auth.config.TeamProperties
import com.onmeet.auth.dto.TeamRequest
import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.Team
import com.onmeet.auth.entity.TeamMember
import com.onmeet.auth.entity.User
import com.onmeet.auth.exception.CompanyMismatchException
import com.onmeet.auth.exception.TeamAlreadyExistsException
import com.onmeet.auth.repository.jpa.CompanyRepository
import com.onmeet.auth.repository.jpa.TeamMemberRepository
import com.onmeet.auth.repository.jpa.TeamRepository
import com.onmeet.auth.repository.jpa.UserRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class TeamServiceImplTest {

    // [Necessary Infrastructure] 비즈니스 로직 테스트를 위한 의존성 Mocking
    @MockK
    private lateinit var teamRepository: TeamRepository

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var teamMemberRepository: TeamMemberRepository

    @MockK
    private lateinit var companyRepository: CompanyRepository

    @MockK
    private lateinit var teamProperties: TeamProperties

    @InjectMockKs
    private lateinit var teamService: TeamServiceImpl

    private val company = Company(id = 1L, name = "Test Company")

    private val normalUser = User(
        id = 1L,
        email = "user@test.com",
        passwordHash = "hash",
        name = "User",
        company = company
    )

    private val managerUser = User(
        id = 2L,
        email = "manager@test.com",
        passwordHash = "hash",
        name = "Manager",
        company = company,
        roles = mutableSetOf(User.Role.USER, User.Role.MANAGER)
    )

    private val otherCompanyUser = User(
        id = 3L,
        email = "other@other.com",
        passwordHash = "hash",
        name = "Other",
        company = Company(id = 2L, name = "Other Company")
    )

    @Test
    @DisplayName("일반 사용자가 팀을 생성하면 PENDING_APPROVAL 상태가 된다")
    // [Essential] 팀 생성 정책 검증 (일반 사용자 승인 대기)
    fun createTeamByNormalUser() {
        // given
        val request = TeamRequest("New Team", "Description", "#FFF")
        val team = Team(name = request.name, description = request.description, color = request.color, company = company, leader = normalUser, status = Team.TeamStatus.PENDING_APPROVAL)
        
        every { teamRepository.findByNameAndCompanyId(request.name, company.id!!) } returns null
        every { teamRepository.save(any()) } returns team

        // when
        val result = teamService.createTeam(normalUser, request)

        // then
        assertEquals(Team.TeamStatus.PENDING_APPROVAL, result.status)
        assertEquals(normalUser, result.leader)
        verify(exactly = 1) { teamRepository.save(any()) }
    }

    @Test
    @DisplayName("매니저가 팀을 생성하면 ACTIVE 상태가 되며 팀원이 바로 매핑된다")
    // [Essential] 팀 생성 정책 검증 (매니저 즉시 활성화)
    fun createTeamByManager() {
        // given
        val request = TeamRequest("New Team", "Description", "#FFF", listOf(1L, 2L), 1L)
        val team = Team(name = request.name, description = request.description, color = request.color, company = company, leader = normalUser, status = Team.TeamStatus.ACTIVE)
        
        every { teamRepository.findByNameAndCompanyId(request.name, company.id!!) } returns null
        every { userRepository.findAllById(request.memberIds!!) } returns listOf(normalUser, managerUser)
        every { teamRepository.save(any()) } returns team
        every { teamMemberRepository.save(any()) } returnsArgument 0

        // when
        val result = teamService.createTeam(managerUser, request)

        // then
        assertEquals(Team.TeamStatus.ACTIVE, result.status)
        verify(exactly = 1) { teamRepository.save(any()) }
        verify(exactly = 2) { teamMemberRepository.save(any()) }
    }

    @Test
    @DisplayName("이미 존재하는 팀 이름으로 생성 시 예외 발생")
    fun createTeamDuplicatedError() {
        // given
        val request = TeamRequest("Existing Team", "Description", "#FFF")
        
        every { teamRepository.findByNameAndCompanyId(request.name, company.id!!) } returns Team(name = "Existing Team", company = company)

        // when / then
        assertThrows<TeamAlreadyExistsException> {
            teamService.createTeam(normalUser, request)
        }
    }

    @Test
    @DisplayName("다른 회사의 유저를 팀원으로 추가하려 하면 에러 발생 (매니저)")
    fun createTeamCompanyMismatchError() {
        // given
        val request = TeamRequest("New Team", "Description", "#FFF", listOf(1L, 3L), 1L)
        
        every { teamRepository.findByNameAndCompanyId(request.name, company.id!!) } returns null
        every { userRepository.findAllById(request.memberIds!!) } returns listOf(normalUser, otherCompanyUser)

        // when / then
        assertThrows<CompanyMismatchException> {
            teamService.createTeam(managerUser, request)
        }
    }

    @Test
    @DisplayName("팀을 승인하면 ACTIVE 상태가 되고, 팀장이 팀원으로 추가된다")
    // [Essential] 팀 승인 및 멤버 자동 추가 로직 검증
    fun approveTeamSuccess() {
        // given
        val team = Team(id = 100L, name = "Pending Team", company = company, leader = normalUser, status = Team.TeamStatus.PENDING_APPROVAL)
        
        every { teamRepository.findById(100L) } returns Optional.of(team)
        every { teamMemberRepository.save(any()) } returnsArgument 0
        every { teamRepository.save(any()) } returns team

        // when
        teamService.approveTeam(100L, managerUser)

        // then
        assertEquals(Team.TeamStatus.ACTIVE, team.status)
        verify(exactly = 1) { teamMemberRepository.save(match { it.user == normalUser && it.role == TeamMember.TeamRole.LEADER }) }
        verify(exactly = 1) { teamRepository.save(team) }
    }

    @Test
    @DisplayName("팀 생성을 거절하면 팀 엔티티가 삭제된다")
    fun rejectTeamSuccess() {
        val team = Team(id = 100L, name = "Pending Team", company = company, leader = normalUser, status = Team.TeamStatus.PENDING_APPROVAL)
        every { teamRepository.findById(100L) } returns Optional.of(team)
        every { teamRepository.delete(team) } returns Unit

        teamService.rejectTeam(100L, managerUser)

        verify(exactly = 1) { teamRepository.delete(team) }
    }

    @Test
    @DisplayName("팀장을 새로 할당하면 기존 팀장은 멤버가 되고 새 팀장에게 LEADER 권한이 부여된다")
    fun assignLeaderSuccess() {
        val team = Team(id = 100L, name = "Team A", company = company, leader = normalUser, status = Team.TeamStatus.ACTIVE)
        val oldLeaderMembership = TeamMember(id = 1L, user = normalUser, team = team, role = TeamMember.TeamRole.LEADER)
        val newLeaderMembership = TeamMember(id = 2L, user = managerUser, team = team, role = TeamMember.TeamRole.MEMBER)

        every { teamRepository.findById(100L) } returns Optional.of(team)
        every { userRepository.findById(managerUser.id!!) } returns Optional.of(managerUser)
        every { teamMemberRepository.findLeaderByTeamId(100L) } returns oldLeaderMembership
        every { teamMemberRepository.findByUserIdAndTeamId(managerUser.id!!, 100L) } returns newLeaderMembership
        every { teamMemberRepository.save(any()) } returnsArgument 0
        every { teamRepository.save(any()) } returns team

        teamService.assignLeader(100L, managerUser, managerUser.id!!)

        assertEquals(managerUser, team.leader)
        assertEquals(TeamMember.TeamRole.MEMBER, oldLeaderMembership.role)
        assertEquals(TeamMember.TeamRole.LEADER, newLeaderMembership.role)
        verify(exactly = 2) { teamMemberRepository.save(any()) }
    }

    @Test
    @DisplayName("팀장 권한을 다른 멤버에게 위임하면 위임자는 멤버가 되고 새 리더가 LEADER 권한을 얻는다")
    fun delegateLeaderSuccess() {
        val team = Team(id = 100L, name = "Team A", company = company, leader = normalUser, status = Team.TeamStatus.ACTIVE)
        val currentLeaderMembership = TeamMember(id = 1L, user = normalUser, team = team, role = TeamMember.TeamRole.LEADER)
        
        every { teamRepository.findById(100L) } returns Optional.of(team)
        every { userRepository.findById(managerUser.id!!) } returns Optional.of(managerUser)
        every { teamMemberRepository.findByUserIdAndTeamId(normalUser.id!!, 100L) } returns currentLeaderMembership
        every { teamMemberRepository.findByUserIdAndTeamId(managerUser.id!!, 100L) } returns null // 새 팀장은 아직 팀에 없다고 가정
        every { teamMemberRepository.save(any()) } returnsArgument 0
        every { teamRepository.save(any()) } returns team

        teamService.delegateLeader(100L, normalUser, managerUser.id!!)

        assertEquals(managerUser, team.leader)
        assertEquals(TeamMember.TeamRole.MEMBER, currentLeaderMembership.role)
        verify(exactly = 2) { teamMemberRepository.save(any()) }
    }

    @Test
    @DisplayName("팀을 삭제하면 팀 엔티티가 삭제된다")
    fun dissolveTeamSuccess() {
        val team = Team(id = 100L, name = "Team A", company = company, leader = normalUser, status = Team.TeamStatus.ACTIVE)
        
        every { teamRepository.findById(100L) } returns Optional.of(team)
        every { teamRepository.delete(team) } returns Unit

        teamService.dissolveTeam(100L, managerUser)

        verify(exactly = 1) { teamRepository.delete(team) }
    }
}
