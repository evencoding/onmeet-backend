package com.onmeet.video.meeting.service;

import com.onmeet.video.common.exception.BizException;
import com.onmeet.video.common.exception.ErrorCode;
import com.onmeet.video.infra.external.AuthServiceClient;
import com.onmeet.video.infra.external.NotificationServiceClient;
import com.onmeet.video.meeting.dto.InvitationResponse;
import com.onmeet.video.meeting.entity.InvitationStatus;
import com.onmeet.video.meeting.entity.MeetingRoom;
import com.onmeet.video.meeting.entity.RoomInvitation;
import com.onmeet.video.meeting.repository.MeetingRoomRepository;
import com.onmeet.video.meeting.repository.RoomInvitationRepository;
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
    private final NotificationServiceClient notificationClient;

    public RoomInvitationService(RoomInvitationRepository invitationRepository,
            MeetingRoomRepository roomRepository,
            AuthServiceClient authServiceClient,
            NotificationServiceClient notificationClient) {
        this.invitationRepository = invitationRepository;
        this.roomRepository = roomRepository;
        this.authServiceClient = authServiceClient;
        this.notificationClient = notificationClient;
    }

    @Transactional
    public InvitationResponse invite(Long roomId, Long inviteeUserId, Long inviterUserId) {
        MeetingRoom room = findRoom(roomId);

        if (room.isEnded()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Cannot invite to an ended room");
        }

        if (inviteeUserId.equals(inviterUserId)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Cannot invite yourself");
        }

        if (invitationRepository.existsByRoomIdAndInviteeUserIdAndStatus(
                roomId, inviteeUserId, InvitationStatus.PENDING)) {
            throw new BizException(ErrorCode.CONFLICT, "Invitation already pending for this user");
        }

        // Validate invitee user exists
        if (!authServiceClient.userExists(inviteeUserId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "Invitee user not found: " + inviteeUserId);
        }

        RoomInvitation invitation = new RoomInvitation(room, inviterUserId, inviteeUserId);
        InvitationResponse response = toResponse(invitationRepository.save(invitation));

        // TODO: [video-service] Kafka 알림 이벤트 추가 - MEETING_INVITATION
        // 초대받은 사용자에게 초대 알림 (기존 REST API 호출 로직은 추후 제거)
        notificationClient.sendNotification(
                inviteeUserId, "MEETING_INVITATION",
                "회의 초대",
                room.getTitle() + " 회의에 초대되었습니다.",
                "/meeting/" + roomId,
                inviterUserId, "MEETING", String.valueOf(roomId));

        return response;
    }

    @Transactional
    public List<InvitationResponse> inviteBulk(Long roomId, List<Long> inviteeUserIds, Long inviterUserId) {
        MeetingRoom room = findRoom(roomId);

        if (room.isEnded()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Cannot invite to an ended room");
        }

        // Validate all invitee users exist
        Map<Long, Boolean> userExistsMap = authServiceClient.batchUserExists(inviteeUserIds);
        List<Long> nonExistentUsers = inviteeUserIds.stream()
                .filter(userId -> !userExistsMap.getOrDefault(userId, false))
                .collect(Collectors.toList());

        if (!nonExistentUsers.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "Some users not found: " + nonExistentUsers);
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

        // 초대받은 사용자들에게 초대 알림 일괄 발송
        for (InvitationResponse result : results) {
            notificationClient.sendNotification(
                    result.inviteeUserId(), "MEETING_INVITATION",
                    "회의 초대",
                    room.getTitle() + " 회의에 초대되었습니다.",
                    "/meeting/" + roomId,
                    inviterUserId, "MEETING", String.valueOf(roomId));
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
            throw new BizException(ErrorCode.INVALID_REQUEST, "Invitation is no longer pending");
        }

        invitation.accept();

        // TODO: [video-service] Kafka 알림 이벤트 추가 - INVITATION_ACCEPTED
        // 호스트에게 초대 수락 알림
        notificationClient.sendNotification(
                invitation.getInviterUserId(), "INVITATION_ACCEPTED",
                "초대 수락",
                "사용자가 회의 초대를 수락했습니다.",
                "/meeting/" + invitation.getRoom().getId(),
                userId, "MEETING", String.valueOf(invitation.getRoom().getId()));

        return toResponse(invitation);
    }

    @Transactional
    public InvitationResponse decline(Long invitationId, Long userId) {
        RoomInvitation invitation = findInvitation(invitationId);
        validateInvitee(invitation, userId);

        if (!invitation.isPending()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Invitation is no longer pending");
        }

        invitation.decline();

        // TODO: [video-service] Kafka 알림 이벤트 추가 - INVITATION_DECLINED
        // 호스트에게 초대 거절 알림
        notificationClient.sendNotification(
                invitation.getInviterUserId(), "INVITATION_DECLINED",
                "초대 거절",
                "사용자가 회의 초대를 거절했습니다.",
                "/meeting/" + invitation.getRoom().getId(),
                userId, "MEETING", String.valueOf(invitation.getRoom().getId()));

        return toResponse(invitation);
    }

    @Transactional
    public void cancelInvitation(Long roomId, Long inviteeUserId, Long requesterId) {
        MeetingRoom room = findRoom(roomId);

        if (!room.isHost(requesterId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Only the host can cancel invitations");
        }

        RoomInvitation invitation = invitationRepository.findByRoomIdAndInviteeUserId(roomId, inviteeUserId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Invitation not found"));

        if (!invitation.isPending()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Invitation is no longer pending");
        }

        invitation.cancel();

        // TODO: [video-service] Kafka 알림 이벤트 추가 - INVITATION_CANCELLED // title 포함)
        // 초대 취소된 사용자에게 취소 알림
        notificationClient.sendNotification(
                inviteeUserId, "INVITATION_CANCELLED",
                "초대 취소",
                "회의 초대가 취소되었습니다.",
                "/meeting/" + roomId,
                requesterId, "MEETING", String.valueOf(roomId));
    }

    private MeetingRoom findRoom(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Room not found"));
    }

    private RoomInvitation findInvitation(Long invitationId) {
        return invitationRepository.findById(invitationId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Invitation not found"));
    }

    private void validateInvitee(RoomInvitation invitation, Long userId) {
        if (!invitation.getInviteeUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Only the invitee can perform this action");
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
