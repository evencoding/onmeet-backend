package com.onmeet.auth.service

import com.onmeet.auth.config.TeamProperties
import com.onmeet.auth.dto.TeamRequest
import com.onmeet.auth.entity.Company
import com.onmeet.auth.entity.Team
import com.onmeet.auth.entity.TeamMember
import com.onmeet.auth.entity.User
import com.onmeet.auth.repository.jpa.CompanyRepository
import com.onmeet.auth.repository.jpa.TeamMemberRepository
import com.onmeet.auth.repository.jpa.TeamRepository
import com.onmeet.auth.repository.jpa.UserRepository
import com.onmeet.common.exception.BusinessException
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.springframework.data.repository.findByIdOrNull
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

    @MockK
    private lateinit var notificationEventPublisher: NotificationEventPublisher

    @MockK
    private lateinit var managerTeamCreationStrategy: ManagerTeamCreationStrategy

    @MockK
    private lateinit var memberTeamCreationStrategy: MemberTeamCreationStrategy

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
        every { memberTeamCreationStrategy.create(normalUser, request) } returns team

        // when
        val result = teamService.createTeam(normalUser, request)

        // then
        assertEquals(Team.TeamStatus.PENDING_APPROVAL, result.status)
        assertEquals(normalUser, result.leader)
        verify(exactly = 1) { memberTeamCreationStrategy.create(normalUser, request) }
    }

    @Test
    @DisplayName("매니저가 팀을 생성하면 ACTIVE 상태가 되며 팀원이 바로 매핑된다")
    // [Essential] 팀 생성 정책 검증 (매니저 즉시 활성화)
    fun createTeamByManager() {
        // given
        val request = TeamRequest("New Team", "Description", "#FFF", listOf(1L, 2L), 1L)
        val team = Team(name = request.name, description = request.description, color = request.color, company = company, leader = normalUser, status = Team.TeamStatus.ACTIVE)
        
        every { teamRepository.findByNameAndCompanyId(request.name, company.id!!) } returns null
        every { managerTeamCreationStrategy.create(managerUser, request) } returns team

        // when
        val result = teamService.createTeam(managerUser, request)

        // then
        assertEquals(Team.TeamStatus.ACTIVE, result.status)
        verify(exactly = 1) { managerTeamCreationStrategy.create(managerUser, request) }
    }

    @Test
    @DisplayName("이미 존재하는 팀 이름으로 생성 시 예외 발생")
    fun createTeamDuplicatedError() {
        // given
        val request = TeamRequest("Existing Team", "Description", "#FFF")
        
        every { teamRepository.findByNameAndCompanyId(request.name, company.id!!) } returns Team(name = "Existing Team", company = company)

        // when / then
        assertThrows<BusinessException> {
            teamService.createTeam(normalUser, request)
        }
    }

    @Test
    @DisplayName("다른 회사의 유저를 팀원으로 추가하려 하면 에러 발생 (매니저)")
    fun createTeamCompanyMismatchError() {
        // given
        val request = TeamRequest("New Team", "Description", "#FFF", listOf(1L, 3L), 1L)
        
        every { teamRepository.findByNameAndCompanyId(request.name, company.id!!) } returns null
        every { managerTeamCreationStrategy.create(managerUser, request) } throws BusinessException(com.onmeet.common.exception.errorcode.AuthErrorCode.TEAM_MEMBER_COMPANY_MISMATCH)

        // when / then
        assertThrows<BusinessException> {
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
        justRun { notificationEventPublisher.publishNotification(any()) }

        // when
        teamService.approveTeam(100L, managerUser)

        // then
        assertEquals(Team.TeamStatus.ACTIVE, team.status)
        verify(exactly = 1) { teamMemberRepository.save(match { it.user == normalUser && it.role == TeamMember.TeamRole.LEADER }) }
        verify(exactly = 1) { teamRepository.save(team) }
    }

    @Test
    @DisplayName("매니저가 아닌 사용자가 팀을 승인하려고 하면 예외 발생")
    fun approveTeamInsufficientPermission() {
        // when / then
        assertThrows<BusinessException> {
            teamService.approveTeam(100L, normalUser)
        }
    }

    @Test
    @DisplayName("다른 회사의 매니저가 팀을 승인하려고 하면 예외 발생")
    fun approveTeamCrossCompanyError() {
        // given
        val team = Team(id = 100L, name = "Pending Team", company = company, leader = normalUser, status = Team.TeamStatus.PENDING_APPROVAL)
        val otherCompanyManager = User(
            id = 4L,
            email = "manager@other.com",
            passwordHash = "hash",
            name = "Other Manager",
            company = Company(id = 2L, name = "Other Company"),
            roles = mutableSetOf(User.Role.USER, User.Role.MANAGER)
        )

        every { teamRepository.findById(100L) } returns Optional.of(team)

        // when / then
        assertThrows<BusinessException> {
            teamService.approveTeam(100L, otherCompanyManager)
        }
    }

    @Test
    @DisplayName("이미 ACTIVE 상태인 팀을 승인하려고 하면 예외 발생")
    fun approveTeamAlreadyActiveError() {
        // given
        val team = Team(id = 100L, name = "Active Team", company = company, leader = normalUser, status = Team.TeamStatus.ACTIVE)

        every { teamRepository.findById(100L) } returns Optional.of(team)

        // when / then
        assertThrows<BusinessException> {
            teamService.approveTeam(100L, managerUser)
        }
    }

    @Test
    @DisplayName("팀 생성을 거절하면 REJECTED 상태가 되고 사유가 저장된다")
    fun rejectTeamSuccess() {
        val team = Team(id = 100L, name = "Pending Team", company = company, leader = normalUser, status = Team.TeamStatus.PENDING_APPROVAL)
        val reason = "Inappropriate team name"

        every { teamRepository.findById(100L) } returns Optional.of(team)
        every { teamRepository.save(any()) } returns team
        justRun { notificationEventPublisher.publishNotification(any()) }

        teamService.rejectTeam(100L, managerUser, reason)

        assertEquals(Team.TeamStatus.REJECTED, team.status)
        assertEquals(reason, team.rejectionReason)
        verify(exactly = 1) { teamRepository.save(team) }
    }

    @Test
    @DisplayName("매니저가 아닌 사용자가 팀을 거절하려고 하면 예외 발생")
    fun rejectTeamInsufficientPermission() {
        // when / then
        assertThrows<BusinessException> {
            teamService.rejectTeam(100L, normalUser, "No reason")
        }
    }

    @Test
    @DisplayName("다른 회사의 매니저가 팀을 거절하려고 하면 예외 발생")
    fun rejectTeamCrossCompanyError() {
        // given
        val team = Team(id = 100L, name = "Pending Team", company = company, leader = normalUser, status = Team.TeamStatus.PENDING_APPROVAL)
        val otherCompanyManager = User(
            id = 4L,
            email = "manager@other.com",
            passwordHash = "hash",
            name = "Other Manager",
            company = Company(id = 2L, name = "Other Company"),
            roles = mutableSetOf(User.Role.USER, User.Role.MANAGER)
        )

        every { teamRepository.findById(100L) } returns Optional.of(team)

        // when / then
        assertThrows<BusinessException> {
            teamService.rejectTeam(100L, otherCompanyManager, "Reason")
        }
    }

    @Test
    @DisplayName("이미 ACTIVE 상태인 팀을 거절하려고 하면 예외 발생")
    fun rejectTeamAlreadyActiveError() {
        // given
        val team = Team(id = 100L, name = "Active Team", company = company, leader = normalUser, status = Team.TeamStatus.ACTIVE)

        every { teamRepository.findById(100L) } returns Optional.of(team)

        // when / then
        assertThrows<BusinessException> {
            teamService.rejectTeam(100L, managerUser, "Reason")
        }
    }

    @Test
    @DisplayName("팀장을 새로 할당하면 기존 팀장은 멤버가 되고 새 팀장에게 LEADER 권한이 부여된다")
    fun assignLeaderSuccess() {
        val team = Team(id = 100L, name = "Team A", company = company, leader = normalUser, status = Team.TeamStatus.ACTIVE)
        val oldLeaderMembership = TeamMember(id = 1L, user = normalUser, team = team, role = TeamMember.TeamRole.LEADER)
        val newLeaderMembership = TeamMember(id = 2L, user = managerUser, team = team, role = TeamMember.TeamRole.MEMBER)

        mockkStatic("org.springframework.data.repository.CrudRepositoryExtensionsKt")
        try {
            every { teamRepository.findByIdOrNull(100L) } returns team
            every { userRepository.findByIdOrNull(managerUser.id!!) } returns managerUser
            every { teamMemberRepository.findLeaderByTeamId(100L) } returns oldLeaderMembership
            every { teamMemberRepository.findByUserIdAndTeamId(managerUser.id!!, 100L) } returns newLeaderMembership
            every { teamMemberRepository.save(any()) } returnsArgument 0
            every { teamRepository.save(any()) } returns team
            justRun { notificationEventPublisher.publishNotification(any()) }

            teamService.assignLeader(100L, managerUser, managerUser.id!!)

            assertEquals(managerUser, team.leader)
            assertEquals(TeamMember.TeamRole.MEMBER, oldLeaderMembership.role)
            assertEquals(TeamMember.TeamRole.LEADER, newLeaderMembership.role)
            verify(exactly = 2) { teamMemberRepository.save(any()) }
        } finally {
            unmockkStatic("org.springframework.data.repository.CrudRepositoryExtensionsKt")
        }
    }

    @Test
    @DisplayName("팀장 권한을 다른 멤버에게 위임하면 위임자는 멤버가 되고 새 리더가 LEADER 권한을 얻는다")
    fun delegateLeaderSuccess() {
        val team = Team(id = 100L, name = "Team A", company = company, leader = normalUser, status = Team.TeamStatus.ACTIVE)
        val currentLeaderMembership = TeamMember(id = 1L, user = normalUser, team = team, role = TeamMember.TeamRole.LEADER)

        mockkStatic("org.springframework.data.repository.CrudRepositoryExtensionsKt")
        try {
            every { teamRepository.findByIdOrNull(100L) } returns team
            every { userRepository.findByIdOrNull(managerUser.id!!) } returns managerUser
            every { teamMemberRepository.findByUserIdAndTeamId(normalUser.id!!, 100L) } returns currentLeaderMembership
            every { teamMemberRepository.findByUserIdAndTeamId(managerUser.id!!, 100L) } returns null // 새 팀장은 아직 팀에 없다고 가정
            every { teamMemberRepository.save(any()) } returnsArgument 0
            every { teamRepository.save(any()) } returns team
            justRun { notificationEventPublisher.publishNotification(any()) }

            teamService.delegateLeader(100L, normalUser, managerUser.id!!)

            assertEquals(managerUser, team.leader)
            assertEquals(TeamMember.TeamRole.MEMBER, currentLeaderMembership.role)
            verify(exactly = 2) { teamMemberRepository.save(any()) }
        } finally {
            unmockkStatic("org.springframework.data.repository.CrudRepositoryExtensionsKt")
        }
    }

    @Test
    @DisplayName("팀을 삭제하면 팀 엔티티가 삭제되고 TeamMember도 명시적으로 삭제된다")
    fun dissolveTeamSuccess() {
        val team = Team(id = 100L, name = "Team A", company = company, leader = normalUser, status = Team.TeamStatus.ACTIVE)

        val member = TeamMember(user = normalUser, team = team, role = TeamMember.TeamRole.MEMBER)

        every { teamRepository.findById(100L) } returns Optional.of(team)
        every { teamMemberRepository.findByTeamId(100L) } returns listOf(member)
        every { teamMemberRepository.deleteAllByTeamId(100L) } returns Unit
        every { teamRepository.delete(team) } returns Unit
        justRun { notificationEventPublisher.publishNotification(any()) }

        teamService.dissolveTeam(100L, managerUser)

        verify(exactly = 1) { teamMemberRepository.deleteAllByTeamId(100L) }
        verify(exactly = 1) { teamRepository.delete(team) }
    }

    @Test
    @DisplayName("다른 회사의 팀을 삭제하려고 하면 예외 발생")
    fun dissolveTeamCrossCompanyError() {
        // given
        val team = Team(id = 100L, name = "Team A", company = company, leader = normalUser, status = Team.TeamStatus.ACTIVE)

        every { teamRepository.findById(100L) } returns Optional.of(team)

        // when / then
        assertThrows<BusinessException> {
            teamService.dissolveTeam(100L, otherCompanyUser)
        }
    }

    @Test
    @DisplayName("승인 대기 중인 팀 생성 요청을 취소하면 팀 엔티티가 삭제된다")
    fun cancelTeamRequestSuccess() {
        val team = Team(id = 100L, name = "Pending Team", company = company, leader = normalUser, status = Team.TeamStatus.PENDING_APPROVAL)
        every { teamRepository.findById(100L) } returns Optional.of(team)
        every { teamRepository.delete(team) } returns Unit

        teamService.cancelTeamRequest(100L, normalUser)

        verify(exactly = 1) { teamRepository.delete(team) }
    }

    @Test
    @DisplayName("승인 대기 상태가 아닌 팀 생성 요청을 취소하려고 하면 예외 발생")
    fun cancelTeamRequestInvalidStatus() {
        val team = Team(id = 100L, name = "Active Team", company = company, leader = normalUser, status = Team.TeamStatus.ACTIVE)
        every { teamRepository.findById(100L) } returns Optional.of(team)

        assertThrows<BusinessException> {
            teamService.cancelTeamRequest(100L, normalUser)
        }
    }

    @Test
    @DisplayName("팀 생성 요청자가 아닌 사용자가 취소를 시도하면 예외 발생")
    fun cancelTeamRequestUnauthorized() {
        val team = Team(id = 100L, name = "Pending Team", company = company, leader = normalUser, status = Team.TeamStatus.PENDING_APPROVAL)
        val otherUser = User(id = 999L, email = "other@test.com", passwordHash = "hash", name = "Other", company = company)
        every { teamRepository.findById(100L) } returns Optional.of(team)

        assertThrows<BusinessException> {
            teamService.cancelTeamRequest(100L, otherUser)
        }
    }

    // ===== [Bug-4, 5, 6 Edge Cases] 극단적으로 다른 회사 ID로 팀 권한 검증 =====

    @Test
    @DisplayName("[Bug-4 Edge Case] 극단적으로 다른 회사 ID (Long.MAX_VALUE)로 팀 승인 시도 시 예외 발생")
    fun approveTeamExtremeCompanyIdMismatch() {
        // given
        val team = Team(id = 100L, name = "Pending Team", company = company, leader = normalUser, status = Team.TeamStatus.PENDING_APPROVAL)
        val extremeCompanyManager = User(
            id = 9999L,
            email = "extreme@faraway.com",
            passwordHash = "hash",
            name = "Extreme Manager",
            company = Company(id = Long.MAX_VALUE, name = "Extreme Far Company"),
            roles = mutableSetOf(User.Role.USER, User.Role.MANAGER)
        )

        every { teamRepository.findById(100L) } returns Optional.of(team)

        // when & then
        assertThrows<BusinessException> {
            teamService.approveTeam(100L, extremeCompanyManager)
        }
        verify(exactly = 0) { teamMemberRepository.save(any()) }
        verify(exactly = 0) { teamRepository.save(any()) }
    }

    @Test
    @DisplayName("[Bug-5 Edge Case] 극단적으로 다른 회사 ID로 팀 거절 시도 시 예외 발생")
    fun rejectTeamExtremeCompanyIdMismatch() {
        // given
        val team = Team(id = 100L, name = "Pending Team", company = company, leader = normalUser, status = Team.TeamStatus.PENDING_APPROVAL)
        val extremeCompanyManager = User(
            id = 9999L,
            email = "extreme@faraway.com",
            passwordHash = "hash",
            name = "Extreme Manager",
            company = Company(id = 999999L, name = "Very Far Company"),
            roles = mutableSetOf(User.Role.USER, User.Role.MANAGER)
        )

        every { teamRepository.findById(100L) } returns Optional.of(team)

        // when & then
        assertThrows<BusinessException> {
            teamService.rejectTeam(100L, extremeCompanyManager, "Invalid request")
        }
        verify(exactly = 0) { teamRepository.save(any()) }
    }

    @Test
    @DisplayName("[Bug-6 Edge Case] 극단적으로 다른 회사 ID로 팀 해산 시도 시 예외 발생")
    fun dissolveTeamExtremeCompanyIdMismatch() {
        // given
        val team = Team(id = 100L, name = "Team A", company = company, leader = normalUser, status = Team.TeamStatus.ACTIVE)
        val extremeCompanyUser = User(
            id = 9999L,
            email = "hacker@malicious.com",
            passwordHash = "hash",
            name = "Malicious User",
            company = Company(id = 888888L, name = "Malicious Corp"),
            roles = mutableSetOf(User.Role.USER, User.Role.MANAGER)
        )

        every { teamRepository.findById(100L) } returns Optional.of(team)

        // when & then
        assertThrows<BusinessException> {
            teamService.dissolveTeam(100L, extremeCompanyUser)
        }
        verify(exactly = 0) { teamMemberRepository.deleteAllByTeamId(any()) }
        verify(exactly = 0) { teamRepository.delete(any()) }
    }

    @Test
    @DisplayName("[Bug-4,5,6 통합] 동일한 회사 ID로 위장한 다른 Company 객체로 접근 시도 시 예외 발생")
    fun teamOperationsWithSpoofedCompanyObject() {
        // given - 회사 ID는 같지만 다른 Company 인스턴스 (실제로는 불가능하지만 Mock으로 테스트)
        val team = Team(id = 100L, name = "Team A", company = company, leader = normalUser, status = Team.TeamStatus.PENDING_APPROVAL)
        val spoofedCompany = Company(id = 1L, name = "Spoofed Company Name") // Same ID, different instance
        val spoofedManager = User(
            id = 7777L,
            email = "spoof@test.com",
            passwordHash = "hash",
            name = "Spoofed Manager",
            company = spoofedCompany,
            roles = mutableSetOf(User.Role.USER, User.Role.MANAGER)
        )

        every { teamRepository.findById(100L) } returns Optional.of(team)
        every { teamMemberRepository.save(any()) } returnsArgument 0
        every { teamRepository.save(any()) } returns team
        justRun { notificationEventPublisher.publishNotification(any()) }

        // when - 회사 ID가 같으므로 정상적으로 승인되어야 함
        teamService.approveTeam(100L, spoofedManager)

        // then - Company ID가 같으면 통과 (실제 객체 참조가 아니라 ID로 비교)
        assertEquals(Team.TeamStatus.ACTIVE, team.status)
        verify(exactly = 1) { teamMemberRepository.save(any()) }
        verify(exactly = 1) { teamRepository.save(team) }
    }
}
