package com.onmeet.auth.service

import com.onmeet.auth.config.InvitationProperties
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
    private val tokenService: TokenService,
    private val invitationProperties: InvitationProperties,
    private val internalRoomClient: com.onmeet.common.client.InternalRoomClient,
    @org.springframework.beans.factory.annotation.Value("\${gateway.shared-secret}")
    private val gatewaySecret: String
) {

    companion object {
        // roomId는 영문자, 숫자, 하이픈, 언더스코어만 허용 (경로 탐색 공격 방지)
        private val ROOM_ID_PATTERN = Regex("^[a-zA-Z0-9_-]+$")
    }

    @Transactional
    fun inviteGuest(request: GuestInviteRequestDto, inviterEmail: String) {
        val user = userRepository.findByEmail(inviterEmail)
            .orElseThrow { UserNotFoundException("User not found") }

        // [Security] [IDOR 방어] video-service 연동을 통해 초대자가 해당 roomId의 호스트인지 검증
        val room = internalRoomClient.getRoomByCode(request.roomId, gatewaySecret)
        if (room.hostUserId != (user.id ?: 0L)) {
            throw com.onmeet.common.exception.InsufficientPermissionException("You do not have permission to invite guests to this room.")
        }

        val uuid = UUID.randomUUID().toString()
        val expiresAt = LocalDateTime.now().plusDays(invitationProperties.guestExpiryDays)

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
        // 이메일 로컬 파트 대신 UUID 앞 부분을 추가하여 고유성 강화
        val guestName = "Guest_${invitation.guestEmail.substringBefore("@")}_${uuid.take(6)}"
        val tokens = tokenService.issueGuestTokens(name = guestName, meetingId = invitation.roomId)

        return GuestJoinResultDto(
            roomId = invitation.roomId,
            guestName = guestName,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken
        )
    }
}
