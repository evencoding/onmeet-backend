package com.onmeet.auth.service

import com.onmeet.auth.dto.GuestInviteRequestDto
import com.onmeet.auth.dto.GuestJoinResultDto
import com.onmeet.auth.entity.GuestInvitation
import com.onmeet.auth.exception.InvalidInvitationException
import com.onmeet.auth.exception.UserNotFoundException
import com.onmeet.auth.repository.jpa.GuestInvitationRepository
import com.onmeet.auth.repository.jpa.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class GuestService(
    private val guestInvitationRepository: GuestInvitationRepository,
    private val userRepository: UserRepository,
    private val emailService: EmailService,
    private val tokenService: TokenService
) {

    @Transactional
    fun inviteGuest(request: GuestInviteRequestDto, inviterEmail: String) {
        val user = userRepository.findByEmail(inviterEmail)
            .orElseThrow { UserNotFoundException("User not found") }

        val uuid = UUID.randomUUID().toString()
        val expiresAt = LocalDateTime.now().plusDays(1) // 1 day expiration

        val invitation = GuestInvitation(
            uuid = uuid,
            guestEmail = request.guestEmail,
            roomId = request.roomId,
            hostName = user.name,
            roomName = request.roomName,
            expiresAt = expiresAt
        )

        guestInvitationRepository.save(invitation)

        emailService.sendGuestInvitationEmail(
            to = request.guestEmail,
            uuid = uuid,
            hostName = user.name,
            roomName = request.roomName
        )
    }

    @Transactional(readOnly = true)
    fun joinMeeting(uuid: String): GuestJoinResultDto {
        val invitation = guestInvitationRepository.findByUuid(uuid)
            .orElseThrow { InvalidInvitationException("Invalid or expired guest invitation link") }

        if (invitation.expiresAt.isBefore(LocalDateTime.now())) {
            throw InvalidInvitationException("This guest invitation link has expired")
        }

        // Issue guest tokens
        val guestName = invitation.guestEmail.substringBefore("@")
        val tokens = tokenService.issueGuestTokens(name = "Guest_$guestName", meetingId = invitation.roomId)

        return GuestJoinResultDto(
            roomId = invitation.roomId,
            guestName = "Guest_$guestName",
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken ?: ""
        )
    }
}
