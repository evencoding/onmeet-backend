package com.onmeet.video.meeting.service.invitation;

import com.onmeet.common.exception.BusinessException;
import com.onmeet.common.exception.errorcode.VideoErrorCode;
import com.onmeet.video.infra.auth.AuthServiceClient;
import com.onmeet.video.meeting.dto.invitation.InvitationResponse;
import com.onmeet.video.meeting.entity.invitation.InvitationStatus;
import com.onmeet.video.meeting.entity.room.MeetingRoom;
import com.onmeet.video.meeting.entity.invitation.RoomInvitation;
import com.onmeet.video.meeting.repository.room.MeetingRoomRepository;
import com.onmeet.video.meeting.repository.invitation.RoomInvitationRepository;
import com.onmeet.video.meeting.event.NotificationEventPublisher;
import com.onmeet.common.dto.NotificationRequestDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomInvitationService {

    private final RoomInvitationRepository invitationRepository;
    private final MeetingRoomRepository roomRepository;
    private final AuthServiceClient authServiceClient;
    private final NotificationEventPublisher notificationEventPublisher;

    public RoomInvitationService(RoomInvitationRepository invitationRepository,
            MeetingRoomRepository roomRepository,
            AuthServiceClient authServiceClient,
            NotificationEventPublisher notificationEventPublisher) {
        this.invitationRepository = invitationRepository;
        this.roomRepository = roomRepository;
        this.authServiceClient = authServiceClient;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    @Transactional
    public InvitationResponse invite(Long roomId, Long inviteeUserId, Long inviterUserId) {
        MeetingRoom room = findRoom(roomId);

        if (room.isEnded()) {
            // TODO: [VIDEO][VideoErrorCode.INVITE_ROOM_ENDED] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.INVITE_ROOM_ENDED);
        }

        if (inviteeUserId.equals(inviterUserId)) {
            // TODO: [VIDEO][VideoErrorCode.SELF_INVITE_DENIED] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.SELF_INVITE_DENIED);
        }

        if (invitationRepository.existsByRoomIdAndInviteeUserIdAndStatus(
                roomId, inviteeUserId, InvitationStatus.PENDING)) {
            // TODO: [VIDEO][VideoErrorCode.INVITATION_ALREADY_PENDING] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.INVITATION_ALREADY_PENDING);
        }

        // Validate invitee user exists
        if (!authServiceClient.userExists(inviteeUserId)) {
            // TODO: [VIDEO][VideoErrorCode.INVITEE_NOT_FOUND] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.INVITEE_NOT_FOUND);
        }

        RoomInvitation invitation = new RoomInvitation(room, inviterUserId, inviteeUserId);
        InvitationResponse response = toResponse(invitationRepository.save(invitation));

        // 초대받은 사용자에게 초대 알림 (Kafka 비동기)
        notificationEventPublisher.publishNotification(
            new NotificationRequestDto(
                inviteeUserId, null, "MEETING_INVITATION", "회의 초대",
                room.getTitle() + " 회의에 초대되었습니다.",
                "/meeting/" + roomId, "MEETING", String.valueOf(roomId), inviterUserId
            )
        );

        return response;
    }

    @Transactional
    public List<InvitationResponse> inviteBulk(Long roomId, List<Long> inviteeUserIds, Long inviterUserId) {
        MeetingRoom room = findRoom(roomId);

        if (room.isEnded()) {
            // TODO: [VIDEO][VideoErrorCode.INVITE_ROOM_ENDED] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.INVITE_ROOM_ENDED);
        }

        // Validate all invitee users exist
        Map<Long, Boolean> userExistsMap = authServiceClient.batchUserExists(inviteeUserIds);
        List<Long> nonExistentUsers = inviteeUserIds.stream()
                .filter(userId -> !userExistsMap.getOrDefault(userId, false))
                .collect(Collectors.toList());

        if (!nonExistentUsers.isEmpty()) {
            // TODO: [VIDEO][VideoErrorCode.BULK_INVITE_USER_NOT_FOUND] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.BULK_INVITE_USER_NOT_FOUND);
        }

        List<InvitationResponse> results = new ArrayList<>();
        for (Long inviteeUserId : inviteeUserIds) {
            if (inviteeUserId.equals(inviterUserId)) {
                continue;
            }
            if (invitationRepository.existsByRoomIdAndInviteeUserIdAndStatus(
                    roomId, inviteeUserId, InvitationStatus.PENDING)) {
                continue;
            }
            RoomInvitation invitation = new RoomInvitation(room, inviterUserId, inviteeUserId);
            results.add(toResponse(invitationRepository.save(invitation)));
        }

        // 초대받은 사용자들에게 초대 알림 일괄 발송 (벌크)
        List<Long> inviteeIds = results.stream()
                .map(InvitationResponse::inviteeUserId)
                .collect(Collectors.toList());

        if (!inviteeIds.isEmpty()) {
            notificationEventPublisher.publishNotification(
                new NotificationRequestDto(
                    null, inviteeIds, "MEETING_INVITATION", "회의 초대",
                    room.getTitle() + " 회의에 초대되었습니다.",
                    "/meeting/" + roomId, "MEETING", String.valueOf(roomId), inviterUserId
                )
            );
        }

        return results;
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> listInvitations(Long roomId) {
        findRoom(roomId);
        return invitationRepository.findByRoomId(roomId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public InvitationResponse accept(Long invitationId, Long userId) {
        RoomInvitation invitation = findInvitation(invitationId);
        validateInvitee(invitation, userId);

        if (!invitation.isPending()) {
            // TODO: [VIDEO][VideoErrorCode.INVITATION_ALREADY_PROCESSED] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.INVITATION_ALREADY_PROCESSED);
        }

        invitation.accept();

        // 호스트에게 초대 수락 알림
        notificationEventPublisher.publishNotification(
            new NotificationRequestDto(
                invitation.getInviterUserId(), null, "INVITATION_ACCEPTED",
                "초대 수락",
                "사용자가 회의 초대를 수락했습니다.",
                "/meeting/" + invitation.getRoom().getId(),
                "MEETING", String.valueOf(invitation.getRoom().getId()), userId
            )
        );

        return toResponse(invitation);
    }

    @Transactional
    public InvitationResponse decline(Long invitationId, Long userId) {
        RoomInvitation invitation = findInvitation(invitationId);
        validateInvitee(invitation, userId);

        if (!invitation.isPending()) {
            // TODO: [VIDEO][VideoErrorCode.INVITATION_ALREADY_PROCESSED] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.INVITATION_ALREADY_PROCESSED);
        }

        invitation.decline();

        // 호스트에게 초대 거절 알림 (Kafka 비동기)
        notificationEventPublisher.publishNotification(
            new NotificationRequestDto(
                invitation.getInviterUserId(), null, "INVITATION_DECLINED", "초대 거절",
                "사용자가 회의 초대를 거절했습니다.",
                "/meeting/" + invitation.getRoom().getId(),
                "MEETING", String.valueOf(invitation.getRoom().getId()), userId
            )
        );

        return toResponse(invitation);
    }

    @Transactional
    public void cancelInvitation(Long roomId, Long inviteeUserId, Long requesterId) {
        MeetingRoom room = findRoom(roomId);

        if (!room.isHost(requesterId)) {
            // TODO: [VIDEO][VideoErrorCode.HOST_ONLY] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.HOST_ONLY);
        }

        RoomInvitation invitation = invitationRepository.findByRoomIdAndInviteeUserId(roomId, inviteeUserId)
                .orElseThrow(() -> new BusinessException(VideoErrorCode.INVITATION_NOT_FOUND));

        if (!invitation.isPending()) {
            // TODO: [VIDEO][VideoErrorCode.INVITATION_ALREADY_PROCESSED] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.INVITATION_ALREADY_PROCESSED);
        }

        invitation.cancel();

        // 초대 취소된 사용자에게 취소 알림
        notificationEventPublisher.publishNotification(
            new NotificationRequestDto(
                inviteeUserId, null, "INVITATION_CANCELLED",
                "초대 취소",
                "회의 초대가 취소되었습니다.",
                "/meeting/" + roomId,
                "MEETING", String.valueOf(roomId), requesterId
            )
        );
    }

    private MeetingRoom findRoom(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(VideoErrorCode.ROOM_NOT_FOUND));
    }

    private RoomInvitation findInvitation(Long invitationId) {
        return invitationRepository.findById(invitationId)
                .orElseThrow(() -> new BusinessException(VideoErrorCode.INVITATION_NOT_FOUND));
    }

    private void validateInvitee(RoomInvitation invitation, Long userId) {
        if (!invitation.getInviteeUserId().equals(userId)) {
            // TODO: [VIDEO][VideoErrorCode.NOT_INVITEE] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.NOT_INVITEE);
        }
    }

    private InvitationResponse toResponse(RoomInvitation invitation) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getRoom().getId(),
                invitation.getInviterUserId(),
                invitation.getInviteeUserId(),
                invitation.getStatus(),
                invitation.getCreatedAt());
    }
}
