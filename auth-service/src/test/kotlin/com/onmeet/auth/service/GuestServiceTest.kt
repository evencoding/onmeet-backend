package com.onmeet.auth.service

import com.onmeet.auth.config.InvitationProperties
import com.onmeet.auth.dto.GuestInviteRequestDto
import com.onmeet.auth.dto.TokenResponse
import com.onmeet.auth.entity.GuestInvitation
import com.onmeet.auth.entity.User
import com.onmeet.common.client.InternalRoomClient
import com.onmeet.common.dto.RoomResponse
import com.onmeet.common.exception.BusinessException
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockKExtension::class)
class GuestServiceTest {

    @MockK
    private lateinit var guestInvitationRepository: com.onmeet.auth.repository.jpa.GuestInvitationRepository

    @MockK
    private lateinit var userRepository: com.onmeet.auth.repository.jpa.UserRepository

    @MockK
    private lateinit var emailService: EmailService

    @MockK
    private lateinit var tokenService: TokenService

    @MockK
    private lateinit var invitationProperties: InvitationProperties

    @MockK
    private lateinit var internalRoomClient: InternalRoomClient

    private lateinit var guestService: GuestService

    @BeforeEach
    fun setUp() {
        guestService = GuestService(
            guestInvitationRepository = guestInvitationRepository,
            userRepository = userRepository,
            emailService = emailService,
            tokenService = tokenService,
            invitationProperties = invitationProperties,
            internalRoomClient = internalRoomClient,
            gatewaySecret = "test-gateway-secret"
        )
    }

    @Test
    fun `inviteGuest should save invitation and send email when host is valid`() {
        // given
        val request = GuestInviteRequestDto("guest@example.com", "room123", "Test Room")
        val user = mockk<User>()
        every { user.name } returns "Host User"
        every { user.id } returns 1L
        every { userRepository.findByEmail("host@example.com") } returns Optional.of(user)
        every { invitationProperties.guestExpiryDays } returns 1L

        val roomResponse = mockk<RoomResponse>()
        every { roomResponse.hostUserId } returns 1L
        every { internalRoomClient.getRoomByCode("room123", "test-gateway-secret") } returns roomResponse

        every { guestInvitationRepository.save(any()) } returns mockk()
        every { emailService.sendGuestInvitationEmail(any(), any(), any(), any()) } returns Unit

        // when
        guestService.inviteGuest(request, "host@example.com")

        // then
        verify { guestInvitationRepository.save(any()) }
        verify(exactly = 1) { emailService.sendGuestInvitationEmail("guest@example.com", any(), "Host User", "Test Room") }
    }

    @Test
    fun `inviteGuest should throw BusinessException when user is not the host`() {
        // given
        val request = GuestInviteRequestDto("guest@example.com", "room123", "Test Room")
        val user = mockk<User>()
        every { user.name } returns "Not The Host"
        every { user.id } returns 99L  // different from hostUserId
        every { userRepository.findByEmail("nothost@example.com") } returns Optional.of(user)

        val roomResponse = mockk<RoomResponse>()
        every { roomResponse.hostUserId } returns 1L  // actual host is user 1
        every { internalRoomClient.getRoomByCode("room123", "test-gateway-secret") } returns roomResponse

        // when & then
        assertThrows<BusinessException> {
            guestService.inviteGuest(request, "nothost@example.com")
        }
        verify(exactly = 0) { guestInvitationRepository.save(any()) }
    }

    @Test
    fun `inviteGuest should throw BusinessException when inviter not found`() {
        // given
        val request = GuestInviteRequestDto("guest@example.com", "room123", "Test Room")
        every { userRepository.findByEmail("unknown@example.com") } returns Optional.empty()

        // when & then
        assertThrows<BusinessException> {
            guestService.inviteGuest(request, "unknown@example.com")
        }
    }

    @Test
    fun `joinMeeting should return tokens for valid uuid`() {
        // given
        val uuid = "valid-uuid"
        val invitation = GuestInvitation(
            uuid = uuid,
            guestEmail = "guest@example.com",
            roomId = "room123",
            hostName = "Host User",
            roomName = "Test Room",
            expiresAt = LocalDateTime.now().plusHours(1)
        )
        val tokenResponse = TokenResponse("access", "refresh")
        val expectedGuestName = "Guest_guest_${uuid.take(6)}"

        every { guestInvitationRepository.findByUuid(uuid) } returns Optional.of(invitation)
        every { tokenService.issueGuestTokens(expectedGuestName, "room123") } returns tokenResponse

        // when
        val result = guestService.joinMeeting(uuid)

        // then
        assertEquals("room123", result.roomId)
        assertEquals(expectedGuestName, result.guestName)
        assertEquals("access", result.accessToken)
        assertEquals("refresh", result.refreshToken)
    }

    @Test
    fun `joinMeeting should throw BusinessException for expired invitation`() {
        // given
        val uuid = "expired-uuid"
        val invitation = GuestInvitation(
            uuid = uuid,
            guestEmail = "guest@example.com",
            roomId = "room123",
            hostName = "Host User",
            roomName = "Test Room",
            expiresAt = LocalDateTime.now().minusHours(1)
        )

        every { guestInvitationRepository.findByUuid(uuid) } returns Optional.of(invitation)

        // when & then
        assertThrows<BusinessException> {
            guestService.joinMeeting(uuid)
        }
    }

    @Test
    fun `joinMeeting should throw BusinessException for invalid uuid`() {
        // given
        val uuid = "invalid-uuid"
        every { guestInvitationRepository.findByUuid(uuid) } returns Optional.empty()

        // when & then
        assertThrows<BusinessException> {
            guestService.joinMeeting(uuid)
        }
    }
}
