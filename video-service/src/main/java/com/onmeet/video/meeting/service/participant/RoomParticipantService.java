package com.onmeet.video.meeting.service.participant;

import com.onmeet.video.common.exception.BizException;
import com.onmeet.video.common.exception.ErrorCode;
import com.onmeet.video.common.util.ClockProvider;
import com.onmeet.video.infra.livekit.LiveKitClient;
import com.onmeet.video.infra.livekit.LiveKitClient.TokenGrants;
import com.onmeet.video.meeting.dto.participant.ParticipantRoleUpdateRequest;
import com.onmeet.video.meeting.dto.participant.RoomParticipantResponse;
import com.onmeet.video.meeting.entity.room.MeetingRoom;
import com.onmeet.video.meeting.entity.participant.ParticipantRole;
import com.onmeet.video.meeting.entity.participant.ParticipantStatus;
import com.onmeet.video.meeting.entity.participant.RoomParticipant;
import com.onmeet.video.meeting.event.MeetingEventPublisher;
import com.onmeet.video.meeting.event.participant.ParticipantEvent;
import com.onmeet.video.meeting.repository.room.MeetingRoomRepository;
import com.onmeet.video.meeting.repository.participant.RoomParticipantRepository;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomParticipantService {

    private final RoomParticipantRepository participantRepository;
    private final MeetingRoomRepository roomRepository;
    private final LiveKitClient liveKitClient;
    private final MeetingEventPublisher eventPublisher;
    private final ClockProvider clockProvider;

    public RoomParticipantService(RoomParticipantRepository participantRepository,
                                  MeetingRoomRepository roomRepository,
                                  LiveKitClient liveKitClient,
                                  MeetingEventPublisher eventPublisher,
                                  ClockProvider clockProvider) {
        this.participantRepository = participantRepository;
        this.roomRepository = roomRepository;
        this.liveKitClient = liveKitClient;
        this.eventPublisher = eventPublisher;
        this.clockProvider = clockProvider;
    }

    @Transactional(readOnly = true)
    public List<RoomParticipantResponse> listCurrent(Long roomId) {
        findRoom(roomId);
        return participantRepository.findByRoomIdAndStatus(roomId, ParticipantStatus.JOINED).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RoomParticipantResponse> listHistory(Long roomId) {
        findRoom(roomId);
        return participantRepository.findByRoomId(roomId).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public RoomParticipantResponse updateRole(Long roomId, Long targetUserId,
                                              ParticipantRoleUpdateRequest request, Long requesterId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, requesterId);

        RoomParticipant participant = participantRepository
            .findByRoomIdAndUserIdAndStatus(roomId, targetUserId, ParticipantStatus.JOINED)
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Participant not found"));

        if (participant.getRole() == ParticipantRole.HOST) {
            throw new BizException(ErrorCode.FORBIDDEN, "Cannot change the host's role");
        }

        participant.updateRole(request.role());
        return toResponse(participant);
    }

    @Transactional
    public void kick(Long roomId, Long targetUserId, Long requesterId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, requesterId);

        if (room.isHost(targetUserId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Cannot kick the host");
        }

        RoomParticipant participant = participantRepository
            .findByRoomIdAndUserIdAndStatus(roomId, targetUserId, ParticipantStatus.JOINED)
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Participant not found"));

        Instant now = clockProvider.now();
        participant.kick(now);

        liveKitClient.removeParticipant(room.getLivekitRoomName(), String.valueOf(targetUserId));

        // TODO: [Notification Service] 강퇴된 참가자에게 강퇴 알림
        eventPublisher.publishParticipantLeft(
            new ParticipantEvent("PARTICIPANT_LEFT", roomId, targetUserId, now));
    }

    @Transactional
    public void mute(Long roomId, Long targetUserId, Long requesterId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, requesterId);

        if (!participantRepository.existsByRoomIdAndUserIdAndStatusIn(
            roomId, targetUserId, List.of(ParticipantStatus.JOINED))) {
            throw new BizException(ErrorCode.NOT_FOUND, "Participant not found");
        }

        liveKitClient.muteParticipantTrack(
            room.getLivekitRoomName(), String.valueOf(targetUserId), "audio", true);
    }

    @Transactional
    public void unmute(Long roomId, Long targetUserId, Long requesterId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, requesterId);

        if (!participantRepository.existsByRoomIdAndUserIdAndStatusIn(
            roomId, targetUserId, List.of(ParticipantStatus.JOINED))) {
            throw new BizException(ErrorCode.NOT_FOUND, "Participant not found");
        }

        liveKitClient.muteParticipantTrack(
            room.getLivekitRoomName(), String.valueOf(targetUserId), "audio", false);
    }

    @Transactional
    public void muteAll(Long roomId, Long requesterId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, requesterId);

        List<RoomParticipant> participants = participantRepository
            .findByRoomIdAndStatus(roomId, ParticipantStatus.JOINED);

        for (RoomParticipant p : participants) {
            if (!room.isHost(p.getUserId())) {
                liveKitClient.muteParticipantTrack(
                    room.getLivekitRoomName(), String.valueOf(p.getUserId()), "audio", true);
            }
        }
    }

    @Transactional
    public void unmuteAll(Long roomId, Long requesterId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, requesterId);

        List<RoomParticipant> participants = participantRepository
            .findByRoomIdAndStatus(roomId, ParticipantStatus.JOINED);

        for (RoomParticipant p : participants) {
            liveKitClient.muteParticipantTrack(
                room.getLivekitRoomName(), String.valueOf(p.getUserId()), "audio", false);
        }
    }

    @Transactional
    public void disableVideoAll(Long roomId, Long requesterId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, requesterId);

        List<RoomParticipant> participants = participantRepository
            .findByRoomIdAndStatus(roomId, ParticipantStatus.JOINED);

        for (RoomParticipant p : participants) {
            if (!room.isHost(p.getUserId())) {
                liveKitClient.muteParticipantTrack(
                    room.getLivekitRoomName(), String.valueOf(p.getUserId()), "video", true);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<RoomParticipantResponse> listWaiting(Long roomId) {
        findRoom(roomId);
        return participantRepository.findByRoomIdAndStatus(roomId, ParticipantStatus.WAITING).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public void admitWaiting(Long roomId, Long targetUserId, Long requesterId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, requesterId);

        RoomParticipant participant = participantRepository
            .findByRoomIdAndUserIdAndStatus(roomId, targetUserId, ParticipantStatus.WAITING)
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "No waiting participant found"));

        int currentCount = participantRepository.countActiveParticipants(roomId);
        if (currentCount >= room.getMaxParticipants()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Room is full");
        }

        Instant now = clockProvider.now();
        participant.admit(now);

        // TODO: [User Service] userId로 실제 사용자 이름 조회하여 participantName에 전달
        String token = liveKitClient.generateToken(
            room.getLivekitRoomName(),
            String.valueOf(targetUserId),
            "user-" + targetUserId,
            TokenGrants.forParticipant()
        );

        // TODO: [Notification Service] 대기실에서 승인된 참가자에게 입장 허용 알림
        eventPublisher.publishParticipantJoined(
            new ParticipantEvent("PARTICIPANT_JOINED", roomId, targetUserId, now));
    }

    @Transactional
    public void rejectWaiting(Long roomId, Long targetUserId, Long requesterId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, requesterId);

        RoomParticipant participant = participantRepository
            .findByRoomIdAndUserIdAndStatus(roomId, targetUserId, ParticipantStatus.WAITING)
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "No waiting participant found"));

        Instant now = clockProvider.now();
        participant.kick(now);
        // TODO: [Notification Service] 대기실에서 거절된 참가자에게 거절 알림
    }

    @Transactional
    public void admitAllWaiting(Long roomId, Long requesterId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, requesterId);

        List<RoomParticipant> waitingList = participantRepository
            .findByRoomIdAndStatus(roomId, ParticipantStatus.WAITING);

        int currentCount = participantRepository.countActiveParticipants(roomId);
        int available = room.getMaxParticipants() - currentCount;

        Instant now = clockProvider.now();
        int admitted = 0;

        for (RoomParticipant p : waitingList) {
            if (admitted >= available) {
                break;
            }
            p.admit(now);
            // TODO: [User Service] userId로 실제 사용자 이름 조회하여 participantName에 전달
            liveKitClient.generateToken(
                room.getLivekitRoomName(),
                String.valueOf(p.getUserId()),
                "user-" + p.getUserId(),
                TokenGrants.forParticipant()
            );
            eventPublisher.publishParticipantJoined(
                new ParticipantEvent("PARTICIPANT_JOINED", roomId, p.getUserId(), now));
            admitted++;
        }
    }

    private MeetingRoom findRoom(Long roomId) {
        return roomRepository.findById(roomId)
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Room not found"));
    }

    private void validateHostOrCoHost(Long roomId, MeetingRoom room, Long userId) {
        if (room.isHost(userId)) {
            return;
        }
        participantRepository.findByRoomIdAndUserIdAndStatus(roomId, userId, ParticipantStatus.JOINED)
            .filter(p -> p.getRole() == ParticipantRole.CO_HOST)
            .orElseThrow(() -> new BizException(ErrorCode.FORBIDDEN, "Only host or co-host can perform this action"));
    }

    private RoomParticipantResponse toResponse(RoomParticipant p) {
        return new RoomParticipantResponse(
            p.getId(),
            p.getRoom().getId(),
            p.getUserId(),
            p.getRole(),
            p.getStatus(),
            p.getJoinedAt(),
            p.getLeftAt(),
            p.getDurationSeconds(),
            p.getDeviceType()
        );
    }
}
