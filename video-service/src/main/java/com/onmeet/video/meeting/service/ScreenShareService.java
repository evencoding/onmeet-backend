package com.onmeet.video.meeting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onmeet.video.common.exception.BizException;
import com.onmeet.video.common.exception.ErrorCode;
import com.onmeet.video.common.util.ClockProvider;
import com.onmeet.video.infra.external.AuthServiceClient;
import com.onmeet.video.infra.external.LiveKitClient;
import com.onmeet.video.infra.external.LiveKitClient.DataPacketKind;
import com.onmeet.video.meeting.dto.DataChannelMessage;
import com.onmeet.video.meeting.dto.ScreenShareResponse;
import com.onmeet.video.meeting.entity.MeetingRoom;
import com.onmeet.video.meeting.entity.ParticipantRole;
import com.onmeet.video.meeting.entity.ParticipantStatus;
import com.onmeet.video.meeting.entity.RoomParticipant;
import com.onmeet.video.meeting.entity.RoomSettings;
import com.onmeet.video.meeting.event.MeetingEventPublisher;
import com.onmeet.video.meeting.event.ScreenShareEvent;
import com.onmeet.video.meeting.repository.MeetingRoomRepository;
import com.onmeet.video.meeting.repository.RoomParticipantRepository;
import com.onmeet.video.meeting.repository.RoomSettingsRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScreenShareService {

    private final MeetingRoomRepository roomRepository;
    private final RoomParticipantRepository participantRepository;
    private final RoomSettingsRepository settingsRepository;
    private final LiveKitClient liveKitClient;
    private final MeetingEventPublisher eventPublisher;
    private final ClockProvider clockProvider;
    private final ObjectMapper objectMapper;
    private final AuthServiceClient authServiceClient;

    public ScreenShareService(MeetingRoomRepository roomRepository,
                              RoomParticipantRepository participantRepository,
                              RoomSettingsRepository settingsRepository,
                              LiveKitClient liveKitClient,
                              MeetingEventPublisher eventPublisher,
                              ClockProvider clockProvider,
                              ObjectMapper objectMapper,
                              AuthServiceClient authServiceClient) {
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.settingsRepository = settingsRepository;
        this.liveKitClient = liveKitClient;
        this.eventPublisher = eventPublisher;
        this.clockProvider = clockProvider;
        this.objectMapper = objectMapper;
        this.authServiceClient = authServiceClient;
    }

    @Transactional
    public ScreenShareResponse startScreenShare(Long roomId, Long userId) {
        MeetingRoom room = findRoom(roomId);

        if (!room.isActive()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Room is not active");
        }

        RoomSettings settings = settingsRepository.findByRoomId(roomId).orElse(null);
        if (settings != null && !settings.isScreenShareAllowed()) {
            throw new BizException(ErrorCode.FORBIDDEN, "Screen sharing is not allowed in this room");
        }

        RoomParticipant participant = participantRepository
            .findByRoomIdAndUserIdAndStatus(roomId, userId, ParticipantStatus.JOINED)
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Not a joined participant of this room"));

        if (participant.isScreenSharing()) {
            throw new BizException(ErrorCode.CONFLICT, "Already sharing screen");
        }

        Instant now = clockProvider.now();
        participant.startScreenShare(now);

        publishDataChannelMessage(room, userId, DataChannelMessage.TYPE_SCREEN_SHARE_START);

        eventPublisher.publishScreenShareStarted(
            new ScreenShareEvent("SCREEN_SHARE_STARTED", roomId, userId, now));

        return new ScreenShareResponse(participant.getId(), userId, now);
    }

    @Transactional
    public void stopScreenShare(Long roomId, Long userId) {
        MeetingRoom room = findRoom(roomId);

        RoomParticipant participant = participantRepository
            .findByRoomIdAndUserIdAndStatus(roomId, userId, ParticipantStatus.JOINED)
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Not a joined participant of this room"));

        if (!participant.isScreenSharing()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Not currently sharing screen");
        }

        participant.stopScreenShare();

        publishDataChannelMessage(room, userId, DataChannelMessage.TYPE_SCREEN_SHARE_STOP);

        eventPublisher.publishScreenShareStopped(
            new ScreenShareEvent("SCREEN_SHARE_STOPPED", roomId, userId, clockProvider.now()));
    }

    @Transactional
    public void forceStopScreenShare(Long roomId, Long targetUserId, Long requesterId) {
        MeetingRoom room = findRoom(roomId);
        validateHostOrCoHost(roomId, room, requesterId);

        RoomParticipant target = participantRepository
            .findByRoomIdAndUserIdAndStatus(roomId, targetUserId, ParticipantStatus.JOINED)
            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "Target participant not found"));

        if (!target.isScreenSharing()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Target is not currently sharing screen");
        }

        target.stopScreenShare();

        liveKitClient.muteParticipantTrack(
            room.getLivekitRoomName(), String.valueOf(targetUserId), "screen_share", true);

        publishDataChannelMessage(room, targetUserId, DataChannelMessage.TYPE_SCREEN_SHARE_STOP);

        eventPublisher.publishScreenShareStopped(
            new ScreenShareEvent("SCREEN_SHARE_STOPPED", roomId, targetUserId, clockProvider.now()));
    }

    @Transactional(readOnly = true)
    public List<ScreenShareResponse> listActiveScreenShares(Long roomId) {
        findRoom(roomId);

        return participantRepository.findByRoomIdAndScreenSharingTrue(roomId).stream()
            .map(p -> new ScreenShareResponse(p.getId(), p.getUserId(), p.getScreenShareStartedAt()))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Long findRoomIdByLivekitName(String livekitRoomName) {
        return roomRepository.findByLivekitRoomName(livekitRoomName)
            .map(MeetingRoom::getId)
            .orElse(null);
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

    private void publishDataChannelMessage(MeetingRoom room, Long userId, String type) {
        String senderName;
        try {
            AuthServiceClient.UserInfo userInfo = authServiceClient.getUserInfo(userId);
            senderName = userInfo != null ? userInfo.name() : "user-" + userId;
        } catch (Exception e) {
            senderName = "user-" + userId;
        }

        DataChannelMessage message = new DataChannelMessage(
            UUID.randomUUID().toString(),
            type,
            userId,
            senderName,
            null,
            null,
            clockProvider.now()
        );

        try {
            byte[] payload = objectMapper.writeValueAsBytes(message);
            liveKitClient.publishData(room.getLivekitRoomName(), payload, DataPacketKind.RELIABLE);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Failed to serialize screen share message");
        }
    }
}
