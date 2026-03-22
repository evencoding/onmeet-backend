package com.onmeet.common.security

import com.onmeet.common.client.InternalSecurityClient
import com.onmeet.common.dto.SecurityCheckResponse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * RemoteTeamSecurity 단위 테스트.
 * InternalSecurityClient를 모킹하여 auth-service 호출 없이 검증한다.
 * Note: @Cacheable is bypassed (no Spring context) — each call hits the actual method.
 */
class RemoteTeamSecurityTest {

    private lateinit var securityClient: InternalSecurityClient
    private lateinit var remoteSecurity: RemoteTeamSecurity

    private val sharedSecret = "test-shared-secret"
    private val teamId = 10L
    private val userId = 42L

    @BeforeEach
    fun setUp() {
        securityClient = mock(InternalSecurityClient::class.java)
        remoteSecurity = RemoteTeamSecurity(securityClient, sharedSecret)
    }

    // ─── isMemberOf ───────────────────────────────────────────────────────

    @Test
    fun `isMemberOf returns true when auth service confirms membership`() {
        `when`(securityClient.checkMember(teamId, userId, sharedSecret))
            .thenReturn(SecurityCheckResponse(authorized = true))

        val result = remoteSecurity.isMemberOf(teamId, userId.toString())

        assertTrue(result)
        verify(securityClient).checkMember(teamId, userId, sharedSecret)
    }

    @Test
    fun `isMemberOf returns false when auth service denies membership`() {
        `when`(securityClient.checkMember(teamId, userId, sharedSecret))
            .thenReturn(SecurityCheckResponse(authorized = false, message = "Not a member"))

        val result = remoteSecurity.isMemberOf(teamId, userId.toString())

        assertFalse(result)
    }

    @Test
    fun `isMemberOf returns false when principal is null`() {
        val result = remoteSecurity.isMemberOf(teamId, null)

        assertFalse(result)
        verifyNoInteractions(securityClient)
    }

    @Test
    fun `isMemberOf returns false when principal is non-numeric string`() {
        val result = remoteSecurity.isMemberOf(teamId, "not-a-number")

        assertFalse(result)
        verifyNoInteractions(securityClient)
    }

    @Test
    fun `isMemberOf accepts Long principal`() {
        `when`(securityClient.checkMember(teamId, userId, sharedSecret))
            .thenReturn(SecurityCheckResponse(authorized = true))

        val result = remoteSecurity.isMemberOf(teamId, userId) // Long type

        assertTrue(result)
    }

    // ─── isLeaderOf ───────────────────────────────────────────────────────

    @Test
    fun `isLeaderOf returns true when auth service confirms leadership`() {
        `when`(securityClient.checkLeader(teamId, userId, sharedSecret))
            .thenReturn(SecurityCheckResponse(authorized = true))

        val result = remoteSecurity.isLeaderOf(teamId, userId.toString())

        assertTrue(result)
        verify(securityClient).checkLeader(teamId, userId, sharedSecret)
    }

    @Test
    fun `isLeaderOf returns false when not a leader`() {
        `when`(securityClient.checkLeader(teamId, userId, sharedSecret))
            .thenReturn(SecurityCheckResponse(authorized = false))

        val result = remoteSecurity.isLeaderOf(teamId, userId.toString())

        assertFalse(result)
    }

    @Test
    fun `isLeaderOf returns false when principal is null`() {
        val result = remoteSecurity.isLeaderOf(teamId, null)

        assertFalse(result)
        verifyNoInteractions(securityClient)
    }

    // ─── belongsToSameCompany ─────────────────────────────────────────────

    @Test
    fun `belongsToSameCompany returns true when same company`() {
        `when`(securityClient.checkCompany(teamId, userId, sharedSecret))
            .thenReturn(SecurityCheckResponse(authorized = true))

        val result = remoteSecurity.belongsToSameCompany(teamId, userId.toString())

        assertTrue(result)
        verify(securityClient).checkCompany(teamId, userId, sharedSecret)
    }

    @Test
    fun `belongsToSameCompany returns false when different company`() {
        `when`(securityClient.checkCompany(teamId, userId, sharedSecret))
            .thenReturn(SecurityCheckResponse(authorized = false, message = "Cross-company access"))

        val result = remoteSecurity.belongsToSameCompany(teamId, userId.toString())

        assertFalse(result)
    }

    @Test
    fun `belongsToSameCompany returns false when principal is null`() {
        val result = remoteSecurity.belongsToSameCompany(teamId, null)

        assertFalse(result)
        verifyNoInteractions(securityClient)
    }

    // ─── Service failure handling ─────────────────────────────────────────

    @Test
    fun `isMemberOf propagates exception from auth service`() {
        `when`(securityClient.checkMember(teamId, userId, sharedSecret))
            .thenThrow(RuntimeException("auth-service down"))

        assertThrows(RuntimeException::class.java) {
            remoteSecurity.isMemberOf(teamId, userId.toString())
        }
    }
}
