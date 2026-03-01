package com.onmeet.meeting.service;

import com.onmeet.common.exception.BizException;
import com.onmeet.common.exception.ErrorCode;
import com.onmeet.meeting.dto.InvitationResponse;
import com.onmeet.meeting.entity.InvitationStatus;
import com.onmeet.meeting.entity.MeetingRoom;
import com.onmeet.meeting.entity.RoomInvitation;
import com.onmeet.meeting.repository.MeetingRoomRepository;
import com.onmeet.meeting.repository.RoomInvitationRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomInvitationService {

    private final RoomInvitationRepository invitationRepository;
    private final MeetingRoomRepository roomRepository;

    public RoomInvitationService(RoomInvitationRepository invitationRepository,
                                 MeetingRoomRepository roomRepository) {
        this.invitationRepository = invitationRepository;
        this.roomRepository = roomRepository;
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

        // TODO: [User Service] inviteeUserId로 사용자 존재 여부 검증
        RoomInvitation invitation = new RoomInvitation(room, inviterUserId, inviteeUserId);
        // TODO: [Notification Service] 초대받은 사용자에게 초대 알림
        return toResponse(invitationRepository.save(invitation));
    }

    @Transactional
    public List<InvitationResponse> inviteBulk(Long roomId, List<Long> inviteeUserIds, Long inviterUserId) {
        MeetingRoom room = findRoom(roomId);

        if (room.isEnded()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Cannot invite to an ended room");
        }

        // TODO: [User Service] inviteeUserIds로 사용자 존재 여부 일괄 검증
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
        // TODO: [Notification Service] 초대받은 사용자들에게 초대 알림 일괄 발송
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
        // TODO: [Notification Service] 호스트에게 초대 수락 알림
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
        // TODO: [Notification Service] 호스트에게 초대 거절 알림
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
        // TODO: [Notification Service] 초대 취소된 사용자에게 취소 알림
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
            invitation.getCreatedAt()
        );
    }
}
