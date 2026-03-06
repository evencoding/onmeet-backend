package com.onmeet.auth.service

import com.onmeet.auth.dto.GuestInviteRequestDto
import com.onmeet.auth.dto.TokenResponse
import com.onmeet.auth.entity.GuestInvitation
import com.onmeet.auth.entity.User
import com.onmeet.auth.exception.InvalidInvitationException
import com.onmeet.auth.exception.UserNotFoundException
import com.onmeet.auth.repository.jpa.GuestInvitationRepository
import com.onmeet.auth.repository.jpa.UserRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockKExtension::class)
class GuestServiceTest {

    @MockK
    private lateinit var guestInvitationRepository: GuestInvitationRepository

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var emailService: EmailService

    @MockK
    private lateinit var tokenService: TokenService

    @InjectMockKs
    private lateinit var guestService: GuestService

    @Test
    fun `inviteGuest should save invitation and send email`() {
        // given
        val request = GuestInviteRequestDto("guest@example.com", "room123", "Test Room")
        val user = io.mockk.mockk<User>()
        every { user.name } returns "Host User"
        every { userRepository.findByEmail("host@example.com") } returns Optional.of(user)
        every { guestInvitationRepository.save(any()) } returns io.mockk.mockk()
        every { emailService.sendGuestInvitationEmail(any(), any(), any(), any()) } returns Unit

        // when
        guestService.inviteGuest(request, "host@example.com")

        // then
        verify { guestInvitationRepository.save(any()) }
        verify(exactly = 1) { emailService.sendGuestInvitationEmail("guest@example.com", any(), "Host User", "Test Room") }
    }

    @Test
    fun `inviteGuest should throw exception if inviter not found`() {
        val request = GuestInviteRequestDto("guest@example.com", "room123", "Test Room")
        every { userRepository.findByEmail("unknown@example.com") } returns Optional.empty()

        assertThrows<UserNotFoundException> {
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

        every { guestInvitationRepository.findByUuid(uuid) } returns Optional.of(invitation)
        every { tokenService.issueGuestTokens("Guest_guest", "room123") } returns tokenResponse

        // when
        val result = guestService.joinMeeting(uuid)

        // then
        assertEquals("room123", result.roomId)
        assertEquals("Guest_guest", result.guestName)
        assertEquals("access", result.accessToken)
        assertEquals("refresh", result.refreshToken)
    }

    @Test
    fun `joinMeeting should throw exception for expired uuid`() {
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
        assertThrows<InvalidInvitationException> {
            guestService.joinMeeting(uuid)
        }
    }

    @Test
    fun `joinMeeting should throw exception for invalid uuid`() {
        val uuid = "invalid-uuid"
        every { guestInvitationRepository.findByUuid(uuid) } returns Optional.empty()

        assertThrows<InvalidInvitationException> {
            guestService.joinMeeting(uuid)
        }
    }
}
