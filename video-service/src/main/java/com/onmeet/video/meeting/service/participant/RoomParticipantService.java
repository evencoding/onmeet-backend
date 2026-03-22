package com.onmeet.video.meeting.service.participant;

import com.onmeet.common.exception.BusinessException;
import com.onmeet.common.exception.errorcode.VideoErrorCode;
import com.onmeet.video.common.util.ClockProvider;
import com.onmeet.video.infra.auth.AuthServiceClient;
import com.onmeet.video.infra.livekit.LiveKitClient;
import com.onmeet.video.infra.livekit.LiveKitClient.TokenGrants;
import com.onmeet.video.infra.livekit.LiveKitProperties;
import com.onmeet.video.meeting.service.waiting.WaitingRoomSseService;
import com.onmeet.video.meeting.dto.participant.ParticipantRoleUpdateRequest;
import com.onmeet.video.meeting.dto.participant.RoomParticipantResponse;
import com.onmeet.video.meeting.entity.room.MeetingRoom;
import com.onmeet.video.meeting.entity.participant.ParticipantRole;
import com.onmeet.video.meeting.entity.participant.ParticipantStatus;
import com.onmeet.video.meeting.entity.participant.RoomParticipant;
import com.onmeet.video.meeting.event.MeetingEventPublisher;
import com.onmeet.video.meeting.event.NotificationEventPublisher;
import com.onmeet.common.dto.NotificationRequestDto;
import com.onmeet.video.meeting.event.participant.ParticipantEvent;
import com.onmeet.video.meeting.repository.room.MeetingRoomRepository;
import com.onmeet.video.meeting.repository.participant.RoomParticipantRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomParticipantService {

    private final RoomParticipantRepository participantRepository;
    private final MeetingRoomRepository roomRepository;
    private final LiveKitClient liveKitClient;
    private final LiveKitProperties liveKitProperties;
    private final MeetingEventPublisher eventPublisher;
    private final ClockProvider clockProvider;
    private final AuthServiceClient authServiceClient;
    private final WaitingRoomSseService waitingRoomSseService;
    private final NotificationEventPublisher notificationEventPublisher;

    public RoomParticipantService(RoomParticipantRepository participantRepository,
            MeetingRoomRepository roomRepository,
            LiveKitClient liveKitClient,
            LiveKitProperties liveKitProperties,
            MeetingEventPublisher eventPublisher,
            ClockProvider clockProvider,
            AuthServiceClient authServiceClient,
            WaitingRoomSseService waitingRoomSseService,
            NotificationEventPublisher notificationEventPublisher) {
        this.participantRepository = participantRepository;
        this.roomRepository = roomRepository;
        this.liveKitClient = liveKitClient;
        this.liveKitProperties = liveKitProperties;
        this.eventPublisher = eventPublisher;
        this.clockProvider = clockProvider;
        this.authServiceClient = authServiceClient;
        this.waitingRoomSseService = waitingRoomSseService;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    @Transactional(readOnly = true)
    public List<RoomParticipantResponse> listCurrent(Long roomId) {
        findRoom(roomId);
        return participantRepository.findByRoomIdAndStatus(roomId, ParticipantStatus.JOINED).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // CHECK [video-담당자]: listHistory 반환 타입 List -> Page
    @Transactional(readOnly = true)
    public Page<RoomParticipantResponse> listHistory(Long roomId, Pageable pageable) {
        findRoom(roomId);
        return participantRepository.findByRoomId(roomId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public RoomParticipantResponse updateRole(Long roomId, Long targetUserId,
            ParticipantRoleUpdateRequest request, Long requesterId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, requesterId);

        RoomParticipant participant = participantRepository
                .findByRoomIdAndUserIdAndStatus(roomId, targetUserId, ParticipantStatus.JOINED)
                .orElseThrow(() -> new BusinessException(VideoErrorCode.PARTICIPANT_NOT_FOUND));

        if (participant.getRole() == ParticipantRole.HOST) {
            // TODO: [VIDEO][VideoErrorCode.HOST_ROLE_CHANGE_DENIED] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.HOST_ROLE_CHANGE_DENIED);
        }

        participant.updateRole(request.role());
        return toResponse(participant);
    }

    @Transactional
    public void kick(Long roomId, Long targetUserId, Long requesterId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, requesterId);

        if (room.isHost(targetUserId)) {
            // TODO: [VIDEO][VideoErrorCode.HOST_KICK_DENIED] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.HOST_KICK_DENIED);
        }

        RoomParticipant participant = participantRepository
                .findByRoomIdAndUserIdAndStatus(roomId, targetUserId, ParticipantStatus.JOINED)
                .orElseThrow(() -> new BusinessException(VideoErrorCode.PARTICIPANT_NOT_FOUND));

        Instant now = clockProvider.now();
        participant.kick(now);

        liveKitClient.removeParticipant(room.getLivekitRoomName(), String.valueOf(targetUserId));

        eventPublisher.publishParticipantLeft(
                new ParticipantEvent("PARTICIPANT_LEFT", roomId, targetUserId, now));
    }

    @Transactional
    public void mute(Long roomId, Long targetUserId, Long requesterId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, requesterId);

        if (!participantRepository.existsByRoomIdAndUserIdAndStatusIn(
                roomId, targetUserId, List.of(ParticipantStatus.JOINED))) {
            // TODO: [VIDEO][VideoErrorCode.PARTICIPANT_NOT_FOUND] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.PARTICIPANT_NOT_FOUND);
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
            // TODO: [VIDEO][VideoErrorCode.PARTICIPANT_NOT_FOUND] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.PARTICIPANT_NOT_FOUND);
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
                .orElseThrow(() -> new BusinessException(VideoErrorCode.WAITING_PARTICIPANT_NOT_FOUND));

        int currentCount = participantRepository.countActiveParticipants(roomId);
        if (currentCount >= room.getMaxParticipants()) {
            // TODO: [VIDEO][VideoErrorCode.ROOM_FULL] 에러메시지 검수 요청
            throw new BusinessException(VideoErrorCode.ROOM_FULL);
        }

        Instant now = clockProvider.now();
        participant.admit(now);

        String participantName;
        try {
            AuthServiceClient.UserInfo userInfo = authServiceClient.getUserInfo(targetUserId);
            participantName = userInfo != null ? userInfo.name() : "user-" + targetUserId;
        } catch (Exception e) {
            participantName = "user-" + targetUserId;
        }

        String token = liveKitClient.generateToken(
                room.getLivekitRoomName(),
                String.valueOf(targetUserId),
                participantName,
                TokenGrants.forParticipant());

        waitingRoomSseService.sendAdmittedEvent(roomId, targetUserId, token,
                liveKitProperties.getUrl(), room.getLivekitRoomName());

        eventPublisher.publishParticipantJoined(
                new ParticipantEvent("PARTICIPANT_JOINED", roomId, targetUserId, now));

        // 대기실 수락 알림 (Kafka 비동기)
        notificationEventPublisher.publishNotification(
            new NotificationRequestDto(
                targetUserId, null, "WAITING_ROOM_ADMITTED", "회의실 입장 수락",
                "'" + room.getTitle() + "' 회의실 입장이 수락되었습니다.",
                "/meeting/" + roomId, "MEETING", String.valueOf(roomId), requesterId,
                null, null
            )
        );
    }

    @Transactional
    public void rejectWaiting(Long roomId, Long targetUserId, Long requesterId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, requesterId);

        RoomParticipant participant = participantRepository
                .findByRoomIdAndUserIdAndStatus(roomId, targetUserId, ParticipantStatus.WAITING)
                .orElseThrow(() -> new BusinessException(VideoErrorCode.WAITING_PARTICIPANT_NOT_FOUND));

        Instant now = clockProvider.now();
        participant.kick(now);

        waitingRoomSseService.sendRejectedEvent(roomId, targetUserId);

        // 대기실 거절 알림 (Kafka 비동기)
        notificationEventPublisher.publishNotification(
            new NotificationRequestDto(
                targetUserId, null, "WAITING_ROOM_REJECTED", "회의실 입장 거절",
                "'" + room.getTitle() + "' 회의실 입장이 거절되었습니다.",
                null, "MEETING", String.valueOf(roomId), requesterId,
                null, null
            )
        );
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

        // Batch fetch user names for all waiting participants
        List<Long> userIds = waitingList.stream()
                .limit(available)
                .map(RoomParticipant::getUserId)
                .collect(Collectors.toList());

        Map<Long, String> userNameMap;
        try {
            List<AuthServiceClient.UserInfo> userInfos = authServiceClient.getBatchUserInfo(userIds);
            userNameMap = userInfos.stream()
                    .collect(Collectors.toMap(
                            AuthServiceClient.UserInfo::userId,
                            AuthServiceClient.UserInfo::name,
                            (a, b) -> a));
        } catch (Exception e) {
            userNameMap = Map.of();
        }

        for (RoomParticipant p : waitingList) {
            if (admitted >= available) {
                break;
            }
            p.admit(now);

            String participantName = userNameMap.getOrDefault(p.getUserId(), "user-" + p.getUserId());

            String token = liveKitClient.generateToken(
                    room.getLivekitRoomName(),
                    String.valueOf(p.getUserId()),
                    participantName,
                    TokenGrants.forParticipant());

            waitingRoomSseService.sendAdmittedEvent(roomId, p.getUserId(), token,
                    liveKitProperties.getUrl(), room.getLivekitRoomName());

            eventPublisher.publishParticipantJoined(
                    new ParticipantEvent("PARTICIPANT_JOINED", roomId, p.getUserId(), now));

            admitted++;
        }

        // 대기실 일괄 수락 알림 (벌크)
        if (!userIds.isEmpty()) {
            notificationEventPublisher.publishNotification(
                new NotificationRequestDto(
                    null, userIds, "WAITING_ROOM_ADMITTED", "회의실 입장 수락",
                    "'" + room.getTitle() + "' 회의실 입장이 수락되었습니다.",
                    "/meeting/" + roomId, "MEETING", String.valueOf(roomId), requesterId,
                    null, null
                )
            );
        }
    }

    private MeetingRoom findRoom(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(VideoErrorCode.ROOM_NOT_FOUND));
    }

    private void validateHostOrCoHost(Long roomId, MeetingRoom room, Long userId) {
        if (room.isHost(userId)) {
            return;
        }
        participantRepository.findByRoomIdAndUserIdAndStatus(roomId, userId, ParticipantStatus.JOINED)
                .filter(p -> p.getRole() == ParticipantRole.CO_HOST)
                .orElseThrow(() -> new BusinessException(VideoErrorCode.HOST_OR_COHOST_ONLY));
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
                p.getDeviceType());
    }
}
